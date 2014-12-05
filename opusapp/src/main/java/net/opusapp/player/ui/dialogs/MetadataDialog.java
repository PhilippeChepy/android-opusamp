package net.opusapp.player.ui.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TableLayout;

import net.opusapp.player.R;
import net.opusapp.player.core.service.providers.AbstractMediaManager;
import net.opusapp.player.core.service.providers.MediaMetadata;
import net.opusapp.player.ui.views.CustomTextView;

import java.util.List;

public class MetadataDialog extends Dialog {

    private TableLayout mTableContainer;

    private boolean mIsReadOnly;

    private boolean mHasEditableContent;

    private Button mPositiveButton;

    private Button mNegativeButton;

    private ViewHolder[] mEditMapping;


    private AbstractMediaManager.Provider mProvider;

    private AbstractMediaManager.Provider.ContentType mContentType;

    private String mContentId;

    private List<MediaMetadata> mMetadataList;



    public MetadataDialog(Context context, int title, AbstractMediaManager.Provider provider, AbstractMediaManager.Provider.ContentType contentType, String contentId) {
        super(context);

        final List<MediaMetadata> metadataList = provider.getMetadataList(contentType, contentId);

        setTitle(title);
        setContentView(R.layout.dialog_metadata_editor);

        mProvider = provider;
        mContentType = contentType;
        mContentId = contentId;
        mMetadataList = metadataList;

        mTableContainer = (TableLayout) findViewById(R.id.table_container);
        mPositiveButton = (Button) findViewById(R.id.dialog_ok);
        mNegativeButton = (Button) findViewById(R.id.dialog_cancel);

        mEditMapping = null;

        initContent(metadataList);
        setReadOnly(true);
    }

    @Override
    public void show() {
        super.show();

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
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

            final View rootView =
                    metadata.mEditable == MediaMetadata.EditType.TYPE_NUMERIC ?
                    layoutInflater.inflate(R.layout.view_metadata_integer_row, mTableContainer, false) :
                    layoutInflater.inflate(R.layout.view_metadata_string_row, mTableContainer, false);

            mEditMapping[dataIndex] = new ViewHolder(mTableContainer, rootView, metadata);
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
        }
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
