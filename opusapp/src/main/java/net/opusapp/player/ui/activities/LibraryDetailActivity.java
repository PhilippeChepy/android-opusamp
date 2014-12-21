package net.opusapp.player.ui.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.PopupMenu;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.mobeta.android.dslv.DragSortListView;

import net.opusapp.player.R;
import net.opusapp.player.core.service.providers.MediaManager;
import net.opusapp.player.ui.adapter.LibraryAdapter;
import net.opusapp.player.ui.adapter.LibraryAdapterFactory;
import net.opusapp.player.ui.utils.PlayerApplication;

public class LibraryDetailActivity extends AbstractPlayerActivity implements LoaderManager.LoaderCallbacks<Cursor> {

    public static final String TAG = LibraryDetailActivity.class.getSimpleName();




    private static final int CONTEXT_MENU_GROUP_ID = 1;

    private static final int CONTEXT_ART_GROUP_ID = 100;




    private static final int OPTION_MENUITEM_SORT = 1;



    /*
        ContentType management
     */
    private MediaManager.Provider.ContentType contentType;

    private String contentSourceId;

    private Cursor cursor;



    /*

     */
    private LibraryAdapter mAdapter;

    private ListView contentList;

    private DragSortListView dragableContentList;



    private static final int COLUMN_SONG_ID = 0;

    private static final int COLUMN_SONG_TITLE = 1;

    private static final int COLUMN_SONG_ARTIST = 2;

    private static final int COLUMN_SONG_TRACK_NUMBER = 3;

    private static final int COLUMN_SONG_ART_URI = 4;

    private static final int COLUMN_SONG_VISIBLE = 5;



    private static final int COLUMN_PLAYLIST_SONG_ID = 0;

    private static final int COLUMN_PLAYLIST_SONG_TITLE = 1;

    private static final int COLUMN_PLAYLIST_SONG_ARTIST = 2;

    private static final int COLUMN_PLAYLIST_TRACK_NUMBER = 3;

    private static final int COLUMN_PLAYLIST_SONG_ART_URI = 4;

    private static final int COLUMN_PLAYLIST_SONG_VISIBLE = 5;



    private static final int COLUMN_ALBUM_ID = 0;

    private static final int COLUMN_ALBUM_NAME = 1;

    private static final int COLUMN_ALBUM_ARTIST = 2;

    private static final int COLUMN_ALBUM_ART_URI = 3;

    private static final int COLUMN_ALBUM_VISIBLE = 4;



    public void doRefresh() {
        getSupportLoaderManager().restartLoader(1, null, this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        final MenuItem sortMenuItem = menu.add(Menu.NONE, OPTION_MENUITEM_SORT, 2, R.string.menuitem_label_sort);
        sortMenuItem.setIcon(PlayerApplication.iconsAreDark() ?  R.drawable.ic_sort_black_48dp : R.drawable.ic_sort_white_48dp);
        MenuItemCompat.setShowAsAction(sortMenuItem, MenuItemCompat.SHOW_AS_ACTION_ALWAYS | MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT);
        sortMenuItem.setOnMenuItemClickListener(onSortOptionMenuItemListener);
        return true;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        if (v == contentList || v == dragableContentList) {
            doOnCreateDetailContextMenu(menu);
        }
        else {
            super.onCreateContextMenu(menu, v, menuInfo);
        }
    }


    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (item.getGroupId() != CONTEXT_MENU_GROUP_ID && item.getGroupId() != CONTEXT_ART_GROUP_ID) {
            return super.onContextItemSelected(item);
        }

        return doOnDetailContextItemSelected(item.getItemId());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {

        Bundle parameters = getIntent().getExtras();
        if (parameters != null) {
            contentType = (MediaManager.Provider.ContentType) parameters.getSerializable(PlayerApplication.CONTENT_TYPE_KEY);
            contentSourceId = parameters.getString(PlayerApplication.CONTENT_SOURCE_ID_KEY);
        }
        else {
            contentType = MediaManager.Provider.ContentType.CONTENT_TYPE_PLAYLIST;
            contentSourceId = null;
        }

        switch (contentType) {
            case CONTENT_TYPE_ARTIST:
            case CONTENT_TYPE_ALBUM:
            case CONTENT_TYPE_ALBUM_ARTIST:
            case CONTENT_TYPE_GENRE:
                super.onCreate(savedInstanceState, R.layout.activity_library_detail, null);
                break;
            case CONTENT_TYPE_PLAYLIST:
                super.onCreate(savedInstanceState, R.layout.activity_library_sortable_detail, null);
                break;
            default:
                throw new IllegalArgumentException();
        }

        PlayerApplication.applyActionBar(this);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);



        final Activity hostActivity = this;
        final LibraryAdapter.LibraryAdapterContainer container = new LibraryAdapter.LibraryAdapterContainer() {
            @Override
            public Activity getActivity() {
                return hostActivity;
            }

            @Override
            public PopupMenu.OnMenuItemClickListener getOnPopupMenuItemClickListener(final int position) {
                return new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem menuItem) {
                        return doOnDetailContextItemSelected(menuItem.getItemId());
                    }
                };
            }

            @Override
            public void createMenu(final Menu menu, int position) {
                cursor.moveToPosition(position);
                doOnCreateDetailContextMenu(menu);
            }
        };

        /*
            Setting adapter
         */
        switch (contentType) {
            case CONTENT_TYPE_ARTIST:
            case CONTENT_TYPE_ALBUM:
                mAdapter = LibraryAdapterFactory.build(container, LibraryAdapterFactory.ADAPTER_SONG, LibraryAdapter.LIBRARY_MANAGER,
                        new int[]{
                                COLUMN_SONG_ID,
                                COLUMN_SONG_TITLE,
                                COLUMN_SONG_ARTIST,
                                COLUMN_SONG_TRACK_NUMBER,
                                COLUMN_SONG_ART_URI,
                                COLUMN_SONG_VISIBLE
                        });
                break;
            case CONTENT_TYPE_PLAYLIST:
                mAdapter = LibraryAdapterFactory.build(container, LibraryAdapterFactory.ADAPTER_PLAYLIST_DETAILS, LibraryAdapter.LIBRARY_MANAGER,
                        new int[]{
                                COLUMN_PLAYLIST_SONG_ID,
                                COLUMN_PLAYLIST_SONG_TITLE,
                                COLUMN_PLAYLIST_SONG_ARTIST,
                                COLUMN_PLAYLIST_TRACK_NUMBER,
                                COLUMN_PLAYLIST_SONG_ART_URI,
                                COLUMN_PLAYLIST_SONG_VISIBLE
                        });
                mAdapter.setTransparentBackground(true);
                break;
            case CONTENT_TYPE_ALBUM_ARTIST:
            case CONTENT_TYPE_GENRE:
                mAdapter = LibraryAdapterFactory.build(container, LibraryAdapterFactory.ADAPTER_ALBUM_SIMPLE, LibraryAdapter.LIBRARY_MANAGER,
                        new int[]{
                                COLUMN_ALBUM_ID,
                                COLUMN_ALBUM_NAME,
                                COLUMN_ALBUM_ARTIST,
                                COLUMN_ALBUM_ART_URI,
                                COLUMN_ALBUM_VISIBLE
                        });
                break;
            default:
                throw new IllegalArgumentException();
        }


        switch (contentType) {
            case CONTENT_TYPE_ARTIST:
            case CONTENT_TYPE_ALBUM:
            case CONTENT_TYPE_ALBUM_ARTIST:
            case CONTENT_TYPE_GENRE:
                contentList = (ListView) findViewById(R.id.list_view_base);
                contentList.setEmptyView(findViewById(R.id.list_view_empty));
                contentList.setAdapter(mAdapter);

                contentList.setOnItemClickListener(contentListOnItemClickListener);
                contentList.setOnCreateContextMenuListener(this);
                break;
            case CONTENT_TYPE_PLAYLIST:
                dragableContentList = (DragSortListView) findViewById(R.id.dragable_list_base);
                dragableContentList.setEmptyView(findViewById(R.id.dragable_list_empty));
                dragableContentList.setAdapter(mAdapter);


                DragSortListView.DropListener dragListener = new DragSortListView.DropListener() {
                    @Override
                    public void drop(int from, int to) {
                        final MediaManager mediaManager = PlayerApplication.libraryMediaManager();
                        final MediaManager.Provider provider = mediaManager.getProvider();
                        provider.playlistMove(contentSourceId, from, to);
                        getSupportLoaderManager().restartLoader(1, null, LibraryDetailActivity.this);
                    }
                };

                DragSortListView.DragScrollProfile scrollProfile = new DragSortListView.DragScrollProfile() {
                    @Override
                    public float getSpeed(float w, long t) {
                        if (w > 0.8F) {
                            return mAdapter.getCount() / 0.001F;
                        }
                        return 10.0F * w;
                    }
                };

                dragableContentList.setOnItemClickListener(contentListOnItemClickListener);
                dragableContentList.setOnCreateContextMenuListener(this);
                dragableContentList.setDropListener(dragListener);
                dragableContentList.setDragScrollProfile(scrollProfile);
                break;
            default:
                throw new IllegalArgumentException();
        }

        getSupportLoaderManager().initLoader(0, null, this);
        getSupportLoaderManager().initLoader(1, null, this);
    }

    @Override
    protected void onDestroy() {
        getSupportLoaderManager().destroyLoader(0);
        getSupportLoaderManager().destroyLoader(1);
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }



    /*
        ContentType cursor loader implementation
     */
    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        if (i == 0) {
            return super.onCreateLoader(i, bundle);
        }

        int[] requestedFields;

        switch (contentType) {
            case CONTENT_TYPE_ARTIST:
            case CONTENT_TYPE_ALBUM:
                requestedFields = new int[] {
                        MediaManager.Provider.SONG_ID,
                        MediaManager.Provider.SONG_TITLE,
                        MediaManager.Provider.SONG_ARTIST,
                        MediaManager.Provider.SONG_TRACK,
                        MediaManager.Provider.SONG_ART_URI,
                        MediaManager.Provider.SONG_VISIBLE,
                };

                return PlayerApplication.buildMediaLoader(
                        PlayerApplication.libraryMediaManager().getProvider(),
                        requestedFields,
                        new int[] { PlayerApplication.library_details_songs_sort_order},
                        null,
                        contentType,
                        contentSourceId);

            case CONTENT_TYPE_PLAYLIST:
                requestedFields = new int[] {
                        MediaManager.Provider.SONG_ID,
                        MediaManager.Provider.SONG_TITLE,
                        MediaManager.Provider.SONG_ARTIST,
                        MediaManager.Provider.SONG_TRACK,
                        MediaManager.Provider.SONG_ART_URI,
                        MediaManager.Provider.SONG_VISIBLE,
                        MediaManager.Provider.PLAYLIST_ENTRY_POSITION,
                };

                return PlayerApplication.buildMediaLoader(
                        PlayerApplication.libraryMediaManager().getProvider(),
                        requestedFields,
                        new int[]{ PlayerApplication.library_details_playlist_sort_order},
                        null,
                        contentType,
                        contentSourceId);
            case CONTENT_TYPE_ALBUM_ARTIST:
            case CONTENT_TYPE_GENRE:
                requestedFields = new int[] {
                        MediaManager.Provider.ALBUM_ID,
                        MediaManager.Provider.ALBUM_NAME,
                        MediaManager.Provider.ALBUM_ARTIST,
                        MediaManager.Provider.ALBUM_ART_URI,
                        MediaManager.Provider.ALBUM_VISIBLE,
                };

                return PlayerApplication.buildAlbumLoader(
                        PlayerApplication.libraryMediaManager().getProvider(),
                        requestedFields,
                        new int[]{ PlayerApplication.library_details_albums_sort_order},
                        null,
                        contentType,
                        contentSourceId);
            default:
                throw new IllegalArgumentException();
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        switch (cursorLoader.getId()) {
            case 0:
                super.onLoaderReset(cursorLoader);
                break;
            case 1:
                if (mAdapter != null) {
                    mAdapter.changeCursor(null);
                    cursor = null;
                }
                break;
        }
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        switch (cursorLoader.getId()) {
            case 0:
                super.onLoadFinished(cursorLoader, cursor);
                break;
            case 1:
                if (mAdapter != null) {
                    this.cursor = cursor;
                    mAdapter.changeCursor(cursor);
                }
                break;
        }
    }



    /*
        ContentType listview item click listeners
     */
    final AdapterView.OnItemClickListener contentListOnItemClickListener = new AdapterView.OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
            switch (contentType) {
                case CONTENT_TYPE_PLAYLIST:
                case CONTENT_TYPE_ARTIST:
                case CONTENT_TYPE_ALBUM:
                    doDetailPlayAction();
                    break;
                case CONTENT_TYPE_ALBUM_ARTIST:
                case CONTENT_TYPE_GENRE:
                    final Intent intent = new Intent(LibraryDetailActivity.this, LibraryDetailWithHeaderActivity.class);
                    intent.putExtra(PlayerApplication.CONTENT_TYPE_KEY, MediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM);
                    intent.putExtra(PlayerApplication.CONTENT_SOURCE_ID_KEY, cursor.getString(COLUMN_ALBUM_ID));
                    intent.putExtra(PlayerApplication.CONTENT_SOURCE_DESCRIPTION_KEY, cursor.getString(COLUMN_ALBUM_NAME));
                    startActivity(intent);
                    break;

            }
        }
    };



    private boolean doDetailPlayAction() {
        switch (contentType) {
            case CONTENT_TYPE_ARTIST:
                return PlayerApplication.artistDetailContextItemSelected(this, PlayerApplication.CONTEXT_MENUITEM_PLAY, contentSourceId, PlayerApplication.library_details_songs_sort_order, cursor.getPosition(), cursor.getString(COLUMN_SONG_ID));
            case CONTENT_TYPE_ALBUM_ARTIST:
                return PlayerApplication.albumArtistDetailContextItemSelected(this, PlayerApplication.CONTEXT_MENUITEM_PLAY, PlayerApplication.library_details_albums_sort_order, cursor.getString(COLUMN_ALBUM_ID));
            case CONTENT_TYPE_ALBUM:
                return PlayerApplication.albumDetailContextItemSelected(this, PlayerApplication.CONTEXT_MENUITEM_PLAY, contentSourceId, PlayerApplication.library_details_songs_sort_order, cursor.getPosition(), cursor.getString(COLUMN_SONG_ID));
            case CONTENT_TYPE_PLAYLIST:
                return PlayerApplication.playlistDetailContextItemSelected(this, PlayerApplication.CONTEXT_MENUITEM_PLAY, contentSourceId, PlayerApplication.library_details_songs_sort_order, cursor.getPosition(), cursor.getString(COLUMN_SONG_ID));
            case CONTENT_TYPE_GENRE:
                return PlayerApplication.genreDetailContextItemSelected(this, PlayerApplication.CONTEXT_MENUITEM_PLAY, PlayerApplication.library_details_albums_sort_order, cursor.getPosition(), cursor.getString(COLUMN_ALBUM_ID));
            default:
                throw new IllegalArgumentException();
        }
    }

    public void doOnCreateDetailContextMenu(Menu menu) {
        switch (contentType) {
            case CONTENT_TYPE_ARTIST:
                PlayerApplication.createSongContextMenu(menu, CONTEXT_MENU_GROUP_ID, cursor.getInt(COLUMN_SONG_VISIBLE) == 1);
                break;
            case CONTENT_TYPE_ALBUM_ARTIST:
                PlayerApplication.createAlbumContextMenu(menu, CONTEXT_MENU_GROUP_ID, cursor.getInt(COLUMN_ALBUM_VISIBLE) == 1);
                break;
            case CONTENT_TYPE_ALBUM:
                PlayerApplication.createSongContextMenu(menu, CONTEXT_MENU_GROUP_ID, cursor.getInt(COLUMN_SONG_VISIBLE) == 1);
                break;
            case CONTENT_TYPE_PLAYLIST:
                PlayerApplication.createSongContextMenu(menu, CONTEXT_MENU_GROUP_ID, cursor.getInt(COLUMN_SONG_VISIBLE) == 1, true);
                break;
            case CONTENT_TYPE_GENRE:
                PlayerApplication.createAlbumContextMenu(menu, CONTEXT_MENU_GROUP_ID, cursor.getInt(COLUMN_ALBUM_VISIBLE) == 1);
                break;
            default:
                throw new IllegalArgumentException();
        }
    }

    public boolean doOnDetailContextItemSelected(int itemId) {
        switch (contentType) {
            case CONTENT_TYPE_ARTIST:
                return PlayerApplication.artistDetailContextItemSelected(this, itemId, contentSourceId, PlayerApplication.library_details_songs_sort_order, cursor.getPosition(), cursor.getString(COLUMN_SONG_ID));
            case CONTENT_TYPE_ALBUM_ARTIST:
                return PlayerApplication.albumArtistDetailContextItemSelected(this, itemId, PlayerApplication.library_details_albums_sort_order, cursor.getString(COLUMN_ALBUM_ID));
            case CONTENT_TYPE_ALBUM:
                return PlayerApplication.albumDetailContextItemSelected(this, itemId, contentSourceId, PlayerApplication.library_details_songs_sort_order, cursor.getPosition(), cursor.getString(COLUMN_SONG_ID));
            case CONTENT_TYPE_PLAYLIST:
                boolean playlistActionResult = PlayerApplication.playlistDetailContextItemSelected(this, itemId, contentSourceId, PlayerApplication.library_details_playlist_sort_order, cursor.getPosition(), cursor.getString(COLUMN_SONG_ID));

                switch (itemId) {
                    case PlayerApplication.CONTEXT_MENUITEM_ADD_TO_PLAYLIST:
                    case PlayerApplication.CONTEXT_MENUITEM_CLEAR:
                    case PlayerApplication.CONTEXT_MENUITEM_DELETE:
                        getSupportLoaderManager().restartLoader(1, null, this);
                }

                return playlistActionResult;
            case CONTENT_TYPE_GENRE:
                return PlayerApplication.genreDetailContextItemSelected(this, itemId, PlayerApplication.library_details_albums_sort_order, cursor.getPosition(), cursor.getString(COLUMN_ALBUM_ID));
            default:
                throw new IllegalArgumentException();
        }
    }



    private final MenuItem.OnMenuItemClickListener onSortOptionMenuItemListener = new MenuItem.OnMenuItemClickListener() {

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(LibraryDetailActivity.this);
            int sortIndex = 0; // case MusicConnector.SORT_A_Z

            switch (contentType) {
                case CONTENT_TYPE_ARTIST:
                case CONTENT_TYPE_ALBUM:
                    switch (PlayerApplication.library_details_songs_sort_order) {
                        case +MediaManager.Provider.SONG_TITLE:  sortIndex = 0; break;
                        case -MediaManager.Provider.SONG_TITLE:  sortIndex = 1; break;
                        case +MediaManager.Provider.SONG_TRACK:  sortIndex = 2; break;
                        case -MediaManager.Provider.SONG_TRACK:  sortIndex = 3; break;
                        case +MediaManager.Provider.SONG_URI:    sortIndex = 4; break;
                        case -MediaManager.Provider.SONG_URI:    sortIndex = 5; break;
                        case +MediaManager.Provider.SONG_ARTIST: sortIndex = 6; break;
                        case -MediaManager.Provider.SONG_ARTIST: sortIndex = 7; break;
                        case +MediaManager.Provider.SONG_ALBUM:  sortIndex = 8; break;
                        case -MediaManager.Provider.SONG_ALBUM:  sortIndex = 9; break;
                    }

                    alertDialogBuilder.setSingleChoiceItems(R.array.sort_songs, sortIndex, songFragmentAlertDialogClickListener);
                    break;
                case CONTENT_TYPE_PLAYLIST:
                    switch (PlayerApplication.library_details_playlist_sort_order) {
                        case +MediaManager.Provider.SONG_TITLE:  sortIndex = 0; break;
                        case -MediaManager.Provider.SONG_TITLE:  sortIndex = 1; break;
                        case +MediaManager.Provider.SONG_TRACK:  sortIndex = 2; break;
                        case -MediaManager.Provider.SONG_TRACK:  sortIndex = 3; break;
                        case +MediaManager.Provider.PLAYLIST_ENTRY_POSITION:  sortIndex = 4; break;
                        case -MediaManager.Provider.PLAYLIST_ENTRY_POSITION:  sortIndex = 5; break;
                        case +MediaManager.Provider.SONG_URI:    sortIndex = 6; break;
                        case -MediaManager.Provider.SONG_URI:    sortIndex = 7; break;
                        case +MediaManager.Provider.SONG_ARTIST: sortIndex = 8; break;
                        case -MediaManager.Provider.SONG_ARTIST: sortIndex = 9; break;
                        case +MediaManager.Provider.SONG_ALBUM:  sortIndex = 10; break;
                        case -MediaManager.Provider.SONG_ALBUM:  sortIndex = 11; break;
                    }

                    alertDialogBuilder.setSingleChoiceItems(R.array.sort_playlist_entries, sortIndex, playlistFragmentAlertDialogClickListener);
                    break;
                case CONTENT_TYPE_ALBUM_ARTIST:
                case CONTENT_TYPE_GENRE:
                    switch (PlayerApplication.library_details_albums_sort_order) {
                        case +MediaManager.Provider.ALBUM_NAME:   sortIndex = 0;  break;
                        case -MediaManager.Provider.ALBUM_NAME:   sortIndex = 1;  break;
                        case +MediaManager.Provider.ALBUM_ARTIST: sortIndex = 2;  break;
                        case -MediaManager.Provider.ALBUM_ARTIST: sortIndex = 3;  break;
                    }

                    alertDialogBuilder.setSingleChoiceItems(R.array.sort_albums, sortIndex, albumFragmentAlertDialogClickListener);
                    break;
                default:
                    throw new IllegalArgumentException();
            }

            alertDialogBuilder.show();
            return true;
        }
    };

    private DialogInterface.OnClickListener songFragmentAlertDialogClickListener = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case 0:  PlayerApplication.library_details_songs_sort_order = MediaManager.Provider.SONG_TITLE; break;
                case 1:  PlayerApplication.library_details_songs_sort_order = -MediaManager.Provider.SONG_TITLE; break;
                case 2:  PlayerApplication.library_details_songs_sort_order = MediaManager.Provider.SONG_TRACK; break;
                case 3:  PlayerApplication.library_details_songs_sort_order = -MediaManager.Provider.SONG_TRACK; break;
                case 4:  PlayerApplication.library_details_songs_sort_order = MediaManager.Provider.SONG_URI; break;
                case 5:  PlayerApplication.library_details_songs_sort_order = -MediaManager.Provider.SONG_URI; break;
                case 6:  PlayerApplication.library_details_songs_sort_order = MediaManager.Provider.SONG_ARTIST; break;
                case 7:  PlayerApplication.library_details_songs_sort_order = -MediaManager.Provider.SONG_ARTIST; break;
                case 8:  PlayerApplication.library_details_songs_sort_order = MediaManager.Provider.SONG_ALBUM; break;
                case 9:  PlayerApplication.library_details_songs_sort_order = -MediaManager.Provider.SONG_ALBUM; break;
            }

            doRefresh();
            dialog.dismiss();
        }
    };

    private DialogInterface.OnClickListener playlistFragmentAlertDialogClickListener = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case 0:  PlayerApplication.library_details_playlist_sort_order = MediaManager.Provider.SONG_TITLE; break;
                case 1:  PlayerApplication.library_details_playlist_sort_order = -MediaManager.Provider.SONG_TITLE; break;
                case 2:  PlayerApplication.library_details_playlist_sort_order = MediaManager.Provider.SONG_TRACK; break;
                case 3:  PlayerApplication.library_details_playlist_sort_order = -MediaManager.Provider.SONG_TRACK; break;
                case 4:  PlayerApplication.library_details_playlist_sort_order = MediaManager.Provider.PLAYLIST_ENTRY_POSITION; break;
                case 5:  PlayerApplication.library_details_playlist_sort_order = -MediaManager.Provider.PLAYLIST_ENTRY_POSITION; break;
                case 6:  PlayerApplication.library_details_playlist_sort_order = MediaManager.Provider.SONG_URI; break;
                case 7:  PlayerApplication.library_details_playlist_sort_order = -MediaManager.Provider.SONG_URI; break;
                case 8:  PlayerApplication.library_details_playlist_sort_order = MediaManager.Provider.SONG_ARTIST; break;
                case 9:  PlayerApplication.library_details_playlist_sort_order = -MediaManager.Provider.SONG_ARTIST; break;
                case 10:  PlayerApplication.library_details_playlist_sort_order = MediaManager.Provider.SONG_ALBUM; break;
                case 11:  PlayerApplication.library_details_playlist_sort_order = -MediaManager.Provider.SONG_ALBUM; break;
            }

            doRefresh();
            dialog.dismiss();
        }
    };

    private DialogInterface.OnClickListener albumFragmentAlertDialogClickListener = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case 0:  PlayerApplication.library_details_albums_sort_order = MediaManager.Provider.ALBUM_NAME; break;
                case 1:  PlayerApplication.library_details_albums_sort_order = -MediaManager.Provider.ALBUM_NAME;  break;
                case 2:  PlayerApplication.library_details_albums_sort_order = MediaManager.Provider.ALBUM_ARTIST;  break;
                case 3:  PlayerApplication.library_details_albums_sort_order = -MediaManager.Provider.ALBUM_ARTIST; break;
            }

            doRefresh();
            dialog.dismiss();
        }
    };
}
