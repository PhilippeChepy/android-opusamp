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
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
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



    public static final int MENUITEM_DELETE = 0;



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

    protected SQLiteDatabase getWritableDatabase() {
        int index = PlayerApplication.getManagerIndex(providerId);

        final AbstractMediaManager.Provider provider = PlayerApplication.mediaManagers[index].getProvider();
        if (provider instanceof LocalProvider) {
            return ((LocalProvider) provider).getWritableDatabase();
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
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        menu.add(Menu.NONE, MENUITEM_DELETE, 1, R.string.menuitem_label_delete);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (!getUserVisibleHint()) {
            return false;
        }

        if (item.getItemId() == MENUITEM_DELETE) {
            final String selectionArgs[] = new String[] {
                    cursor.getString(COLUMN_ID)
            };

            SQLiteDatabase database = getWritableDatabase();
            database.beginTransaction();
            try {
                database.execSQL(
                        "UPDATE " + Entities.Album.TABLE_NAME + " SET " + Entities.Album.COLUMN_FIELD_ORIGINAL_ALBUM_ART_ID + " = 0 " +
                                "WHERE " + Entities.Album.COLUMN_FIELD_ORIGINAL_ALBUM_ART_ID + " = ?", selectionArgs);

                database.execSQL(
                        "UPDATE " + Entities.Media.TABLE_NAME + " SET " + Entities.Media.COLUMN_FIELD_ORIGINAL_ART_ID + " = 0 " +
                                "WHERE " + Entities.Media.COLUMN_FIELD_ORIGINAL_ART_ID + " = ?", selectionArgs);

                database.execSQL(
                        "UPDATE " + Entities.Album.TABLE_NAME + " " +
                                "SET " + Entities.Album.COLUMN_FIELD_ALBUM_ART_ID + " = " + Entities.Album.COLUMN_FIELD_ORIGINAL_ALBUM_ART_ID + " " +
                                "WHERE " + Entities.Album.COLUMN_FIELD_ALBUM_ART_ID + " = ?", selectionArgs);

                database.execSQL(
                        "UPDATE " + Entities.Media.TABLE_NAME + " " +
                                "SET " + Entities.Media.COLUMN_FIELD_ART_ID + " = " + Entities.Media.COLUMN_FIELD_ORIGINAL_ART_ID + " " +
                                "WHERE " + Entities.Media.COLUMN_FIELD_ART_ID + " = ?", selectionArgs);

                database.delete(Entities.Art.TABLE_NAME, Entities.Art._ID + " = ? ", selectionArgs);
                database.delete(Entities.AlbumHasArts.TABLE_NAME, Entities.AlbumHasArts.COLUMN_FIELD_ART_ID + " = ? ", selectionArgs);
                database.setTransactionSuccessful();

                AbstractMediaManager.Provider localProvider = PlayerApplication.mediaManagers[PlayerApplication.getManagerIndex(providerId)].getProvider();
                if (localProvider instanceof LocalProvider) {
                    ((LocalProvider) localProvider).notifyLibraryChanges();

                    Cursor artCursor = database.rawQuery(
                            "SELECT " + Entities.Art.TABLE_NAME + "." + Entities.Art._ID + ", " + Entities.Art.TABLE_NAME + "." + Entities.Art.COLUMN_FIELD_URI + " " +
                            "FROM " + Entities.Album.TABLE_NAME + " " +
                            "LEFT JOIN " + Entities.Art.TABLE_NAME + " ON " + Entities.Art.TABLE_NAME + "." + Entities.Art._ID + " = " + Entities.Album.TABLE_NAME + "." + Entities.Album.COLUMN_FIELD_ALBUM_ART_ID + " " +
                            "WHERE " + Entities.Album.TABLE_NAME + "." + Entities.Album._ID + " = ?",
                            new String[] { String.valueOf(sourceId)}
                    );

                    if (artCursor != null && artCursor.getCount() > 1) {
                        final Activity activity = getActivity();
                        final Intent resultIntent = activity.getIntent();

                        activity.setResult(Activity.RESULT_OK, resultIntent);
                    }
                }
            }
            catch (final Exception exception) {
                LogUtils.LOGException(TAG, "onContextItemSelected", 0, exception);
            }
            finally {
                database.endTransaction();
            }

            getLoaderManager().restartLoader(0, null, this);
        }

        return super.onContextItemSelected(item);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
        cursor.moveToPosition(position);
        final String selectedUri = cursor.getString(COLUMN_URI);

        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.getManagerIndex(providerId)];
        final AbstractMediaManager.Provider provider = mediaManager.getProvider();

        final Activity activity = getActivity();
        final Intent resultIntent = activity.getIntent();

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

            final String imageUri = cursor.getString(COLUMN_URI);
            final String imageName = imageUri.substring(imageUri.lastIndexOf("/") + 1);

            viewholder.lineOne.setText(imageName);

            PlayerApplication.thumbnailImageLoader.cancelDisplayTask(viewholder.image);
            viewholder.image.setImageResource(R.drawable.no_art_small);
            PlayerApplication.thumbnailImageLoader.displayImage(imageUri, viewholder.image);

            return view;
        }
    }
}
