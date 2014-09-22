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
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.Loader;
import android.view.Menu;
import android.webkit.WebView;

import com.google.android.vending.licensing.AESObfuscator;
import com.nostra13.universalimageloader.core.ImageLoader;

import net.opusapp.licensing.LicenseChecker;
import net.opusapp.licensing.LicenseCheckerCallback;
import net.opusapp.licensing.PlaystoreAccountType;
import net.opusapp.licensing.ServerManagedPolicy;
import net.opusapp.player.BuildConfig;
import net.opusapp.player.R;
import net.opusapp.player.core.service.IPlayerService;
import net.opusapp.player.core.service.PlayerService;
import net.opusapp.player.core.service.providers.AbstractMediaManager;
import net.opusapp.player.core.service.providers.MediaManagerFactory;
import net.opusapp.player.core.service.providers.index.database.Entities;
import net.opusapp.player.core.service.providers.index.database.OpenHelper;
import net.opusapp.player.core.service.utils.AbstractSimpleCursorLoader;
import net.opusapp.player.ui.utils.uil.NormalImageLoader;
import net.opusapp.player.ui.utils.uil.ThumbnailImageLoader;
import net.opusapp.player.utils.LogUtils;
import net.opusapp.player.utils.iab.IabHelper;
import net.opusapp.player.utils.iab.IabResult;
import net.opusapp.player.utils.iab.Inventory;
import net.opusapp.player.utils.iab.Purchase;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class PlayerApplication extends Application implements ServiceConnection {

	public final static String TAG = PlayerApplication.class.getSimpleName();



    // Global application context
    public static Context context;

    // Global application instance
    public static PlayerApplication instance;

    // Global service reference.
    public static IPlayerService playerService = null;

    public static ArrayList<ServiceConnection> additionalCallbacks = new ArrayList<ServiceConnection>();

    // Iab
    private IabHelper iabHelper;

    static final String ITEM_DEBUG_SKU = "android.test.purchased";

    static final String ITEM_RELEASE_SKU = "net.opusapp.player.premium";

    static String ITEM_SKU;

    static final String PURCHASE_TOKEN = "purchase-token-noads";



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

        doPrepareTrialCheck();
    }

    public synchronized static void connectService(ServiceConnection additionalConnectionCallback) {
        if (connecting) {
            return;
        }

        if (additionalConnectionCallback != null) {
            additionalCallbacks.add(additionalConnectionCallback);
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
        else if (additionalConnectionCallback != null) {
            additionalConnectionCallback.onServiceConnected(null, (IBinder)playerService);
        }
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
            if (!currentProvider.getPlayer().playerIsPlaying()) {
                // ensure media player is completly stopped if paused.
                currentProvider.getPlayer().playerStop();
                currentProvider = null;
            }
            else {
                currentProvider.getPlayer().resetListeners();
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
                if (currentProvider.getPlayer().playerIsPlaying()) {
                    currentProvider.getPlayer().playerStop();
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




    private static final String CONFIG_FILE_FREEMIUM = "freemium";

    private static final String CONFIG_IS_FREEMIUM = "isFreemium";

    private static final String CONFIG_NO_DISPLAY = "noDisplayCounter";

    private static final String CONFIG_FIRST_RUN = "firstRunNeedWizard";

    public static boolean isFreemium() {
        final SharedPreferences sharedPreferences = context.getSharedPreferences(CONFIG_FILE_FREEMIUM, Context.MODE_PRIVATE);
        return sharedPreferences.getBoolean(CONFIG_IS_FREEMIUM, true);
        //return false;
    }

    public static void setFreemium(boolean freemium) {
        final SharedPreferences sharedPreferences = context.getSharedPreferences(CONFIG_FILE_FREEMIUM, Context.MODE_PRIVATE);
        final SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(CONFIG_IS_FREEMIUM, freemium);
        editor.apply();
    }

    public static int adDisplayGetCounter() {
        final SharedPreferences sharedPreferences = context.getSharedPreferences(CONFIG_FILE_FREEMIUM, Context.MODE_PRIVATE);
        return sharedPreferences.getInt(CONFIG_NO_DISPLAY, 0) + 1;
    }

    public static void adDisplayInc() {
        final SharedPreferences sharedPreferences = context.getSharedPreferences(CONFIG_FILE_FREEMIUM, Context.MODE_PRIVATE);
        int noDisplayCounter = sharedPreferences.getInt(CONFIG_NO_DISPLAY, 0) + 1;

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(CONFIG_NO_DISPLAY, noDisplayCounter);
        editor.apply();
    }

    public static void adDisplayReset() {
        final SharedPreferences sharedPreferences = context.getSharedPreferences(CONFIG_FILE_FREEMIUM, Context.MODE_PRIVATE);

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(CONFIG_NO_DISPLAY, 0);
        editor.apply();
    }

    public static boolean isFirstRun() {
        final SharedPreferences sharedPreferences = context.getSharedPreferences(CONFIG_FILE_FREEMIUM, Context.MODE_PRIVATE);
        return sharedPreferences.getBoolean(CONFIG_FIRST_RUN, true);
    }

    public static void disableFirstRun() {
        final SharedPreferences sharedPreferences = context.getSharedPreferences(CONFIG_FILE_FREEMIUM, Context.MODE_PRIVATE);
        final SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(CONFIG_FIRST_RUN, false);
        editor.apply();
    }

    private static Runnable iabStarted = null;

    public static void iabStart(final Runnable onStarted) {
        if (!isFreemium() || instance.iabHelper != null) {
            return;
        }

        if (BuildConfig.DEBUG) {
            ITEM_SKU = ITEM_DEBUG_SKU;
        }
        else {
            ITEM_SKU = ITEM_RELEASE_SKU;
        }

        iabStarted = onStarted;

        String base64EncodedPublicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAj127PTqHlOpyDVEhSXTQuaEeCH74Rvb0k7NDW0uPj/DthoPX70eOqhLrJ/+jw6fTLmFMIxiBOdTfAvDO6TonIuVgMtooRoz7msrY3gNCT3MUnWWz6907zrfs7J6ocSHQeNzUViuOHHoEoCVvqNhAxtNEUlfvK54Jrkv6kOBg7Kp1WgEOb7O66C5KOiByzP/MReUA+647mUNfehSAi0xFnxfLPKPAKqForbIc3628vpRZ7uSC+nAcdSYVWoDaWcUTagwI7ljflCyKk6Ww6YkCpWP3NlttIao5Ay97TGP7aEAHm5CXlIEosojzYeqAd2gik0aTYXaSJB88jh0ajcaKhwIDAQAB";

        instance.iabHelper = new IabHelper(context, base64EncodedPublicKey);

        instance.iabHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {

            public void onIabSetupFinished(IabResult result)
            {
                if (!result.isSuccess()) {
                    LogUtils.LOGE(TAG, "In-app Billing setup failed: " + result);
                }
                else {
                    LogUtils.LOGI(TAG, "In-app Billing is set up OK");
                    iabCheck();
                }
            }
        });
    }

    public static void iabStop() {
        if (instance.iabHelper != null) {
            instance.iabHelper.dispose();
        }
        instance.iabHelper = null;
    }

    public static void iabPurchase(Activity activity) {
        instance.iabHelper.launchPurchaseFlow(activity, ITEM_SKU, 10001, purchaseFinishedListener, PURCHASE_TOKEN);
    }

    public static boolean iabHandleResult(int requestCode, int resultCode, Intent data) {
        return instance.iabHelper.handleActivityResult(requestCode, resultCode, data);
    }

    private static void iabCheck() {
        new AsyncTask<Void, Void, Void>() {
            protected Inventory inventory;

            @Override
            protected Void doInBackground(Void... params) {
                List<String> items = new ArrayList<String>();
                items.add(ITEM_SKU);

                try {
                    LogUtils.LOGI(TAG, "querying inventory");
                    inventory = instance.iabHelper.queryInventory(false, items);
                }
                catch (final Exception exception) {
                    LogUtils.LOGW(TAG, "unable to query iab informations : " + exception);
                    inventory = null;
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);

                if (inventory != null) {
                    LogUtils.LOGW(TAG, "inventory got");
                    if (inventory.hasPurchase(ITEM_SKU)) {
                        LogUtils.LOGW(TAG, "inventory got : has purchase");
                        setFreemium(false);
                    }
                    // instance.iabHelper.consumeAsync(inventory.getPurchase(ITEM_SKU), consumeFinishedListener);

                    if (iabStarted != null) {
                        iabStarted.run();
                    }
                }
            }
        }.execute();
    }

    private static IabHelper.OnIabPurchaseFinishedListener purchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
        public void onIabPurchaseFinished(IabResult result, Purchase purchase)
        {
            if (result.isFailure()) {
                LogUtils.LOGW(TAG, "purchaseFinished: failure");
            }
            else if (purchase.getSku().equals(ITEM_SKU)) {
                LogUtils.LOGI(TAG, "purchaseFinished: success");
                instance.iabHelper.queryInventoryAsync(receivedInventoryListener);
            }
        }
    };

    private static IabHelper.QueryInventoryFinishedListener receivedInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
        public void onQueryInventoryFinished(IabResult result, Inventory inventory) {

            if (result.isFailure()) {
                LogUtils.LOGW(TAG, "receivedInventory: failure");
            }
            else {
                LogUtils.LOGI(TAG, "receivedInventory: success");
                setFreemium(false);

                if (iabStarted != null) {
                    iabStarted.run();
                }
            }
        }
    };

    /*
    private static IabHelper.OnConsumeFinishedListener consumeFinishedListener = new IabHelper.OnConsumeFinishedListener() {

        public void onConsumeFinished(Purchase purchase, IabResult result) {

            if (result.isSuccess()) {
                setFreemium(false);
            }
            else {
                LogUtils.LOGW(TAG, "consumeFinished: failure");
            }
        }
    };
    */

    private static final String BASE64_PUBLIC_KEY =
            "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQC/uBXD8ItJQg" +
            "Y15Yqw4jcCdzUTJ3L+QG7RSuMjbAJOa8isY/hVyGjnPlTn+S9A" +
            "/IsjtSx7s3oc86X0HOVEOV2O3a8S9MYKvrjNUVMUVDVyxYA3bg" +
            "HZuaXU722pn6FewE1merjWbt0rtsMcJMI7uNpIh/3LjeQ65J2K" +
            "XEWtuFBw1QIDAQAB";

    private static final byte SALT[] = new byte[] {-101, -88, 61, 94, -112, -71, -4, 4, 39, 3, 27, 59, -30, -103, 123, 69, 115, 54, 84, -87};

    private static final String TRIAL_SERVER_URL = "https://opusapp.net:3000/";

    private static LicenseChecker trialChecker;

    private static boolean trialMode = true;

    private static void doPrepareTrialCheck() {
        //Create an url object to the MobileTrial server
        URL trialServerUrl = null;
        try {
            trialServerUrl = URI.create(TRIAL_SERVER_URL).toURL();
        } catch (MalformedURLException exception) {
            LogUtils.LOGException(TAG, "doPrepareTrialCheck", 0, exception);
        }

        final String deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);

        // Construct the LicenseChecker with a ServerManaged Policy
        trialChecker = new LicenseChecker(
                context, new ServerManagedPolicy(context,
                new AESObfuscator(SALT, context.getPackageName(), deviceId)),
                BASE64_PUBLIC_KEY,
                trialServerUrl,
                new PlaystoreAccountType());
    }

    public static void doTrialCheck(LicenseCheckerCallback trialLicenseCheckerCallback) {
        trialChecker.checkAccess(trialLicenseCheckerCallback);
    }

    public static boolean isTrial() {
        return trialMode;
    }

    public static void setTrial(boolean trial) {
        trialMode = trial;
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
        return PlayerApplication.context.getString(R.string.preference_default_various_artists);
	}
}