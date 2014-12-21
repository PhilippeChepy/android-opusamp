package net.opusapp.player.ui.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TableLayout;

import net.opusapp.player.R;
import net.opusapp.player.core.service.providers.MediaManager;
import net.opusapp.player.core.service.providers.MediaMetadata;
import net.opusapp.player.ui.utils.PlayerApplication;
import net.opusapp.player.ui.views.CustomTextView;

import java.util.List;

public class MetadataDialog extends Dialog {

    private TableLayout mTableContainer;

    private LinearLayout mLayoutContainer;

    private boolean mIsReadOnly;

    private boolean mHasEditableContent;

    private Button mPositiveButton;

    private Button mNegativeButton;

    private ViewHolder[] mEditMapping;


    private MediaManager.Provider mProvider;

    private MediaManager.Provider.ContentType mContentType;

    private String mContentId;

    private List<MediaMetadata> mMetadataList;

    private OnEditDoneListener mEditDoneListener;



    public MetadataDialog(Context context, int title, MediaManager.Provider provider, MediaManager.Provider.ContentType contentType, String contentId) {
        super(context);

        final List<MediaMetadata> metadataList = provider.getMetadataList(contentType, contentId);

        setTitle(title);
        if (PlayerApplication.isTablet()) {
            setContentView(R.layout.dialog_metadata_editor);
            mTableContainer = (TableLayout) findViewById(R.id.table_container);
        }
        else {
            setContentView(R.layout.dialog_metadata_phone_editor);
            mLayoutContainer = (LinearLayout) findViewById(R.id.table_container);
        }

        mProvider = provider;
        mContentType = contentType;
        mContentId = contentId;
        mMetadataList = metadataList;

        mPositiveButton = (Button) findViewById(R.id.dialog_ok);
        mNegativeButton = (Button) findViewById(R.id.dialog_cancel);

        mEditMapping = null;

        initContent(metadataList);
        setReadOnly(true);
    }

    @Override
    public void show() {
        super.show();

        if (mIsReadOnly) {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        }
    }

    @Override
    public void dismiss() {
        super.dismiss();
        mEditMapping = null;
    }

    protected void initContent(final List<MediaMetadata> metadataList) {
        final LayoutInflater layoutInflater = getLayoutInflater();

        mEditMapping = new ViewHolder[metadataList.size()];

        for (int dataIndex = 0 ; dataIndex < metadataList.size() ; dataIndex++) {
            final MediaMetadata metadata = metadataList.get(dataIndex);

            if (PlayerApplication.isTablet()) {
                final View rootView = metadata.mEditable == MediaMetadata.EditType.TYPE_NUMERIC ?
                            layoutInflater.inflate(R.layout.view_metadata_integer_row, mTableContainer, false) :
                            layoutInflater.inflate(R.layout.view_metadata_string_row, mTableContainer, false);

                mEditMapping[dataIndex] = new ViewHolder(mTableContainer, rootView, metadata);
            }
            else {
                final View rootView = metadata.mEditable == MediaMetadata.EditType.TYPE_NUMERIC ?
                            layoutInflater.inflate(R.layout.view_metadata_phone_integer_row, mLayoutContainer, false) :
                            layoutInflater.inflate(R.layout.view_metadata_phone_string_row, mLayoutContainer, false);

                mEditMapping[dataIndex] = new ViewHolder(mLayoutContainer, rootView, metadata);
            }
        }
    }

    protected void setReadOnly(boolean readOnly) {
        mIsReadOnly = readOnly;
        mHasEditableContent = false;

        for (final ViewHolder viewHolder : mEditMapping) {
            viewHolder.updateEditableStatus();
        }

        if (readOnly) {
            mPositiveButton.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    dismiss();
                }
            });

            // Left button is "Edit"
            if (mHasEditableContent) {
                mNegativeButton.setVisibility(View.VISIBLE);
            }
            else {
                mNegativeButton.setVisibility(View.GONE);
            }

            mNegativeButton.setText(R.string.alert_dialog_button_edit);
            mNegativeButton.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    setReadOnly(false);
                }
            });
        }
        else {
            mPositiveButton.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    for (final ViewHolder viewHolder : mEditMapping) {
                        viewHolder.updateContentFromInputs();
                    }

                    mProvider.setMetadataList(mContentType, mContentId, mMetadataList);
                    if (mEditDoneListener != null) {
                        mEditDoneListener.onEditDone(MetadataDialog.this);
                    }
                    setReadOnly(true);
                }
            });

            // Left button is "Cancel"
            mNegativeButton.setVisibility(View.VISIBLE);
            mNegativeButton.setText(android.R.string.cancel);
            mNegativeButton.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    setReadOnly(true);

                    for (final ViewHolder viewHolder : mEditMapping) {
                        viewHolder.resetValue();
                    }
                }
            });

            for (ViewHolder holder : mEditMapping) {
                if (holder.mMetadata.mEditable != MediaMetadata.EditType.TYPE_READONLY) {
                    holder.valueView.setFocusableInTouchMode(true);
                    holder.valueView.requestFocus();

                    InputMethodManager inputMethodManager = (InputMethodManager) PlayerApplication.context.getSystemService(Context.INPUT_METHOD_SERVICE);
                    inputMethodManager.showSoftInput(holder.valueView, 0);
                    break;
                }
            }
        }
    }

    public void setOnEditDoneListener(OnEditDoneListener editDoneListener) {
        mEditDoneListener = editDoneListener;
    }

    public interface OnEditDoneListener {

        public void onEditDone(final MetadataDialog dialog);
    }

    class ViewHolder {

        private View mRootView;

        CustomTextView keyView;

        EditText valueView;

        private MediaMetadata mMetadata;

        ViewHolder(final ViewGroup container, final View rootView, final MediaMetadata metadata) {
            mMetadata = metadata;
            mRootView = rootView;

            keyView = (CustomTextView) rootView.findViewById(R.id.metadata_key);
            keyView.setText(metadata.mDescription);

            valueView = (EditText) rootView.findViewById(R.id.metadata_value);
            valueView.setText(metadata.mValue);

            container.addView(rootView);

            updateEditableStatus();
        }

        public void updateEditableStatus() {
            valueView.setEnabled(!mIsReadOnly && mMetadata.mEditable != MediaMetadata.EditType.TYPE_READONLY);

            if (mMetadata.mEditable != MediaMetadata.EditType.TYPE_READONLY) {
                mHasEditableContent = true;
            }

            if (!mIsReadOnly && mMetadata.mEditable == MediaMetadata.EditType.TYPE_READONLY) {
                mRootView.setVisibility(View.GONE);
            }
            else {
                mRootView.setVisibility(View.VISIBLE);
            }
        }

        public void updateContentFromInputs() {
            mMetadata.mValue = valueView.getText().toString();
        }

        public void resetValue() {
            valueView.setText(mMetadata.mValue);
        }
    }
}
