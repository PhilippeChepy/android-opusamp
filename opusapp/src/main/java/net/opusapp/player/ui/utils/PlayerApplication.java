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
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.Loader;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextUtils;
import android.view.Menu;
import android.view.View;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.TextView;

import com.astuetz.PagerSlidingTabStrip;

import net.opusapp.player.R;
import net.opusapp.player.core.service.PlayerService;
import net.opusapp.player.core.service.providers.MediaManager;
import net.opusapp.player.core.service.providers.MediaManagerFactory;
import net.opusapp.player.core.service.providers.index.database.Entities;
import net.opusapp.player.core.service.providers.index.database.OpenHelper;
import net.opusapp.player.core.service.utils.AbstractSimpleCursorLoader;
import net.opusapp.player.core.service.utils.CursorUtils;
import net.opusapp.player.licensing.BuildSpecific;
import net.opusapp.player.ui.activities.LibraryMainActivity;
import net.opusapp.player.ui.dialogs.EditTextDialog;
import net.opusapp.player.ui.dialogs.MetadataDialog;
import net.opusapp.player.utils.LogUtils;

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
    private static MediaManager mediaManagers[] = null;

    private static int playerMediaManagerIndex = 0;

    private static int libraryMediaManagerIndex = 0;


    private static boolean connecting;



    // UI/UX communication
    public static final String CONTENT_TYPE_KEY = "type_key";

    public static final String CONTENT_SOURCE_ID_KEY = "id_key";

    public static final String CONTENT_SOURCE_DESCRIPTION_KEY = "description_key";

    private static final String CONFIG_FILE = "global-config";


    // Library explorer options
    public static boolean library_show_hidden = false;

    public static int library_album_artists_sort_order = MediaManager.Provider.ALBUM_ARTIST_NAME;

    public static int library_albums_sort_order = MediaManager.Provider.ALBUM_NAME;

    public static int library_artists_sort_order = MediaManager.Provider.ARTIST_NAME;

    public static int library_genres_sort_order = MediaManager.Provider.GENRE_NAME;

    public static int library_playlists_sort_order = MediaManager.Provider.PLAYLIST_NAME;

    public static int library_songs_sort_order = MediaManager.Provider.SONG_TRACK;

    public static int library_storage_sort_order = MediaManager.Provider.STORAGE_DISPLAY_NAME;



    public static int library_details_songs_sort_order = MediaManager.Provider.SONG_TRACK;

    public static int library_details_playlist_sort_order = MediaManager.Provider.PLAYLIST_ENTRY_POSITION;

    public static int library_details_albums_sort_order = MediaManager.Provider.ALBUM_NAME;



    @Override
    public void onCreate() {
        super.onCreate();

        context = getApplicationContext();
        instance = this;

        allocateMediaManagers();

        playerMediaManagerIndex = getLibraryPlayerIndex();
        libraryMediaManagerIndex = getLibraryLibraryIndex();

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

        final SQLiteDatabase database = OpenHelper.getInstance().getWritableDatabase();
        final MediaManager playerProvider = playerMediaManager();

        final String[] columns = new String[] {
                Entities.Provider._ID,
                Entities.Provider.COLUMN_FIELD_PROVIDER_TYPE,
                Entities.Provider.COLUMN_FIELD_PROVIDER_NAME
        };

        final int COLUMN_ID = 0;

        final int COLUMN_TYPE = 1;

        final int COLUMN_NAME = 2;

        final String orderBy = Entities.Provider.COLUMN_FIELD_PROVIDER_POSITION;

        final Cursor cursor = database.query(Entities.Provider.TABLE_NAME, columns, null, null, null, null, orderBy);

        if (CursorUtils.ifNotEmpty(cursor)) {
            mediaManagers = new MediaManager[cursor.getCount()];

            if (playerProvider != null && playerProvider.getPlayer().playerIsPlaying()) {
                while (cursor.moveToNext()) {
                    int newProviderId = cursor.getInt(COLUMN_ID);
                    int newProviderType = cursor.getInt(COLUMN_TYPE);
                    final String newProviderName = cursor.getString(COLUMN_NAME);

                    if (newProviderId == playerProvider.getId()) {
                        mediaManagers[cursor.getPosition()] = playerProvider;
                    }
                    else {
                        final MediaManager mediaManager = MediaManagerFactory.buildMediaManager(newProviderType, newProviderId, newProviderName);
                        final MediaManager.Player player = mediaManager.getPlayer();

                        mediaManagers[cursor.getPosition()] = mediaManager;

                        // load equalizer settings
                        restoreEqualizerSettings(player);
                        player.equalizerApplyProperties();
                    }
                }
            }
            else {
                while (cursor.moveToNext()) {
                    int newProviderId = cursor.getInt(COLUMN_ID);
                    int newProviderType = cursor.getInt(COLUMN_TYPE);
                    final String newProviderName = cursor.getString(COLUMN_NAME);

                    final MediaManager mediaManager = MediaManagerFactory.buildMediaManager(newProviderType, newProviderId, newProviderName);
                    final MediaManager.Player player = mediaManager.getPlayer();

                    mediaManagers[cursor.getPosition()] = mediaManager;

                    // load equalizer settings
                    restoreEqualizerSettings(player);
                    player.equalizerApplyProperties();
                }
            }

            CursorUtils.free(cursor);
        }
    }

    public static MediaManager playerMediaManager() {
        if (mediaManagers == null || playerMediaManagerIndex >= mediaManagers.length) {
            return null;
        }

        return mediaManagers[playerMediaManagerIndex];
    }

    public static MediaManager libraryMediaManager() {
        if (mediaManagers == null || libraryMediaManagerIndex >= mediaManagers.length) {
            return null;
        }

        return mediaManagers[libraryMediaManagerIndex];
    }

    public static void setPlayerManager(int id) {
        for (int managerIndex = 0 ; managerIndex < mediaManagers.length ; managerIndex++) {
            if (mediaManagers[managerIndex].getId() == id) {
                playerMediaManagerIndex = managerIndex;
                return;
            }
        }
    }

    public static void setLibraryManager(int id) {
        for (int managerIndex = 0 ; managerIndex < mediaManagers.length ; managerIndex++) {
            if (mediaManagers[managerIndex].getId() == id) {
                libraryMediaManagerIndex = managerIndex;
                return;
            }
        }
    }

    public static boolean thereIsScanningMediaManager() {
        for (MediaManager mediaManager : mediaManagers) {
            if (mediaManager.getProvider().scanIsRunning()) {
                return true;
            }
        }

        return false;
    }

    public static MediaManager mediaManager(int id) {
        for (final MediaManager mediaManager : mediaManagers) {
            if (mediaManager.getId() == id) {
                return mediaManager;
            }
        }

        return null;
    }

    public static void optimizeDatabases(final Activity parent) {
        final ProgressDialog progressDialog = new ProgressDialog(parent);

        final AsyncTask<Void, Integer, Void> optimizationTask = new AsyncTask<Void, Integer, Void>() {

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                progressDialog.setTitle(R.string.preference_dialog_title_database_optimization);
                progressDialog.setIndeterminate(true);
                progressDialog.show();
            }

            @Override
            protected Void doInBackground(Void... params) {
                publishProgress(0);
                final SQLiteDatabase database = OpenHelper.getInstance().getWritableDatabase();
                database.rawQuery("VACUUM;", null);

                for (int index = 0 ; index < PlayerApplication.mediaManagers.length ; index++) {
                    publishProgress(index + 1);
                    PlayerApplication.mediaManagers[index].getProvider().databaseMaintain();
                }

                return null;
            }

            @Override
            protected void onProgressUpdate(Integer... values) {
                super.onProgressUpdate(values);

                if (values[0] == 0) {
                    progressDialog.setMessage(parent.getString(R.string.progress_dialog_label_global_database));
                }
                else {
                    progressDialog.setMessage(String.format(parent.getString(R.string.progress_dialog_label_current_database), values[0], PlayerApplication.mediaManagers.length));
                }
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                progressDialog.dismiss();
            }
        };

        optimizationTask.execute();
    }

    public static Loader<Cursor> buildAlbumArtistLoader(final MediaManager.Provider provider, final int[] requestedFields, final int[] sortFields, final String filter) {
        return new AbstractSimpleCursorLoader(PlayerApplication.context) {
            @Override
            public Cursor loadInBackground() {
                return provider.buildCursor(MediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM_ARTIST, requestedFields, sortFields, filter, null, null);
            }
        };
    }

    public static Loader<Cursor> buildAlbumLoader(final MediaManager.Provider provider, final int[] requestedFields, final int[] sortFields, final String filter, final MediaManager.Provider.ContentType contentType, final String sourceId) {
        return new AbstractSimpleCursorLoader(PlayerApplication.context) {
            @Override
            public Cursor loadInBackground() {
                return provider.buildCursor(MediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM, requestedFields, sortFields, filter, contentType, sourceId);
            }
        };
    }

    public static Loader<Cursor> buildArtistLoader(final MediaManager.Provider provider, final int[] requestedFields, final int[] sortFields, final String filter) {
        return new AbstractSimpleCursorLoader(PlayerApplication.context) {
            @Override
            public Cursor loadInBackground() {
                return provider.buildCursor(MediaManager.Provider.ContentType.CONTENT_TYPE_ARTIST, requestedFields, sortFields, filter, null, null);
            }
        };
    }

    public static Loader<Cursor> buildGenreLoader(final MediaManager.Provider provider, final int[] requestedFields, final int[] sortFields, final String filter) {
        return new AbstractSimpleCursorLoader(PlayerApplication.context) {
            @Override
            public Cursor loadInBackground() {
                return provider.buildCursor(MediaManager.Provider.ContentType.CONTENT_TYPE_GENRE, requestedFields, sortFields, filter, null, null);
            }
        };
    }

    public static Loader<Cursor> buildPlaylistLoader(final MediaManager.Provider provider, final int[] requestedFields, final int[] sortFields, final String filter) {
        return new AbstractSimpleCursorLoader(PlayerApplication.context) {
            @Override
            public Cursor loadInBackground() {
                return provider.buildCursor(MediaManager.Provider.ContentType.CONTENT_TYPE_PLAYLIST, requestedFields, sortFields, filter, null, null);
            }
        };
    }

    public static Loader<Cursor> buildMediaLoader(final MediaManager.Provider provider, final int[] requestedFields, final int[] sortFields, final String filter, final MediaManager.Provider.ContentType contentType, final String sourceId) {
        return new AbstractSimpleCursorLoader(PlayerApplication.context) {
            @Override
            public Cursor loadInBackground() {
                return provider.buildCursor(MediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, requestedFields, sortFields, filter, contentType, sourceId);
            }
        };
    }

    public static Loader<Cursor> buildStorageLoader(final MediaManager.Provider provider, final int[] requestedFields, final int[] sortFields, final String filter) {
        return new AbstractSimpleCursorLoader(PlayerApplication.context) {
            @Override
            public Cursor loadInBackground() {
                return provider.buildCursor(MediaManager.Provider.ContentType.CONTENT_TYPE_STORAGE, requestedFields, sortFields, filter, null, null);
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

        if (playerMediaManagerIndex == libraryMediaManagerIndex) {
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
                return doContextActionPlay(MediaManager.Provider.ContentType.CONTENT_TYPE_DEFAULT, null, library_songs_sort_order, position);
            case CONTEXT_MENUITEM_PLAY_NEXT:
                return doContextActionPlayNext(MediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, songId, library_songs_sort_order);
            case CONTEXT_MENUITEM_ADD_TO_QUEUE:
                return doContextActionAddToQueue(MediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, songId, library_songs_sort_order);
            case CONTEXT_MENUITEM_ADD_TO_PLAYLIST:
                return doContextActionAddToPlaylist(hostActivity, MediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, songId, library_songs_sort_order);
            case CONTEXT_MENUITEM_HIDE:
                return doContextActionToggleVisibility(MediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, songId);
            case CONTEXT_MENUITEM_DETAIL:
                doContextActionDetail(hostActivity, MediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, songId);
                return false;
            case CONTEXT_MENUITEM_DELETE:
                doContextActionMediaRemoveFromQueue(playlistId, position);
                return true;
            case CONTEXT_MENUITEM_CLEAR:
                doContextActionPlaylistClear(playlistId);
                return true;
            default:
                return false;
        }
    }

    public static void createPlaylistContextMenu(Menu menu, int groupId, boolean visible) {
        menu.add(groupId, CONTEXT_MENUITEM_PLAY, 1, R.string.menuitem_label_play);
        if (playerMediaManagerIndex == libraryMediaManagerIndex) {
            menu.add(groupId, CONTEXT_MENUITEM_PLAY_NEXT, 2, R.string.menuitem_label_play_next);
            menu.add(groupId, CONTEXT_MENUITEM_ADD_TO_QUEUE, 3, R.string.menuitem_label_add_to_queue);
        }
        menu.add(groupId, CONTEXT_MENUITEM_HIDE, 4, visible ? R.string.menuitem_label_hide : R.string.menuitem_label_show);
        menu.add(groupId, CONTEXT_MENUITEM_DELETE, 5, R.string.menuitem_label_delete);
    }

    public static boolean playlistDetailContextItemSelected(FragmentActivity hostActivity, int itemId, String playlistId, int sortOrder, int position, String songId) {
        switch (itemId) {
            case CONTEXT_MENUITEM_PLAY:
                return doContextActionPlay(MediaManager.Provider.ContentType.CONTENT_TYPE_PLAYLIST, playlistId, sortOrder, position);
            case CONTEXT_MENUITEM_PLAY_NEXT:
                return doContextActionPlayNext(MediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, songId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_QUEUE:
                return doContextActionAddToQueue(MediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, songId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_PLAYLIST:
                return doContextActionAddToPlaylist(hostActivity, MediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, songId, sortOrder);
            case CONTEXT_MENUITEM_HIDE:
                return doContextActionToggleVisibility(MediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, songId);
            case CONTEXT_MENUITEM_DETAIL:
                doContextActionDetail(hostActivity, MediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, songId);
                return false;
            case CONTEXT_MENUITEM_DELETE:
                doContextActionMediaRemoveFromQueue(playlistId, position);
                return true;
            case CONTEXT_MENUITEM_CLEAR:
                doContextActionPlaylistClear(playlistId);
                return true;
            default:
                return false;
        }
    }

    public static boolean playlistContextItemSelected(Activity hostActivity, int itemId, String playlistId, int sortOrder, int position) {
        switch (itemId) {
            case CONTEXT_MENUITEM_PLAY:
                return doContextActionPlay(MediaManager.Provider.ContentType.CONTENT_TYPE_PLAYLIST, playlistId, sortOrder, position);
            case CONTEXT_MENUITEM_PLAY_NEXT:
                return doContextActionPlayNext(MediaManager.Provider.ContentType.CONTENT_TYPE_PLAYLIST, playlistId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_QUEUE:
                return doContextActionAddToQueue(MediaManager.Provider.ContentType.CONTENT_TYPE_PLAYLIST, playlistId, sortOrder);
            case CONTEXT_MENUITEM_HIDE:
                return doContextActionToggleVisibility(MediaManager.Provider.ContentType.CONTENT_TYPE_PLAYLIST, playlistId);
            case CONTEXT_MENUITEM_DELETE:
                return doContextActionPlaylistDelete(hostActivity, playlistId);
            default:
                return false;
        }
    }

    public static void createGenreContextMenu(Menu menu, int groupId, boolean visible) {
        menu.add(groupId, CONTEXT_MENUITEM_PLAY, 1, R.string.menuitem_label_play);

        if (playerMediaManagerIndex == libraryMediaManagerIndex) {
            menu.add(groupId, CONTEXT_MENUITEM_PLAY_NEXT, 2, R.string.menuitem_label_play_next);
            menu.add(groupId, CONTEXT_MENUITEM_ADD_TO_QUEUE, 3, R.string.menuitem_label_add_to_queue);
        }

        menu.add(groupId, CONTEXT_MENUITEM_ADD_TO_PLAYLIST, 4, R.string.menuitem_label_add_to_playlist);
        menu.add(groupId, CONTEXT_MENUITEM_HIDE, 5, visible ? R.string.menuitem_label_hide : R.string.menuitem_label_show);
    }

    public static boolean genreDetailContextItemSelected(FragmentActivity hostActivity, int itemId, int sortOrder, int position, String albumId) {
        switch (itemId) {
            case CONTEXT_MENUITEM_PLAY:
                return doContextActionPlay(MediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM, albumId, sortOrder, position);
            case CONTEXT_MENUITEM_PLAY_NEXT:
                return doContextActionPlayNext(MediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM, albumId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_QUEUE:
                return doContextActionAddToQueue(MediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM, albumId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_PLAYLIST:
                return doContextActionAddToPlaylist(hostActivity, MediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM, albumId, sortOrder);
            case CONTEXT_MENUITEM_HIDE:
                return doContextActionToggleVisibility(MediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM, albumId);
            default:
                return false;
        }
    }

    public static boolean genreContextItemSelected(Activity hostActivity, int itemId, String genreId, int sortOrder, int position) {
        switch (itemId) {
            case CONTEXT_MENUITEM_PLAY:
                return doContextActionPlay(MediaManager.Provider.ContentType.CONTENT_TYPE_GENRE, genreId, sortOrder, position);
            case CONTEXT_MENUITEM_PLAY_NEXT:
                return doContextActionPlayNext(MediaManager.Provider.ContentType.CONTENT_TYPE_GENRE, genreId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_QUEUE:
                return doContextActionAddToQueue(MediaManager.Provider.ContentType.CONTENT_TYPE_GENRE, genreId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_PLAYLIST:
                return doContextActionAddToPlaylist(hostActivity, MediaManager.Provider.ContentType.CONTENT_TYPE_GENRE, genreId, sortOrder);
            case CONTEXT_MENUITEM_HIDE:
                return doContextActionToggleVisibility(MediaManager.Provider.ContentType.CONTENT_TYPE_GENRE, genreId);
            default:
                return false;
        }
    }

    public static void createArtistContextMenu(Menu menu, int groupId, boolean visible) {
        menu.add(groupId, CONTEXT_MENUITEM_PLAY, 1, R.string.menuitem_label_play);

        if (playerMediaManagerIndex == libraryMediaManagerIndex) {
            menu.add(groupId, CONTEXT_MENUITEM_PLAY_NEXT, 2, R.string.menuitem_label_play_next);
            menu.add(groupId, CONTEXT_MENUITEM_ADD_TO_QUEUE, 3, R.string.menuitem_label_add_to_queue);
        }
        menu.add(groupId, CONTEXT_MENUITEM_ADD_TO_PLAYLIST, 4, R.string.menuitem_label_add_to_playlist);
        menu.add(groupId, CONTEXT_MENUITEM_HIDE, 5, visible ? R.string.menuitem_label_hide : R.string.menuitem_label_show);
    }

    public static boolean artistDetailContextItemSelected(FragmentActivity hostActivity, int itemId, String artistId, int sortOrder, int position, String songId) {
        switch (itemId) {
            case CONTEXT_MENUITEM_PLAY:
                return doContextActionPlay(MediaManager.Provider.ContentType.CONTENT_TYPE_ARTIST, artistId, sortOrder, position);
            case CONTEXT_MENUITEM_PLAY_NEXT:
                return doContextActionPlayNext(MediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, songId, 0);
            case CONTEXT_MENUITEM_ADD_TO_QUEUE:
                return doContextActionAddToQueue(MediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, songId, 0);
            case CONTEXT_MENUITEM_ADD_TO_PLAYLIST:
                return doContextActionAddToPlaylist(hostActivity, MediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, songId, 0);
            case CONTEXT_MENUITEM_HIDE:
                return doContextActionToggleVisibility(MediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, songId);
            default:
                return false;
        }
    }

    public static boolean artistContextItemSelected(Activity hostActivity, int itemId, String artistId, int sortOrder, int position) {
        switch (itemId) {
            case CONTEXT_MENUITEM_PLAY:
                return doContextActionPlay(MediaManager.Provider.ContentType.CONTENT_TYPE_ARTIST, artistId, sortOrder, position);
            case CONTEXT_MENUITEM_PLAY_NEXT:
                return doContextActionPlayNext(MediaManager.Provider.ContentType.CONTENT_TYPE_ARTIST, artistId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_QUEUE:
                return doContextActionAddToQueue(MediaManager.Provider.ContentType.CONTENT_TYPE_ARTIST, artistId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_PLAYLIST:
                return doContextActionAddToPlaylist(hostActivity, MediaManager.Provider.ContentType.CONTENT_TYPE_ARTIST, artistId, sortOrder);
            case CONTEXT_MENUITEM_HIDE:
                return doContextActionToggleVisibility(MediaManager.Provider.ContentType.CONTENT_TYPE_ARTIST, artistId);
            default:
                return false;
        }
    }

    public static void createAlbumContextMenu(Menu menu, int groupId, boolean visible) {
        menu.add(groupId, CONTEXT_MENUITEM_PLAY, 1, R.string.menuitem_label_play);

        if (playerMediaManagerIndex == libraryMediaManagerIndex) {
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
                return doContextActionPlay(MediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM, albumId, sortOrder, position);
            case CONTEXT_MENUITEM_PLAY_NEXT:
                return doContextActionPlayNext(MediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, songId, 0);
            case CONTEXT_MENUITEM_ADD_TO_QUEUE:
                return doContextActionAddToQueue(MediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, songId, 0);
            case CONTEXT_MENUITEM_ADD_TO_PLAYLIST:
                return doContextActionAddToPlaylist(hostActivity, MediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, songId, 0);
            case CONTEXT_MENUITEM_HIDE:
                return doContextActionToggleVisibility(MediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, songId);
            case CONTEXT_MENUITEM_DETAIL:
                doContextActionDetail(hostActivity, MediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA, songId);
                return false;
            default:
                return false;
        }
    }

    public static boolean albumContextItemSelected(Activity hostActivity, int itemId, String albumId, int sortOrder, int position) {
        switch (itemId) {
            case CONTEXT_MENUITEM_PLAY:
                return doContextActionPlay(MediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM, albumId, sortOrder, position);
            case CONTEXT_MENUITEM_PLAY_NEXT:
                return doContextActionPlayNext(MediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM, albumId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_QUEUE:
                return doContextActionAddToQueue(MediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM, albumId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_PLAYLIST:
                return doContextActionAddToPlaylist(hostActivity, MediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM, albumId, sortOrder);
            case CONTEXT_MENUITEM_HIDE:
                return doContextActionToggleVisibility(MediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM, albumId);
            case CONTEXT_MENUITEM_DETAIL:
                doContextActionDetail(hostActivity, MediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM, albumId);
                return false;
            default:
                return false;
        }
    }

    public static void createAlbumArtistContextMenu(Menu menu, int groupId, boolean visible) {
        menu.add(groupId, CONTEXT_MENUITEM_PLAY, 1, R.string.menuitem_label_play);

        if (playerMediaManagerIndex == libraryMediaManagerIndex) {
            menu.add(groupId, CONTEXT_MENUITEM_PLAY_NEXT, 2, R.string.menuitem_label_play_next);
            menu.add(groupId, CONTEXT_MENUITEM_ADD_TO_QUEUE, 3, R.string.menuitem_label_add_to_queue);
        }
        menu.add(groupId, CONTEXT_MENUITEM_ADD_TO_PLAYLIST, 4, R.string.menuitem_label_add_to_playlist);
        menu.add(groupId, CONTEXT_MENUITEM_HIDE, 5, visible ? R.string.menuitem_label_hide : R.string.menuitem_label_show);
    }

    public static boolean albumArtistDetailContextItemSelected(FragmentActivity hostActivity, int itemId, int sortOrder, String albumId) {
        switch (itemId) {
            case CONTEXT_MENUITEM_PLAY:
                return doContextActionPlay(MediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM, albumId, sortOrder, 0);
            case CONTEXT_MENUITEM_PLAY_NEXT:
                return doContextActionPlayNext(MediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM, albumId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_QUEUE:
                return doContextActionAddToQueue(MediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM, albumId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_PLAYLIST:
                return doContextActionAddToPlaylist(hostActivity, MediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM, albumId, sortOrder);
            case CONTEXT_MENUITEM_HIDE:
                return doContextActionToggleVisibility(MediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM, albumId);
            default:
                return false;
        }
    }

    public static boolean albumArtistContextItemSelected(Activity hostActivity, int itemId, String albumArtistId, int sortOrder, int position) {
        switch (itemId) {
            case CONTEXT_MENUITEM_PLAY:
                return doContextActionPlay(MediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM_ARTIST, albumArtistId, sortOrder, position);
            case CONTEXT_MENUITEM_PLAY_NEXT:
                return doContextActionPlayNext(MediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM_ARTIST, albumArtistId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_QUEUE:
                return doContextActionAddToQueue(MediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM_ARTIST, albumArtistId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_PLAYLIST:
                return doContextActionAddToPlaylist(hostActivity, MediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM_ARTIST, albumArtistId, sortOrder);
            case CONTEXT_MENUITEM_HIDE:
                return doContextActionToggleVisibility(MediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM_ARTIST, albumArtistId);
            default:
                return false;
        }
    }

    public static void createStorageContextMenu(Menu menu, int groupId) {
        menu.add(groupId, CONTEXT_MENUITEM_PLAY, 1, R.string.menuitem_label_play);

        if (playerMediaManagerIndex == libraryMediaManagerIndex) {
            menu.add(groupId, CONTEXT_MENUITEM_PLAY_NEXT, 2, R.string.menuitem_label_play_next);
            menu.add(groupId, CONTEXT_MENUITEM_ADD_TO_QUEUE, 3, R.string.menuitem_label_add_to_queue);
        }
    }

    public static boolean storageContextItemSelected(int itemId, String storageId, int sortOrder, int position) {
        switch (itemId) {
            case CONTEXT_MENUITEM_PLAY:
                return doContextActionPlay(MediaManager.Provider.ContentType.CONTENT_TYPE_STORAGE, storageId, sortOrder, position);
            case CONTEXT_MENUITEM_PLAY_NEXT:
                return doContextActionPlayNext(MediaManager.Provider.ContentType.CONTENT_TYPE_STORAGE, storageId, sortOrder);
            case CONTEXT_MENUITEM_ADD_TO_QUEUE:
                return doContextActionAddToQueue(MediaManager.Provider.ContentType.CONTENT_TYPE_STORAGE, storageId, sortOrder);
            default:
                return false;
        }
    }


    // Context actions
    public static boolean doContextActionPlay(MediaManager.Provider.ContentType sourceType, String sourceId, int sortOrder, int position) {
        doPrepareProviderSwitch();

        final MediaManager mediaManager = PlayerApplication.libraryMediaManager();
        final MediaManager.Provider provider = mediaManager.getProvider();

        return provider.play(sourceType, sourceId, sortOrder, position, PlayerApplication.lastSearchFilter);
    }

    private static boolean doContextActionPlayNext(MediaManager.Provider.ContentType sourceType, String sourceId, int sortOrder) {
        final MediaManager mediaManager = PlayerApplication.libraryMediaManager();
        final MediaManager.Provider provider = mediaManager.getProvider();

        return provider.playNext(sourceType, sourceId, sortOrder, PlayerApplication.lastSearchFilter);
    }

    private static boolean doContextActionAddToQueue(MediaManager.Provider.ContentType sourceType, String sourceId, int sortOrder) {
        final MediaManager mediaManager = PlayerApplication.libraryMediaManager();
        final MediaManager.Provider provider = mediaManager.getProvider();

        return provider.playlistAdd(null, sourceType, sourceId, sortOrder, PlayerApplication.lastSearchFilter);
    }

    public static boolean doContextActionAddToPlaylist(final Activity parent, final MediaManager.Provider.ContentType sourceType, final String sourceId, final int sortOrder) {
        final MediaManager mediaManager = PlayerApplication.libraryMediaManager();
        final MediaManager.Provider provider = mediaManager.getProvider();

        showPlaylistManagementDialog(parent, new PlaylistManagementRunnable() {
            public void run(String playlistId) {
                LogUtils.LOGD(TAG, "trying to add to " + playlistId);
                provider.playlistAdd(playlistId, sourceType, sourceId, sortOrder, PlayerApplication.lastSearchFilter);
            }
        });
        return true;
    }

    private static boolean doContextActionToggleVisibility(MediaManager.Provider.ContentType sourceType, String sourceId) {
        final MediaManager mediaManager = PlayerApplication.libraryMediaManager();
        final MediaManager.Provider provider = mediaManager.getProvider();

        provider.setProperty(sourceType, sourceId, MediaManager.Provider.ContentProperty.CONTENT_VISIBILITY_TOGGLE, null, null);

        return true;
    }

    private static boolean doContextActionMediaRemoveFromQueue(String playlistId, int position) {
        final MediaManager mediaManager = PlayerApplication.libraryMediaManager();
        final MediaManager.Provider provider = mediaManager.getProvider();

        provider.playlistRemove(playlistId, position);
        return true;
    }

    private static boolean doContextActionPlaylistClear(String playlistId) {
        final MediaManager mediaManager = PlayerApplication.libraryMediaManager();
        final MediaManager.Provider provider = mediaManager.getProvider();

        provider.playlistClear(playlistId);
        return true;
    }

    private static boolean doContextActionDetail(Activity hostActivity, MediaManager.Provider.ContentType contentType, String contentId) {
        final MediaManager mediaManager = PlayerApplication.libraryMediaManager();
        final MediaManager.Provider provider = mediaManager.getProvider();

        int titleResId = R.string.alert_dialog_title_media_properties;
        switch (contentType) {
            case CONTENT_TYPE_ALBUM:
                titleResId = R.string.alert_dialog_title_album_properties;
                break;
        }

        final MetadataDialog metadataDialog = new MetadataDialog(hostActivity, titleResId, provider, contentType, contentId);
        metadataDialog.show();

        return true;
    }

    private static boolean doContextActionPlaylistDelete(final Activity hostActivity, final String playlistId) {
        final MediaManager mediaManager = PlayerApplication.libraryMediaManager();
        final MediaManager.Provider provider = mediaManager.getProvider();

        final DialogInterface.OnClickListener newPlaylistPositiveOnClickListener = new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                provider.playlistDelete(playlistId);
                if (hostActivity instanceof LibraryMainActivity) {
                    ((LibraryMainActivity)hostActivity).refresh();
                }
            }
        };

        final DialogInterface.OnClickListener newPlaylistNegativeOnClickListener = new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                // nothing to be done.
            }
        };

        final TextView textView = new TextView(hostActivity);

        new AlertDialog.Builder(hostActivity)
                .setTitle(R.string.alert_dialog_title_playlist_delete)
                .setMessage(R.string.alert_dialog_message_playlist_delete)
                .setView(textView)
                .setPositiveButton(android.R.string.ok, newPlaylistPositiveOnClickListener)
                .setNegativeButton(android.R.string.cancel, newPlaylistNegativeOnClickListener)
                .show();
        return true;
    }



    private static void doPrepareProviderSwitch() {
        if (PlayerApplication.libraryMediaManager().getId() != PlayerApplication.playerMediaManager().getId()) {
            boolean playing = PlayerApplication.playerService.isPlaying();

            if (playing) {
                PlayerApplication.playerService.stop();
            }

            PlayerApplication.setPlayerManager(PlayerApplication.libraryMediaManager().getId());
            PlayerApplication.saveLibraryIndexes();
            PlayerApplication.playerService.queueReload();
        }
    }



    /*

     */
    interface PlaylistManagementRunnable {
        public void run(String playlistId);
    }



    protected static void showPlaylistManagementDialog(final Activity hostActivity, final PlaylistManagementRunnable runnable) {
        final MediaManager mediaManager = PlayerApplication.libraryMediaManager();
        final MediaManager.Provider provider = mediaManager.getProvider();

        final Cursor playlistCursor = provider.buildCursor(
                MediaManager.Provider.ContentType.CONTENT_TYPE_PLAYLIST,
                new int[] {
                        MediaManager.Provider.PLAYLIST_ID,
                        MediaManager.Provider.PLAYLIST_NAME
                },
                new int[] {
                        MediaManager.Provider.PLAYLIST_ID
                },
                null,
                null,
                null
        );

        final int PLAYLIST_COLUMN_ID = 0;
        final int PLAYLIST_COLUMN_NAME = 1;

        if (playlistCursor != null) {
            final ArrayList<String> playlistItemIds = new ArrayList<>();
            final ArrayList<String> playlistItemDescriptions = new ArrayList<>();

            playlistItemIds.add(null);
            playlistItemDescriptions.add(hostActivity.getString(R.string.label_new_playlist));

            while (playlistCursor.moveToNext()) {
                playlistItemIds.add(playlistCursor.getString(PLAYLIST_COLUMN_ID));
                playlistItemDescriptions.add(playlistCursor.getString(PLAYLIST_COLUMN_NAME));
            }

            final DialogInterface.OnClickListener dialogOnClickListener = new DialogInterface.OnClickListener() {
                final MediaManager mediaManager = PlayerApplication.libraryMediaManager();
                final MediaManager.Provider mediaProvider = mediaManager.getProvider();

                final EditText nameEditText = new EditText(hostActivity);

                final DialogInterface.OnClickListener newPlaylistPositiveOnClickListener = new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Editable nameEditable = nameEditText.getText();
                        if (nameEditable != null) {
                            String playlistId = mediaProvider.playlistNew(nameEditable.toString());
                            if (playlistId != null) {
                                runnable.run(playlistId);

                                if (hostActivity instanceof LibraryMainActivity) {
                                    ((LibraryMainActivity)hostActivity).refresh();
                                }
                            }
                        }
                    }
                };

                final DialogInterface.OnClickListener newPlaylistNegativeOnClickListener = new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        // nothing to be done.
                    }
                };

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (which == 0) {
                        // New playlist
                        new AlertDialog.Builder(hostActivity)
                                .setTitle(R.string.label_new_playlist)
                                .setView(nameEditText)
                                .setPositiveButton(android.R.string.ok, newPlaylistPositiveOnClickListener)
                                .setNegativeButton(android.R.string.cancel, newPlaylistNegativeOnClickListener)
                                .show();
                    }
                    else {
                        runnable.run(playlistItemIds.get(which));
                    }
                }
            };

            new AlertDialog.Builder(hostActivity)
                    .setTitle(R.string.alert_dialog_title_add_to_playlist)
                    .setItems(playlistItemDescriptions.toArray(new String[playlistItemDescriptions.size()]), dialogOnClickListener)
                    .show();
        }
    }


    public static void addLibrary(final Activity parent, final Runnable completionRunnable) {
        final SQLiteDatabase database = OpenHelper.getInstance().getWritableDatabase();

        final ArrayList<Integer> managerItemIds = new ArrayList<>();
        final ArrayList<String> managerItemDescriptions = new ArrayList<>();

        final MediaManagerFactory.MediaManagerDescription managerList[] = MediaManagerFactory.getMediaManagerList();

        for (MediaManagerFactory.MediaManagerDescription mediaManager : managerList) {
            if (mediaManager != null && mediaManager.isEnabled) {
                managerItemIds.add(mediaManager.typeId);
                managerItemDescriptions.add(mediaManager.description);
            }
        }

        // Edit library name.
        final EditTextDialog.ButtonClickListener editionDone = new EditTextDialog.ButtonClickListener() {

            @Override
            public void click(EditTextDialog dialog) {
                final String collectionName = dialog.getText();

                if (!TextUtils.isEmpty(collectionName)) {
                    final String columns[] = new String[] {
                            "COUNT(*) AS " + Entities.Provider._COUNT
                    };

                    final Cursor cursor = database.query(Entities.Provider.TABLE_NAME, columns, null, null, null, null, null);
                    long count = 0;
                    if (CursorUtils.ifNotEmpty(cursor)) {
                        cursor.moveToFirst();
                        count = cursor.getLong(0);

                        CursorUtils.free(cursor);
                    }

                    ContentValues contentValues = new ContentValues();
                    contentValues.put(Entities.Provider.COLUMN_FIELD_PROVIDER_NAME, collectionName);
                    contentValues.put(Entities.Provider.COLUMN_FIELD_PROVIDER_TYPE, ((LibraryEditTextDialog) dialog).managerType);
                    contentValues.put(Entities.Provider.COLUMN_FIELD_PROVIDER_POSITION, count + 1);

                    long rowId = database.insert(Entities.Provider.TABLE_NAME, null, contentValues);
                    if (rowId < 0) {
                        LogUtils.LOGW(TAG, "new library: database insertion failure.");
                    } else {
                        configureLibrary(parent, (int) rowId);
                        completionRunnable.run();
                    }
                }
            }
        };

        final EditTextDialog.ButtonClickListener editionCancelled = new EditTextDialog.ButtonClickListener() {

            @Override
            public void click(EditTextDialog dialog) {
                // nothing to be done.
            }
        };


        // Choose library type.
        final DialogInterface.OnClickListener managerTypeSelectionDone = new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialogInterface, final int which) {
                final LibraryEditTextDialog editTextDialog = new LibraryEditTextDialog(parent, R.string.label_new_library);
                editTextDialog.managerType = managerItemIds.get(which);
                editTextDialog.setPositiveButtonRunnable(editionDone);
                editTextDialog.setNegativeButtonRunnable(editionCancelled);
                editTextDialog.show();
            }
        };

        new AlertDialog.Builder(parent)
                .setTitle(R.string.alert_dialog_title_type_of_library)
                .setItems(managerItemDescriptions.toArray(new String[managerItemDescriptions.size()]), managerTypeSelectionDone)
                .show();
    }

    public static void editLibrary(final Activity parent, final int managerId, final String initialName, final Runnable completionRunnable) {
        final SQLiteDatabase database = OpenHelper.getInstance().getWritableDatabase();

        final EditTextDialog.ButtonClickListener editionDone = new EditTextDialog.ButtonClickListener() {

            @Override
            public void click(EditTextDialog dialog) {
                final String collectionName = dialog.getText();

                if (!TextUtils.isEmpty(collectionName)) {
                    ContentValues contentValues = new ContentValues();
                    contentValues.put(Entities.Provider.COLUMN_FIELD_PROVIDER_NAME, collectionName);

                    database.update(
                            Entities.Provider.TABLE_NAME,
                            contentValues,
                            Entities.Provider._ID + " = ? ",
                            new String[] {
                                    String.valueOf(managerId)
                            });

                    if (managerId < 0) {
                        LogUtils.LOGW(TAG, "edit library: database insertion failure.");
                    } else {
                        configureLibrary(parent, managerId);
                        completionRunnable.run();
                    }
                }
            }
        };

        final EditTextDialog.ButtonClickListener editionCancelled = new EditTextDialog.ButtonClickListener() {

            @Override
            public void click(EditTextDialog dialog) {
                // nothing to be done.
            }
        };


        final EditTextDialog editTextDialog = new EditTextDialog(parent, R.string.label_new_library);
        editTextDialog.setText(initialName);
        editTextDialog.setPositiveButtonRunnable(editionDone);
        editTextDialog.setNegativeButtonRunnable(editionCancelled);
        editTextDialog.show();
    }

    public static void configureLibrary(Activity sourceActivity, int mediaProviderId) {
        LogUtils.LOGD(TAG, "providerId : " + mediaProviderId);
        final SQLiteDatabase database = OpenHelper.getInstance().getWritableDatabase();

        int mediaProviderType = 0;

        final String columns[] = new String[] {
                Entities.Provider._ID
        };

        final Cursor cursor = database.query(Entities.Provider.TABLE_NAME, columns, null, null, null, null, null);
        if (CursorUtils.ifNotEmpty(cursor)) {
            cursor.moveToFirst();
            mediaProviderType = cursor.getInt(0);

            CursorUtils.free(cursor);
        }

        final MediaManager localLibraryProvider = MediaManagerFactory.buildMediaManager(mediaProviderType, mediaProviderId, null);
        final MediaManager.Provider provider = localLibraryProvider.getProvider();

        final MediaManager.ProviderAction providerAction = provider.getAction(MediaManager.Provider.ACTION_INDEX_SETTINGS);

        if (providerAction != null) {
            /* launch activity */ providerAction.launch(sourceActivity);
        }
    }

    static class LibraryEditTextDialog extends EditTextDialog {

        int managerType = 0;

        public LibraryEditTextDialog(Context context, int titleId) {
            super(context, titleId);
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
            final MediaManager mediaManager = PlayerApplication.mediaManagers[managerIndex];

            if (mediaManager.getId() == providerId) {
                return managerIndex;
            }
        }

        return -1;
    }



    public static void saveLibraryIndexes() {
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        final SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.putInt(PREFERENCE_LIBRARY_PLAYER_INDEX, playerMediaManagerIndex);
        editor.putInt(PREFERENCE_LIBRARY_LIBRARY_INDEX, libraryMediaManagerIndex);
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



    public static void saveEqualizerSettings(MediaManager.Player player) {
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

    public static void restoreEqualizerSettings(MediaManager.Player player) {
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
