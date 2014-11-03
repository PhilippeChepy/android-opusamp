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

import net.opusapp.player.R;
import net.opusapp.player.core.service.providers.AbstractMediaManager;
import net.opusapp.player.ui.adapter.LibraryAdapter;
import net.opusapp.player.ui.adapter.LibraryAdapterFactory;
import net.opusapp.player.ui.utils.MusicConnector;
import net.opusapp.player.ui.utils.PlayerApplication;

public class LibraryDetailActivity extends AbstractPlayerActivity implements LoaderManager.LoaderCallbacks<Cursor> {

    public static final String TAG = LibraryDetailActivity.class.getSimpleName();

    private static final int CONTEXT_MENU_GROUP_ID = 1;

    private static final int CONTEXT_ART_GROUP_ID = 100;



    /*
        Actionbar
     */
    private static final int OPTION_MENUITEM_SORT = 1;



    /*
        ContentType management
     */
    private AbstractMediaManager.Provider.ContentType contentType;

    private String contentSourceId;

    private Cursor cursor;



    /*

     */
    private LibraryAdapter adapter;

    private ListView contentList;



    private static final int COLUMN_SONG_ID = 0;

    private static final int COLUMN_SONG_TITLE = 1;

    private static final int COLUMN_SONG_ARTIST = 2;

    private static final int COLUMN_SONG_ART_URI = 3;

    private static final int COLUMN_SONG_VISIBLE = 4;

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
        final MenuItem sortMenuItem = menu.add(Menu.NONE, OPTION_MENUITEM_SORT, 2, R.string.menuitem_label_sort);
        sortMenuItem.setIcon(PlayerApplication.iconsAreDark() ?  R.drawable.ic_action_sort_2 : R.drawable.ic_action_sort_2_dark);
        MenuItemCompat.setShowAsAction(sortMenuItem, MenuItemCompat.SHOW_AS_ACTION_ALWAYS | MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT);
        sortMenuItem.setOnMenuItemClickListener(onSortOptionMenuItemListener);
        return true;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        if (v == contentList) {
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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState, R.layout.activity_library_detail, null);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Bundle parameters = getIntent().getExtras();
        if (parameters != null) {
            contentType = (AbstractMediaManager.Provider.ContentType) parameters.getSerializable(PlayerApplication.CONTENT_TYPE_KEY);
            contentSourceId = parameters.getString(PlayerApplication.CONTENT_SOURCE_ID_KEY);

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
                case CONTENT_TYPE_PLAYLIST:
                    adapter = LibraryAdapterFactory.build(container, LibraryAdapterFactory.ADAPTER_SONG, LibraryAdapter.LIBRARY_MANAGER,
                            new int[]{
                                    COLUMN_SONG_ID,
                                    COLUMN_SONG_TITLE,
                                    COLUMN_SONG_ARTIST,
                                    COLUMN_SONG_ART_URI,
                                    COLUMN_SONG_VISIBLE
                            });
                    break;
                case CONTENT_TYPE_ALBUM_ARTIST:
                case CONTENT_TYPE_GENRE:
                    adapter = LibraryAdapterFactory.build(container, LibraryAdapterFactory.ADAPTER_ALBUM_SIMPLE, LibraryAdapter.LIBRARY_MANAGER,
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
        }

        contentList = (ListView) findViewById(R.id.list_view_base);
        contentList.setAdapter(adapter);

        contentList.setOnItemClickListener(contentListOnItemClickListener);
        contentList.setOnCreateContextMenuListener(this);

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
                        AbstractMediaManager.Provider.SONG_ID,
                        AbstractMediaManager.Provider.SONG_TITLE,
                        AbstractMediaManager.Provider.SONG_ARTIST,
                        AbstractMediaManager.Provider.SONG_ART_URI,
                        AbstractMediaManager.Provider.SONG_VISIBLE,
                };

                return PlayerApplication.buildMediaLoader(
                        PlayerApplication.libraryManagerIndex,
                        requestedFields,
                        new int[] { MusicConnector.details_songs_sort_order },
                        null,
                        contentType,
                        contentSourceId);

            case CONTENT_TYPE_PLAYLIST:
                requestedFields = new int[] {
                        AbstractMediaManager.Provider.SONG_ID,
                        AbstractMediaManager.Provider.SONG_TITLE,
                        AbstractMediaManager.Provider.SONG_ARTIST,
                        AbstractMediaManager.Provider.SONG_ART_URI,
                        AbstractMediaManager.Provider.SONG_VISIBLE,

                        AbstractMediaManager.Provider.PLAYLIST_ENTRY_POSITION
                };

                return PlayerApplication.buildMediaLoader(
                        PlayerApplication.libraryManagerIndex,
                        requestedFields,
                        new int[]{ MusicConnector.details_songs_sort_order },
                        null,
                        contentType,
                        contentSourceId);
            case CONTENT_TYPE_ALBUM_ARTIST:
            case CONTENT_TYPE_GENRE:
                requestedFields = new int[] {
                        AbstractMediaManager.Provider.ALBUM_ID,
                        AbstractMediaManager.Provider.ALBUM_NAME,
                        AbstractMediaManager.Provider.ALBUM_ARTIST,
                        AbstractMediaManager.Provider.ALBUM_ART_URI,
                        AbstractMediaManager.Provider.ALBUM_VISIBLE,
                };

                return PlayerApplication.buildAlbumLoader(
                        PlayerApplication.libraryManagerIndex,
                        requestedFields,
                        new int[]{ MusicConnector.details_albums_sort_order },
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
                if (adapter != null) {
                    adapter.changeCursor(null);
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
                if (adapter != null) {
                    this.cursor = cursor;
                    adapter.changeCursor(cursor);
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
                    intent.putExtra(PlayerApplication.CONTENT_TYPE_KEY, AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM);
                    intent.putExtra(PlayerApplication.CONTENT_SOURCE_ID_KEY, cursor.getString(COLUMN_ALBUM_ID));
                    startActivity(intent);
                    break;

            }
        }
    };



    private boolean doDetailPlayAction() {
        switch (contentType) {
            case CONTENT_TYPE_ARTIST:
                return PlayerApplication.artistDetailContextItemSelected(this, PlayerApplication.CONTEXT_MENUITEM_PLAY, contentSourceId, MusicConnector.details_songs_sort_order, cursor.getPosition(), cursor.getString(COLUMN_SONG_ID));
            case CONTENT_TYPE_ALBUM_ARTIST:
                return PlayerApplication.albumArtistDetailContextItemSelected(this, PlayerApplication.CONTEXT_MENUITEM_PLAY, MusicConnector.details_albums_sort_order, cursor.getString(COLUMN_ALBUM_ID));
            case CONTENT_TYPE_ALBUM:
                return PlayerApplication.albumDetailContextItemSelected(this, PlayerApplication.CONTEXT_MENUITEM_PLAY, contentSourceId, MusicConnector.details_songs_sort_order, cursor.getPosition(), cursor.getString(COLUMN_SONG_ID));
            case CONTENT_TYPE_PLAYLIST:
                return PlayerApplication.playlistDetailContextItemSelected(this, PlayerApplication.CONTEXT_MENUITEM_PLAY, contentSourceId, MusicConnector.details_songs_sort_order, cursor.getPosition(), cursor.getString(COLUMN_SONG_ID));
            case CONTENT_TYPE_GENRE:
                return PlayerApplication.genreDetailContextItemSelected(this, PlayerApplication.CONTEXT_MENUITEM_PLAY, contentSourceId, MusicConnector.details_albums_sort_order, cursor.getPosition(), cursor.getString(COLUMN_ALBUM_ID));
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
                return PlayerApplication.artistDetailContextItemSelected(this, itemId, contentSourceId, MusicConnector.details_songs_sort_order, cursor.getPosition(), cursor.getString(COLUMN_SONG_ID));
            case CONTENT_TYPE_ALBUM_ARTIST:
                return PlayerApplication.albumArtistDetailContextItemSelected(this, itemId, MusicConnector.details_albums_sort_order, cursor.getString(COLUMN_ALBUM_ID));
            case CONTENT_TYPE_ALBUM:
                return PlayerApplication.albumDetailContextItemSelected(this, itemId, contentSourceId, MusicConnector.details_songs_sort_order, cursor.getPosition(), cursor.getString(COLUMN_SONG_ID));
            case CONTENT_TYPE_PLAYLIST:
                boolean playlistActionResult = PlayerApplication.playlistDetailContextItemSelected(this, itemId, contentSourceId, MusicConnector.details_songs_sort_order, cursor.getPosition(), cursor.getString(COLUMN_SONG_ID));

                switch (itemId) {
                    case PlayerApplication.CONTEXT_MENUITEM_ADD_TO_PLAYLIST:
                    case PlayerApplication.CONTEXT_MENUITEM_CLEAR:
                    case PlayerApplication.CONTEXT_MENUITEM_DELETE:
                        getSupportLoaderManager().restartLoader(1, null, this);
                }

                return playlistActionResult;
            case CONTENT_TYPE_GENRE:
                return PlayerApplication.genreDetailContextItemSelected(this, itemId, contentSourceId, MusicConnector.details_albums_sort_order, cursor.getPosition(), cursor.getString(COLUMN_ALBUM_ID));
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
                case CONTENT_TYPE_PLAYLIST:
                    switch (MusicConnector.details_songs_sort_order) {
                        case +AbstractMediaManager.Provider.SONG_TITLE:  sortIndex = 0; break;
                        case -AbstractMediaManager.Provider.SONG_TITLE:  sortIndex = 1; break;
                        case +AbstractMediaManager.Provider.SONG_TRACK:  sortIndex = 2; break;
                        case -AbstractMediaManager.Provider.SONG_TRACK:  sortIndex = 3; break;
                        case +AbstractMediaManager.Provider.SONG_URI:    sortIndex = 4; break;
                        case -AbstractMediaManager.Provider.SONG_URI:    sortIndex = 5; break;
                        case +AbstractMediaManager.Provider.SONG_ARTIST: sortIndex = 6; break;
                        case -AbstractMediaManager.Provider.SONG_ARTIST: sortIndex = 7; break;
                        case +AbstractMediaManager.Provider.SONG_ALBUM:  sortIndex = 8; break;
                        case -AbstractMediaManager.Provider.SONG_ALBUM:  sortIndex = 9; break;
                    }

                    alertDialogBuilder.setSingleChoiceItems(R.array.sort_songs, sortIndex, songFragmentAlertDialogClickListener);
                    break;
                case CONTENT_TYPE_ALBUM_ARTIST:
                case CONTENT_TYPE_GENRE:
                    switch (MusicConnector.details_albums_sort_order) {
                        case +AbstractMediaManager.Provider.ALBUM_NAME:   sortIndex = 0;  break;
                        case -AbstractMediaManager.Provider.ALBUM_NAME:   sortIndex = 1;  break;
                        case +AbstractMediaManager.Provider.ALBUM_ARTIST: sortIndex = 2;  break;
                        case -AbstractMediaManager.Provider.ALBUM_ARTIST: sortIndex = 3;  break;
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
                case 0:  MusicConnector.details_songs_sort_order = AbstractMediaManager.Provider.SONG_TITLE; break;
                case 1:  MusicConnector.details_songs_sort_order = -AbstractMediaManager.Provider.SONG_TITLE; break;
                case 2:  MusicConnector.details_songs_sort_order = AbstractMediaManager.Provider.SONG_TRACK; break;
                case 3:  MusicConnector.details_songs_sort_order = -AbstractMediaManager.Provider.SONG_TRACK; break;
                case 4:  MusicConnector.details_songs_sort_order = AbstractMediaManager.Provider.SONG_URI; break;
                case 5:  MusicConnector.details_songs_sort_order = -AbstractMediaManager.Provider.SONG_URI; break;
                case 6:  MusicConnector.details_songs_sort_order = AbstractMediaManager.Provider.SONG_ARTIST; break;
                case 7:  MusicConnector.details_songs_sort_order = -AbstractMediaManager.Provider.SONG_ARTIST; break;
                case 8:  MusicConnector.details_songs_sort_order = AbstractMediaManager.Provider.SONG_ALBUM; break;
                case 9:  MusicConnector.details_songs_sort_order = -AbstractMediaManager.Provider.SONG_ALBUM; break;
            }

            doRefresh();
            dialog.dismiss();
        }
    };

    private DialogInterface.OnClickListener albumFragmentAlertDialogClickListener = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case 0:  MusicConnector.details_albums_sort_order = AbstractMediaManager.Provider.ALBUM_NAME; break;
                case 1:  MusicConnector.details_albums_sort_order = -AbstractMediaManager.Provider.ALBUM_NAME;  break;
                case 2:  MusicConnector.details_albums_sort_order = AbstractMediaManager.Provider.ALBUM_ARTIST;  break;
                case 3:  MusicConnector.details_albums_sort_order = -AbstractMediaManager.Provider.ALBUM_ARTIST; break;
            }

            doRefresh();
            dialog.dismiss();
        }
    };
}
