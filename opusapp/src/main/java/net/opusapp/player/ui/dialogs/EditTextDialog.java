package net.opusapp.player.ui.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.widget.Button;

import net.opusapp.player.R;
import net.opusapp.player.ui.views.CustomFilteringEditText;


public class EditTextDialog extends Dialog {

    private CustomFilteringEditText mCustomFilteringEditText;

    private Button mPositiveButton;

    private Button mNegativeButton;



    public EditTextDialog(Context context, int titleId) {
        super(context);
        setTitle(titleId);

        setContentView(R.layout.dialog_edit_text);

        mCustomFilteringEditText = (CustomFilteringEditText) findViewById(R.id.custom_edit_text);

        mPositiveButton = (Button) findViewById(R.id.dialog_ok);
        mPositiveButton.setVisibility(View.GONE);

        mNegativeButton = (Button) findViewById(R.id.dialog_cancel);
        mNegativeButton.setVisibility(View.GONE);
    }

    @Override
    public void show() {
        super.show();
    }

    @Override
    public void dismiss() {
        super.dismiss();
    }

    public void setText(String text) {
        mCustomFilteringEditText.setText(text);
    }

    public String getText() {
        return mCustomFilteringEditText.getText().toString();
    }

    public void setPositiveButtonRunnable(final ButtonClickListener clickListener) {
        if (clickListener != null) {
            mPositiveButton.setVisibility(View.VISIBLE);
            mPositiveButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    clickListener.click(EditTextDialog.this);
                    dismiss();
                }
            });
        }
        else {
            mPositiveButton.setVisibility(View.GONE);
        }
    }

    public void setNegativeButtonRunnable(final ButtonClickListener clickListener) {
        if (clickListener != null) {
            mNegativeButton.setVisibility(View.VISIBLE);
            mNegativeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    clickListener.click(EditTextDialog.this);
                    dismiss();
                }
            });
        }
        else {
            mNegativeButton.setVisibility(View.GONE);
        }
    }

    public interface ButtonClickListener {

        public void click(EditTextDialog dialog);
    }
}
