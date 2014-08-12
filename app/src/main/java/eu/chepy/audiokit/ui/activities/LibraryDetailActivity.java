/*
 * LibraryDetailActivity.java
 *
 * Copyright (c) 2014, Philippe Chepy
 * All rights reserved.
 *
 * This software is the confidential and proprietary information
 * of Philippe Chepy.
 * You shall not disclose such Confidential Information.
 *
 * http://www.chepy.eu
 */
package eu.chepy.audiokit.ui.activities;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.PopupMenu;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;

import com.nineoldandroids.view.ViewHelper;

import eu.chepy.audiokit.R;
import eu.chepy.audiokit.core.service.providers.AbstractMediaManager;
import eu.chepy.audiokit.core.service.providers.AbstractMediaProvider;
import eu.chepy.audiokit.ui.adapter.LibraryAdapter;
import eu.chepy.audiokit.ui.adapter.LibraryAdapterFactory;
import eu.chepy.audiokit.ui.adapter.ux.HeaderWrapperAdapter;
import eu.chepy.audiokit.ui.utils.MusicConnector;
import eu.chepy.audiokit.ui.utils.PlayerApplication;
import eu.chepy.audiokit.ui.utils.uil.ProviderImageDownloader;

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
    private AbstractMediaProvider.ContentType contentType;

    private String contentSourceId;

    private Cursor cursor;



    /*

     */
    private LibraryAdapter adapter;

    private HeaderWrapperAdapter wrappedAdapter;

    private ListView contentList;



    /*
        Action bar fadin effect
     */
    private Drawable actionbarBackground;

    private int actionbarHeight;

    private int minHeaderTranslation;

    private View header;

    private View placeHolderView;

    private TypedValue mTypedValue = new TypedValue();



    private static final int COLUMN_SONG_ID = 0;

    private static final int COLUMN_SONG_TITLE = 1;

    private static final int COLUMN_SONG_ARTIST = 2;

    private static final int COLUMN_ALBUM_ID = 0;

    private static final int COLUMN_ALBUM_NAME = 1;

    private static final int COLUMN_ALBUM_ARTIST = 2;


    private static final int COLUMN_ID = 0;

    private int COLUMN_VISIBLE = 0;



    private static final int SELECT_IMAGE_LEGACY = 0;

    private static final int SELECT_IMAGE_KITKAT = 1;


    private static final int CONTEXT_MENUITEM_USE_FILE_ART = 100;

    private static final int CONTEXT_MENUITEM_RESTORE_ART = 101;



    public void doRefresh() {
        getSupportLoaderManager().restartLoader(1, null, this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        final MenuItem sortMenuItem = menu.add(Menu.NONE, OPTION_MENUITEM_SORT, 2, R.string.menu_label_sort);
        sortMenuItem.setIcon(R.drawable.ic_action_sort_2_dark);
        MenuItemCompat.setShowAsAction(sortMenuItem, MenuItemCompat.SHOW_AS_ACTION_ALWAYS | MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT);
        sortMenuItem.setOnMenuItemClickListener(onSortOptionMenuItemListener);

        return true;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        int position = 0;
        if (menuInfo != null) {
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
            position = info.position - 1;
        }

        cursor.moveToPosition(position);
        doOnCreateContextMenu(menu);
        super.onCreateContextMenu(menu, v, menuInfo);
    }


    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (item.getGroupId() != CONTEXT_MENU_GROUP_ID && item.getGroupId() != CONTEXT_ART_GROUP_ID) {
            return getPlayerView().onContextItemSelected(item);
        }

        return doOnContextItemSelected(item.getGroupId(), item.getItemId());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        getSupportActionBar().show();
        setContentView(R.layout.activity_library_detail);

        initPlayerView(savedInstanceState);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Bundle parameters = getIntent().getExtras();
        if (parameters != null) {
            contentType = (AbstractMediaProvider.ContentType) parameters.getSerializable(PlayerApplication.CONTENT_TYPE_KEY);
            contentSourceId = parameters.getString(PlayerApplication.CONTENT_SOURCE_ID_KEY);

            switch (contentType) {
                case CONTENT_TYPE_ARTIST:
                case CONTENT_TYPE_ALBUM:
                    COLUMN_VISIBLE = 3;
                    break;
                case CONTENT_TYPE_PLAYLIST:
                    COLUMN_VISIBLE = 3;
                    break;
                case CONTENT_TYPE_ALBUM_ARTIST:
                case CONTENT_TYPE_GENRE:
                    COLUMN_VISIBLE = 3;
                    break;
                default:
                    throw new IllegalArgumentException();
            }

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
                            cursor.moveToPosition(position);
                            return doOnContextItemSelected(menuItem.getGroupId(), menuItem.getItemId());
                        }
                    };
                }

                @Override
                public void createMenu(final Menu menu, int position) {
                    cursor.moveToPosition(position);
                    doOnCreateContextMenu(menu);
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
                            new int[] {
                                    COLUMN_SONG_ID,
                                    COLUMN_SONG_TITLE,
                                    COLUMN_SONG_ARTIST,
                                    COLUMN_VISIBLE
                            });
                    break;
                case CONTENT_TYPE_ALBUM_ARTIST:
                case CONTENT_TYPE_GENRE:
                    adapter = LibraryAdapterFactory.build(container, LibraryAdapterFactory.ADAPTER_ALBUM_SIMPLE, LibraryAdapter.LIBRARY_MANAGER,
                            new int[]{
                                    COLUMN_ALBUM_ID,
                                    COLUMN_ALBUM_NAME,
                                    COLUMN_ALBUM_ARTIST,
                                    COLUMN_VISIBLE
                            });
                    break;
                default:
                    throw new IllegalArgumentException();
            }
        }

        minHeaderTranslation = -getResources().getDimensionPixelSize(R.dimen.header_height) + getActionBarHeight();

        contentList = (ListView) findViewById(R.id.list_view_base);
        header = findViewById(R.id.header);

        switch (contentType) {
            case CONTENT_TYPE_ARTIST:
            case CONTENT_TYPE_PLAYLIST:
            case CONTENT_TYPE_ALBUM_ARTIST:
            case CONTENT_TYPE_GENRE:
                placeHolderView = getLayoutInflater().inflate(R.layout.view_header_placeholder, contentList, false);

                ViewGroup.LayoutParams params = header.getLayoutParams();
                if (params != null) {
                    params.height = getActionBarHeight();
                }

                if (placeHolderView != null) {
                    params = placeHolderView.getLayoutParams();
                    if (params != null) {
                        params.height = getActionBarHeight();
                    }
                }
                contentList.addHeaderView(placeHolderView);
                break;
            case CONTENT_TYPE_ALBUM:
                placeHolderView = getLayoutInflater().inflate(R.layout.listview_header_details, contentList, false);
                if (placeHolderView != null) {
                    ((ImageView) placeHolderView).setImageResource(R.drawable.no_art_normal);

                    final String songArtUri =
                            ProviderImageDownloader.SCHEME_URI_PREFIX +
                            ProviderImageDownloader.SUBTYPE_ALBUM + "/" +
                            PlayerApplication.libraryManagerIndex + "/" +
                            contentSourceId;
                    PlayerApplication.normalImageLoader.displayImage(songArtUri, (ImageView) placeHolderView);
                }

                actionbarBackground = getResources().getDrawable(R.drawable.holo_background);

                wrappedAdapter = new HeaderWrapperAdapter(adapter, placeHolderView);
                contentList.setOnScrollListener(contentListOnScrollListener);
                break;
            default:
                break;
        }

        if (wrappedAdapter != null) {
            contentList.setAdapter(wrappedAdapter);
        }
        else {
            contentList.setAdapter(adapter);
        }

        contentList.setOnItemClickListener(contentListOnItemClickListener);
        contentList.setOnCreateContextMenuListener(this);

        getSupportLoaderManager().initLoader(0, null, this);
        getSupportLoaderManager().initLoader(1, null, this);

        if (PlayerApplication.playerService == null) {
            final Context context = getApplicationContext();

            if (context != null) {
                PlayerApplication.connectService(this);
            }
        }
        else {
            getPlayerView().registerServiceListener();
        }

        System.gc();
    }

    @Override
    protected void onDestroy() {
        getSupportLoaderManager().destroyLoader(0);
        getSupportLoaderManager().destroyLoader(1);

        getPlayerView().onActivityDestroy();
        getPlayerView().unregisterServiceListener();
        PlayerApplication.unregisterServiceCallback(this);

        unbindDrawables(findViewById(R.id.drawer_layout));
        super.onDestroy();
        System.gc();
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

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        getPlayerView().saveInstanceState(outState, getSupportActionBar().isShowing());
    }



    /*
        ContentType cursor loader implementation
     */
    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        if (i == 0) {
            return getPlayerView().onCreateLoader(i, bundle);
        }

        int[] requestedFields;

        switch (contentType) {
            case CONTENT_TYPE_ARTIST:
            case CONTENT_TYPE_ALBUM:
                requestedFields = new int[] {
                        AbstractMediaProvider.SONG_ID,
                        AbstractMediaProvider.SONG_TITLE,
                        AbstractMediaProvider.SONG_ARTIST,
                        AbstractMediaProvider.SONG_VISIBLE,
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
                        AbstractMediaProvider.SONG_ID,
                        AbstractMediaProvider.SONG_TITLE,
                        AbstractMediaProvider.SONG_ARTIST,
                        AbstractMediaProvider.SONG_VISIBLE,

                        AbstractMediaProvider.PLAYLIST_ENTRY_POSITION
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
                        AbstractMediaProvider.ALBUM_ID,
                        AbstractMediaProvider.ALBUM_NAME,
                        AbstractMediaProvider.ALBUM_ARTIST,
                        AbstractMediaProvider.ALBUM_VISIBLE,
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
            getPlayerView().onLoaderReset(cursorLoader);
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
            getPlayerView().onLoadFinished(cursorLoader, cursor);
            break;
        case 1:
            if (adapter != null) {
                this.cursor = cursor;
                adapter.changeCursor(cursor);

                if (wrappedAdapter != null) {
                    // Hack: needed for wrappedAdapter to be visually updated.
                    contentList.invalidateViews();
                }

                if (contentType == AbstractMediaProvider.ContentType.CONTENT_TYPE_ALBUM) {
                    final String songArtUri =
                            ProviderImageDownloader.SCHEME_URI_PREFIX +
                            ProviderImageDownloader.SUBTYPE_ALBUM + "/" +
                            PlayerApplication.libraryManagerIndex + "/" +
                            contentSourceId;
                    PlayerApplication.normalImageLoader.displayImage(songArtUri, (ImageView) placeHolderView);
                }
            }
            break;
        }
    }

    @TargetApi(19)
    public void doImageChangeRequest() {
        if (PlayerApplication.hasKitkat()) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            startActivityForResult(intent, SELECT_IMAGE_KITKAT);
        } else {
            Intent intent = new Intent();
            intent.setType("image/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(intent, getString(R.string.label_select_cover)), SELECT_IMAGE_LEGACY);
        }
    }

    @TargetApi(19)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent imageReturnedIntent) {
        super.onActivityResult(requestCode, resultCode, imageReturnedIntent);

        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
        final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();

        switch(requestCode) {
            case SELECT_IMAGE_KITKAT:
            case SELECT_IMAGE_LEGACY:
                if(resultCode == RESULT_OK){
                    final Uri imageUriData = imageReturnedIntent.getData();

                    if (requestCode == SELECT_IMAGE_KITKAT) {
                        final int flags = imageReturnedIntent.getFlags()
                                & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                        getContentResolver().takePersistableUriPermission(imageUriData, flags);
                    }


                    if (imageUriData != null) {
                        final String imageUri = imageUriData.toString();
                        // TODO: clear only old cached image... in provider
                        //ImageLoader.getInstance().clearMemoryCache();
                        //ImageLoader.getInstance().clearDiscCache();

                        PlayerApplication.normalImageLoader.displayImage(imageUri, (ImageView) placeHolderView);

                        final DialogInterface.OnClickListener artUpdateSongPositiveOnClickListener = new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                mediaProvider.setProperty(AbstractMediaProvider.ContentType.CONTENT_TYPE_ALBUM, contentSourceId, AbstractMediaProvider.ContentProperty.CONTENT_ART_URI, imageUri, true);
                            }
                        };

                        final DialogInterface.OnClickListener artUpdateSongNegativeOnClickListener = new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                mediaProvider.setProperty(AbstractMediaProvider.ContentType.CONTENT_TYPE_ALBUM, contentSourceId, AbstractMediaProvider.ContentProperty.CONTENT_ART_URI, imageUri, false);
                            }
                        };

                        new AlertDialog.Builder(this)
                                .setTitle(R.string.alert_title_art_change_tracks)
                                .setMessage(R.string.alert_art_change_tracks)
                                .setPositiveButton(android.R.string.yes, artUpdateSongPositiveOnClickListener)
                                .setNegativeButton(android.R.string.no, artUpdateSongNegativeOnClickListener)
                                .show();
                    }
                }
        }
    }



    /*
        ContentType listview item click listeners
     */
    final AdapterView.OnItemClickListener contentListOnItemClickListener = new AdapterView.OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
            final Intent intent = new Intent(LibraryDetailActivity.this, LibraryDetailActivity.class);

            switch (contentType) {
                case CONTENT_TYPE_PLAYLIST:
                case CONTENT_TYPE_ARTIST:
                    if (position > 0) {
                        doPlayAction(position - 1);
                    }
                    break;
                case CONTENT_TYPE_ALBUM:
                    if (position > 0) {
                        doPlayAction(position - 1);
                    }
                    else {
                        openContextMenu(contentList);
                    }
                    break;
                case CONTENT_TYPE_ALBUM_ARTIST:
                case CONTENT_TYPE_GENRE:
                    if (position > 0) {
                        cursor.moveToPosition(position - 1);
                        intent.putExtra(PlayerApplication.CONTENT_TYPE_KEY, AbstractMediaProvider.ContentType.CONTENT_TYPE_ALBUM);
                        intent.putExtra(PlayerApplication.CONTENT_SOURCE_ID_KEY, cursor.getString(COLUMN_ID));
                        startActivity(intent);
                    }
                    break;

            }
        }
    };



    /*
        Scroll listener of listview (used to set actionbar transparency)
     */
    final AbsListView.OnScrollListener contentListOnScrollListener = new AbsListView.OnScrollListener() {

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
        }

        @Override
        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            int scrollY = getScrollY();
            ViewHelper.setTranslationY(header, Math.max(-scrollY, minHeaderTranslation));
            float ratio = clamp(ViewHelper.getTranslationY(header) / minHeaderTranslation, 0.0f, 1.0f);

            actionbarBackground.setAlpha((int) (ratio * 255));
            getSupportActionBar().setBackgroundDrawable(actionbarBackground);
        }
    };

    /*

     */
    public int getActionBarHeight() {
        if (actionbarHeight != 0) {
            return actionbarHeight;
        }

        actionbarHeight = 0;
        final Resources.Theme theme = getTheme();
        if (theme != null) {
            theme.resolveAttribute(android.support.v7.appcompat.R.attr.actionBarSize, mTypedValue, true);
            actionbarHeight = TypedValue.complexToDimensionPixelSize(mTypedValue.data, getResources().getDisplayMetrics());
        }

        return actionbarHeight;
    }

    public static float clamp(float value, float max, float min) {
        return Math.max(Math.min(value, min), max);
    }

    public int getScrollY() {
        View c = contentList.getChildAt(0);
        if (c == null) {
            return 0;
        }

        int firstVisiblePosition = contentList.getFirstVisiblePosition();
        int top = c.getTop();

        int headerHeight = 0;
        if (firstVisiblePosition >= 1) {
            headerHeight = placeHolderView.getHeight();
        }

        return -top + firstVisiblePosition * c.getHeight() + headerHeight;
    }



    private boolean doPlayAction(int position) {

        switch (contentType) {
            case CONTENT_TYPE_ARTIST:
                return PlayerApplication.artistDetailContextItemSelected(this, PlayerApplication.CONTEXT_MENUITEM_PLAY, contentSourceId, MusicConnector.details_songs_sort_order, cursor.getPosition(), cursor.getString(COLUMN_SONG_ID));
            case CONTENT_TYPE_ALBUM_ARTIST:
                return PlayerApplication.albumArtistDetailContextItemSelected(this, PlayerApplication.CONTEXT_MENUITEM_PLAY, MusicConnector.details_albums_sort_order, cursor.getString(COLUMN_ALBUM_ID));
            case CONTENT_TYPE_ALBUM:
                cursor.moveToPosition(position);
                return PlayerApplication.albumDetailContextItemSelected(this, PlayerApplication.CONTEXT_MENUITEM_PLAY, contentSourceId, MusicConnector.details_songs_sort_order, cursor.getPosition(), cursor.getString(COLUMN_SONG_ID));
            case CONTENT_TYPE_PLAYLIST:
                return PlayerApplication.playlistDetailContextItemSelected(this, PlayerApplication.CONTEXT_MENUITEM_PLAY, contentSourceId, MusicConnector.details_songs_sort_order, cursor.getPosition(), cursor.getString(COLUMN_SONG_ID));
            case CONTENT_TYPE_GENRE:
                return PlayerApplication.genreDetailContextItemSelected(this, PlayerApplication.CONTEXT_MENUITEM_PLAY, contentSourceId, MusicConnector.details_albums_sort_order, cursor.getPosition(), cursor.getString(COLUMN_ALBUM_ID));
            default:
                throw new IllegalArgumentException();
        }
    }

    public void doOnCreateContextMenu(Menu menu) {
        switch (contentType) {
            case CONTENT_TYPE_ARTIST:
                PlayerApplication.createSongContextMenu(menu, CONTEXT_MENU_GROUP_ID, cursor.getInt(COLUMN_VISIBLE) == 1);
                break;
            case CONTENT_TYPE_ALBUM_ARTIST:
                PlayerApplication.createAlbumContextMenu(menu, CONTEXT_MENU_GROUP_ID, cursor.getInt(COLUMN_VISIBLE) == 1);
                break;
            case CONTENT_TYPE_ALBUM:
                Log.w(TAG, ""+cursor.getPosition());
                if (cursor.getPosition() == -1) {
                    menu.add(CONTEXT_ART_GROUP_ID, CONTEXT_MENUITEM_USE_FILE_ART, 2, R.string.context_menu_use_file_art);
                    menu.add(CONTEXT_ART_GROUP_ID, CONTEXT_MENUITEM_RESTORE_ART, 3, R.string.context_menu_restore_file_art);
                }
                else {
                    PlayerApplication.createSongContextMenu(menu, CONTEXT_MENU_GROUP_ID, cursor.getInt(COLUMN_VISIBLE) == 1);
                }
                break;
            case CONTENT_TYPE_PLAYLIST:
                PlayerApplication.createSongContextMenu(menu, CONTEXT_MENU_GROUP_ID, cursor.getInt(COLUMN_VISIBLE) == 1, true);
                break;
            case CONTENT_TYPE_GENRE:
                PlayerApplication.createAlbumContextMenu(menu, CONTEXT_MENU_GROUP_ID, cursor.getInt(COLUMN_VISIBLE) == 1);
                break;
            default:
                throw new IllegalArgumentException();
        }
    }

    public boolean doOnContextItemSelected(int groupId, int itemId) {
        switch (contentType) {
            case CONTENT_TYPE_ARTIST:
                return PlayerApplication.artistDetailContextItemSelected(this, itemId, contentSourceId, MusicConnector.details_songs_sort_order, cursor.getPosition(), cursor.getString(COLUMN_SONG_ID));
            case CONTENT_TYPE_ALBUM_ARTIST:
                return PlayerApplication.albumArtistDetailContextItemSelected(this, itemId, MusicConnector.details_albums_sort_order, cursor.getString(COLUMN_ALBUM_ID));
            case CONTENT_TYPE_ALBUM:
                if (groupId == CONTEXT_ART_GROUP_ID) {
                    switch (itemId) {
                        case CONTEXT_MENUITEM_USE_FILE_ART:
                            doImageChangeRequest();
                            break;
                        case CONTEXT_MENUITEM_RESTORE_ART:
                            // TODO: art restoration.
                            break;
                    }
                    return true;
                }
                else {
                    return PlayerApplication.albumDetailContextItemSelected(this, itemId, contentSourceId, MusicConnector.details_songs_sort_order, cursor.getPosition(), cursor.getString(COLUMN_SONG_ID));
                }
            case CONTENT_TYPE_PLAYLIST:
                boolean playlistActionResult = PlayerApplication.playlistDetailContextItemSelected(this, itemId, contentSourceId, MusicConnector.details_songs_sort_order, cursor.getPosition(), cursor.getString(COLUMN_SONG_ID));

                if (itemId == PlayerApplication.CONTEXT_MENUITEM_CLEAR || itemId == PlayerApplication.CONTEXT_MENUITEM_DELETE) {
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
                        case +AbstractMediaProvider.SONG_TITLE:  sortIndex = 0; break;
                        case -AbstractMediaProvider.SONG_TITLE:  sortIndex = 1; break;
                        case +AbstractMediaProvider.SONG_TRACK:  sortIndex = 2; break;
                        case -AbstractMediaProvider.SONG_TRACK:  sortIndex = 3; break;
                        case +AbstractMediaProvider.SONG_URI:    sortIndex = 4; break;
                        case -AbstractMediaProvider.SONG_URI:    sortIndex = 5; break;
                        case +AbstractMediaProvider.SONG_ARTIST: sortIndex = 6; break;
                        case -AbstractMediaProvider.SONG_ARTIST: sortIndex = 7; break;
                        case +AbstractMediaProvider.SONG_ALBUM:  sortIndex = 8; break;
                        case -AbstractMediaProvider.SONG_ALBUM:  sortIndex = 9; break;
                    }

                    alertDialogBuilder.setSingleChoiceItems(R.array.sort_songs, sortIndex, songFragmentAlertDialogClickListener);
                    break;
                case CONTENT_TYPE_ALBUM_ARTIST:
                case CONTENT_TYPE_GENRE:
                    switch (MusicConnector.details_albums_sort_order) {
                        case +AbstractMediaProvider.ALBUM_NAME:   sortIndex = 0;  break;
                        case -AbstractMediaProvider.ALBUM_NAME:   sortIndex = 1;  break;
                        case +AbstractMediaProvider.ALBUM_ARTIST: sortIndex = 2;  break;
                        case -AbstractMediaProvider.ALBUM_ARTIST: sortIndex = 3;  break;
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
                case 0:  MusicConnector.details_songs_sort_order = AbstractMediaProvider.SONG_TITLE; break;
                case 1:  MusicConnector.details_songs_sort_order = -AbstractMediaProvider.SONG_TITLE; break;
                case 2:  MusicConnector.details_songs_sort_order = AbstractMediaProvider.SONG_TRACK; break;
                case 3:  MusicConnector.details_songs_sort_order = -AbstractMediaProvider.SONG_TRACK; break;
                case 4:  MusicConnector.details_songs_sort_order = AbstractMediaProvider.SONG_URI; break;
                case 5:  MusicConnector.details_songs_sort_order = -AbstractMediaProvider.SONG_URI; break;
                case 6:  MusicConnector.details_songs_sort_order = AbstractMediaProvider.SONG_ARTIST; break;
                case 7:  MusicConnector.details_songs_sort_order = -AbstractMediaProvider.SONG_ARTIST; break;
                case 8:  MusicConnector.details_songs_sort_order = AbstractMediaProvider.SONG_ALBUM; break;
                case 9:  MusicConnector.details_songs_sort_order = -AbstractMediaProvider.SONG_ALBUM; break;
            }

            doRefresh();
            dialog.dismiss();
        }
    };

    private DialogInterface.OnClickListener albumFragmentAlertDialogClickListener = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case 0:  MusicConnector.details_albums_sort_order = AbstractMediaProvider.ALBUM_NAME; break;
                case 1:  MusicConnector.details_albums_sort_order = -AbstractMediaProvider.ALBUM_NAME;  break;
                case 2:  MusicConnector.details_albums_sort_order = AbstractMediaProvider.ALBUM_ARTIST;  break;
                case 3:  MusicConnector.details_albums_sort_order = -AbstractMediaProvider.ALBUM_ARTIST; break;
            }

            doRefresh();
            dialog.dismiss();
        }
    };
}
