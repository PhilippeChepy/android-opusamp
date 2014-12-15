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
package net.opusapp.player.ui.utils;

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
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.Loader;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.View;
import android.webkit.WebView;

import com.astuetz.PagerSlidingTabStrip;

import net.opusapp.player.R;
import net.opusapp.player.core.service.PlayerService;
import net.opusapp.player.core.service.providers.AbstractMediaManager;
import net.opusapp.player.core.service.providers.MediaManagerFactory;
import net.opusapp.player.core.service.providers.index.database.Entities;
import net.opusapp.player.core.service.providers.index.database.OpenHelper;
import net.opusapp.player.core.service.utils.AbstractSimpleCursorLoader;
import net.opusapp.player.core.service.utils.CursorUtils;
import net.opusapp.player.licensing.BuildSpecific;

import java.io.File;
import java.util.ArrayList;

public class PlayerApplication extends Application implements ServiceConnection {

	public final static String TAG = PlayerApplication.class.getSimpleName();



    // Global application context
    public static Context context;

    // Global application instance
    public static PlayerApplication instance;

    // Global service reference.
    public static PlayerService.PlayerBinder playerBinder = null;

    public static PlayerService playerService = null;

    private static ArrayList<ServiceConnection> mServiceListenerList = new ArrayList<>();



    //
    private static SQLiteOpenHelper databaseHelper;

    public static int playerManagerIndex = 0;

    public static int libraryManagerIndex = 0;

    public static AbstractMediaManager mediaManagers[] = null;

    public static SQLiteOpenHelper getDatabaseOpenHelper() {
        return databaseHelper;
    }


    private static boolean connecting;



    // UI/UX communication
    public static final String CONTENT_TYPE_KEY = "type_key";

    public static final String CONTENT_SOURCE_ID_KEY = "id_key";

    public static final String CONTENT_SOURCE_DESCRIPTION_KEY = "description_key";



    private static final String CONFIG_FILE = "global-config";



    @Override
    public void onCreate() {
        super.onCreate();

        context = getApplicationContext();
        instance = this;

        databaseHelper = new OpenHelper();
        allocateMediaManagers();
        playerManagerIndex = getLibraryPlayerIndex();
        libraryManagerIndex = getLibraryLibraryIndex();

        BuildSpecific.initApp();
    }

    public synchronized static void connectService(ServiceConnection serviceListener) {
        if (connecting) {
            return;
        }

        if (serviceListener != null) {
            if (!mServiceListenerList.contains(serviceListener)) {
                mServiceListenerList.add(serviceListener);
            }
        }

        if (playerService == null) {
            connecting = true;
            if (context != null) {
                final Intent playerService = new Intent(context, PlayerService.class);
                context.bindService(playerService, instance, Context.BIND_AUTO_CREATE);
                context.startService(playerService);
            }
            connecting = false;
        }
        else if (serviceListener != null) {
            serviceListener.onServiceConnected(null, playerBinder);
        }
    }

    public synchronized static void disconnectService(ServiceConnection serviceListener) {
        if (serviceListener != null && mServiceListenerList.contains(serviceListener)) {
            mServiceListenerList.remove(serviceListener);
        }
    }

    @Override
    public synchronized void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        playerBinder = (PlayerService.PlayerBinder)iBinder;
        playerService = playerBinder.getService();

        for (ServiceConnection callback : mServiceListenerList) {
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
            if (!currentProvider.getPlayer().playerIsPlaying()) {
                // ensure media player is completly stopped if paused.
                currentProvider.getPlayer().playerStop();
                currentProvider = null;
            }
            else {
                currentProviderId = currentProvider.getMediaManagerId();
                currentProviderType = currentProvider.getMediaManagerType();
            }
        }

        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        if (database != null) {
            final String[] columns = new String[]{
                    Entities.Provider._ID,
                    Entities.Provider.COLUMN_FIELD_PROVIDER_TYPE
            };

            final int COLUMN_ID = 0;

            final int COLUMN_TYPE = 1;

            final String orderBy = Entities.Provider.COLUMN_FIELD_PROVIDER_POSITION;

            final Cursor cursor = database.query(Entities.Provider.TABLE_NAME, columns, null, null, null, null, orderBy);
            if (CursorUtils.ifNotEmpty(cursor)) {
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

                        // load equalizer settings
                        AbstractMediaManager.Player player = mediaManagers[index].getPlayer();

                        restoreEqualizerSettings(player);
                        player.equalizerApplyProperties();
                    }
                }

                CursorUtils.free(cursor);
            }

            if (currentProvider != null) {
                if (currentProvider.getPlayer().playerIsPlaying()) {
                    currentProvider.getPlayer().playerStop();
                }

                if (currentProvider.getProvider().scanIsRunning()) {
                    currentProvider.getProvider().scanCancel();
                }
                currentProvider.getProvider().erase();
            }

            if (mediaManagers == null) {
                libraryManagerIndex = -1;
                playerManagerIndex = -1;
            }
            else {
                if (libraryManagerIndex >= mediaManagers.length) {
                    libraryManagerIndex = 0;
                }

                if (playerManagerIndex >= mediaManagers.length) {
                    playerManagerIndex = 0;
                }
            }
        }

        if (playerService != null) {
            playerService.notifyProviderChanged();
        }
    }

    public static Loader<Cursor> buildAlbumArtistLoader(final int managerIndex, final int[] requestedFields, final int[] sortFields, final String filter) {
        return new AbstractSimpleCursorLoader(PlayerApplication.context) {
            @Override
            public Cursor loadInBackground() {
                return  mediaManagers[managerIndex].getProvider().buildCursor(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM_ARTIST, requestedFields, sortFields, filter, null, null);
            }
        };
    }

    public static Loader<Cursor> buildAlbumLoader(final int managerIndex, final int[] requestedFields, final int[] sortFields, final String filter, final AbstractMediaManager.Provider.ContentType contentType, final String sourceId) {
        return new AbstractSimpleCursorLoader(PlayerApplication.context) {
            @Override
            public Cursor loadInBackground() {
                return  mediaManagers[managerIndex].getProvider().buildCursor(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM, requestedFields, sortFields, filter, contentType, sourceId);
            }
        };
    }

    public static Loader<Cursor> buildArtistLoader(final int managerIndex, final int[] requestedFields, final int[] sortFields, final String filter) {
        return new AbstractSimpleCursorLoader(PlayerApplication.context) {
            @Override
            public Cursor loadInBackground() {
                return  mediaManagers[managerIndex].getProvider().buildCursor(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_ARTIST, requestedFields, sortFields, filter, null, null);
            }
        };
    }

    public static Loader<Cursor> buildGenreLoader(final int managerIndex, final int[] requestedFields, final int[] sortFields, final String filter) {
        return new AbstractSimpleCursorLoader(PlayerApplication.context) {
            @Override
            public Cursor loadInBackground() {
                return  mediaManagers[managerIndex].getProvider().buildCursor(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_GENRE, requestedFields, sortFields, filter, null, null);
            }
        };
    }

    public static Loader<Cursor> buildPlaylistLoader(final int managerIndex, final int[] requestedFields, final int[] sortFields, final String filter) {
        return new AbstractSimpleCursorLoader(PlayerApplication.context) {
            @Override
            public Cursor loadInBackground() {
                return  mediaManagers[managerIndex].getProvider().buildCursor(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_PLAYLIST, requestedFields, sortFields, filter, null, null);
            }
        };
    }

    public static Loader<Cursor> buildMediaLoader(final int managerIndex, final int[] requestedFields, final int[] sortFields, final String filter, final AbstractMediaManager.Provider.ContentType contentType, final String sourceId) {
        return new AbstractSimpleCursorLoader(PlayerApplication.context) {
            @Override
            public Cursor loadInBackground() {
                return  mediaManagers[managerIndex].getProvider().buildCursor(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, requestedFields, sortFields, filter, contentType, sourceId);
            }
        };
    }

    public static Loader<Cursor> buildStorageLoader(final int managerIndex, final int[] requestedFields, final int[] sortFields, final String filter) {
        return new AbstractSimpleCursorLoader(PlayerApplication.context) {
            @Override
            public Cursor loadInBackground() {
                return  mediaManagers[managerIndex].getProvider().buildCursor(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_STORAGE, requestedFields, sortFields, filter, null, null);
            }
        };
    }



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

    public static String formatSecs(long durationSecs) {
        long mins = (durationSecs / 60);
        long secs = (durationSecs % 60);

        return  (mins < 10 ? "0" + mins : mins) + (secs < 10 ? (secs == 0 ? ":00" : ":0" + secs) : ":" + secs);
    }

    public static void createSongContextMenu(Menu menu, int groupId, boolean visible) {
        createSongContextMenu(menu, groupId, visible, false);
    }

    public static void createSongContextMenu(Menu menu, int groupId, boolean visible, boolean isPlaylistDetails) {
        menu.add(groupId, CONTEXT_MENUITEM_PLAY, 1, R.string.menuitem_label_play);
        if (PlayerApplication.libraryManagerIndex == PlayerApplication.playerManagerIndex) {
            menu.add(groupId, CONTEXT_MENUITEM_PLAY_NEXT, 2, R.string.menuitem_label_play_next);
            menu.add(groupId, CONTEXT_MENUITEM_ADD_TO_QUEUE, 3, R.string.menuitem_label_add_to_queue);
        }
        menu.add(groupId, CONTEXT_MENUITEM_ADD_TO_PLAYLIST, 4, R.string.menuitem_label_add_to_playlist);

        if (isPlaylistDetails) {
            menu.add(groupId, CONTEXT_MENUITEM_CLEAR, 5, R.string.menuitem_label_remove_all);
            menu.add(groupId, CONTEXT_MENUITEM_DELETE, 6, R.string.menuitem_label_remove);
        }
        else {
            menu.add(groupId, CONTEXT_MENUITEM_HIDE, 5, visible ? R.string.menuitem_label_hide : R.string.menuitem_label_show);
        }
        menu.add(groupId, CONTEXT_MENUITEM_DETAIL, 7, R.string.menuitem_label_details);
    }

    public static boolean songContextItemSelected(Activity hostActivity, int itemId, String songId, int position) {
        return songContextItemSelected(hostActivity, itemId, songId, position, null);
    }

    public static boolean songContextItemSelected(Activity hostActivity, int itemId, String songId, int position, String playlistId) {
        switch (itemId) {
            case CONTEXT_MENUITEM_PLAY:
                return MusicConnector.doContextActionPlay(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_DEFAULT, null, MusicConnector.songs_sort_order, position);
            case CONTEXT_MENUITEM_PLAY_NEXT:
                return MusicConnector.doContextActionPlayNext(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, songId, MusicConnector.songs_sort_order);
            case CONTEXT_MENUITEM_ADD_TO_QUEUE:
                return MusicConnector.doContextActionAddToQueue(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, songId, MusicConnector.songs_sort_order);
            case CONTEXT_MENUITEM_ADD_TO_PLAYLIST:
                return MusicConnector.doContextActionAddToPlaylist(hostActivity, AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, songId, MusicConnector.songs_sort_order);
            case CONTEXT_MENUITEM_HIDE:
                return MusicConnector.doContextActionToggleVisibility(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, songId);
            case CONTEXT_MENUITEM_DETAIL:
                MusicConnector.doContextActionDetail(hostActivity, AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, songId);
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
        menu.add(groupId, CONTEXT_MENUITEM_PLAY, 1, R.string.menuitem_label_play);
        if (PlayerApplication.libraryManagerIndex == PlayerApplication.playerManagerIndex) {
            menu.add(groupId, CONTEXT_MENUITEM_PLAY_NEXT, 2, R.string.menuitem_label_play_next);
            menu.add(groupId, CONTEXT_MENUITEM_ADD_TO_QUEUE, 3, R.string.menuitem_label_add_to_queue);
        }
        menu.add(groupId, CONTEXT_MENUITEM_HIDE, 4, visible ? R.string.menuitem_label_hide : R.string.menuitem_label_show);
        menu.add(groupId, CONTEXT_MENUITEM_DELETE, 5, R.string.menuitem_label_delete);
    }

    public static boolean playlistDetailContextItemSelected(FragmentActivity hostActivity, int itemId, String playlistId, int sortOrder, int position, String songId) {
        switch (itemId) {
            case CONTEXT_MENUITEM_PLAY:
                return MusicConnector.doContextActionPlay(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_PLAYLIST, playlistId, sortOrder, position);
            case CONTEXT_MENUITEM_PLAY_NEXT:
                return MusicConnector.doContextActionPlayNext(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, songId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_QUEUE:
                return MusicConnector.doContextActionAddToQueue(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, songId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_PLAYLIST:
                return MusicConnector.doContextActionAddToPlaylist(hostActivity, AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, songId, sortOrder);
            case CONTEXT_MENUITEM_HIDE:
                return MusicConnector.doContextActionToggleVisibility(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, songId);
            case CONTEXT_MENUITEM_DETAIL:
                MusicConnector.doContextActionDetail(hostActivity, AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, songId);
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

    public static boolean playlistContextItemSelected(Activity hostActivity, int itemId, String playlistId, int sortOrder, int position) {
        switch (itemId) {
            case CONTEXT_MENUITEM_PLAY:
                return MusicConnector.doContextActionPlay(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_PLAYLIST, playlistId, sortOrder, position);
            case CONTEXT_MENUITEM_PLAY_NEXT:
                return MusicConnector.doContextActionPlayNext(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_PLAYLIST, playlistId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_QUEUE:
                return MusicConnector.doContextActionAddToQueue(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_PLAYLIST, playlistId, sortOrder);
            case CONTEXT_MENUITEM_HIDE:
                return MusicConnector.doContextActionToggleVisibility(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_PLAYLIST, playlistId);
            case CONTEXT_MENUITEM_DELETE:
                return MusicConnector.doContextActionPlaylistDelete(hostActivity, playlistId);
            default:
                return false;
        }
    }

    public static void createGenreContextMenu(Menu menu, int groupId, boolean visible) {
        menu.add(groupId, CONTEXT_MENUITEM_PLAY, 1, R.string.menuitem_label_play);
        if (PlayerApplication.libraryManagerIndex == PlayerApplication.playerManagerIndex) {
            menu.add(groupId, CONTEXT_MENUITEM_PLAY_NEXT, 2, R.string.menuitem_label_play_next);
            menu.add(groupId, CONTEXT_MENUITEM_ADD_TO_QUEUE, 3, R.string.menuitem_label_add_to_queue);
        }
        menu.add(groupId, CONTEXT_MENUITEM_ADD_TO_PLAYLIST, 4, R.string.menuitem_label_add_to_playlist);
        menu.add(groupId, CONTEXT_MENUITEM_HIDE, 5, visible ? R.string.menuitem_label_hide : R.string.menuitem_label_show);
    }

    public static boolean genreDetailContextItemSelected(FragmentActivity hostActivity, int itemId, String genreId, int sortOrder, int position, String albumId) {
        switch (itemId) {
            case CONTEXT_MENUITEM_PLAY:
                return MusicConnector.doContextActionPlay(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM, albumId, sortOrder, position);
            case CONTEXT_MENUITEM_PLAY_NEXT:
                return MusicConnector.doContextActionPlayNext(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM, albumId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_QUEUE:
                return MusicConnector.doContextActionAddToQueue(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM, albumId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_PLAYLIST:
                return MusicConnector.doContextActionAddToPlaylist(hostActivity, AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM, albumId, sortOrder);
            case CONTEXT_MENUITEM_HIDE:
                return MusicConnector.doContextActionToggleVisibility(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM, albumId);
            default:
                return false;
        }
    }

    public static boolean genreContextItemSelected(Activity hostActivity, int itemId, String genreId, int sortOrder, int position) {
        switch (itemId) {
            case CONTEXT_MENUITEM_PLAY:
                return MusicConnector.doContextActionPlay(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_GENRE, genreId, sortOrder, position);
            case CONTEXT_MENUITEM_PLAY_NEXT:
                return MusicConnector.doContextActionPlayNext(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_GENRE, genreId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_QUEUE:
                return MusicConnector.doContextActionAddToQueue(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_GENRE, genreId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_PLAYLIST:
                return MusicConnector.doContextActionAddToPlaylist(hostActivity, AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_GENRE, genreId, sortOrder);
            case CONTEXT_MENUITEM_HIDE:
                return MusicConnector.doContextActionToggleVisibility(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_GENRE, genreId);
            default:
                return false;
        }
    }

    public static void createArtistContextMenu(Menu menu, int groupId, boolean visible) {
        menu.add(groupId, CONTEXT_MENUITEM_PLAY, 1, R.string.menuitem_label_play);
        if (PlayerApplication.libraryManagerIndex == PlayerApplication.playerManagerIndex) {
            menu.add(groupId, CONTEXT_MENUITEM_PLAY_NEXT, 2, R.string.menuitem_label_play_next);
            menu.add(groupId, CONTEXT_MENUITEM_ADD_TO_QUEUE, 3, R.string.menuitem_label_add_to_queue);
        }
        menu.add(groupId, CONTEXT_MENUITEM_ADD_TO_PLAYLIST, 4, R.string.menuitem_label_add_to_playlist);
        menu.add(groupId, CONTEXT_MENUITEM_HIDE, 5, visible ? R.string.menuitem_label_hide : R.string.menuitem_label_show);
    }

    public static boolean artistDetailContextItemSelected(FragmentActivity hostActivity, int itemId, String artistId, int sortOrder, int position, String songId) {
        switch (itemId) {
            case CONTEXT_MENUITEM_PLAY:
                return MusicConnector.doContextActionPlay(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_ARTIST, artistId, sortOrder, position);
            case CONTEXT_MENUITEM_PLAY_NEXT:
                return MusicConnector.doContextActionPlayNext(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, songId, 0);
            case CONTEXT_MENUITEM_ADD_TO_QUEUE:
                return MusicConnector.doContextActionAddToQueue(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, songId, 0);
            case CONTEXT_MENUITEM_ADD_TO_PLAYLIST:
                return MusicConnector.doContextActionAddToPlaylist(hostActivity, AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, songId, 0);
            case CONTEXT_MENUITEM_HIDE:
                return MusicConnector.doContextActionToggleVisibility(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, songId);
            default:
                return false;
        }
    }

    public static boolean artistContextItemSelected(Activity hostActivity, int itemId, String artistId, int sortOrder, int position) {
        switch (itemId) {
            case CONTEXT_MENUITEM_PLAY:
                return MusicConnector.doContextActionPlay(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_ARTIST, artistId, sortOrder, position);
            case CONTEXT_MENUITEM_PLAY_NEXT:
                return MusicConnector.doContextActionPlayNext(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_ARTIST, artistId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_QUEUE:
                return MusicConnector.doContextActionAddToQueue(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_ARTIST, artistId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_PLAYLIST:
                return MusicConnector.doContextActionAddToPlaylist(hostActivity, AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_ARTIST, artistId, sortOrder);
            case CONTEXT_MENUITEM_HIDE:
                return MusicConnector.doContextActionToggleVisibility(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_ARTIST, artistId);
            default:
                return false;
        }
    }

    public static void createAlbumContextMenu(Menu menu, int groupId, boolean visible) {
        menu.add(groupId, CONTEXT_MENUITEM_PLAY, 1, R.string.menuitem_label_play);
        if (PlayerApplication.libraryManagerIndex == PlayerApplication.playerManagerIndex) {
            menu.add(groupId, CONTEXT_MENUITEM_PLAY_NEXT, 2, R.string.menuitem_label_play_next);
            menu.add(groupId, CONTEXT_MENUITEM_ADD_TO_QUEUE, 3, R.string.menuitem_label_add_to_queue);
        }
        menu.add(groupId, CONTEXT_MENUITEM_ADD_TO_PLAYLIST, 4, R.string.menuitem_label_add_to_playlist);
        menu.add(groupId, CONTEXT_MENUITEM_HIDE, 5, visible ? R.string.menuitem_label_hide : R.string.menuitem_label_show);
        menu.add(groupId, CONTEXT_MENUITEM_DETAIL, 6, R.string.menuitem_label_details);
    }

    public static boolean albumDetailContextItemSelected(FragmentActivity hostActivity, int itemId, String albumId, int sortOrder, int position, String songId) {
        switch (itemId) {
            case CONTEXT_MENUITEM_PLAY:
                return MusicConnector.doContextActionPlay(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM, albumId, sortOrder, position);
            case CONTEXT_MENUITEM_PLAY_NEXT:
                return MusicConnector.doContextActionPlayNext(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, songId, 0);
            case CONTEXT_MENUITEM_ADD_TO_QUEUE:
                return MusicConnector.doContextActionAddToQueue(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, songId, 0);
            case CONTEXT_MENUITEM_ADD_TO_PLAYLIST:
                return MusicConnector.doContextActionAddToPlaylist(hostActivity, AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, songId, 0);
            case CONTEXT_MENUITEM_HIDE:
                return MusicConnector.doContextActionToggleVisibility(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, songId);
            case CONTEXT_MENUITEM_DETAIL:
                MusicConnector.doContextActionDetail(hostActivity, AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, songId);
                return false;
            default:
                return false;
        }
    }

    public static boolean albumContextItemSelected(Activity hostActivity, int itemId, String albumId, int sortOrder, int position) {
        switch (itemId) {
            case CONTEXT_MENUITEM_PLAY:
                return MusicConnector.doContextActionPlay(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM, albumId, sortOrder, position);
            case CONTEXT_MENUITEM_PLAY_NEXT:
                return MusicConnector.doContextActionPlayNext(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM, albumId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_QUEUE:
                return MusicConnector.doContextActionAddToQueue(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM, albumId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_PLAYLIST:
                return MusicConnector.doContextActionAddToPlaylist(hostActivity, AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM, albumId, sortOrder);
            case CONTEXT_MENUITEM_HIDE:
                return MusicConnector.doContextActionToggleVisibility(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM, albumId);
            case CONTEXT_MENUITEM_DETAIL:
                MusicConnector.doContextActionDetail(hostActivity, AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM, albumId);
                return false;
            default:
                return false;
        }
    }

    public static void createAlbumArtistContextMenu(Menu menu, int groupId, boolean visible) {
        menu.add(groupId, CONTEXT_MENUITEM_PLAY, 1, R.string.menuitem_label_play);
        if (PlayerApplication.libraryManagerIndex == PlayerApplication.playerManagerIndex) {
            menu.add(groupId, CONTEXT_MENUITEM_PLAY_NEXT, 2, R.string.menuitem_label_play_next);
            menu.add(groupId, CONTEXT_MENUITEM_ADD_TO_QUEUE, 3, R.string.menuitem_label_add_to_queue);
        }
        menu.add(groupId, CONTEXT_MENUITEM_ADD_TO_PLAYLIST, 4, R.string.menuitem_label_add_to_playlist);
        menu.add(groupId, CONTEXT_MENUITEM_HIDE, 5, visible ? R.string.menuitem_label_hide : R.string.menuitem_label_show);
    }

    public static boolean albumArtistDetailContextItemSelected(FragmentActivity hostActivity, int itemId, int sortOrder, String albumId) {
        switch (itemId) {
            case CONTEXT_MENUITEM_PLAY:
                return MusicConnector.doContextActionPlay(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM, albumId, sortOrder, 0);
            case CONTEXT_MENUITEM_PLAY_NEXT:
                return MusicConnector.doContextActionPlayNext(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM, albumId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_QUEUE:
                return MusicConnector.doContextActionAddToQueue(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM, albumId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_PLAYLIST:
                return MusicConnector.doContextActionAddToPlaylist(hostActivity, AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM, albumId, sortOrder);
            case CONTEXT_MENUITEM_HIDE:
                return MusicConnector.doContextActionToggleVisibility(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM, albumId);
            default:
                return false;
        }
    }

    public static boolean albumArtistContextItemSelected(Activity hostActivity, int itemId, String albumArtistId, int sortOrder, int position) {
        switch (itemId) {
            case CONTEXT_MENUITEM_PLAY:
                return MusicConnector.doContextActionPlay(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM_ARTIST, albumArtistId, sortOrder, position);
            case CONTEXT_MENUITEM_PLAY_NEXT:
                return MusicConnector.doContextActionPlayNext(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM_ARTIST, albumArtistId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_QUEUE:
                return MusicConnector.doContextActionAddToQueue(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM_ARTIST, albumArtistId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_PLAYLIST:
                return MusicConnector.doContextActionAddToPlaylist(hostActivity, AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM_ARTIST, albumArtistId, sortOrder);
            case CONTEXT_MENUITEM_HIDE:
                return MusicConnector.doContextActionToggleVisibility(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM_ARTIST, albumArtistId);
            default:
                return false;
        }
    }

    public static void createStorageContextMenu(Menu menu, int groupId) {
        menu.add(groupId, CONTEXT_MENUITEM_PLAY, 1, R.string.menuitem_label_play);
        if (PlayerApplication.libraryManagerIndex == PlayerApplication.playerManagerIndex) {
            menu.add(groupId, CONTEXT_MENUITEM_PLAY_NEXT, 2, R.string.menuitem_label_play_next);
            menu.add(groupId, CONTEXT_MENUITEM_ADD_TO_QUEUE, 3, R.string.menuitem_label_add_to_queue);
        }
    }

    public static boolean storageContextItemSelected(int itemId, String storageId, int sortOrder, int position) {
        switch (itemId) {
            case CONTEXT_MENUITEM_PLAY:
                return MusicConnector.doContextActionPlay(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_STORAGE, storageId, sortOrder, position);
            case CONTEXT_MENUITEM_PLAY_NEXT:
                return MusicConnector.doContextActionPlayNext(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_STORAGE, storageId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_QUEUE:
                return MusicConnector.doContextActionAddToQueue(AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_STORAGE, storageId, sortOrder);
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



    public static void saveLibraryIndexes() {
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        final SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.putInt(PREFERENCE_LIBRARY_PLAYER_INDEX, playerManagerIndex);
        editor.putInt(PREFERENCE_LIBRARY_LIBRARY_INDEX, libraryManagerIndex);
        editor.apply();
    }

    public static int getLibraryPlayerIndex() {
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        int libraryIndex = sharedPreferences.getInt(PREFERENCE_LIBRARY_PLAYER_INDEX, 0);
        if (libraryIndex >= mediaManagers.length) {
            libraryIndex = 0;
        }

        return libraryIndex;
    }

    public static int getLibraryLibraryIndex() {
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        int libraryIndex = sharedPreferences.getInt(PREFERENCE_LIBRARY_LIBRARY_INDEX, 0);
        if (libraryIndex >= mediaManagers.length) {
            libraryIndex = 0;
        }

        return libraryIndex;
    }



    /*
        Audio effects
     */
    public final static String CONFIG_EQUALIZER_ENABLED = "equalizer_enabled";

    public final static String CONFIG_EQUALIZER_PREAMP = "equalizer_preamp";

    public final static String CONFIG_EQUALIZER_BAND0 = "equalizer_band0";

    public final static String CONFIG_EQUALIZER_BAND1 = "equalizer_band1";

    public final static String CONFIG_EQUALIZER_BAND2 = "equalizer_band2";

    public final static String CONFIG_EQUALIZER_BAND3 = "equalizer_band3";

    public final static String CONFIG_EQUALIZER_BAND4 = "equalizer_band4";

    public final static String CONFIG_EQUALIZER_BAND5 = "equalizer_band5";

    public final static String CONFIG_EQUALIZER_BAND6 = "equalizer_band6";

    public final static String CONFIG_EQUALIZER_BAND7 = "equalizer_band7";

    public final static String CONFIG_EQUALIZER_BAND8 = "equalizer_band8";

    public final static String CONFIG_EQUALIZER_BAND9 = "equalizer_band9";



    public static void saveEqualizerSettings(AbstractMediaManager.Player player) {
        final SharedPreferences sharedPreferences = context.getSharedPreferences(CONFIG_FILE, Context.MODE_PRIVATE);
        final SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(CONFIG_EQUALIZER_ENABLED, player.equalizerIsEnabled());
        editor.putLong(CONFIG_EQUALIZER_PREAMP, player.equalizerBandGetGain(0));
        editor.putLong(CONFIG_EQUALIZER_BAND0, player.equalizerBandGetGain(1));
        editor.putLong(CONFIG_EQUALIZER_BAND1, player.equalizerBandGetGain(2));
        editor.putLong(CONFIG_EQUALIZER_BAND2, player.equalizerBandGetGain(3));
        editor.putLong(CONFIG_EQUALIZER_BAND3, player.equalizerBandGetGain(4));
        editor.putLong(CONFIG_EQUALIZER_BAND4, player.equalizerBandGetGain(5));
        editor.putLong(CONFIG_EQUALIZER_BAND5, player.equalizerBandGetGain(6));
        editor.putLong(CONFIG_EQUALIZER_BAND6, player.equalizerBandGetGain(7));
        editor.putLong(CONFIG_EQUALIZER_BAND7, player.equalizerBandGetGain(8));
        editor.putLong(CONFIG_EQUALIZER_BAND8, player.equalizerBandGetGain(9));
        editor.putLong(CONFIG_EQUALIZER_BAND9, player.equalizerBandGetGain(10));
        editor.apply();
    }

    public static void restoreEqualizerSettings(AbstractMediaManager.Player player) {
        final SharedPreferences sharedPreferences = context.getSharedPreferences(CONFIG_FILE, Context.MODE_PRIVATE);

        player.equalizerSetEnabled(sharedPreferences.getBoolean(CONFIG_EQUALIZER_ENABLED, false));
        player.equalizerBandSetGain(0, sharedPreferences.getLong(CONFIG_EQUALIZER_PREAMP, 20));
        player.equalizerBandSetGain(1, sharedPreferences.getLong(CONFIG_EQUALIZER_BAND0, 20));
        player.equalizerBandSetGain(2, sharedPreferences.getLong(CONFIG_EQUALIZER_BAND1, 20));
        player.equalizerBandSetGain(3, sharedPreferences.getLong(CONFIG_EQUALIZER_BAND2, 20));
        player.equalizerBandSetGain(4, sharedPreferences.getLong(CONFIG_EQUALIZER_BAND3, 20));
        player.equalizerBandSetGain(5, sharedPreferences.getLong(CONFIG_EQUALIZER_BAND4, 20));
        player.equalizerBandSetGain(6, sharedPreferences.getLong(CONFIG_EQUALIZER_BAND5, 20));
        player.equalizerBandSetGain(7, sharedPreferences.getLong(CONFIG_EQUALIZER_BAND6, 20));
        player.equalizerBandSetGain(8, sharedPreferences.getLong(CONFIG_EQUALIZER_BAND7, 20));
        player.equalizerBandSetGain(9, sharedPreferences.getLong(CONFIG_EQUALIZER_BAND8, 20));
        player.equalizerBandSetGain(10, sharedPreferences.getLong(CONFIG_EQUALIZER_BAND9, 20));
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
				.setTitle(R.string.preference_title_opensource)
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



    // Color settings
    public static boolean uiColorsChanged = false;

    public static int getBackgroundColor() {
        final SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPrefs.getInt(context.getString(R.string.preference_key_primary_color), 0xff03a9f4);
    }

    public static int getAccentColor() {
        final SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPrefs.getInt(context.getString(R.string.preference_key_accent_color), 0xff01579b);
    }

    public static int getForegroundColor() {
        final SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPrefs.getInt(context.getString(R.string.preference_key_foreground_color), 0xffffffff);
    }

    public static boolean iconsAreDark() {
        final SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPrefs.getBoolean(context.getString(R.string.preference_key_toolbar_dark_icons), false);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static Toolbar applyActionBar(ActionBarActivity activity) {
        Toolbar toolbar = (Toolbar) activity.findViewById(iconsAreDark() ? R.id.main_toolbar_light : R.id.main_toolbar_dark);
        Toolbar otherToolbar = (Toolbar) activity.findViewById(!iconsAreDark() ? R.id.main_toolbar_light : R.id.main_toolbar_dark);

        if (toolbar != null) {
            toolbar.setVisibility(View.VISIBLE);
            toolbar.setBackgroundColor(getBackgroundColor());
            activity.setSupportActionBar(toolbar);
        }

        if (otherToolbar != null) {
            otherToolbar.setVisibility(View.GONE);
        }

        if (hasLollipop()) {
            activity.getWindow().setStatusBarColor(getAccentColor());
        }

        return toolbar;
    }

    public static void applyThemeOnPagerTabs(PagerSlidingTabStrip scrollingTabs) {
        scrollingTabs.setBackgroundColor(getBackgroundColor());
        scrollingTabs.setIndicatorColor(getAccentColor());
        scrollingTabs.setTextColor(getForegroundColor());
    }



    private static final String CONFIG_FIRST_RUN = "isFirstRun";



    public static boolean isFirstRun() {
        final SharedPreferences sharedPreferences = context.getSharedPreferences(CONFIG_FILE, Context.MODE_PRIVATE);
        return sharedPreferences.getBoolean(CONFIG_FIRST_RUN, true);
    }

    public static void disableFirstRun() {
        final SharedPreferences sharedPreferences = context.getSharedPreferences(CONFIG_FILE, Context.MODE_PRIVATE);
        final SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(CONFIG_FIRST_RUN, false);
        editor.apply();
    }


    public static boolean hasFroyo() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO;
    }

    public static boolean hasGingerbread() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD;
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

    public static boolean hasLollipop() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    public static boolean isTablet() {
        final int layout = context.getResources().getConfiguration().screenLayout;
        return (layout & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE;
    }

    public static File getDiskCacheDir(String uniqueName) {
        // Check if media is mounted or storage is built-in, if so, try and use external cache dir
        // otherwise use internal cache dir
        final String cachePath =
                Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) ||
                        !isExternalStorageRemovable() ? getExternalCacheDir(context).getPath() :
                        context.getCacheDir().getPath();

        return new File(cachePath + File.separator + uniqueName);
    }

    public static File getExternalCacheDir(Context context) {
        if (hasFroyo()) {
            return context.getExternalCacheDir();
        }

        // Before Froyo we need to construct the external cache dir ourselves
        final String cacheDir = "/Android/data/" + context.getPackageName() + "/cache/";
        return new File(Environment.getExternalStorageDirectory().getPath() + cacheDir);
    }

    public static boolean isExternalStorageRemovable() {
        return !hasGingerbread() || Environment.isExternalStorageRemovable();
    }

	public static float convertPixelsToDp(float width, Context context) {
		return width / (context.getResources().getDisplayMetrics().densityDpi / 160.0f);
	}
	
	public static int getListColumns() {
		float width = convertPixelsToDp(context.getResources().getDisplayMetrics().widthPixels, context);
        return (int) Math.floor(width / 225.0F);
	}

	public static String getVariousArtists() {
        return PlayerApplication.context.getString(R.string.preference_default_various_artists);
	}

    public static int getIntPreference(final int preferenceKeyResId, int defaultValue) {
        final SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        return Integer.parseInt(sharedPrefs.getString(PlayerApplication.context.getString(preferenceKeyResId), String.valueOf(defaultValue)));
    }
}
