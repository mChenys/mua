package io.github.zeleven.mua;

import android.content.SharedPreferences;
import android.text.Editable;
import android.text.Selection;
import android.text.TextWatcher;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.widget.TextView;

import java.util.LinkedList;

/**
 * 撤销操作帮助类
 */
public class UndoRedoHelper {
    /**
     * Is undo/redo being performed? This member signals if an undo/redo
     * operation is currently being performed. Changes in the text during
     * undo/redo are not recorded because it would mess up the undo history.
     */
    private boolean mIsUndoOrRedo = false;

    /**
     * The edit history.
     * 维护历史编辑记录
     */
    private EditHistory mEditHistory;

    /**
     * The change listener.
     */
    private EditTextChangeListener mChangeListener;

    /**
     * The edit text.
     */
    private TextView mTextView;

    // =================================================================== //

    /**
     * Create a new TextViewUndoRedo and attach it to the specified TextView.
     *
     * @param textView
     *            The text view for which the undo/redo is implemented.
     */
    public UndoRedoHelper(TextView textView) {
        mTextView = textView;
        mEditHistory = new EditHistory();
        mChangeListener = new EditTextChangeListener();
        mTextView.addTextChangedListener(mChangeListener);
    }

    // =================================================================== //

    /**
     * Disconnect this undo/redo from the text view.
     */
    public void disconnect() {
        mTextView.removeTextChangedListener(mChangeListener);
    }

    /**
     * Set the maximum history size. If size is negative, then history size is
     * only limited by the device memory.
     */
    public void setMaxHistorySize(int maxHistorySize) {
        mEditHistory.setMaxHistorySize(maxHistorySize);
    }

    /**
     * Clear history.
     */
    public void clearHistory() {
        mEditHistory.clear();
    }

    /**
     * Can undo be performed?
     */
    public boolean getCanUndo() {
        return (mEditHistory.mmPosition > 0);
    }

    /**
     * 撤销
     * Perform undo.
     */
    public void undo() {
        // 从历史记录中获取前一个编辑项
        EditItem edit = mEditHistory.getPrevious();
        if (edit == null) {
            return;
        }

        Editable text = mTextView.getEditableText();
        // 编辑项的开始位置
        int start = edit.mmStart;
        // 编辑项的结束位置
        int end = start + (edit.mmAfter != null ? edit.mmAfter.length() : 0);

        // 标记当前正在操作撤销,避免添加到历史记录栈中
        mIsUndoOrRedo = true;
        // 恢复之前的操作
        text.replace(start, end, edit.mmBefore);
        mIsUndoOrRedo = false;

        // This will get rid of underlines inserted when editor tries to come
        // up with a suggestion.
        for (Object o : text.getSpans(0, text.length(), UnderlineSpan.class)) {
            text.removeSpan(o);
        }
        // 重置光标位置到上次位置
        Selection.setSelection(text, edit.mmBefore == null ? start
                : (start + edit.mmBefore.length()));
    }

    /**
     * Can redo be performed?
     */
    public boolean getCanRedo() {
        return (mEditHistory.mmPosition < mEditHistory.mmHistory.size());
    }

    /**
     * 重做
     * Perform redo.
     */
    public void redo() {
        // 获取下一个编辑项操作恢复
        EditItem edit = mEditHistory.getNext();
        if (edit == null) {
            return;
        }

        Editable text = mTextView.getEditableText();
        int start = edit.mmStart;
        int end = start + (edit.mmBefore != null ? edit.mmBefore.length() : 0);

        mIsUndoOrRedo = true;
        // 添加重新编辑的内容
        text.replace(start, end, edit.mmAfter);
        mIsUndoOrRedo = false;

        // This will get rid of underlines inserted when editor tries to come
        // up with a suggestion.
        for (Object o : text.getSpans(0, text.length(), UnderlineSpan.class)) {
            text.removeSpan(o);
        }
        // 更新光标位置
        Selection.setSelection(text, edit.mmAfter == null ? start
                : (start + edit.mmAfter.length()));
    }

    /**
     * Store preferences.
     */
    public void storePersistentState(SharedPreferences.Editor editor, String prefix) {
        // Store hash code of text in the editor so that we can check if the
        // editor contents has changed.
        editor.putString(prefix + ".hash",
                String.valueOf(mTextView.getText().toString().hashCode()));
        editor.putInt(prefix + ".maxSize", mEditHistory.mmMaxHistorySize);
        editor.putInt(prefix + ".position", mEditHistory.mmPosition);
        editor.putInt(prefix + ".size", mEditHistory.mmHistory.size());

        int i = 0;
        for (EditItem ei : mEditHistory.mmHistory) {
            String pre = prefix + "." + i;

            editor.putInt(pre + ".start", ei.mmStart);
            editor.putString(pre + ".before", ei.mmBefore.toString());
            editor.putString(pre + ".after", ei.mmAfter.toString());

            i++;
        }
    }

    /**
     * Restore preferences.
     *
     * @param prefix
     *            The preference key prefix used when state was stored.
     * @return did restore succeed? If this is false, the undo history will be
     *         empty.
     */
    public boolean restorePersistentState(SharedPreferences sp, String prefix)
            throws IllegalStateException {

        boolean ok = doRestorePersistentState(sp, prefix);
        if (!ok) {
            mEditHistory.clear();
        }

        return ok;
    }

    private boolean doRestorePersistentState(SharedPreferences sp, String prefix) {

        String hash = sp.getString(prefix + ".hash", null);
        if (hash == null) {
            // No state to be restored.
            return true;
        }

        if (Integer.valueOf(hash) != mTextView.getText().toString().hashCode()) {
            return false;
        }

        mEditHistory.clear();
        mEditHistory.mmMaxHistorySize = sp.getInt(prefix + ".maxSize", -1);

        int count = sp.getInt(prefix + ".size", -1);
        if (count == -1) {
            return false;
        }

        for (int i = 0; i < count; i++) {
            String pre = prefix + "." + i;

            int start = sp.getInt(pre + ".start", -1);
            String before = sp.getString(pre + ".before", null);
            String after = sp.getString(pre + ".after", null);

            if (start == -1 || before == null || after == null) {
                return false;
            }
            mEditHistory.add(new EditItem(start, before, after));
        }

        mEditHistory.mmPosition = sp.getInt(prefix + ".position", -1);
        if (mEditHistory.mmPosition == -1) {
            return false;
        }

        return true;
    }

    // =================================================================== //

    /**
     * 历史编辑记录
     * Keeps track of all the edit history of a text.
     */
    private final class EditHistory {

        /**
         * 用来标记当调用getNext方法后需要被恢复的EditItem的position,如果没有调用getPrevious方法,那么position=mmHistory.size()
         * The position from which an EditItem will be retrieved when getNext()
         * is called. If getPrevious() has not been called, this has the same
         * value as mmHistory.size().
         */
        private int mmPosition = 0;

        /**
         * 最大历史集大小
         * Maximum undo history size.
         */
        private int mmMaxHistorySize = -1;

        /**
         * 历史编辑记录集合
         * The list of edits in chronological order.
         */
        private final LinkedList<EditItem> mmHistory = new LinkedList<EditItem>();

        /**
         * 清空历史集
         * Clear history.
         */
        private void clear() {
            mmPosition = 0;
            mmHistory.clear();
        }

        /**
         * 在当前position处添加EditItem,如果当前position之后已经有历史记录,那么可以直接删掉
         * Adds a new edit operation to the history at the current position. If
         * executed after a call to getPrevious() removes all the future history
         * (elements with positions >= current history position).
         */
        private void add(EditItem item) {
            while (mmHistory.size() > mmPosition) {
                mmHistory.removeLast();
            }
            mmHistory.add(item);
            mmPosition++;

            if (mmMaxHistorySize >= 0) {
                // 如果有设置最大历史记录,那么需要判断是否越界,越界则从最开头开始删除
                trimHistory();
            }
        }

        /**
         * 设置最大历史集大小
         * Set the maximum history size. If size is negative, then history size
         * is only limited by the device memory.
         */
        private void setMaxHistorySize(int maxHistorySize) {
            mmMaxHistorySize = maxHistorySize;
            if (mmMaxHistorySize >= 0) {
                trimHistory();
            }
        }

        /**
         * 针对有设置最大历史记录的情况,控制历史集的大小
         * Trim history when it exceeds max history size.
         */
        private void trimHistory() {
            while (mmHistory.size() > mmMaxHistorySize) {
                mmHistory.removeFirst();
                mmPosition--;
            }

            if (mmPosition < 0) {
                mmPosition = 0;
            }
        }

        /**
         * 从历史集中获取当前位置的前一条编辑记录
         * Traverses the history backward by one position, returns and item at
         * that position.
         */
        private EditItem getPrevious() {
            if (mmPosition == 0) {
                return null;
            }
            mmPosition--;
            return mmHistory.get(mmPosition);
        }

        /**
         * 从历史集中获取当前位置的下一条编辑记录
         * Traverses the history forward by one position, returns and item at
         * that position.
         */
        private EditItem getNext() {
            if (mmPosition >= mmHistory.size()) {
                return null;
            }

            EditItem item = mmHistory.get(mmPosition);
            // 取完下一条,当前位置++以便下一次撤销操作可以--
            mmPosition++;
            return item;
        }
    }

    /**
     * 单个编辑单元
     * Represents the changes performed by a single edit operation.
     */
    private final class EditItem {
        private final int mmStart; // 编辑后的开始位置
        private final CharSequence mmBefore; // 编辑前的内容
        private final CharSequence mmAfter; // 编辑后新增的内容

        /**
         * Constructs EditItem of a modification that was applied at position
         * start and replaced CharSequence before with CharSequence after.
         */
        public EditItem(int start, CharSequence before, CharSequence after) {
            mmStart = start;
            mmBefore = before;
            mmAfter = after;
        }
    }

    /**
     * Class that listens to changes in the text.
     */
    private final class EditTextChangeListener implements TextWatcher {

        /**
         * The text that will be removed by the change event.
         */
        private CharSequence mBeforeChange;

        /**
         * The text that was inserted by the change event.
         */
        private CharSequence mAfterChange;

        public void beforeTextChanged(CharSequence s, int start, int count,
                                      int after) {
            if (mIsUndoOrRedo) {
                // 来自撤销或者重做,不处理
                return;
            }

            mBeforeChange = s.subSequence(start, start + count);
            Log.e("cys", "mBeforeChange:"+mBeforeChange+" start:"+start+" count:"+count+" after:"+after+" s:"+s);
        }

        public void onTextChanged(CharSequence s, int start, int before,
                                  int count) {
            if (mIsUndoOrRedo) {
                // 来自撤销或者重做,不处理
                return;
            }

            mAfterChange = s.subSequence(start, start + count);
            mEditHistory.add(new EditItem(start, mBeforeChange, mAfterChange));

            Log.e("cys", ">>>mAfterChange:"+mAfterChange+" start:"+start+" count:"+count+" before:"+before+" s:"+s);
        }

        public void afterTextChanged(Editable s) {
        }
    }
}
