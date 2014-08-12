/*
 * ApplicationHelper.java
 *
 * Copyright (c) 2012, Philippe Chepy
 * All rights reserved.
 *
 * This software is the confidential and proprietary information
 * of Philippe Chepy.
 * You shall not disclose such Confidential Information.
 *
 * http://www.chepy.eu
 */
package eu.chepy.audiokit.ui.utils;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.Menu;
import android.webkit.WebView;

import com.nostra13.universalimageloader.core.ImageLoader;

import java.io.File;
import java.util.ArrayList;

import eu.chepy.audiokit.R;
import eu.chepy.audiokit.core.service.IPlayerService;
import eu.chepy.audiokit.core.service.PlayerService;
import eu.chepy.audiokit.core.service.providers.AbstractMediaManager;
import eu.chepy.audiokit.core.service.providers.AbstractMediaProvider;
import eu.chepy.audiokit.core.service.providers.MediaManagerFactory;
import eu.chepy.audiokit.core.service.providers.index.database.Entities;
import eu.chepy.audiokit.core.service.providers.index.database.OpenHelper;
import eu.chepy.audiokit.core.service.utils.AbstractSimpleCursorLoader;
import eu.chepy.audiokit.ui.utils.uil.NormalImageLoader;
import eu.chepy.audiokit.ui.utils.uil.ThumbnailImageLoader;
import eu.chepy.audiokit.utils.LogUtils;

public class PlayerApplication extends Application implements ServiceConnection {

	public final static String TAG = PlayerApplication.class.getSimpleName();



    // Global application context
    public static Context context;

    // Global application instance
    public static PlayerApplication instance;

    // Global service reference.
    public static IPlayerService playerService = null;

    public static ArrayList<ServiceConnection> additionalCallbacks = new ArrayList<ServiceConnection>();



    /*

     */
    private static SQLiteOpenHelper databaseHelper;

    public static int playerManagerIndex = 0;

    public static int libraryManagerIndex = 0;

    public static AbstractMediaManager mediaManagers[] = null;

    public static SQLiteOpenHelper getDatabaseOpenHelper() {
        return databaseHelper;
    }


    public static ImageLoader normalImageLoader;

    public static ImageLoader thumbnailImageLoader;

    private static boolean connecting;


    @Override
    public void onCreate() {
        super.onCreate();

        context = getApplicationContext();
        instance = this;

        databaseHelper = new OpenHelper(this);
        allocateMediaManagers();
        playerManagerIndex = getLibraryPlayerIndex();
        libraryManagerIndex = getLibraryLibraryIndex();

        normalImageLoader = NormalImageLoader.getInstance();
        thumbnailImageLoader = ThumbnailImageLoader.getInstance();
    }

    public synchronized static void connectService(ServiceConnection additionalConnectionCallback) {
        if (connecting) {
            return;
        }

        additionalCallbacks.add(additionalConnectionCallback);
        connecting = true;
        if (context != null) {
            final Intent playerService = new Intent(context, PlayerService.class);
            context.bindService(playerService, instance, Context.BIND_AUTO_CREATE);
            context.startService(playerService);
        }
        connecting = false;
    }

    public synchronized static void unregisterServiceCallback(ServiceConnection additionalCallback) {
        additionalCallbacks.remove(additionalCallback);
    }

    @Override
    public synchronized void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        playerService = IPlayerService.Stub.asInterface(iBinder);

        for (ServiceConnection callback : additionalCallbacks) {
            callback.onServiceConnected(componentName, iBinder);
        }
    }

    @Override
    public synchronized void onServiceDisconnected(ComponentName componentName) {
        playerService = null;
    }

    public static void allocateMediaManagers() {
        AbstractMediaManager currentProvider = null;
        int currentProviderId = -1;
        int currentProviderType = AbstractMediaManager.INVALID_MEDIA_MANAGER;

        if (mediaManagers != null) {
            currentProvider = mediaManagers[playerManagerIndex];
            if (!currentProvider.getMediaPlayer().playerIsPlaying()) {
                // ensure media player is completly stopped if paused.
                currentProvider.getMediaPlayer().playerStop();
                currentProvider = null;
            }
            else {
                currentProvider.getMediaPlayer().resetListeners();
                currentProviderId = currentProvider.getMediaManagerId();
                currentProviderType = currentProvider.getMediaManagerType();
            }
        }

        SQLiteDatabase database = databaseHelper.getWritableDatabase();
        if (database != null) {
            final String[] columns = new String[]{
                    Entities.Provider._ID,
                    Entities.Provider.COLUMN_FIELD_PROVIDER_TYPE
            };

            final int COLUMN_ID = 0;

            final int COLUMN_TYPE = 1;

            final String orderBy = Entities.Provider.COLUMN_FIELD_PROVIDER_POSITION;

            Cursor cursor = database.query(Entities.Provider.TABLE_NAME, columns, null, null, null, null, orderBy);
            if (cursor != null && cursor.getCount() > 0) {
                mediaManagers = new AbstractMediaManager[cursor.getCount()];

                for (int index = 0 ; index < cursor.getCount() ; index++) {
                    cursor.moveToNext();
                    int providerType = cursor.getInt(COLUMN_TYPE);
                    int providerId = cursor.getInt(COLUMN_ID);

                    if (providerId == currentProviderId && providerType == currentProviderType) {
                        mediaManagers[index] = currentProvider;

                        currentProvider = null;
                        currentProviderId = -1;
                        currentProviderType = AbstractMediaManager.INVALID_MEDIA_MANAGER;

                        playerManagerIndex = index;
                    }
                    else {
                        mediaManagers[index] = MediaManagerFactory.buildMediaManager(providerType, providerId);
                    }
                }

                cursor.close();
            }

            if (currentProvider != null) {
                if (currentProvider.getMediaPlayer().playerIsPlaying()) {
                    currentProvider.getMediaPlayer().playerStop();
                }
                currentProvider.getMediaProvider().erase();
            }

            if (libraryManagerIndex >= mediaManagers.length) {
                libraryManagerIndex = 0;
            }

            if (playerManagerIndex >= mediaManagers.length) {
                playerManagerIndex = 0;
            }
        }

        if (playerService != null) {
            try {
                playerService.notifyProviderChanged();
            }
            catch (final RemoteException remoteException) {
                LogUtils.LOGException(TAG, "allocateMediaManagers", 0, remoteException);
            }
        }
    }

    public static Loader<Cursor> buildAlbumArtistLoader(final int managerIndex, final int[] requestedFields, final int[] sortFields, final String filter) {
        return new AbstractSimpleCursorLoader(PlayerApplication.context) {
            @Override
            public Cursor loadInBackground() {
                return  mediaManagers[managerIndex].getMediaProvider().buildCursor(AbstractMediaProvider.ContentType.CONTENT_TYPE_ALBUM_ARTIST, requestedFields, sortFields, filter);
            }
        };
    }

    public static Loader<Cursor> buildAlbumLoader(final int managerIndex, final int[] requestedFields, final int[] sortFields, final String filter, final AbstractMediaProvider.ContentType contentType, final String sourceId) {
        return new AbstractSimpleCursorLoader(PlayerApplication.context) {
            @Override
            public Cursor loadInBackground() {
                return  mediaManagers[managerIndex].getMediaProvider().buildCursor(AbstractMediaProvider.ContentType.CONTENT_TYPE_ALBUM, requestedFields, sortFields, filter, contentType, sourceId);
            }
        };
    }

    public static Loader<Cursor> buildArtistLoader(final int managerIndex, final int[] requestedFields, final int[] sortFields, final String filter) {
        return new AbstractSimpleCursorLoader(PlayerApplication.context) {
            @Override
            public Cursor loadInBackground() {
                return  mediaManagers[managerIndex].getMediaProvider().buildCursor(AbstractMediaProvider.ContentType.CONTENT_TYPE_ARTIST, requestedFields, sortFields, filter);
            }
        };
    }

    public static Loader<Cursor> buildGenreLoader(final int managerIndex, final int[] requestedFields, final int[] sortFields, final String filter) {
        return new AbstractSimpleCursorLoader(PlayerApplication.context) {
            @Override
            public Cursor loadInBackground() {
                return  mediaManagers[managerIndex].getMediaProvider().buildCursor(AbstractMediaProvider.ContentType.CONTENT_TYPE_GENRE, requestedFields, sortFields, filter);
            }
        };
    }

    public static Loader<Cursor> buildPlaylistLoader(final int managerIndex, final int[] requestedFields, final int[] sortFields, final String filter) {
        return new AbstractSimpleCursorLoader(PlayerApplication.context) {
            @Override
            public Cursor loadInBackground() {
                return  mediaManagers[managerIndex].getMediaProvider().buildCursor(AbstractMediaProvider.ContentType.CONTENT_TYPE_PLAYLIST, requestedFields, sortFields, filter);
            }
        };
    }

    public static Loader<Cursor> buildMediaLoader(final int managerIndex, final int[] requestedFields, final int[] sortFields, final String filter, final AbstractMediaProvider.ContentType contentType, final String sourceId) {
        return new AbstractSimpleCursorLoader(PlayerApplication.context) {
            @Override
            public Cursor loadInBackground() {
                return  mediaManagers[managerIndex].getMediaProvider().buildCursor(AbstractMediaProvider.ContentType.CONTENT_TYPE_MEDIA, requestedFields, sortFields, filter, contentType, sourceId);
            }
        };
    }

    public static Loader<Cursor> buildStorageLoader(final int managerIndex, final int[] requestedFields, final int[] sortFields, final String filter) {
        return new AbstractSimpleCursorLoader(PlayerApplication.context) {
            @Override
            public Cursor loadInBackground() {
                return  mediaManagers[managerIndex].getMediaProvider().buildCursor(AbstractMediaProvider.ContentType.CONTENT_TYPE_STORAGE, requestedFields, sortFields, filter);
            }
        };
    }



    /*
        UI/UX communication
     */
    public static final String CONTENT_TYPE_KEY = "type_key";

    public static final String CONTENT_SOURCE_ID_KEY = "id_key";



    public static String lastSearchFilter = null;



    /*
        Context Menu
     */
    public static final int CONTEXT_MENUITEM_PLAY = 0;

    public static final int CONTEXT_MENUITEM_PLAY_NEXT = 1;

    public static final int CONTEXT_MENUITEM_ADD_TO_QUEUE = 2;

    public static final int CONTEXT_MENUITEM_ADD_TO_PLAYLIST = 3;

    public static final int CONTEXT_MENUITEM_HIDE = 4;

    public static final int CONTEXT_MENUITEM_CLEAR = 5;

    public static final int CONTEXT_MENUITEM_DELETE = 6;

    public static final int CONTEXT_MENUITEM_DETAIL = 7;



    /*

     */
    public static String formatMSecs(long duration) {
        return  formatSecs((int) (duration / 1000));
    }

    public static String formatSecs(int durationSecs) {
        int mins = (durationSecs / 60);
        int secs = (durationSecs % 60);

        return  (mins < 10 ? "0" + mins : mins) + (secs < 10 ? (secs == 0 ? ":00" : ":0" + secs) : ":" + secs);
    }

    public static void createSongContextMenu(Menu menu, int groupId, boolean visible) {
        createSongContextMenu(menu, groupId, visible, false);
    }

    public static void createSongContextMenu(Menu menu, int groupId, boolean visible, boolean isPlaylistDetails) {
        menu.add(groupId, CONTEXT_MENUITEM_PLAY, 1, R.string.context_menu_play);
        if (PlayerApplication.libraryManagerIndex == PlayerApplication.playerManagerIndex) {
            menu.add(groupId, CONTEXT_MENUITEM_PLAY_NEXT, 2, R.string.context_menu_play_next);
            menu.add(groupId, CONTEXT_MENUITEM_ADD_TO_QUEUE, 3, R.string.context_menu_add_to_queue);
        }
        menu.add(groupId, CONTEXT_MENUITEM_ADD_TO_PLAYLIST, 4, R.string.context_menu_add_to_playlist);

        if (isPlaylistDetails) {
            menu.add(groupId, CONTEXT_MENUITEM_CLEAR, 5, R.string.context_menu_remove_all);
            menu.add(groupId, CONTEXT_MENUITEM_DELETE, 6, R.string.context_menu_remove);
        }
        else {
            menu.add(groupId, CONTEXT_MENUITEM_HIDE, 5, visible ? R.string.context_menu_hide : R.string.context_menu_show);
        }
        menu.add(groupId, CONTEXT_MENUITEM_DETAIL, 7, R.string.context_menu_details);
    }

    public static boolean songContextItemSelected(Activity hostActivity, int itemId, String songId, int position) {
        return songContextItemSelected(hostActivity, itemId, songId, position, null, 0);
    }

    public static boolean songContextItemSelected(Activity hostActivity, int itemId, String songId, int position, String playlistId, int playlistPosition) {
        Log.e(TAG, "playlistPosition = " + playlistPosition);
        switch (itemId) {
            case CONTEXT_MENUITEM_PLAY:
                return MusicConnector.doContextActionPlay(AbstractMediaProvider.ContentType.CONTENT_TYPE_DEFAULT, null, MusicConnector.songs_sort_order, position);
            case CONTEXT_MENUITEM_PLAY_NEXT:
                return MusicConnector.doContextActionPlayNext(AbstractMediaProvider.ContentType.CONTENT_TYPE_MEDIA, songId, MusicConnector.songs_sort_order);
            case CONTEXT_MENUITEM_ADD_TO_QUEUE:
                return MusicConnector.doContextActionAddToQueue(AbstractMediaProvider.ContentType.CONTENT_TYPE_MEDIA, songId, MusicConnector.songs_sort_order);
            case CONTEXT_MENUITEM_ADD_TO_PLAYLIST:
                return MusicConnector.doContextActionAddToPlaylist(hostActivity, AbstractMediaProvider.ContentType.CONTENT_TYPE_MEDIA, songId, MusicConnector.songs_sort_order);
            case CONTEXT_MENUITEM_HIDE:
                return MusicConnector.doContextActionToggleVisibility(AbstractMediaProvider.ContentType.CONTENT_TYPE_MEDIA, songId);
            case CONTEXT_MENUITEM_DETAIL:
                MusicConnector.doContextActionDetail(hostActivity, AbstractMediaProvider.ContentType.CONTENT_TYPE_MEDIA, songId);
                return false;
            case CONTEXT_MENUITEM_DELETE:
                MusicConnector.doContextActionMediaRemoveFromQueue(playlistId, position);
                return true;
            case CONTEXT_MENUITEM_CLEAR:
                MusicConnector.doContextActionPlaylistClear(playlistId);
                return true;
            default:
                return false;
        }
    }

    public static void createPlaylistContextMenu(Menu menu, int groupId, boolean visible) {
        menu.add(groupId, CONTEXT_MENUITEM_PLAY, 1, R.string.context_menu_play);
        if (PlayerApplication.libraryManagerIndex == PlayerApplication.playerManagerIndex) {
            menu.add(groupId, CONTEXT_MENUITEM_PLAY_NEXT, 2, R.string.context_menu_play_next);
            menu.add(groupId, CONTEXT_MENUITEM_ADD_TO_QUEUE, 3, R.string.context_menu_add_to_queue);
        }
        menu.add(groupId, CONTEXT_MENUITEM_HIDE, 4, visible ? R.string.context_menu_hide : R.string.context_menu_show);
        menu.add(groupId, CONTEXT_MENUITEM_DELETE, 5, R.string.context_menu_delete);
    }

    public static boolean playlistDetailContextItemSelected(FragmentActivity hostActivity, int itemId, String playlistId, int sortOrder, int position, String songId) {
        switch (itemId) {
            case CONTEXT_MENUITEM_PLAY:
                return MusicConnector.doContextActionPlay(AbstractMediaProvider.ContentType.CONTENT_TYPE_PLAYLIST, playlistId, sortOrder, position);
            case CONTEXT_MENUITEM_PLAY_NEXT:
                return MusicConnector.doContextActionPlayNext(AbstractMediaProvider.ContentType.CONTENT_TYPE_MEDIA, songId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_QUEUE:
                return MusicConnector.doContextActionAddToQueue(AbstractMediaProvider.ContentType.CONTENT_TYPE_MEDIA, songId, sortOrder);
            case CONTEXT_MENUITEM_HIDE:
                return MusicConnector.doContextActionToggleVisibility(AbstractMediaProvider.ContentType.CONTENT_TYPE_MEDIA, songId);
            case CONTEXT_MENUITEM_DETAIL:
                MusicConnector.doContextActionDetail(hostActivity, AbstractMediaProvider.ContentType.CONTENT_TYPE_MEDIA, songId);
                return false;
            case CONTEXT_MENUITEM_DELETE:
                return MusicConnector.doContextActionPlaylistDelete(hostActivity, playlistId);
            default:
                return false;
        }
    }

    public static boolean playlistContextItemSelected(Activity hostActivity, int itemId, String playlistId, int sortOrder, int position) {
        switch (itemId) {
            case CONTEXT_MENUITEM_PLAY:
                return MusicConnector.doContextActionPlay(AbstractMediaProvider.ContentType.CONTENT_TYPE_PLAYLIST, playlistId, sortOrder, position);
            case CONTEXT_MENUITEM_PLAY_NEXT:
                return MusicConnector.doContextActionPlayNext(AbstractMediaProvider.ContentType.CONTENT_TYPE_PLAYLIST, playlistId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_QUEUE:
                return MusicConnector.doContextActionAddToQueue(AbstractMediaProvider.ContentType.CONTENT_TYPE_PLAYLIST, playlistId, sortOrder);
            case CONTEXT_MENUITEM_HIDE:
                return MusicConnector.doContextActionToggleVisibility(AbstractMediaProvider.ContentType.CONTENT_TYPE_PLAYLIST, playlistId);
            case CONTEXT_MENUITEM_DELETE:
                return MusicConnector.doContextActionPlaylistDelete(hostActivity, playlistId);
            default:
                return false;
        }
    }

    public static void createGenreContextMenu(Menu menu, int groupId, boolean visible) {
        menu.add(groupId, CONTEXT_MENUITEM_PLAY, 1, R.string.context_menu_play);
        if (PlayerApplication.libraryManagerIndex == PlayerApplication.playerManagerIndex) {
            menu.add(groupId, CONTEXT_MENUITEM_PLAY_NEXT, 2, R.string.context_menu_play_next);
            menu.add(groupId, CONTEXT_MENUITEM_ADD_TO_QUEUE, 3, R.string.context_menu_add_to_queue);
        }
        menu.add(groupId, CONTEXT_MENUITEM_ADD_TO_PLAYLIST, 4, R.string.context_menu_add_to_playlist);
        menu.add(groupId, CONTEXT_MENUITEM_HIDE, 5, visible ? R.string.context_menu_hide : R.string.context_menu_show);
    }

    public static boolean genreDetailContextItemSelected(FragmentActivity hostActivity, int itemId, String genreId, int sortOrder, int position, String albumId) {
        switch (itemId) {
            case CONTEXT_MENUITEM_PLAY:
                return MusicConnector.doContextActionPlay(AbstractMediaProvider.ContentType.CONTENT_TYPE_GENRE, genreId, sortOrder, position);
            case CONTEXT_MENUITEM_PLAY_NEXT:
                return MusicConnector.doContextActionPlayNext(AbstractMediaProvider.ContentType.CONTENT_TYPE_ALBUM, albumId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_QUEUE:
                return MusicConnector.doContextActionAddToQueue(AbstractMediaProvider.ContentType.CONTENT_TYPE_ALBUM, albumId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_PLAYLIST:
                return MusicConnector.doContextActionAddToPlaylist(hostActivity, AbstractMediaProvider.ContentType.CONTENT_TYPE_ALBUM, albumId, sortOrder);
            case CONTEXT_MENUITEM_HIDE:
                return MusicConnector.doContextActionToggleVisibility(AbstractMediaProvider.ContentType.CONTENT_TYPE_ALBUM, albumId);
            default:
                return false;
        }
    }

    public static boolean genreContextItemSelected(Activity hostActivity, int itemId, String genreId, int sortOrder, int position) {
        switch (itemId) {
            case CONTEXT_MENUITEM_PLAY:
                return MusicConnector.doContextActionPlay(AbstractMediaProvider.ContentType.CONTENT_TYPE_GENRE, genreId, sortOrder, position);
            case CONTEXT_MENUITEM_PLAY_NEXT:
                return MusicConnector.doContextActionPlayNext(AbstractMediaProvider.ContentType.CONTENT_TYPE_GENRE, genreId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_QUEUE:
                return MusicConnector.doContextActionAddToQueue(AbstractMediaProvider.ContentType.CONTENT_TYPE_GENRE, genreId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_PLAYLIST:
                return MusicConnector.doContextActionAddToPlaylist(hostActivity, AbstractMediaProvider.ContentType.CONTENT_TYPE_GENRE, genreId, sortOrder);
            case CONTEXT_MENUITEM_HIDE:
                return MusicConnector.doContextActionToggleVisibility(AbstractMediaProvider.ContentType.CONTENT_TYPE_GENRE, genreId);
            default:
                return false;
        }
    }

    public static void createArtistContextMenu(Menu menu, int groupId, boolean visible) {
        menu.add(groupId, CONTEXT_MENUITEM_PLAY, 1, R.string.context_menu_play);
        if (PlayerApplication.libraryManagerIndex == PlayerApplication.playerManagerIndex) {
            menu.add(groupId, CONTEXT_MENUITEM_PLAY_NEXT, 2, R.string.context_menu_play_next);
            menu.add(groupId, CONTEXT_MENUITEM_ADD_TO_QUEUE, 3, R.string.context_menu_add_to_queue);
        }
        menu.add(groupId, CONTEXT_MENUITEM_ADD_TO_PLAYLIST, 4, R.string.context_menu_add_to_playlist);
        menu.add(groupId, CONTEXT_MENUITEM_HIDE, 5, visible ? R.string.context_menu_hide : R.string.context_menu_show);
    }

    public static boolean artistDetailContextItemSelected(FragmentActivity hostActivity, int itemId, String artistId, int sortOrder, int position, String songId) {
        switch (itemId) {
            case CONTEXT_MENUITEM_PLAY:
                return MusicConnector.doContextActionPlay(AbstractMediaProvider.ContentType.CONTENT_TYPE_ARTIST, artistId, sortOrder, position);
            case CONTEXT_MENUITEM_PLAY_NEXT:
                return MusicConnector.doContextActionPlayNext(AbstractMediaProvider.ContentType.CONTENT_TYPE_MEDIA, songId, 0);
            case CONTEXT_MENUITEM_ADD_TO_QUEUE:
                return MusicConnector.doContextActionAddToQueue(AbstractMediaProvider.ContentType.CONTENT_TYPE_MEDIA, songId, 0);
            case CONTEXT_MENUITEM_ADD_TO_PLAYLIST:
                return MusicConnector.doContextActionAddToPlaylist(hostActivity, AbstractMediaProvider.ContentType.CONTENT_TYPE_MEDIA, songId, 0);
            case CONTEXT_MENUITEM_HIDE:
                return MusicConnector.doContextActionToggleVisibility(AbstractMediaProvider.ContentType.CONTENT_TYPE_MEDIA, songId);
            default:
                return false;
        }
    }

    public static boolean artistContextItemSelected(Activity hostActivity, int itemId, String artistId, int sortOrder, int position) {
        switch (itemId) {
            case CONTEXT_MENUITEM_PLAY:
                return MusicConnector.doContextActionPlay(AbstractMediaProvider.ContentType.CONTENT_TYPE_ARTIST, artistId, sortOrder, position);
            case CONTEXT_MENUITEM_PLAY_NEXT:
                return MusicConnector.doContextActionPlayNext(AbstractMediaProvider.ContentType.CONTENT_TYPE_ARTIST, artistId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_QUEUE:
                return MusicConnector.doContextActionAddToQueue(AbstractMediaProvider.ContentType.CONTENT_TYPE_ARTIST, artistId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_PLAYLIST:
                return MusicConnector.doContextActionAddToPlaylist(hostActivity, AbstractMediaProvider.ContentType.CONTENT_TYPE_ARTIST, artistId, sortOrder);
            case CONTEXT_MENUITEM_HIDE:
                return MusicConnector.doContextActionToggleVisibility(AbstractMediaProvider.ContentType.CONTENT_TYPE_ARTIST, artistId);
            default:
                return false;
        }
    }

    public static void createAlbumContextMenu(Menu menu, int groupId, boolean visible) {
        menu.add(groupId, CONTEXT_MENUITEM_PLAY, 1, R.string.context_menu_play);
        if (PlayerApplication.libraryManagerIndex == PlayerApplication.playerManagerIndex) {
            menu.add(groupId, CONTEXT_MENUITEM_PLAY_NEXT, 2, R.string.context_menu_play_next);
            menu.add(groupId, CONTEXT_MENUITEM_ADD_TO_QUEUE, 3, R.string.context_menu_add_to_queue);
        }
        menu.add(groupId, CONTEXT_MENUITEM_ADD_TO_PLAYLIST, 4, R.string.context_menu_add_to_playlist);
        menu.add(groupId, CONTEXT_MENUITEM_HIDE, 5, visible ? R.string.context_menu_hide : R.string.context_menu_show);
        menu.add(groupId, CONTEXT_MENUITEM_DETAIL, 6, R.string.context_menu_details);
    }

    public static boolean albumDetailContextItemSelected(FragmentActivity hostActivity, int itemId, String albumId, int sortOrder, int position, String songId) {
        switch (itemId) {
            case CONTEXT_MENUITEM_PLAY:
                return MusicConnector.doContextActionPlay(AbstractMediaProvider.ContentType.CONTENT_TYPE_ALBUM, albumId, sortOrder, position);
            case CONTEXT_MENUITEM_PLAY_NEXT:
                return MusicConnector.doContextActionPlayNext(AbstractMediaProvider.ContentType.CONTENT_TYPE_MEDIA, songId, 0);
            case CONTEXT_MENUITEM_ADD_TO_QUEUE:
                return MusicConnector.doContextActionAddToQueue(AbstractMediaProvider.ContentType.CONTENT_TYPE_MEDIA, songId, 0);
            case CONTEXT_MENUITEM_ADD_TO_PLAYLIST:
                return MusicConnector.doContextActionAddToPlaylist(hostActivity, AbstractMediaProvider.ContentType.CONTENT_TYPE_MEDIA, songId, 0);
            case CONTEXT_MENUITEM_HIDE:
                return MusicConnector.doContextActionToggleVisibility(AbstractMediaProvider.ContentType.CONTENT_TYPE_MEDIA, songId);
            case CONTEXT_MENUITEM_DETAIL:
                MusicConnector.doContextActionDetail(hostActivity, AbstractMediaProvider.ContentType.CONTENT_TYPE_MEDIA, songId);
                return false;
            default:
                return false;
        }
    }

    public static boolean albumContextItemSelected(Activity hostActivity, int itemId, String albumId, int sortOrder, int position) {
        switch (itemId) {
            case CONTEXT_MENUITEM_PLAY:
                return MusicConnector.doContextActionPlay(AbstractMediaProvider.ContentType.CONTENT_TYPE_ALBUM, albumId, sortOrder, position);
            case CONTEXT_MENUITEM_PLAY_NEXT:
                return MusicConnector.doContextActionPlayNext(AbstractMediaProvider.ContentType.CONTENT_TYPE_ALBUM, albumId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_QUEUE:
                return MusicConnector.doContextActionAddToQueue(AbstractMediaProvider.ContentType.CONTENT_TYPE_ALBUM, albumId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_PLAYLIST:
                return MusicConnector.doContextActionAddToPlaylist(hostActivity, AbstractMediaProvider.ContentType.CONTENT_TYPE_ALBUM, albumId, sortOrder);
            case CONTEXT_MENUITEM_HIDE:
                return MusicConnector.doContextActionToggleVisibility(AbstractMediaProvider.ContentType.CONTENT_TYPE_ALBUM, albumId);
            case CONTEXT_MENUITEM_DETAIL:
                MusicConnector.doContextActionDetail(hostActivity, AbstractMediaProvider.ContentType.CONTENT_TYPE_ALBUM, albumId);
                return false;
            default:
                return false;
        }
    }

    public static void createAlbumArtistContextMenu(Menu menu, int groupId, boolean visible) {
        menu.add(groupId, CONTEXT_MENUITEM_PLAY, 1, R.string.context_menu_play);
        if (PlayerApplication.libraryManagerIndex == PlayerApplication.playerManagerIndex) {
            menu.add(groupId, CONTEXT_MENUITEM_PLAY_NEXT, 2, R.string.context_menu_play_next);
            menu.add(groupId, CONTEXT_MENUITEM_ADD_TO_QUEUE, 3, R.string.context_menu_add_to_queue);
        }
        menu.add(groupId, CONTEXT_MENUITEM_ADD_TO_PLAYLIST, 4, R.string.context_menu_add_to_playlist);
        menu.add(groupId, CONTEXT_MENUITEM_HIDE, 5, visible ? R.string.context_menu_hide : R.string.context_menu_show);
    }

    public static boolean albumArtistDetailContextItemSelected(FragmentActivity hostActivity, int itemId, int sortOrder, String albumId) {
        switch (itemId) {
            case CONTEXT_MENUITEM_PLAY:
                return MusicConnector.doContextActionPlay(AbstractMediaProvider.ContentType.CONTENT_TYPE_ALBUM, albumId, sortOrder, 0);
            case CONTEXT_MENUITEM_PLAY_NEXT:
                return MusicConnector.doContextActionPlayNext(AbstractMediaProvider.ContentType.CONTENT_TYPE_ALBUM, albumId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_QUEUE:
                return MusicConnector.doContextActionAddToQueue(AbstractMediaProvider.ContentType.CONTENT_TYPE_ALBUM, albumId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_PLAYLIST:
                return MusicConnector.doContextActionAddToPlaylist(hostActivity, AbstractMediaProvider.ContentType.CONTENT_TYPE_ALBUM, albumId, sortOrder);
            case CONTEXT_MENUITEM_HIDE:
                return MusicConnector.doContextActionToggleVisibility(AbstractMediaProvider.ContentType.CONTENT_TYPE_ALBUM, albumId);
            default:
                return false;
        }
    }

    public static boolean albumArtistContextItemSelected(Activity hostActivity, int itemId, String albumArtistId, int sortOrder, int position) {
        switch (itemId) {
            case CONTEXT_MENUITEM_PLAY:
                return MusicConnector.doContextActionPlay(AbstractMediaProvider.ContentType.CONTENT_TYPE_ALBUM_ARTIST, albumArtistId, sortOrder, position);
            case CONTEXT_MENUITEM_PLAY_NEXT:
                return MusicConnector.doContextActionPlayNext(AbstractMediaProvider.ContentType.CONTENT_TYPE_ALBUM_ARTIST, albumArtistId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_QUEUE:
                return MusicConnector.doContextActionAddToQueue(AbstractMediaProvider.ContentType.CONTENT_TYPE_ALBUM_ARTIST, albumArtistId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_PLAYLIST:
                return MusicConnector.doContextActionAddToPlaylist(hostActivity, AbstractMediaProvider.ContentType.CONTENT_TYPE_ALBUM_ARTIST, albumArtistId, sortOrder);
            case CONTEXT_MENUITEM_HIDE:
                return MusicConnector.doContextActionToggleVisibility(AbstractMediaProvider.ContentType.CONTENT_TYPE_ALBUM_ARTIST, albumArtistId);
            default:
                return false;
        }
    }

    public static void createStorageContextMenu(Menu menu, int groupId) {
        menu.add(groupId, CONTEXT_MENUITEM_PLAY, 1, R.string.context_menu_play);
        if (PlayerApplication.libraryManagerIndex == PlayerApplication.playerManagerIndex) {
            menu.add(groupId, CONTEXT_MENUITEM_PLAY_NEXT, 2, R.string.context_menu_play_next);
            menu.add(groupId, CONTEXT_MENUITEM_ADD_TO_QUEUE, 3, R.string.context_menu_add_to_queue);
        }
    }

    public static boolean storageContextItemSelected(int itemId, String storageId, int sortOrder, int position) {
        switch (itemId) {
            case CONTEXT_MENUITEM_PLAY:
                return MusicConnector.doContextActionPlay(AbstractMediaProvider.ContentType.CONTENT_TYPE_STORAGE, storageId, sortOrder, position);
            case CONTEXT_MENUITEM_PLAY_NEXT:
                return MusicConnector.doContextActionPlayNext(AbstractMediaProvider.ContentType.CONTENT_TYPE_STORAGE, storageId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_QUEUE:
                return MusicConnector.doContextActionAddToQueue(AbstractMediaProvider.ContentType.CONTENT_TYPE_STORAGE, storageId, sortOrder);
            default:
                return false;
        }
    }



    public static String fileToUri(File file) {
        if (file == null) {
            return null;
        }

        return String.format("file://%s%s%s", file.getParent(), File.separator, file.getName());
    }

    public static File uriToFile(String uri) {
        if (uri == null) {
            return null;
        }

        if (uri.startsWith("file://")) {
            return new File(uri.substring(7, uri.length()));
        }

        return new File(uri);
    }

    public static int getManagerIndex(int providerId) {

        for (int managerIndex = 0 ; managerIndex < PlayerApplication.mediaManagers.length ; managerIndex++) {
            final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[managerIndex];

            if (mediaManager.getMediaManagerId() == providerId) {
                return managerIndex;
            }
        }

        return -1;
    }



    @TargetApi(8)
    public static File getMusicDirectory() {
        if (hasFroyo()) {
            return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        }

        return new File(Environment.getExternalStorageDirectory().getPath() + "/Music/");
    }

    public static void saveLibraryIndexes() {
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        final SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.putInt(PREFERENCE_LIBRARY_PLAYER_INDEX, playerManagerIndex);
        editor.putInt(PREFERENCE_LIBRARY_LIBRARY_INDEX, libraryManagerIndex);
        editor.apply();
    }

    public static int getLibraryPlayerIndex() {
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPreferences.getInt(PREFERENCE_LIBRARY_PLAYER_INDEX, 0);
    }

    public static int getLibraryLibraryIndex() {
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPreferences.getInt(PREFERENCE_LIBRARY_LIBRARY_INDEX, 0);
    }




	/*
	 * PlayerActivity Preferences
	 */
    public final static String PREFERENCE_LIBRARY_PLAYER_INDEX = "playlist_index";

    public final static String PREFERENCE_LIBRARY_LIBRARY_INDEX = "library_index";

	public final static String PREFERENCE_PLAYER_LAST_REPEAT_MODE = "last_repeat_mode";
	
	public final static String PREFERENCE_PLAYER_LAST_SHUFFLE_MODE = "last_shuffle_mode";
	
	public final static String PREFERENCE_PLAYER_LAST_PLAYLIST_POSITION = "last_playlist_position";

	public static AlertDialog showOpenSourceDialog(final Context context) {
		final WebView webView = new WebView(context);
		webView.loadUrl("file:///android_asset/licenses.html");
		return new AlertDialog.Builder(context)
				.setTitle(R.string.preference_licenses_title)
				.setView(webView)
				.setPositiveButton(android.R.string.ok,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(final DialogInterface dialog,
									final int whichButton) {
								dialog.dismiss();
							}
						}).create();
	}

    public static boolean hasFroyo() {
        // Can use static final constants like FROYO, declared in later versions
        // of the OS since they are inlined at compile time. This is guaranteed behavior.
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO;
    }

    public static boolean hasHoneycomb() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB;
    }

    public static boolean hasICS() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH;
    }

    public static boolean hasICS_MR1() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1;
    }

    public static boolean hasJellyBean() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;
    }

    public static boolean hasKitkat() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
    }

	public static float convertPixelsToDp(float width, Context context) {
		return width / (context.getResources().getDisplayMetrics().densityDpi / 160.0f);
	}
	
	public static int getListColumns() {
		float width = convertPixelsToDp(context.getResources().getDisplayMetrics().widthPixels, context);
        return (int) Math.floor(width / 225.0F);
	}

	public static final int NOTIFICATION_PLAY_ID = 1;
	
	public static String getVariousArtists() {
        return PlayerApplication.context.getString(R.string.preference_various_artists_default_value);
	}
}
