package io.github.zeleven.mua;

import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

/**
 * An action class of editor.
 */
public class EditorAction {
    private Context context; // context instance
    private EditText editText; // edittext instance
    private UndoRedoHelper undoRedoHelper;

    public EditorAction() {
    }

    public EditorAction(Context context) {
        this.context = context;
    }

    public EditorAction(Context context, EditText editText) {
        this.context = context;
        this.editText = editText;
        this.undoRedoHelper = new UndoRedoHelper(editText);
    }

    /**
     * 插入标题
     * Insert markdown heading markup, if there was nothing input, insert "#" markup to 0 position.
     * Otherwise, insert the markup at position which after line break.
     */
    public void heading() {
        int start = editText.getSelectionStart();
        String textContent = editText.getText().toString();
        // 在当前光标向前查找最后一个换行符
        int lineBreakPos = textContent.lastIndexOf("\n", start);
        int insertPos;
        if (lineBreakPos == -1) {
            // 位于首行,将#插入行首
            insertPos = 0;
        } else {
            // 光标位于换行符前,将#插入下一行
            insertPos = lineBreakPos + 1;
        }
        editText.getText().insert(insertPos, "#");
        String afterInsert = editText.getText().toString().substring(insertPos + 1);
        // 在#号后添加空格
        if (!afterInsert.startsWith("#") && !afterInsert.startsWith(" ")) {
            editText.getText().insert(insertPos + 1, " ");
        }
        // 重新定位光标
        editText.setSelection(editText.getText().length());
    }

    /**
     * 添加加粗
     * Add markdown bold markup.
     * If there was select something, add markup beside the selected text.
     * Otherwise, just add the markup and set the cursor position at the middle of markup.
     */
    public void bold() {
        int start = editText.getSelectionStart();
        int end = editText.getSelectionEnd();
        if (start > 0) {
            String before = editText.getText().toString().substring(0, start);
            if (before.endsWith("*")) {
                // 插入前已经存在*,加入空格
                editText.getText().insert(start, " ");
                start++;
                end++;
            }
        }
        if (start == end) {
            editText.getText().insert(start, "****");
            editText.setSelection(start + 2);
        } else {
            // 光标选中字后添加加粗
            editText.getText().insert(start, "**");
            editText.getText().insert(end + 2, "**");
        }
    }

    /**
     * 添加斜体
     * Add markdown italic markup.
     * If there was select something, add markup beside the selected text.
     * Otherwise, just add the markup and set cursor position at the middle of markup.
     */
    public void italic() {
        // Add markdown italic markup
        int start = editText.getSelectionStart();
        int end = editText.getSelectionEnd();
        String before = editText.getText().toString().substring(0, start);
        if (before.endsWith("*")) {
            // 插入前已经存在*,加入空格
            editText.getText().insert(start, " ");
            start++;
            end++;
        }
        if (start == end) {
            editText.getText().insert(start, "**");
            editText.setSelection(start + 1);
        } else {
            // 光标选中字后添加斜体
            editText.getText().insert(start, "*");
            editText.getText().insert(end + 1, "*");
        }
    }

    /**
     * 添加代码块
     * Add markdown code markup.
     * If there was select something, add markup beside the selected text.
     * Otherwise, open a dialog to input programming language name,
     * and insert block code markup after input.
     */
    public void insertCode() {
        int start = editText.getSelectionStart();
        int end = editText.getSelectionEnd();
        if (start == end) { // Insert block code markup if there was nothing selected.
            AlertDialog.Builder blockCodeDialog = new AlertDialog.Builder(context);
            blockCodeDialog.setTitle(R.string.editor_dialog_title_insert_block_code);

            LayoutInflater inflater = ((AppCompatActivity) context).getLayoutInflater();
            final View view = inflater.inflate(R.layout.dialog_insert_code, null);
            blockCodeDialog.setView(view);

            blockCodeDialog.setNegativeButton(R.string.cancel,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });
            blockCodeDialog.setPositiveButton(R.string.dialog_btn_insert,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            EditText langNameET = view.findViewById(R.id.lang_name);
                            String langName = langNameET.getText().toString();
                            int end = editText.getSelectionEnd();
                            editText.getText().insert(end, "\n```" + langName + "\n\n```\n");
                            // 定位光标到代码开头处
                            editText.setSelection(end + 5 + langName.length());
                        }
                    });
            blockCodeDialog.show();
        } else { // Otherwise, insert inline code markup
            editText.getText().insert(start, "`");
            editText.getText().insert(end + 1, "`");
        }
    }

    /**
     * 添加引用
     * Add markdown block quote markup.
     */
    public void quote() {
        int start = editText.getSelectionStart();
        int end = editText.getSelectionEnd();
        if (start == end) {
            if (start == 0) {
                // 首行
                editText.getText().insert(start, "> ");
                editText.setSelection(start + 2);
            } else {
                // 非首行,另起一行
                editText.getText().insert(start, "\n> ");
                editText.setSelection(start + 3);
            }

        } else {
            // 光标选中字后添加引用
            editText.getText().insert(start, "\n> ");
            editText.setSelection(end + 3);
        }
    }

    /**
     * 添加有序列表
     * Add markdown ordered list markup.
     */
    public void orderedList() {
        int start = editText.getSelectionStart();
        editText.getText().insert(start, "\n1. ");
    }

    /**
     * 添加无序列表
     * Add markdown unordered list markup.
     */
    public void unorderedList() {
        int start = editText.getSelectionStart();
        editText.getText().insert(start, "\n* ");
    }

    /**
     * 添加链接
     * Insert markdown link markup.
     */
    public void insertLink() {
        AlertDialog.Builder linkDialog = new AlertDialog.Builder(context);
        linkDialog.setTitle(R.string.dialog_title_insert_link);
        LayoutInflater inflater = ((AppCompatActivity) context).getLayoutInflater();
        final View view = inflater.inflate(R.layout.dialog_insert_link, null);
        final EditText linkDisplayTextET = view.findViewById(R.id.link_display_text);
        final EditText linkContentET = view.findViewById(R.id.link_content);

        final int start = editText.getSelectionStart();
        final int end = editText.getSelectionEnd();
        if (start < end) {
            // 将选中的块作为标题
            String selectedContent = editText.getText().subSequence(start, end).toString();
            linkDisplayTextET.setText(selectedContent);
            linkContentET.requestFocus();
        }
        linkDialog.setView(view);
        linkDialog.setNegativeButton(R.string.cancel,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

        linkDialog.setPositiveButton(R.string.dialog_btn_insert,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String linkDisplayText = linkDisplayTextET.getText().toString();
                        String linkContent = linkContentET.getText().toString();
                        if (linkDisplayText.equals("")) {
                            linkDisplayText = "Link";
                        }
                        String content = "[" + linkDisplayText + "]" + "(" + linkContent + ")";
                        if (start == end) {
                            editText.getText().insert(start, content);
                        } else {
                            editText.getText().replace(start, end, content);
                        }
                        if (linkContent.equals("")) {
                            // 如果未填写链接,将光标定位到括号内
                            editText.setSelection(start + content.length() - 1);
                        }
                    }
                });
        linkDialog.show();
    }

    /**
     * 添加图片
     * Insert markdown image markup.
     *
     * @param displayText display text of image.
     * @param imageUri    the uri of image.
     */
    public void insertImage(String displayText, String imageUri) {
        int start = editText.getSelectionStart();
        String content = "\n\n![" + displayText + "](" + imageUri + ")\n\n";
        editText.getText().insert(start, content);
        if (imageUri.equals("")) {
            // 如果未填写链接,将光标定位到括号内
            editText.setSelection(start + content.length() - 3);
        }
    }

    /**
     * 撤销操作
     * Undo
     */
    public void undo() {
        if (undoRedoHelper != null) {
            undoRedoHelper.undo();
        }
    }

    /**
     * 恢复撤销
     * Redo
     */
    public void redo() {
        if (undoRedoHelper != null) {
            undoRedoHelper.redo();
        }
    }

    /**
     * Save file.
     */
//    public void save() {
//
//    }
    public void update(String filePath) {
        FileUtils.saveContent(new File(filePath), editText.getText().toString());
    }

    /**
     * Rename file.
     */
//    public String rename(String rootPath, String fileName) {
//
//    }

    /**
     * 清空
     * Clear text in component.
     */
    public void clearAll() {
        if (editText.getText().toString().equals("")) {
            Toast.makeText(context, R.string.toast_content_empty, Toast.LENGTH_SHORT).show();
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setMessage(R.string.dialog_message_clear_all);
            builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });
            builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    editText.setText("");
                    Toast.makeText(context, R.string.toast_message_cleared, Toast.LENGTH_SHORT).show();
                }
            });
            builder.show();
        }
    }

    /**
     * 打开帮助文档
     * Opening docs fragment
     */
    public void checkDocs() {
        ((AppCompatActivity) context).getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new HelpFragment())
                .addToBackStack(null)
                .commit();
    }

    /**
     * 统计字数
     * statistics dialog
     */
    public void statistics() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.dialog_title_statistics);

        LayoutInflater inflater = ((AppCompatActivity) context).getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_statistics, null);
        TextView textLengthTV = view.findViewById(R.id.text_length);
        textLengthTV.setText(editText.getText().length() + "");

        builder.setView(view);
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        builder.show();
    }

    /**
     * 切换键盘
     * Toggle keyboard according flag. If flag equals 0, hide or close keyboard. Otherwise, open.
     *
     * @param flag A flag which indicate it should open keyboard or not.
     */
    public void toggleKeyboard(int flag) {
        InputMethodManager inputMethodManager = (InputMethodManager)
                (context.getSystemService(Context.INPUT_METHOD_SERVICE));
        if (inputMethodManager != null) {
            if (flag == 0) {
                View currentFocus = ((AppCompatActivity) context).getCurrentFocus();
                if (currentFocus != null) {
                    inputMethodManager.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
                }
            } else {
                inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
            }
        }
    }
}
