package net.opusapp.player.core.service.providers.local.ui.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;

import net.opusapp.player.R;
import net.opusapp.player.core.service.providers.AbstractMediaManager;
import net.opusapp.player.core.service.providers.local.LocalProvider;
import net.opusapp.player.core.service.providers.local.database.Entities;
import net.opusapp.player.core.service.utils.AbstractSimpleCursorLoader;
import net.opusapp.player.ui.adapter.holder.GridViewHolder;
import net.opusapp.player.ui.utils.PlayerApplication;
import net.opusapp.player.ui.utils.uil.ProviderImageDownloader;
import net.opusapp.player.ui.views.CustomTextView;
import net.opusapp.player.ui.views.RefreshableView;
import net.opusapp.player.utils.LogUtils;

public class ArtSelectionFragment extends Fragment implements RefreshableView, LoaderManager.LoaderCallbacks<Cursor>, AdapterView.OnItemClickListener {

    public static final String TAG = ArtSelectionFragment.class.getSimpleName();



    private GridView gridView;

    private ArtSelectionAdapter adapter;

    private int providerId;

    private long sourceId;

    private int contentType;



    private Cursor cursor;



    private static final int COLUMN_ID = 0;

    private static final int COLUMN_URI = 1;



    public final static int CONTENT_EMBEDDED_TAGS = 1;

    public final static int CONTENT_LOCAL_FILESYSTEM = 2;

    public final static int CONTENT_INTERNET = 3;

    public static final String CONTENT_TYPE_KEY = "type_key";



    protected SQLiteDatabase getReadableDatabase() {
        int index = PlayerApplication.getManagerIndex(providerId);

        final AbstractMediaManager.Provider provider = PlayerApplication.mediaManagers[index].getProvider();
        if (provider instanceof LocalProvider) {
            return ((LocalProvider) provider).getReadableDatabase();
        }

        return null;
    }



    @Override
    public void refresh() {
        if( gridView != null ) {
            getLoaderManager().restartLoader(0, null, this);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.view_list_gridview, container, false);
        if (rootView != null) {
            gridView = (GridView) rootView.findViewById(R.id.grid_view_base);
            gridView.setEmptyView(rootView.findViewById(R.id.grid_view_empty));

            final CustomTextView emptyDescription = (CustomTextView) rootView.findViewById(R.id.empty_description);
            emptyDescription.setText(R.string.ni_cover_arts);
        }

        return rootView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        adapter = new ArtSelectionAdapter(getActivity());
        gridView.setOnCreateContextMenuListener(this);
        gridView.setOnItemClickListener(this);
        gridView.setAdapter(adapter);
        gridView.setNumColumns(PlayerApplication.getListColumns() * 2);

        Bundle arguments = getArguments();
        if (arguments != null) {
            providerId = arguments.getInt(AbstractMediaManager.Provider.KEY_PROVIDER_ID);
            contentType = arguments.getInt(CONTENT_TYPE_KEY);
            sourceId = arguments.getLong(AbstractMediaManager.Provider.KEY_SOURCE_ID);
        }

        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle bundle) {
        final String[] projection = new String[] {
                Entities.Art.TABLE_NAME + "." + Entities.Art._ID,
                Entities.Art.TABLE_NAME + "." + Entities.Art.COLUMN_FIELD_URI
        };

        final String sortOrder = Entities.Art.COLUMN_FIELD_URI;

        switch (contentType) {
            case CONTENT_EMBEDDED_TAGS:
                return new AbstractSimpleCursorLoader(getActivity()) {
                    @Override
                    public Cursor loadInBackground() {
                        SQLiteDatabase database = getReadableDatabase();
                        if (database != null) {
                            final String table = Entities.Art.TABLE_NAME + " LEFT JOIN " + Entities.Media.TABLE_NAME + " ON " + Entities.Art.TABLE_NAME + "." + Entities.Art._ID + " = " + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_ORIGINAL_ART_ID;
                            final String selection = "(" + Entities.Art.COLUMN_FIELD_URI_IS_EMBEDDED + " = 1) AND (" + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_ALBUM_ID + " = ?)";
                            final String selectionArgs[] = new String[] {
                                    String.valueOf(sourceId)
                            };

                            return database.query(table, projection, selection, selectionArgs, null, null, sortOrder);
                        }
                        return null;
                    }
                };
            case CONTENT_LOCAL_FILESYSTEM:
                return new AbstractSimpleCursorLoader(getActivity()) {
                    @Override
                    public Cursor loadInBackground() {
                        SQLiteDatabase database = getReadableDatabase();
                        if (database != null) {
                            final String table = Entities.Art.TABLE_NAME + " LEFT JOIN " + Entities.AlbumHasArts.TABLE_NAME + " ON " + Entities.Art.TABLE_NAME + "." + Entities.Art._ID + " = " + Entities.AlbumHasArts.TABLE_NAME + "." + Entities.AlbumHasArts.COLUMN_FIELD_ART_ID;
                            final String selection = "(" + Entities.Art.COLUMN_FIELD_URI_IS_EMBEDDED + " <> 1) AND (" + Entities.AlbumHasArts.TABLE_NAME + "." + Entities.AlbumHasArts.COLUMN_FIELD_ALBUM_ID + " = ?)";
                            final String selectionArgs[] = new String[] {
                                    String.valueOf(sourceId)
                            };

                            return database.query(table, projection, selection, selectionArgs, null, null, sortOrder);
                        }
                        return null;
                    }
                };
        }
        return null;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (data == null) {
            return;
        }

        adapter.changeCursor(data);
        cursor = data;
        gridView.invalidateViews();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        if (adapter != null) {
            adapter.changeCursor(null);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
        cursor.moveToPosition(position);
        final String selectedUri = cursor.getString(COLUMN_URI);
        final int selectedId = cursor.getInt(COLUMN_ID);

        LogUtils.LOGE(TAG, "onItemClick selectedUri : " + selectedUri);
        LogUtils.LOGE(TAG, "onItemClick selectedId  : " + selectedId);

        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.getManagerIndex(providerId)];
        final AbstractMediaManager.Provider provider = mediaManager.getProvider();

        final Activity activity = getActivity();
        final Intent resultIntent = activity.getIntent();

        resultIntent.putExtra(AbstractMediaManager.Provider.KEY_SELECTED_ART_URI, selectedUri);
        resultIntent.putExtra(AbstractMediaManager.Provider.KEY_SELECTED_ART_ID, selectedId);

        final DialogInterface.OnClickListener artUpdateSongPositiveOnClickListener = new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                provider.setProperty(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM, String.valueOf(sourceId), AbstractMediaManager.Provider.ContentProperty.CONTENT_ART_URI, selectedUri, true);
                activity.setResult(Activity.RESULT_OK, resultIntent);
                activity.finish();
            }
        };

        final DialogInterface.OnClickListener artUpdateSongNegativeOnClickListener = new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                provider.setProperty(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM, String.valueOf(sourceId), AbstractMediaManager.Provider.ContentProperty.CONTENT_ART_URI, selectedUri, false);
                activity.setResult(Activity.RESULT_OK, resultIntent);
                activity.finish();
            }
        };

        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.alert_dialog_title_art_change_tracks)
                .setMessage(R.string.alert_dialog_message_art_change_tracks)
                .setPositiveButton(R.string.label_yes, artUpdateSongPositiveOnClickListener)
                .setNegativeButton(R.string.label_no, artUpdateSongNegativeOnClickListener)
                .show();
    }



    public class ArtSelectionAdapter extends SimpleCursorAdapter {

        public ArtSelectionAdapter(Context context) {
            super(context, R.layout.view_item_art_selection, null, new String[] {}, new int[] {}, 0);
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            final View view = super.getView(position, convertView, parent);

            Cursor cursor = (Cursor) getItem(position);
            final GridViewHolder viewholder;

            if (view != null) {
                viewholder = new GridViewHolder(view);
                view.setTag(viewholder);
            } else {
                viewholder = (GridViewHolder)convertView.getTag();
            }

            String imageName = cursor.getString(COLUMN_URI);
            imageName = imageName.substring(imageName.lastIndexOf("/") + 1);

            final String imageUri =
                    ProviderImageDownloader.SCHEME_URI_PREFIX +
                            ProviderImageDownloader.SUBTYPE_ART + "/" +
                            PlayerApplication.getManagerIndex(providerId) + "/" +
                            cursor.getString(COLUMN_ID);

            viewholder.lineOne.setText(imageName);
            PlayerApplication.thumbnailImageLoader.cancelDisplayTask(viewholder.image);
            viewholder.image.setImageResource(R.drawable.no_art_small);
            PlayerApplication.thumbnailImageLoader.displayImage(imageUri, viewholder.image);

            return view;
        }
    }
}
