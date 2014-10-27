/*
 * LocalMediaProvider.java
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
package net.opusapp.player.core.service.providers.local;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Environment;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import net.opusapp.player.R;
import net.opusapp.player.core.service.providers.AbstractMediaManager;
import net.opusapp.player.core.service.providers.MediaMetadata;
import net.opusapp.player.core.service.providers.local.database.Entities;
import net.opusapp.player.core.service.providers.local.database.OpenHelper;
import net.opusapp.player.core.service.providers.local.ui.activities.SearchPathActivity;
import net.opusapp.player.core.service.providers.local.ui.activities.SettingsActivity;
import net.opusapp.player.ui.utils.MusicConnector;
import net.opusapp.player.ui.utils.PlayerApplication;
import net.opusapp.player.ui.utils.uil.ProviderImageDownloader;
import net.opusapp.player.utils.Base64;
import net.opusapp.player.utils.LogUtils;
import net.opusapp.player.utils.backport.android.content.SharedPreferencesCompat;
import net.opusapp.player.utils.jni.JniMediaLib;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class LocalProvider implements AbstractMediaManager.Provider {



    public static final String TAG = LocalProvider.class.getSimpleName();



    private OpenHelper openHelper;

    private LocalMediaManager mediaManager;



    /*
        Scanner
    */
    private boolean scanning = false;

    private boolean cancelingScan = false;

    private Thread scanThread = null;

    private ArrayList<OnLibraryChangeListener> scanListeners;



    /*
        Storage explorer
     */
    private File currentFolder = null;

    private List<File> fileList;

    public boolean isAtRootLevel;



    /*
        Actions interfaces
     */




    public final AlbumArtistEmptyAction EMPTY_ACTION_ALBUM_ARTIST = new AlbumArtistEmptyAction();

    public final AlbumEmptyAction EMPTY_ACTION_ALBUM = new AlbumEmptyAction();

    public final ArtistEmptyAction EMPTY_ACTION_ARTIST = new ArtistEmptyAction();

    public final GenreEmptyAction EMPTY_ACTION_GENRE = new GenreEmptyAction();

    public final SongEmptyAction EMPTY_ACTION_SONG = new SongEmptyAction();



    public AbstractMediaManager.ProviderAction ACTION_LIST[] = new AbstractMediaManager.ProviderAction[] {
            new LocationAction(),
            new SettingsAction()
    };



    public LocalProvider(LocalMediaManager mediaManager) {
        this.mediaManager = mediaManager;

        openHelper = new OpenHelper(mediaManager.getMediaManagerId());
        scanListeners = new ArrayList<OnLibraryChangeListener>();

        currentFolder = new File(Environment.getExternalStorageDirectory().getAbsolutePath());
        isAtRootLevel = true;

        final Resources resources = PlayerApplication.context.getResources();
        final SharedPreferences sharedPrefs = PlayerApplication.context.getSharedPreferences("provider-" + mediaManager.getMediaManagerId(), Context.MODE_PRIVATE);

        final String[] tabTitles = resources.getStringArray(R.array.preference_values_tab_visibility);

        Set<String> defaultTabs = new HashSet<String>(Arrays.asList(tabTitles));
        Set<String> userEnabledTabs = SharedPreferencesCompat.getStringSet(sharedPrefs, resources.getString(R.string.preference_key_tab_visibility), null);

        // Default instead of no selection.
        if (userEnabledTabs == null || userEnabledTabs.size() == 0) {
            SharedPreferences.Editor editor = sharedPrefs.edit();
            SharedPreferencesCompat.EditorCompat.putStringSet(editor, resources.getString(R.string.preference_key_tab_visibility), defaultTabs);
            editor.apply();
        }

        final String genreMode = resources.getString(R.string.preference_key_genre_display);
        if (TextUtils.isEmpty(genreMode)) {
            SharedPreferences.Editor editor = sharedPrefs.edit();
            editor.putString(resources.getString(R.string.preference_key_genre_display), resources.getString(R.string.preference_list_value_genre_show_albums));
            editor.apply();
        }
    }

    public SQLiteDatabase getWritableDatabase() {
        return openHelper.getWritableDatabase();
    }

    public SQLiteDatabase getReadableDatabase() {
        return openHelper.getReadableDatabase();
    }

    public void setLastPlayed(String mediaUri) {
        SQLiteDatabase database = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(Entities.Media.COLUMN_FIELD_LAST_PLAYED, new Date().getTime());

        final String where = Entities.Media.COLUMN_FIELD_URI + " = ?";
        final String whereArgs[] = new String[] {
                mediaUri
        };

        database.update(Entities.Media.TABLE_NAME, values, where, whereArgs);
    }

    protected void doEraseProviderData() {
        int playerIndex = PlayerApplication.getLibraryPlayerIndex();
        int currentIndex = PlayerApplication.getManagerIndex(mediaManager.getMediaManagerId());

        if (playerIndex == currentIndex) {
            PlayerApplication.mediaManagers[playerIndex].getPlayer().playerStop();
            PlayerApplication.playerManagerIndex = 0;
        }

        openHelper.deleteDatabaseFile();

        File filePath = PlayerApplication.context.getFilesDir();
        if (filePath != null) {
            File providerPrefs = new File(filePath.getPath() + "/shared_prefs/provider-" + mediaManager.getMediaManagerId() + ".xml");
            if (!providerPrefs.delete()) {
                LogUtils.LOGE(TAG, "deleting provider-" + mediaManager.getMediaManagerId() + " preferences failed");
            }
        }
    }

    @Override
    public void erase() {

        addLibraryChangeListener(new OnLibraryChangeListener() {
            @Override
            public void libraryChanged() {
            }

            @Override
            public void libraryScanStarted() {
            }

            @Override
            public void libraryScanFinished() {
                doEraseProviderData();
            }
        });

        if (scanIsRunning()) {
            scanCancel();
        }
        else {
            doEraseProviderData();
        }
    }

    @Override
    public boolean scanStart() {
        if (scanThread == null) {
            scanThread = new Thread() {
                @Override
                public void run() {
                    cancelingScan = false;
                    doSyncStartScan(new SyncScanContext());
                    scanThread = null;
                }
            };

            scanThread.start();
            return true;
        }
        return false;
    }

    @Override
    public boolean scanCancel() {
        cancelingScan = true;
        return true;
    }

    @Override
    public boolean scanIsRunning() {
        return scanning;
    }

    @Override
    public void addLibraryChangeListener(OnLibraryChangeListener libraryChangeListener) {
        if (scanListeners.indexOf(libraryChangeListener) < 0) {
            scanListeners.add(libraryChangeListener);
        }
    }

    @Override
    public void removeLibraryChangeListener(OnLibraryChangeListener libraryChangeListener) {
        scanListeners.remove(libraryChangeListener);
    }

    @Override
    public Cursor buildCursor(ContentType contentType, int[] fields, int[] sortFields, String filter, ContentType source, String sourceId) {
        if (source == null) {
            source = ContentType.CONTENT_TYPE_DEFAULT;
        }

        switch (contentType) {
            case CONTENT_TYPE_ALBUM:
                return doBuildAlbumCursor(fields, sortFields, filter, source, sourceId);
            case CONTENT_TYPE_ALBUM_ARTIST:
                return doBuildAlbumArtistCursor(fields, sortFields, filter);
            case CONTENT_TYPE_ARTIST:
                return doBuildArtistCursor(fields, sortFields, filter);
            case CONTENT_TYPE_GENRE:
                return doBuildGenreCursor(fields, sortFields, filter);
            case CONTENT_TYPE_PLAYLIST:
                return doBuildPlaylistCursor(fields, sortFields, filter);
            case CONTENT_TYPE_MEDIA:
                return doBuildMediaCursor(fields, sortFields, filter, source, sourceId);
            case CONTENT_TYPE_STORAGE:
                return doBuildStorageCursor(fields, sortFields, filter);
        }

        return null;
    }

    @Override
    public boolean play(ContentType contentType, String sourceId, int sortOrder, int position, String filter) {
        SQLiteDatabase database = openHelper.getWritableDatabase();

        if (database != null) {
            if (PlayerApplication.playerService != null) {
                try {
                    database.delete(Entities.PlaylistEntry.TABLE_NAME, Entities.PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = ?", new String[]{"0"});
                    if (contentType == ContentType.CONTENT_TYPE_STORAGE) {
                        if (currentFolder.getParentFile() != null) {
                            position--; // bypass position[0] (parent file).
                        }

                        if (!doPlaylistAddContent(null, 0, fileList, true)) {
                            return false;
                        }
                    }
                    else if (!doPlaylistAddContent(null, 0, contentType, sourceId, sortOrder, filter)) {
                        return false;
                    }

                    PlayerApplication.playerService.queueReload();

                    if (PlayerApplication.playerService.queueGetSize() > position) {
                        PlayerApplication.playerService.queueSetPosition(position);

                        if (!PlayerApplication.playerService.isPlaying()) {
                            PlayerApplication.playerService.play();
                        }
                    }
                    return true;
                }
                catch (final RemoteException remoteException) {
                    LogUtils.LOGException(TAG, "play", 0, remoteException);
                }
            }
        }

        return false;
    }

    @Override
    public boolean playNext(ContentType contentType, String sourceId, int sortOrder, String filter) {
        SQLiteDatabase database = openHelper.getWritableDatabase();

        if (database != null) {
            if (PlayerApplication.playerService != null) {
                try {
                    int position = PlayerApplication.playerService.queueGetPosition();

                    if (contentType == ContentType.CONTENT_TYPE_STORAGE) {
                        try {
                            final File selection = PlayerApplication.uriToFile(new String(Base64.decode(sourceId)));
                            final List<File> filePlaylist = new ArrayList<File>();
                            filePlaylist.add(selection);

                            if (!doPlaylistAddContent(null, position + 1, filePlaylist, false)) {
                                return false;
                            }
                        }
                        catch (final IOException exception) {
                            LogUtils.LOGException(TAG, "playNext", 0, exception);
                        }
                    }
                    if (!doPlaylistAddContent(null, position + 1, contentType, sourceId, sortOrder, filter)) {
                        return false;
                    }

                    PlayerApplication.playerService.queueReload();
                    return true;
                }
                catch (final RemoteException remoteException) {
                    LogUtils.LOGException(TAG, "play", 0, remoteException);
                }
            }
        }

        return false;
    }

    @Override
    public AbstractMediaManager.Media[] getCurrentPlaylist(AbstractMediaManager.Player player) {

        int[] requestedFields = new int[] {
                AbstractMediaManager.Provider.SONG_ID,
                AbstractMediaManager.Provider.SONG_URI,
                AbstractMediaManager.Provider.SONG_TITLE,
                AbstractMediaManager.Provider.SONG_ARTIST,
                AbstractMediaManager.Provider.SONG_ALBUM,
                AbstractMediaManager.Provider.SONG_DURATION
        };

        int[] sortOrder = new int[] {
                AbstractMediaManager.Provider.PLAYLIST_ENTRY_POSITION
        };

        final int COLUMN_SONG_ID = 0;

        final int COLUMN_SONG_URI = 1;

        final int COLUMN_SONG_TITLE = 2;

        final int COLUMN_SONG_ARTIST = 3;

        final int COLUMN_SONG_ALBUM = 4;

        final int COLUMN_SONG_DURATION = 5;

        Cursor cursor = buildCursor(
                AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA,
                requestedFields,
                sortOrder,
                null,
                AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_PLAYLIST,
                null);

        if (cursor == null) {
            return null;
        }

        LocalMedia[] playlist = new LocalMedia[cursor.getCount()];

        int i = -1;
        while (cursor.moveToNext()) {
            i++;
            playlist[i] = new LocalMedia(player, cursor.getString(COLUMN_SONG_URI));
            playlist[i].name = cursor.getString(COLUMN_SONG_TITLE);
            playlist[i].album = cursor.getString(COLUMN_SONG_ALBUM);
            playlist[i].artist = cursor.getString(COLUMN_SONG_ARTIST);
            playlist[i].duration = cursor.getLong(COLUMN_SONG_DURATION);
            playlist[i].artUri =
                    ProviderImageDownloader.SCHEME_URI_PREFIX + ProviderImageDownloader.SUBTYPE_MEDIA + "/" +
                            PlayerApplication.playerManagerIndex + "/" + cursor.getInt(COLUMN_SONG_ID);
        }
        cursor.close();

        return playlist;
    }

    @Override
    public String playlistNew(String playlistName) {
        SQLiteDatabase database = openHelper.getWritableDatabase();

        if (database != null) {
            ContentValues contentValues = new ContentValues();
            contentValues.put(Entities.Playlist.COLUMN_FIELD_PLAYLIST_NAME, playlistName);
            contentValues.put(Entities.Playlist.COLUMN_FIELD_VISIBLE, true);
            contentValues.put(Entities.Playlist.COLUMN_FIELD_USER_HIDDEN, false);

            return String.valueOf(database.insert(Entities.Playlist.TABLE_NAME, null, contentValues));
        }

        return null;
    }

    @Override
    public boolean playlistDelete(String playlistId) {
        SQLiteDatabase database = openHelper.getWritableDatabase();

        if (playlistId == null) {
            playlistId = "0";
        }

        return database != null &&
                database.delete(Entities.Playlist.TABLE_NAME, Entities.Playlist._ID + " = ?", new String[] { playlistId}) > 0 &&
                database.delete(Entities.PlaylistEntry.TABLE_NAME, Entities.PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = ? ", new String[] { playlistId}) > 0;
    }

    @Override
    public boolean playlistAdd(String playlistId, ContentType contentType, String sourceId, int sortOrder, String filter) {
        SQLiteDatabase database = openHelper.getReadableDatabase();

        if (playlistId == null) {
            playlistId = "0";
        }

        if (database != null) {
            if (PlayerApplication.playerService != null) {
                try {
                    int position = 0;

                    Cursor cursor = database.query(
                            Entities.PlaylistEntry.TABLE_NAME,
                            new String[] { "COUNT(*) AS CNT" },
                            Entities.PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = ? ",
                            new String[] {playlistId},
                            null,
                            null,
                            null);

                    if (cursor != null && cursor.getCount() == 1) {
                        cursor.moveToFirst();
                        position = cursor.getInt(0);
                    }
                    if (cursor != null) {
                        cursor.close();
                    }
                    LogUtils.LOGW(TAG, "playlistAdd : position = " + position);

                    if (contentType == ContentType.CONTENT_TYPE_STORAGE) {
                        try {
                            final File selection = PlayerApplication.uriToFile(new String(Base64.decode(sourceId)));
                            final List<File> filePlaylist = new ArrayList<File>();
                            filePlaylist.add(selection);

                            if (!doPlaylistAddContent(null, position, filePlaylist, false)) {
                                return false;
                            }
                        }
                        catch (final IOException exception) {
                            LogUtils.LOGException(TAG, "playlistAdd", 0, exception);
                        }
                    }
                    else if (!doPlaylistAddContent(playlistId, position, contentType, sourceId, sortOrder, filter)) {
                        return false;
                    }

                    PlayerApplication.playerService.queueReload();
                    return true;
                }
                catch (final RemoteException remoteException) {
                    LogUtils.LOGException(TAG, "play", 0, remoteException);
                }
            }
        }

        return false;
    }

    @Override
    public void playlistMove(String playlistId, int moveFrom, int moveTo) {
        if (moveFrom == moveTo) {
            return; // done.
        }

        if (playlistId == null) {
            playlistId = "0";
        }

        SQLiteDatabase database = openHelper.getWritableDatabase();
        if (database != null) {
            database.beginTransaction();
            try {
                if (moveFrom < moveTo) {
                    int lowerIndex = Math.min(moveFrom, moveTo);
                    int upperIndex = Math.max(moveFrom, moveTo);

                    // Playlist is 0, 1, 2, 3, 4, ..., indexFrom, indexFrom + 1, indexFrom + 2, ..., indexTo, ...
                    database.execSQL(
                            "UPDATE " + Entities.PlaylistEntry.TABLE_NAME + " SET " + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " = -1 " +
                                    "WHERE " +
                                    "(" + Entities.PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = " + playlistId + ") AND " +
                                    "(" + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " = " + lowerIndex + ")"
                    );

                    // Playlist is -1, 0, 1, 2, 3, 4, ..., indexFrom + 1, indexFrom + 2, ...
                    database.execSQL(
                            "UPDATE " + Entities.PlaylistEntry.TABLE_NAME + " " +
                                    "SET " + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " = " + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " - 1 " +
                                    "WHERE " +
                                    "(" + Entities.PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = " + playlistId + ") AND " +
                                    "(" + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " BETWEEN " + lowerIndex + " AND " + upperIndex + ")"
                    );


                    // Playlist is 0, 1, 2, 3, 4, ..., indexFrom + 1, indexFrom + 2, ..., indexTo - 1, indexTo, ...
                    database.execSQL(
                            "UPDATE " + Entities.PlaylistEntry.TABLE_NAME + " SET " + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " = " + upperIndex + " " +
                                    "WHERE " +
                                    "(" + Entities.PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = " + playlistId + ") AND " +
                                    "(" + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " = -1)"
                    );
                } else {
                    int lowerIndex = Math.min(moveFrom, moveTo);
                    int upperIndex = Math.max(moveFrom, moveTo);

                    database.execSQL(
                            "UPDATE " + Entities.PlaylistEntry.TABLE_NAME + " SET " + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " = -1 " +
                                    "WHERE " +
                                    "(" + Entities.PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = " + playlistId + ") AND " +
                                    "(" + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " = " + upperIndex + ")"
                    );

                    database.execSQL(
                            "UPDATE " + Entities.PlaylistEntry.TABLE_NAME + " " +
                                    "SET " + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " = " + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " + 1 " +
                                    "WHERE " +
                                    "(" + Entities.PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = " + playlistId + ") AND " +
                                    "(" + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " BETWEEN " + lowerIndex + " AND " + upperIndex + ")"
                    );

                    database.execSQL(
                            "UPDATE " + Entities.PlaylistEntry.TABLE_NAME + " " +
                                    "SET " + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " = " + lowerIndex + " " +
                                    "WHERE " +
                                    "(" + Entities.PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = " + playlistId + ") AND " +
                                    "(" + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " = -1)"
                    );
                }
                database.setTransactionSuccessful();
            }
            catch (final SQLException sqlException) {
                LogUtils.LOGException(TAG, "playlistMove", 0, sqlException);
            }
            finally {
                database.endTransaction();
            }
        }
    }

    @Override
    public void playlistRemove(String playlistId, int position) {
        SQLiteDatabase database = openHelper.getWritableDatabase();

        if (playlistId == null) {
            playlistId = "0";
        }

        if (database != null) {
            database.beginTransaction();
            try {
                database.delete(
                        Entities.PlaylistEntry.TABLE_NAME,
                        Entities.PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = ? AND " + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " = ? ",
                        new String[]{
                                playlistId,
                                String.valueOf(position),
                        }
                );

                database.execSQL(
                        "UPDATE " + Entities.PlaylistEntry.TABLE_NAME + " " +
                                "SET " + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " = " + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " - 1 " +
                                "WHERE (" + Entities.PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = ?) AND (" + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " >= ?) ",
                        new String[]{
                                playlistId,
                                String.valueOf(position),
                        }
                );
                database.setTransactionSuccessful();
            }
            catch (final SQLException sqlException) {
                LogUtils.LOGException(TAG, "playlistRemove", 0, sqlException);
            }
            finally {
                database.endTransaction();
            }
        }
    }

    @Override
    public void playlistClear(String playlistId) {
        SQLiteDatabase database = openHelper.getWritableDatabase();

        if (playlistId == null) {
            playlistId = "0";
        }

        if (database != null) {
            database.delete(Entities.PlaylistEntry.TABLE_NAME, Entities.PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = ? ", new String[]{playlistId});
        }
    }

    @Override
    public boolean hasFeature(Feature feature) {
        switch (feature) {
            case CONSTRAINT_REQUIRE_SD_CARD: // TODO: add test here if currently using sdcard!
            case CONSTRAINT_REQUIRE_CONNECTION:
                return false;
            case SUPPORT_HIDING:
            case SUPPORT_ART:
            case SUPPORT_CONFIGURATION:
                return true;
        }
        return false;
    }

    @Override
    public void setProperty(ContentType contentType, Object target, ContentProperty key, Object object, Object options) {
        switch (key) {
            case CONTENT_VISIBILITY_TOGGLE:
                doToggleVisibility(contentType, (String) target);
                break;
            case CONTENT_ART_URI:
                switch (contentType) {
                    case CONTENT_TYPE_ALBUM:
                        doUpdateAlbumCover((String) target, (String) object, (Boolean) options);
                        break;
                    case CONTENT_TYPE_MEDIA:
                        // TODO: not yet used
                        break;
                }
                break;
            case CONTENT_ART_ORIGINAL_URI:
                switch (contentType) {
                    case CONTENT_TYPE_ALBUM:
                        doRestoreAlbumCover((String) target, (Boolean) options);
                        break;
                    case CONTENT_TYPE_MEDIA:
                        // TODO: not yet used
                        break;
                }
                break;
            case CONTENT_STORAGE_UPDATE_VIEW:
                int targetIndex = (Integer) target;

                if (currentFolder.getParentFile() != null) {
                    if (targetIndex == 0) {
                        currentFolder = currentFolder.getParentFile();
                        break;
                    }
                    else {
                        targetIndex--;
                    }
                }

                File fileTarget = fileList.get(targetIndex);
                if (fileTarget.isDirectory()) {
                    currentFolder = fileTarget;
                }

                break;
            case CONTENT_STORAGE_CURRENT_LOCATION:
                currentFolder = new File((String) target);
                fileList = getStorageFileList(new SyncScanContext(), null, null);
                break;
        }
    }

    @Override
    public Object getProperty(ContentType contentType, Object target, ContentProperty key) {
        final Resources resources = PlayerApplication.context.getResources();

        switch (key) {
            case CONTENT_ART_STREAM:
                switch (contentType) {
                    case CONTENT_TYPE_ALBUM:
                        return getAlbumArt((String) target);
                    case CONTENT_TYPE_MEDIA:
                        return getSongArt((String) target);
                    case CONTENT_TYPE_STORAGE:
                        return getStorageArt((String) target);
                    default:
                        return null;
                }
            case CONTENT_STORAGE_HAS_CHILD:
                int targetIndex = (Integer) target;

                if (currentFolder.getParentFile() != null) {
                    if (targetIndex != 0) {
                        targetIndex--;
                    }
                    else {
                        return true; // current folder has childs !
                    }
                }
                return fileList.get(targetIndex).isDirectory();
            case CONTENT_STORAGE_HAS_PARENT:
                return currentFolder.getParentFile() != null;
            case CONTENT_STORAGE_CURRENT_LOCATION:
                return currentFolder.getAbsolutePath();
            case CONTENT_STORAGE_RESOURCE_POSITION:
                for (int index = 0 ; index < fileList.size() ; index++) {
                    final File file = fileList.get(index);
                    final String targetFilePath = (String) target;

                    if (file.getAbsolutePath().equals(targetFilePath)) {
                        return index + 1;
                    }
                }
                return -1;
            case CONTENT_METADATA_LIST:
                ArrayList<MediaMetadata> mediaMetadataList = new ArrayList<MediaMetadata>();

                switch (contentType) {
                    case CONTENT_TYPE_ALBUM:
                        final SQLiteDatabase database = getReadableDatabase();

                        final String albumSelection = Entities.Media.COLUMN_FIELD_ALBUM_ID + " = ? ";

                        final String albumSelectionArgs[] = new String[] {
                                (String) target
                        };

                        // Track count
                        final Cursor trackCountCursor = database.rawQuery(
                            "SELECT COUNT(*) " +
                                    "FROM " + Entities.Media.TABLE_NAME + " " +
                                    "WHERE " + Entities.Media.COLUMN_FIELD_ALBUM_ID + " = ? ", albumSelectionArgs);

                        long trackCount = 0;
                        if (trackCountCursor != null) {
                            if (trackCountCursor.moveToFirst()) {
                                trackCount = trackCountCursor.getInt(0);
                            }
                            trackCountCursor.close();
                        }

                        // Total duration
                        String totalDuration = PlayerApplication.context.getString(R.string.label_metadata_unknown);
                        final Cursor totalCursor = database.rawQuery(
                                "SELECT SUM(" + Entities.Media.COLUMN_FIELD_DURATION + ") " +
                                        "FROM " + Entities.Media.TABLE_NAME + " " +
                                        "WHERE " + Entities.Media.COLUMN_FIELD_ALBUM_ID + " = ? ", albumSelectionArgs);
                        if (totalCursor != null) {
                            if (totalCursor.moveToFirst()) {
                                totalDuration = PlayerApplication.formatSecs(totalCursor.getInt(0));
                            }
                            totalCursor.close();
                        }

                        // Album artist
                        final String artistsColumns[] = new String[] {
                                Entities.Media.COLUMN_FIELD_ARTIST
                        };

                        final Cursor artistsCursor = database.query(Entities.Media.TABLE_NAME, artistsColumns, albumSelection, albumSelectionArgs, Entities.Media.COLUMN_FIELD_ARTIST, null, Entities.Media.COLUMN_FIELD_ARTIST);
                        final ArrayList<String> artists = new ArrayList<String>();

                        if (artistsCursor != null) {
                            while (artistsCursor.moveToNext()) {
                                artists.add(artistsCursor.getString(0));
                            }
                            artistsCursor.close();
                        }

                        // Genre
                        final String genresColumns[] = new String[] {
                                Entities.Media.COLUMN_FIELD_GENRE
                        };

                        final Cursor genreCursor = database.query(Entities.Media.TABLE_NAME, genresColumns, albumSelection, albumSelectionArgs, Entities.Media.COLUMN_FIELD_GENRE, null, Entities.Media.COLUMN_FIELD_GENRE);
                        final ArrayList<String> genres = new ArrayList<String>();

                        if (genreCursor != null) {
                            while (genreCursor.moveToNext()) {
                                genres.add(genreCursor.getString(0));
                            }
                            genreCursor.close();
                        }


                        // Data compilation
                        mediaMetadataList.add(new MediaMetadata(0, resources.getString(R.string.label_metadata_album_track_count), String.valueOf(trackCount), false));
                        mediaMetadataList.add(new MediaMetadata(1, resources.getString(R.string.label_metadata_album_duration), totalDuration, false));
                        mediaMetadataList.add(new MediaMetadata(2, resources.getQuantityString(R.plurals.label_metadata_album_artists, artists.size()), TextUtils.join(", ", artists), false));
                        mediaMetadataList.add(new MediaMetadata(2, resources.getQuantityString(R.plurals.label_metadata_album_genres, genres.size()), TextUtils.join(", ", genres), false));

                        break;
                    case CONTENT_TYPE_MEDIA:
                        final int requestedFields[] = new int[] {
                            SONG_URI,
                            SONG_DURATION,
                            SONG_BITRATE,
                            SONG_SAMPLE_RATE,
                            SONG_CODEC,
                            SONG_SCORE,
                            SONG_FIRST_PLAYED,
                            SONG_LAST_PLAYED,
                            SONG_TITLE,
                            SONG_ARTIST,
                            SONG_ALBUM_ARTIST,
                            SONG_ALBUM,
                            SONG_GENRE,
                            SONG_YEAR,
                            SONG_TRACK,
                            SONG_DISC,
                        };

                        final int fieldLabelIds[] = new int[] {
                            R.string.label_metadata_media_uri,
                            R.string.label_metadata_media_duration,
                            R.string.label_metadata_media_bitrate,
                            R.string.label_metadata_media_samplerate,
                            R.string.label_metadata_media_codec,
                            R.string.label_metadata_media_score,
                            R.string.label_metadata_media_first_played,
                            R.string.label_metadata_media_last_played,
                            R.string.label_metadata_media_title,
                            R.string.label_metadata_media_artist,
                            R.string.label_metadata_media_album_artist,
                            R.string.label_metadata_media_album,
                            R.string.label_metadata_media_genre,
                            R.string.label_metadata_media_year,
                            R.string.label_metadata_media_track,
                            R.string.label_metadata_media_disc,
                        };

                        Cursor cursor = doBuildMediaCursor(requestedFields, new int[] {}, "", ContentType.CONTENT_TYPE_MEDIA, (String) target);

                        if (cursor != null && cursor.getCount() > 0) {
                            cursor.moveToFirst();

                            for (int columnIndex = 0; columnIndex < cursor.getColumnCount(); columnIndex++) {
                                if (!cursor.isNull(columnIndex)) {
                                    boolean editable = false;
                                    switch (requestedFields[columnIndex]) {
                                        case SONG_TITLE:
                                        case SONG_ARTIST:
                                        case SONG_ALBUM_ARTIST:
                                        case SONG_ALBUM:
                                        case SONG_GENRE:
                                        case SONG_YEAR:
                                        case SONG_TRACK:
                                        case SONG_DISC:
                                            editable = true;
                                    }

                                    String value = cursor.getString(columnIndex);
                                    if (requestedFields[columnIndex] == SONG_DURATION) {
                                        value = PlayerApplication.formatSecs(cursor.getInt(columnIndex));
                                    }

                                    mediaMetadataList.add(new MediaMetadata(columnIndex, resources.getString(fieldLabelIds[columnIndex]), value, editable));
                                }
                            }
                        }
                }
                return mediaMetadataList;
            default:
        }
        return null;
    }


    @Override
    public boolean hasContentType(ContentType contentType) {
        final Resources resources = PlayerApplication.context.getResources();
        final SharedPreferences sharedPrefs = PlayerApplication.context.getSharedPreferences("provider-" + mediaManager.getMediaManagerId(), Context.MODE_PRIVATE);

        final String[] tabTitles = resources.getStringArray(R.array.preference_values_tab_visibility);

        Set<String> defaultTabs = new HashSet<String>(Arrays.asList(tabTitles));
        Set<String> userEnabledTabs = SharedPreferencesCompat.getStringSet(sharedPrefs, resources.getString(R.string.preference_key_tab_visibility), defaultTabs);

        if (userEnabledTabs.size() == 0) {
            userEnabledTabs = defaultTabs;
        }

        switch (contentType) {
            case CONTENT_TYPE_PLAYLIST:
                return  userEnabledTabs.contains(resources.getString(R.string.tab_label_playlists));
            case CONTENT_TYPE_ARTIST:
                return  userEnabledTabs.contains(resources.getString(R.string.tab_label_artists));
            case CONTENT_TYPE_ALBUM_ARTIST:
                return  userEnabledTabs.contains(resources.getString(R.string.tab_label_album_artists));
            case CONTENT_TYPE_ALBUM:
                return  userEnabledTabs.contains(resources.getString(R.string.tab_label_albums));
            case CONTENT_TYPE_MEDIA:
                return  userEnabledTabs.contains(resources.getString(R.string.tab_label_songs));
            case CONTENT_TYPE_GENRE:
                return  userEnabledTabs.contains(resources.getString(R.string.tab_label_genres));
            case CONTENT_TYPE_STORAGE:
                return  userEnabledTabs.contains(resources.getString(R.string.tab_label_storage));
        }

        return false;
    }

    @Override
    public AbstractMediaManager.AbstractEmptyContentAction getEmptyContentAction(ContentType contentType) {
        switch (contentType) {
            case CONTENT_TYPE_ARTIST:
                return EMPTY_ACTION_ARTIST;
            case CONTENT_TYPE_ALBUM_ARTIST:
                return EMPTY_ACTION_ALBUM_ARTIST;
            case CONTENT_TYPE_ALBUM:
                return EMPTY_ACTION_ALBUM;
            case CONTENT_TYPE_MEDIA:
                return EMPTY_ACTION_SONG;
            case CONTENT_TYPE_GENRE:
                return EMPTY_ACTION_GENRE;
        }
        return null;
    }

    @Override
    public AbstractMediaManager.ProviderAction getAbstractProviderAction(int index) {
        return ACTION_LIST[index];
    }

    @Override
    public AbstractMediaManager.ProviderAction[] getAbstractProviderActionList() {
        return ACTION_LIST;
    }



    protected void doNotifyLibraryChanges() {
        for (OnLibraryChangeListener libraryChangeListener : scanListeners) {
            libraryChangeListener.libraryChanged();
        }
    }

    protected boolean doToggleAlbumVisibility(String albumId) {
        final SQLiteDatabase database = openHelper.getWritableDatabase();

        if (database != null) {
            database.execSQL(
                    "UPDATE " + Entities.Album.TABLE_NAME +
                            "   SET " + Entities.Album.COLUMN_FIELD_USER_HIDDEN + " = ~" + Entities.Album.COLUMN_FIELD_USER_HIDDEN + " " +
                            "WHERE " + Entities.Album._ID + " = " + albumId
            );

            doNotifyLibraryChanges();
            return true;
        }

        return false;
    }

    protected boolean doToggleAlbumArtistVisibility(String albumArtistId) {
        final SQLiteDatabase database = openHelper.getWritableDatabase();

        if (database != null) {
            database.execSQL(
                    "UPDATE " + Entities.AlbumArtist.TABLE_NAME +
                            "   SET " + Entities.AlbumArtist.COLUMN_FIELD_USER_HIDDEN + " = ~" + Entities.AlbumArtist.COLUMN_FIELD_USER_HIDDEN + " " +
                            "WHERE " + Entities.AlbumArtist._ID + " = " + albumArtistId
            );

            doNotifyLibraryChanges();
            return true;
        }

        return false;
    }

    protected boolean doToggleArtistVisibility(String artistId) {
        final SQLiteDatabase database = openHelper.getWritableDatabase();

        if (database != null) {
            database.execSQL(
                    "UPDATE " + Entities.Artist.TABLE_NAME +
                            "   SET " + Entities.Artist.COLUMN_FIELD_USER_HIDDEN + " = ~" + Entities.Artist.COLUMN_FIELD_USER_HIDDEN + " " +
                            "WHERE " + Entities.Artist._ID + " = " + artistId
            );

            doNotifyLibraryChanges();
            return true;
        }

        return false;
    }

    protected boolean doToggleGenreVisibility(String genreId) {

        final SQLiteDatabase database = openHelper.getWritableDatabase();

        if (database != null) {
            database.execSQL(
                    "UPDATE " + Entities.Genre.TABLE_NAME +
                            "   SET " + Entities.Genre.COLUMN_FIELD_USER_HIDDEN + " = ~" + Entities.Genre.COLUMN_FIELD_USER_HIDDEN + " " +
                            "WHERE " + Entities.Genre._ID + " = " + genreId
            );

            doNotifyLibraryChanges();
            return true;
        }

        return false;
    }

    protected boolean doToggleMediaVisibility(String mediaId) {
        final SQLiteDatabase database = openHelper.getWritableDatabase();

        if (database != null) {
            database.execSQL(
                    "UPDATE " + Entities.Media.TABLE_NAME +
                            "   SET " + Entities.Media.COLUMN_FIELD_USER_HIDDEN + " = ~" + Entities.Media.COLUMN_FIELD_USER_HIDDEN + " " +
                            "WHERE " + Entities.Media._ID + " = " + mediaId
            );

            doNotifyLibraryChanges();
            return true;
        }

        return false;
    }

    protected boolean doTogglePlaylistVisibility(String playlistId) {
        final SQLiteDatabase database = openHelper.getWritableDatabase();

        if (database != null) {
            database.execSQL(
                    "UPDATE " + Entities.Playlist.TABLE_NAME +
                            "   SET " + Entities.Playlist.COLUMN_FIELD_USER_HIDDEN + " = ~" + Entities.Playlist.COLUMN_FIELD_USER_HIDDEN + " " +
                            "WHERE " + Entities.Playlist._ID + " = " + playlistId
            );

            doNotifyLibraryChanges();
            return true;
        }

        return false;
    }

    protected boolean doToggleVisibility(ContentType source, String sourceId) {
        switch (source) {
            case CONTENT_TYPE_DEFAULT:
                return false;
            case CONTENT_TYPE_MEDIA:
                return doToggleMediaVisibility(sourceId);
            case CONTENT_TYPE_ARTIST:
                return doToggleArtistVisibility(sourceId);
            case CONTENT_TYPE_ALBUM_ARTIST:
                return doToggleAlbumArtistVisibility(sourceId);
            case CONTENT_TYPE_ALBUM:
                return doToggleAlbumVisibility(sourceId);
            case CONTENT_TYPE_PLAYLIST:
                return doTogglePlaylistVisibility(sourceId);
            case CONTENT_TYPE_GENRE:
                return doToggleGenreVisibility(sourceId);
        }

        return false;
    }

    protected Cursor doBuildAlbumArtistCursor(final int[] requestedFields, final int[] sortFields, String filter) {
        final ArrayList<String> columnsList = new ArrayList<String>();

        for (int field : requestedFields) {
            switch (field) {
                case ALBUM_ARTIST_ID:
                    columnsList.add(Entities.AlbumArtist._ID);
                    break;
                case ALBUM_ARTIST_NAME:
                    columnsList.add(Entities.AlbumArtist.COLUMN_FIELD_ARTIST_NAME);
                    break;
                case ALBUM_ARTIST_VISIBLE:
                    columnsList.add("(" + Entities.AlbumArtist.COLUMN_FIELD_USER_HIDDEN + "= 0)");
                    break;
            }
        }

        final String[] columns = columnsList.toArray(new String[columnsList.size()]);

        // sort order
        String orderBy = "";
        for (int field : sortFields) {
            switch (field) {
                case ALBUM_ARTIST_NAME:
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Entities.AlbumArtist.COLUMN_FIELD_ARTIST_NAME + " COLLATE NOCASE ASC";
                    break;
                case -ALBUM_ARTIST_NAME:
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Entities.AlbumArtist.COLUMN_FIELD_ARTIST_NAME + " COLLATE NOCASE DESC";
                    break;
            }
        }

        if (TextUtils.isEmpty(orderBy)) {
            orderBy = Entities.AlbumArtist.COLUMN_FIELD_ARTIST_NAME + " COLLATE NOCASE ASC";
        }

        String where = MusicConnector.show_hidden ? null : "(" + Entities.AlbumArtist.COLUMN_FIELD_USER_HIDDEN + " = 0) ";

        if (!TextUtils.isEmpty(filter)) {
            if (where != null) {
                where = where + " AND ";
            }
            else {
                where = "";
            }

            where = where + "(" + Entities.AlbumArtist.COLUMN_FIELD_ARTIST_NAME + " LIKE '%" + filter + "%')";
        }

        // query.
        final SQLiteDatabase database = openHelper.getReadableDatabase();
        if (database != null) {
            return database.query(Entities.AlbumArtist.TABLE_NAME, columns, where, null, null, null, orderBy);
        }
        return null;
    }

    protected Cursor doBuildAlbumCursor(final int[] requestedFields, final int[] sortFields, String filter, final ContentType source, final String sourceId) {
        final ArrayList<String> columnsList = new ArrayList<String>();

        for (int field : requestedFields) {
            switch (field) {
                case AbstractMediaManager.Provider.ALBUM_ID:
                    columnsList.add(Entities.Album.TABLE_NAME + "." + Entities.Album._ID);
                    break;
                case AbstractMediaManager.Provider.ALBUM_NAME:
                    columnsList.add(Entities.Album.TABLE_NAME + "." + Entities.Album.COLUMN_FIELD_ALBUM_NAME);
                    break;
                case AbstractMediaManager.Provider.ALBUM_ARTIST:
                    columnsList.add(Entities.Album.TABLE_NAME + "." + Entities.Album.COLUMN_FIELD_ALBUM_ARTIST);
                    break;
                case AbstractMediaManager.Provider.ALBUM_VISIBLE:
                    columnsList.add("(" + Entities.Album.TABLE_NAME + "." + Entities.Album.COLUMN_FIELD_USER_HIDDEN + "= 0)");
                    break;
            }
        }

        final String[] columns = columnsList.toArray(new String[columnsList.size()]);

        // sort order
        String orderBy = "";
        for (int field : sortFields) {
            switch (field) {
                case ALBUM_NAME:
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Entities.Album.TABLE_NAME + "." + Entities.Album.COLUMN_FIELD_ALBUM_NAME + " COLLATE NOCASE ASC";
                    break;
                case -ALBUM_NAME:
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Entities.Album.TABLE_NAME + "." + Entities.Album.COLUMN_FIELD_ALBUM_NAME + " COLLATE NOCASE DESC";
                    break;
                case ALBUM_ARTIST:
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Entities.Album.TABLE_NAME + "." + Entities.Album.COLUMN_FIELD_ALBUM_ARTIST + " COLLATE NOCASE ASC";
                    break;
                case -ALBUM_ARTIST:
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Entities.Album.TABLE_NAME + "." + Entities.Album.COLUMN_FIELD_ALBUM_ARTIST + " COLLATE NOCASE DESC";
                    break;
            }
        }

        if (TextUtils.isEmpty(orderBy)) {
            orderBy = Entities.Album.TABLE_NAME + "." + Entities.Album.COLUMN_FIELD_ALBUM_NAME + " COLLATE NOCASE ASC";
        }

        // setting details arguments
        String selection = MusicConnector.show_hidden ? "" : "(" + Entities.Album.TABLE_NAME + "." + Entities.Album.COLUMN_FIELD_USER_HIDDEN + " = 0) ";
        String[] selectionArgs = null;
        String groupBy = null;

        String tableDescription = Entities.Album.TABLE_NAME;

        String localSourceId = sourceId;
        if (localSourceId == null) {
            localSourceId = "0"; // null playlist is default playlist. (e.g. id = 0)
        }
        switch (source) {
            case CONTENT_TYPE_ALBUM_ARTIST:
                if (!TextUtils.isEmpty(selection)) {
                    selection = selection + " AND ";
                }
                selection = selection + Entities.Album.COLUMN_FIELD_ALBUM_ARTIST_ID + " = ? ";
                selectionArgs = new String[] {
                        localSourceId
                };
                break;
            case CONTENT_TYPE_GENRE:
                tableDescription =
                        Entities.Album.TABLE_NAME + " JOIN " + Entities.Media.TABLE_NAME +
                                " ON " + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_ALBUM_ID + " = " + Entities.Album.TABLE_NAME + "." + Entities.Album._ID;

                if (!TextUtils.isEmpty(selection)) {
                    selection = selection + " AND ";
                }

                selection = selection + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_GENRE_ID + " = ? ";
                selectionArgs = new String[] {
                        localSourceId
                };
                groupBy = Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_ALBUM_ID;
                break;
            /*
            case CONTENT_TYPE_ARTIST:
            case CONTENT_TYPE_ALBUM:
            case CONTENT_TYPE_PLAYLIST:
                throw new IllegalArgumentException();
            */
        }


        if (!TextUtils.isEmpty(filter)) {
            selection = selection + " AND ";

            selection = selection + "(" +
                    "(" + Entities.Album.TABLE_NAME + "." + Entities.Album.COLUMN_FIELD_ALBUM_ARTIST + " LIKE '%" + filter + "%') OR " +
                    "(" + Entities.Album.TABLE_NAME + "." + Entities.Album.COLUMN_FIELD_ALBUM_NAME + " LIKE '%" + filter + "%') " + ")";
        }

        // query.
        final SQLiteDatabase database = openHelper.getReadableDatabase();
        if (database != null) {
            return database.query(tableDescription, columns, selection, selectionArgs, groupBy, null, orderBy);
        }
        return null;
    }

    protected Cursor doBuildArtistCursor(final int[] requestedFields, final int[] sortFields, String filter) {
        final ArrayList<String> columnsList = new ArrayList<String>();

        for (int field : requestedFields) {
            switch (field) {
                case AbstractMediaManager.Provider.ARTIST_ID:
                    columnsList.add(Entities.Artist._ID);
                    break;
                case AbstractMediaManager.Provider.ARTIST_NAME:
                    columnsList.add(Entities.Artist.COLUMN_FIELD_ARTIST_NAME);
                    break;
                case AbstractMediaManager.Provider.ARTIST_VISIBLE:
                    columnsList.add("(" + Entities.Artist.COLUMN_FIELD_USER_HIDDEN + "= 0)");
                    break;
            }
        }

        final String[] columns = columnsList.toArray(new String[columnsList.size()]);

        // sort order
        String orderBy = "";
        for (int field : sortFields) {
            switch (field) {
                case ARTIST_NAME:
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Entities.Artist.COLUMN_FIELD_ARTIST_NAME + " COLLATE NOCASE ASC";
                    break;
                case -ARTIST_NAME:
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Entities.Artist.COLUMN_FIELD_ARTIST_NAME + " COLLATE NOCASE DESC";
                    break;
            }
        }

        if (TextUtils.isEmpty(orderBy)) {
            orderBy = Entities.Artist.COLUMN_FIELD_ARTIST_NAME + " COLLATE NOCASE ASC";
        }

        String selection = MusicConnector.show_hidden ? null : Entities.AlbumArtist.COLUMN_FIELD_USER_HIDDEN + " = 0 ";

        if (!TextUtils.isEmpty(filter)) {
            if (selection != null) {
                selection = selection + " AND ";
            }
            else {
                selection = "";
            }

            selection = selection + "(" +
                    "(" + Entities.Artist.COLUMN_FIELD_ARTIST_NAME + " LIKE '%" + filter + "%') " + ")";
        }

        // query.
        final SQLiteDatabase database = openHelper.getReadableDatabase();
        if (database != null) {
            return database.query(Entities.Artist.TABLE_NAME, columns, selection, null, null, null, orderBy);
        }
        return null;
    }

    protected Cursor doBuildGenreCursor(final int[] requestedFields, final int[] sortFields, String filter) {
        final ArrayList<String> columnsList = new ArrayList<String>();

        for (int field : requestedFields) {
            switch (field) {
                case AbstractMediaManager.Provider.GENRE_ID:
                    columnsList.add(Entities.Genre._ID);
                    break;
                case AbstractMediaManager.Provider.GENRE_NAME:
                    columnsList.add(Entities.Genre.COLUMN_FIELD_GENRE_NAME);
                    break;
                case AbstractMediaManager.Provider.GENRE_VISIBLE:
                    columnsList.add("(" + Entities.Genre.COLUMN_FIELD_USER_HIDDEN + "= 0)");
                    break;
            }
        }

        final String[] columns = columnsList.toArray(new String[columnsList.size()]);

        // sort order
        String orderBy = "";
        for (int field : sortFields) {
            switch (field) {
                case GENRE_NAME:
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Entities.Genre.COLUMN_FIELD_GENRE_NAME + " COLLATE NOCASE ASC";
                    break;
                case -GENRE_NAME:
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Entities.Genre.COLUMN_FIELD_GENRE_NAME + " COLLATE NOCASE DESC";
                    break;
            }
        }

        if (TextUtils.isEmpty(orderBy)) {
            orderBy = Entities.Genre.COLUMN_FIELD_GENRE_NAME + " COLLATE NOCASE ASC";
        }

        String selection = MusicConnector.show_hidden ? null : Entities.Genre.COLUMN_FIELD_USER_HIDDEN + " = 0 ";

        if (!TextUtils.isEmpty(filter)) {
            if (selection != null) {
                selection = selection + " AND ";
            }
            else {
                selection = "";
            }

            selection = selection + "(" +
                    "(" + Entities.Genre.COLUMN_FIELD_GENRE_NAME + " LIKE '%" + filter + "%') " + ")";
        }

        // query.
        final SQLiteDatabase database = openHelper.getReadableDatabase();
        if (database != null) {
            return database.query(Entities.Genre.TABLE_NAME, columns, selection, null, null, null, orderBy);
        }
        return null;
    }

    protected Cursor doBuildPlaylistCursor(int[] requestedFields, int[] sortFields, String filter) {

        final ArrayList<String> columnsList = new ArrayList<String>();

        for (int field : requestedFields) {
            switch (field) {
                case AbstractMediaManager.Provider.PLAYLIST_ID:
                    columnsList.add(Entities.Playlist._ID);
                    break;
                case AbstractMediaManager.Provider.PLAYLIST_NAME:
                    columnsList.add(Entities.Playlist.COLUMN_FIELD_PLAYLIST_NAME);
                    break;
                case AbstractMediaManager.Provider.PLAYLIST_VISIBLE:
                    columnsList.add("(" + Entities.Playlist.COLUMN_FIELD_VISIBLE + "<> 0) AND (" + Entities.Playlist.COLUMN_FIELD_USER_HIDDEN + "= 0)");
                    break;
            }
        }

        final String[] columns = columnsList.toArray(new String[columnsList.size()]);

        // sort order
        String orderBy = "";
        for (int field : sortFields) {
            switch (field) {
                case PLAYLIST_NAME:
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Entities.Playlist.COLUMN_FIELD_PLAYLIST_NAME + " COLLATE NOCASE ASC";
                    break;
                case -PLAYLIST_NAME:
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Entities.Playlist.COLUMN_FIELD_PLAYLIST_NAME + " COLLATE NOCASE DESC";
                    break;
            }
        }

        if (TextUtils.isEmpty(orderBy)) {
            orderBy = Entities.Playlist.COLUMN_FIELD_PLAYLIST_NAME + " COLLATE NOCASE ASC";
        }

        String selection = MusicConnector.show_hidden ?
                "(" + Entities.Playlist.COLUMN_FIELD_VISIBLE + " <> 0) AND (" + Entities.Playlist._ID + " <> 0)" :
                "(" + Entities.Playlist.COLUMN_FIELD_USER_HIDDEN + " = 0) AND " +
                        "(" + Entities.Playlist.COLUMN_FIELD_VISIBLE + " <> 0) AND (" + Entities.Playlist._ID + " <> 0)";

        if (!TextUtils.isEmpty(filter)) {
            selection = selection + " AND (" +
                    "(" + Entities.Playlist.COLUMN_FIELD_PLAYLIST_NAME + " LIKE '%" + filter + "%') " + ")";
        }

        // query.
        final SQLiteDatabase database = openHelper.getReadableDatabase();
        if (database != null) {
            return database.query(Entities.Playlist.TABLE_NAME, columns, selection, null, null, null, orderBy);
        }
        return null;
    }

    protected Cursor doBuildMediaCursor(int[] requestedFields, int[] sortFields, String filter, ContentType contentType, String sourceId) {
        final Resources resources = PlayerApplication.context.getResources();
        final SharedPreferences sharedPrefs = PlayerApplication.context.getSharedPreferences("provider-" + mediaManager.getMediaManagerId(), Context.MODE_PRIVATE);

        boolean localArts = sharedPrefs.getBoolean(resources.getString(R.string.preference_key_display_local_art), false);
        boolean manageMissingTags = sharedPrefs.getBoolean(resources.getString(R.string.preference_key_display_source_if_no_tags), true);


        boolean usesSongTable = false;
        boolean usesArtTable = false;
        boolean usesPlaylistEntryTable = false;

        final ArrayList<String> columnsList = new ArrayList<String>();

        for (int field : requestedFields) {
            switch (field) {
                case AbstractMediaManager.Provider.SONG_ID:
                    usesSongTable = true;
                    columnsList.add(Entities.Media.TABLE_NAME + "." + Entities.Media._ID);
                    break;
                case AbstractMediaManager.Provider.SONG_URI:
                    usesSongTable = true;
                    columnsList.add(Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_URI);
                    break;
                case AbstractMediaManager.Provider.SONG_ART_URI:
                    usesArtTable = true;
                    usesSongTable = true;

                    if (localArts) {
                        columnsList.add(Entities.Art.TABLE_NAME + "." + Entities.Art.COLUMN_FIELD_URI);
                    }
                    else {
                        columnsList.add("NULL AS " + Entities.Art.TABLE_NAME + "." + Entities.Art.COLUMN_FIELD_URI);
                    }
                    break;
                case AbstractMediaManager.Provider.SONG_DURATION:
                    usesSongTable = true;
                    columnsList.add(Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_DURATION);
                    break;
                case AbstractMediaManager.Provider.SONG_BITRATE:
                    usesSongTable = true;
                    columnsList.add(Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_BITRATE);
                    break;
                case AbstractMediaManager.Provider.SONG_SAMPLE_RATE:
                    usesSongTable = true;
                    columnsList.add(Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_SAMPLE_RATE);
                    break;
                case AbstractMediaManager.Provider.SONG_CODEC:
                    usesSongTable = true;
                    columnsList.add(Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_CODEC);
                    break;
                case AbstractMediaManager.Provider.SONG_SCORE:
                    usesSongTable = true;
                    columnsList.add(Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_SCORE);
                    break;
                case AbstractMediaManager.Provider.SONG_FIRST_PLAYED:
                    usesSongTable = true;
                    columnsList.add(Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_FIRST_PLAYED);
                    break;
                case AbstractMediaManager.Provider.SONG_LAST_PLAYED:
                    usesSongTable = true;
                    columnsList.add(Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_LAST_PLAYED);
                    break;
                case AbstractMediaManager.Provider.SONG_TITLE:
                    usesSongTable = true;

                    if (manageMissingTags) {
                        columnsList.add(
                            "CASE (LENGTH(TRIM(" + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_TITLE + "))) "+
                            "WHEN 0 THEN " + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_URI + " " +
                            "ELSE " + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_TITLE + " " +
                            "END " +
                            "AS " + Entities.Media.COLUMN_FIELD_TITLE
                        );
                    }
                    else {
                        columnsList.add(Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_TITLE);
                    }

                    break;
                case AbstractMediaManager.Provider.SONG_ARTIST:
                    usesSongTable = true;
                    columnsList.add(Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_ARTIST);
                    break;
                case AbstractMediaManager.Provider.SONG_ARTIST_ID:
                    usesSongTable = true;
                    columnsList.add(Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_ARTIST_ID);
                    break;
                case AbstractMediaManager.Provider.SONG_ALBUM_ARTIST:
                    usesSongTable = true;
                    columnsList.add(Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_ALBUM_ARTIST);
                    break;
                case AbstractMediaManager.Provider.SONG_ALBUM_ARTIST_ID:
                    usesSongTable = true;
                    columnsList.add(Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_ALBUM_ARTIST_ID);
                    break;
                case AbstractMediaManager.Provider.SONG_ALBUM:
                    usesSongTable = true;
                    columnsList.add(Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_ALBUM);
                    break;
                case AbstractMediaManager.Provider.SONG_ALBUM_ID:
                    usesSongTable = true;
                    columnsList.add(Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_ALBUM_ID);
                    break;
                case AbstractMediaManager.Provider.SONG_GENRE:
                    usesSongTable = true;
                    columnsList.add(Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_GENRE);
                    break;
                case AbstractMediaManager.Provider.SONG_GENRE_ID:
                    usesSongTable = true;
                    columnsList.add(Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_GENRE_ID);
                    break;
                case AbstractMediaManager.Provider.SONG_YEAR:
                    usesSongTable = true;
                    columnsList.add(Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_YEAR);
                    break;
                case AbstractMediaManager.Provider.SONG_TRACK:
                    usesSongTable = true;
                    columnsList.add(Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_TRACK);
                    break;
                case AbstractMediaManager.Provider.SONG_DISC:
                    usesSongTable = true;
                    columnsList.add(Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_DISC);
                    break;
                case AbstractMediaManager.Provider.SONG_BPM:
                    usesSongTable = true;
                    columnsList.add(Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_BPM);
                    break;
                case AbstractMediaManager.Provider.SONG_COMMENT:
                    usesSongTable = true;
                    columnsList.add(Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_COMMENT);
                    break;
                case AbstractMediaManager.Provider.SONG_LYRICS:
                    usesSongTable = true;
                    columnsList.add(Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_LYRICS);
                    break;
                case AbstractMediaManager.Provider.SONG_VISIBLE:
                    usesSongTable = true;
                    columnsList.add(
                            "(" + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_USER_HIDDEN + "= 0) AND (" + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_VISIBLE + " <> 0)"
                    );
                    break;
                case AbstractMediaManager.Provider.PLAYLIST_ENTRY_ID:
                    usesPlaylistEntryTable = true;
                    columnsList.add(Entities.PlaylistEntry.TABLE_NAME + "." + Entities.PlaylistEntry._ID);
                    break;
                case AbstractMediaManager.Provider.PLAYLIST_ENTRY_POSITION:
                    usesPlaylistEntryTable = true;
                    columnsList.add(Entities.PlaylistEntry.TABLE_NAME + "." + Entities.PlaylistEntry.COLUMN_FIELD_POSITION);
                    break;
            }
        }

        final String[] columns = columnsList.toArray(new String[columnsList.size()]);// sort order
        String orderBy = "";
        for (int field : sortFields) {
            switch (field) {
                case SONG_URI:
                    usesSongTable = true;
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_URI + " COLLATE NOCASE ASC";
                    break;
                case -SONG_URI:
                    usesSongTable = true;
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_URI + " COLLATE NOCASE DESC";
                    break;
                case SONG_FIRST_PLAYED:
                    usesSongTable = true;
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_FIRST_PLAYED + " COLLATE NOCASE ASC";
                    break;
                case -SONG_FIRST_PLAYED:
                    usesSongTable = true;
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_FIRST_PLAYED + " COLLATE NOCASE DESC";
                    break;
                case SONG_LAST_PLAYED:
                    usesSongTable = true;
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_LAST_PLAYED + " COLLATE NOCASE ASC";
                    break;
                case -SONG_LAST_PLAYED:
                    usesSongTable = true;
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_LAST_PLAYED + " COLLATE NOCASE DESC";
                    break;
                case SONG_TITLE:
                    usesSongTable = true;
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_TITLE + " COLLATE NOCASE ASC";
                    break;
                case -SONG_TITLE:
                    usesSongTable = true;
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_TITLE + " COLLATE NOCASE DESC";
                    break;
                case SONG_ARTIST:
                    usesSongTable = true;
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_ARTIST + " COLLATE NOCASE ASC";
                    break;
                case -SONG_ARTIST:
                    usesSongTable = true;
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_ARTIST + " COLLATE NOCASE DESC";
                    break;
                case SONG_ALBUM_ARTIST:
                    usesSongTable = true;
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_ALBUM_ARTIST + " COLLATE NOCASE ASC";
                    break;
                case -SONG_ALBUM_ARTIST:
                    usesSongTable = true;
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_ALBUM_ARTIST + " COLLATE NOCASE DESC";
                    break;
                case SONG_ALBUM:
                    usesSongTable = true;
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_ALBUM + " COLLATE NOCASE ASC";
                    break;
                case -SONG_ALBUM:
                    usesSongTable = true;
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_ALBUM + " COLLATE NOCASE DESC";
                    break;
                case SONG_TRACK:
                    usesSongTable = true;
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_TRACK + " COLLATE NOCASE ASC";
                    break;
                case -SONG_TRACK:
                    usesSongTable = true;
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_TRACK + " COLLATE NOCASE DESC";
                    break;
                case SONG_DISC:
                    usesSongTable = true;
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_DISC + " COLLATE NOCASE ASC";
                    break;
                case -SONG_DISC:
                    usesSongTable = true;
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_DISC + " COLLATE NOCASE DESC";
                    break;
                case PLAYLIST_ENTRY_POSITION:
                    usesPlaylistEntryTable = true;
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Entities.PlaylistEntry.TABLE_NAME + "." + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " COLLATE NOCASE ASC";
                    break;
                case -PLAYLIST_ENTRY_POSITION:
                    usesPlaylistEntryTable = true;
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Entities.PlaylistEntry.TABLE_NAME + "." + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " COLLATE NOCASE DESC";
                    break;
            }
        }

        if (TextUtils.isEmpty(orderBy)) {
            orderBy = Entities.Media.COLUMN_FIELD_TITLE + " COLLATE NOCASE ASC";
        }

        String showFiles = usesPlaylistEntryTable ? " OR (" + Entities.Media.COLUMN_FIELD_IS_QUEUE_FILE_ENTRY + " <> 0)" : "";

        String selection = MusicConnector.show_hidden ?
                "((" + Entities.Media.COLUMN_FIELD_VISIBLE + " <> 0) " + showFiles + ")" :
                "(((" + Entities.Media.COLUMN_FIELD_VISIBLE + " <> 0) AND (" + Entities.Media.COLUMN_FIELD_USER_HIDDEN + " = 0))" + showFiles + ")";
        String[] selectionArgs = null;

        if (sourceId == null) {
            sourceId = "0"; // null playlist is default playlist. (e.g. id = 0)
        }

        switch (contentType) {
            case CONTENT_TYPE_MEDIA:
                if (!TextUtils.isEmpty(selection)) {
                    selection = selection + " AND ";
                }
                selection = selection + "(" + Entities.Media._ID + " = ?) ";
                selectionArgs = new String[] {
                        sourceId
                };
                break;
            case CONTENT_TYPE_ARTIST:
                if (!TextUtils.isEmpty(selection)) {
                    selection = selection + " AND ";
                }
                selection = selection + "(" + Entities.Media.COLUMN_FIELD_ARTIST_ID + " = ?) ";
                selectionArgs = new String[] {
                        sourceId
                };
                break;
            case CONTENT_TYPE_ALBUM:
                if (!TextUtils.isEmpty(selection)) {
                    selection = selection + " AND ";
                }
                selection = selection + "(" + Entities.Media.COLUMN_FIELD_ALBUM_ID + " = ?) ";
                selectionArgs = new String[] {
                        sourceId
                };
                break;
            case CONTENT_TYPE_PLAYLIST:
                if (!TextUtils.isEmpty(selection)) {
                    selection = selection + " AND ";
                }

                usesPlaylistEntryTable = true;
                selection = selection + "(" + Entities.PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = ?) ";
                selectionArgs = new String[] {
                        sourceId
                };
                break;
            case CONTENT_TYPE_ALBUM_ARTIST:
                if (!TextUtils.isEmpty(selection)) {
                    selection = selection + " AND ";
                }
                selection = selection + "(" + Entities.Media.COLUMN_FIELD_ALBUM_ARTIST_ID + " = ?) ";
                selectionArgs = new String[] {
                        sourceId
                };
                break;
            case CONTENT_TYPE_GENRE:
                if (!TextUtils.isEmpty(selection)) {
                    selection = selection + " AND ";
                }
                selection = selection + "(" + Entities.Media.COLUMN_FIELD_GENRE_ID + " = ?) ";
                selectionArgs = new String[] {
                        sourceId
                };
                break;
        }

        if (!TextUtils.isEmpty(filter)) {
            selection = selection + "AND (" +
                    "(" + Entities.Media.COLUMN_FIELD_ARTIST + " LIKE '%" + filter + "%') OR " +
                    "(" + Entities.Media.COLUMN_FIELD_ALBUM + " LIKE '%" + filter + "%') OR " +
                    "(" + Entities.Media.COLUMN_FIELD_ALBUM_ARTIST + " LIKE '%" + filter + "%') OR " +
                    "(" + Entities.Media.COLUMN_FIELD_GENRE + " LIKE '%" + filter + "%') OR " +
                    "(" + Entities.Media.COLUMN_FIELD_TITLE + " LIKE '%" + filter + "%') " + ")";
        }

        // query.
        String tableDescription = Entities.Media.TABLE_NAME;
        if (usesPlaylistEntryTable) {
            tableDescription = Entities.PlaylistEntry.TABLE_NAME;

            if (usesSongTable) {
                tableDescription +=
                        " JOIN " + Entities.Media.TABLE_NAME +
                                " ON " +
                                        Entities.Media.TABLE_NAME + "." + Entities.Media._ID + " = " +
                                        Entities.PlaylistEntry.TABLE_NAME + "." + Entities.PlaylistEntry.COLUMN_FIELD_SONG_ID;
            }
        }

        if (usesArtTable) {
            tableDescription +=
                    " JOIN " + Entities.Art.TABLE_NAME +
                            " ON " +
                            Entities.Art.TABLE_NAME + "." + Entities.Art._ID + " = " +
                            Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_ART_ID;
        }

        final SQLiteDatabase database = openHelper.getReadableDatabase();
        if (database != null) {
            return database.query(tableDescription, columns, selection, selectionArgs, null, null, orderBy);
        }
        return null;
    }

    protected Cursor doBuildStorageCursor(int[] requestedFields, int[] sortFields, String filter) {
        final String[] columns = new String[requestedFields.length + 1];
        final Object[] currentRow = new Object[requestedFields.length + 1];

        columns[requestedFields.length] = "_id";
        for (int columnIndex = 0 ; columnIndex < requestedFields.length ; columnIndex++) {
            columns[columnIndex] = String.valueOf(requestedFields[columnIndex]);
        }

        final MatrixCursor cursor = new MatrixCursor(columns);
        final SyncScanContext scanContext = new SyncScanContext();

        if (currentFolder.getParentFile() != null) {
            currentRow[requestedFields.length] = 0;

            for (int columnIndex = 0; columnIndex < requestedFields.length; columnIndex++) {
                switch (requestedFields[columnIndex]) {
                    case SONG_ID:
                        currentRow[columnIndex] = 0;
                        break;
                    case SONG_URI:
                        currentRow[columnIndex] = PlayerApplication.fileToUri(currentFolder.getParentFile());
                        break;
                    case SONG_ART_URI:
                        currentRow[columnIndex] = "drawable://" + R.drawable.ic_action_arrow_left_top;
                        break;
                    case SONG_DURATION:
                    case SONG_BITRATE:
                    case SONG_SAMPLE_RATE:
                    case SONG_CODEC:
                    case SONG_SCORE:
                    case SONG_FIRST_PLAYED:
                    case SONG_LAST_PLAYED:
                    case SONG_TITLE:
                    case SONG_ARTIST:
                    case SONG_ALBUM:
                    case SONG_GENRE:
                    case SONG_YEAR:
                    case SONG_TRACK:
                    case SONG_DISC:
                    case SONG_BPM:
                    case SONG_COMMENT:
                    case SONG_LYRICS:
                    case SONG_VISIBLE:
                        currentRow[columnIndex] = 1;
                        break;
                    case STORAGE_ID:
                        currentRow[columnIndex] = Base64.encodeBytes(PlayerApplication.fileToUri(currentFolder.getParentFile()).getBytes());
                        break;
                    case STORAGE_DISPLAY_NAME:
                        currentRow[columnIndex] = PlayerApplication.context.getString(R.string.fs_parent_directory);
                        break;
                    case STORAGE_DISPLAY_DETAIL:
                        currentRow[columnIndex] = PlayerApplication.context.getString(R.string.fs_directory);
                        break;
                    case SONG_ARTIST_ID:
                    case SONG_ALBUM_ARTIST:
                    case SONG_ALBUM_ARTIST_ID:
                    case SONG_ALBUM_ID:
                    case SONG_GENRE_ID:
                        currentRow[columnIndex] = "-1";
                }
            }
            cursor.addRow(currentRow);
        }

        fileList = getStorageFileList(scanContext, filter, sortFields);

        for (File currentFile : fileList) {
            if (!currentFile.isDirectory()) {
                currentRow[requestedFields.length] = cursor.getCount();
                for (int columnIndex = 0 ; columnIndex < requestedFields.length; columnIndex++) {
                    switch (requestedFields[columnIndex]) {
                        case SONG_ID:
                            currentRow[columnIndex] = cursor.getCount();
                            break;
                        case SONG_URI:
                            currentRow[columnIndex] = PlayerApplication.fileToUri(currentFile);
                            break;
                        case SONG_ART_URI:
                            currentRow[columnIndex] = ProviderImageDownloader.SCHEME_URI_PREFIX +
                                    ProviderImageDownloader.SUBTYPE_STORAGE + "/" +
                                    PlayerApplication.getManagerIndex(mediaManager.getMediaManagerId()) + "/" +
                                    Base64.encodeBytes(PlayerApplication.fileToUri(currentFile).getBytes());
                            break;
                        case SONG_DURATION:
                        case SONG_BITRATE:
                        case SONG_SAMPLE_RATE:
                        case SONG_CODEC:
                            currentRow[columnIndex] = null;
                            break;
                        case SONG_SCORE:
                        case SONG_FIRST_PLAYED:
                        case SONG_LAST_PLAYED:
                            currentRow[columnIndex] = 0;
                            break;
                        case SONG_TITLE:
                        case SONG_ARTIST:
                            currentRow[columnIndex] = null;
                            break;
                        case SONG_ALBUM:
                        case SONG_GENRE:
                        case SONG_YEAR:
                        case SONG_TRACK:
                        case SONG_DISC:
                        case SONG_BPM:
                        case SONG_COMMENT:
                        case SONG_LYRICS:
                        case SONG_VISIBLE:
                            currentRow[columnIndex] = currentFile.isHidden() ? 0 : 1;
                            break;
                        case STORAGE_ID:
                            currentRow[columnIndex] = Base64.encodeBytes(PlayerApplication.fileToUri(currentFile).getBytes());
                            break;
                        case STORAGE_DISPLAY_NAME:
                            currentRow[columnIndex] = currentFile.getName();
                            break;
                        case STORAGE_DISPLAY_DETAIL:
                            currentRow[columnIndex] = doByteCountFormating(currentFile.length());
                            break;
                        case SONG_ARTIST_ID:
                        case SONG_ALBUM_ARTIST:
                        case SONG_ALBUM_ARTIST_ID:
                        case SONG_ALBUM_ID:
                        case SONG_GENRE_ID:
                            currentRow[columnIndex] = "-1";
                        default:
                            currentRow[columnIndex] = null;
                    }
                }
            }
            else {
                currentRow[requestedFields.length] = cursor.getCount();
                for (int columnIndex = 0 ; columnIndex < requestedFields.length ; columnIndex++) {
                    switch (requestedFields[columnIndex]) {
                        case SONG_ID:
                            currentRow[columnIndex] = cursor.getCount();
                            break;
                        case SONG_URI:
                            currentRow[columnIndex] = PlayerApplication.fileToUri(currentFile);
                            break;
                        case SONG_ART_URI:
                            currentRow[columnIndex] = "drawable://" + R.drawable.ic_action_folder_closed;
                            break;
                        case SONG_DURATION:
                        case SONG_BITRATE:
                        case SONG_SAMPLE_RATE:
                        case SONG_CODEC:
                        case SONG_SCORE:
                        case SONG_FIRST_PLAYED:
                        case SONG_LAST_PLAYED:
                        case SONG_TITLE:
                        case SONG_ARTIST:
                        case SONG_ALBUM:
                        case SONG_GENRE:
                        case SONG_YEAR:
                        case SONG_TRACK:
                        case SONG_DISC:
                        case SONG_BPM:
                        case SONG_COMMENT:
                        case SONG_LYRICS:
                            currentRow[columnIndex] = null;
                            break;
                        case SONG_VISIBLE:
                            currentRow[columnIndex] = currentFile.isHidden() ? 0 : 1;
                            break;
                        case STORAGE_ID:
                            currentRow[columnIndex] = Base64.encodeBytes(PlayerApplication.fileToUri(currentFile).getBytes());
                            break;
                        case STORAGE_DISPLAY_NAME:
                            currentRow[columnIndex] = currentFile.getName();
                            break;
                        case STORAGE_DISPLAY_DETAIL:
                            currentRow[columnIndex] = PlayerApplication.context.getString(R.string.fs_directory);
                            break;
                        case SONG_ARTIST_ID:
                        case SONG_ALBUM_ARTIST:
                        case SONG_ALBUM_ARTIST_ID:
                        case SONG_ALBUM_ID:
                        case SONG_GENRE_ID:
                            currentRow[columnIndex] = "-1";
                        default:
                            currentRow[columnIndex] = null;
                    }
                }
            }
            cursor.addRow(currentRow);
        }

        return cursor;
    }



    public static String doByteCountFormating(long bytes) {
        int unit = 1024;
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = "KMGTPE".charAt(exp-1) + "i";
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }



    protected InputStream getStorageArt(String media) {
        String targetPath;

        try {
            targetPath = new String(Base64.decode(media));
        }
        catch (final IOException ioException) {
            return null;
        }

        File mediaFile = PlayerApplication.uriToFile(targetPath);
        return JniMediaLib.getCoverInputStream(mediaFile);
    }

    protected InputStream getSongArt(String songId) {
        final SQLiteDatabase database = openHelper.getReadableDatabase();
        if (database == null) {
            return null;
        }

        final String tableName =
                Entities.Media.TABLE_NAME + " LEFT JOIN " + Entities.Art.TABLE_NAME + " ON " +
                        Entities.Art.TABLE_NAME + "." + Entities.Art._ID + " = " +
                        Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_ART_ID;

        final String[] columns = new String[] {
                Entities.Art.COLUMN_FIELD_URI,
                Entities.Art.COLUMN_FIELD_URI_IS_EMBEDDED
        };

        final String selection = Entities.Media.TABLE_NAME + "." + Entities.Media._ID + " = ? ";

        final String[] selectionArgs = new String[] {
                songId
        };

        final int COLUMN_ART_URI = 0;
        final int COLUMN_ART_IS_EMBEDDED = 1;

        boolean usesEmbeddedArt = false;


        String songArtUri = null;
        Cursor cursor = database.query(tableName, columns, selection, selectionArgs, null, null, null);
        if (cursor != null && cursor.getCount() > 0) {
            cursor.moveToFirst();

            songArtUri = cursor.getString(COLUMN_ART_URI);
            usesEmbeddedArt = cursor.getInt(COLUMN_ART_IS_EMBEDDED) != 0;
        }

        if (cursor != null) {
            cursor.close();
        }

        if (!TextUtils.isEmpty(songArtUri)) {
            if (usesEmbeddedArt) {
                return JniMediaLib.getCoverInputStream(PlayerApplication.uriToFile(songArtUri));
            }
            else {
                try {
                    return PlayerApplication.context.getContentResolver().openInputStream(Uri.parse(songArtUri));
                }
                catch (final FileNotFoundException fileNotFoundException) {
                    return null;
                }
            }
        }
        return null;
    }

    protected InputStream getAlbumArt(String albumId) {
        final SQLiteDatabase database = openHelper.getReadableDatabase();
        if (database == null) {
            return null;
        }

        final String tableName =
                Entities.Album.TABLE_NAME + " LEFT JOIN " + Entities.Art.TABLE_NAME + " ON " +
                        Entities.Art.TABLE_NAME + "." + Entities.Art._ID + " = " +
                        Entities.Album.TABLE_NAME + "." + Entities.Album.COLUMN_FIELD_ALBUM_ART_ID;


        final String[] columns = new String[]{
                Entities.Art.TABLE_NAME + "." + Entities.Art.COLUMN_FIELD_URI,
                Entities.Art.TABLE_NAME + "." + Entities.Art.COLUMN_FIELD_URI_IS_EMBEDDED
        };

        final int COLUMN_ART_URI = 0;
        final int COLUMN_ART_IS_EMBEDDED = 1;

        String selection = Entities.Album.TABLE_NAME + "." + Entities.Album._ID + " = ? ";
        final String[] selectionArgs = new String[]{ albumId };

        String albumArtUri = null;
        boolean isEmbedded = false;

        Cursor cursor = database.query(tableName, columns, selection, selectionArgs, null, null, null);
        if (cursor != null && cursor.getCount() > 0) {
            cursor.moveToFirst();
            albumArtUri = cursor.getString(COLUMN_ART_URI);
            isEmbedded = cursor.getInt(COLUMN_ART_IS_EMBEDDED) == 1;
        }

        if (cursor != null) {
            cursor.close();
        }

        try {
            if (!TextUtils.isEmpty(albumArtUri)) {
                if (isEmbedded) {
                    return JniMediaLib.getCoverInputStream(PlayerApplication.uriToFile(albumArtUri));
                }
                else {
                    return PlayerApplication.context.getContentResolver().openInputStream(Uri.parse(albumArtUri));
                }
            }
            return null;
        } catch (final FileNotFoundException fileNotFoundException) {
            return null;
        }
    }

    protected void doUpdateAlbumCover(String albumId, String uri, boolean updateTracks) {
        SQLiteDatabase database = openHelper.getWritableDatabase();
        long artId;

        if (database != null) {
            final String columns[] = new String[] {
                Entities.Art._ID
            };
            final String selection = Entities.Art.COLUMN_FIELD_URI + " = ? ";
            final String selectionArgs[] = new String[] {
                uri
            };

            Cursor cursor = database.query(Entities.Art.TABLE_NAME, columns, selection, selectionArgs, null, null, null);

            if (cursor != null && cursor.getCount() > 0) {
                cursor.moveToFirst();
                artId = cursor.getLong(0);
            }
            else {
                ContentValues artData = new ContentValues();
                artData.put(Entities.Art.COLUMN_FIELD_URI, uri);
                artData.put(Entities.Art.COLUMN_FIELD_URI_IS_EMBEDDED, false);
                artId = database.insert(Entities.Art.TABLE_NAME, null, artData);
            }

            if (cursor != null) {
                cursor.close();
            }


            final String whereAlbumId[] = new String[] {
                    albumId
            };



            ContentValues contentValues = new ContentValues();
            contentValues.put(Entities.Album.COLUMN_FIELD_ALBUM_ART_ID, artId);

            database.update(Entities.Album.TABLE_NAME, contentValues, Entities.Album._ID + " = ? ", whereAlbumId);

            if (updateTracks) {
                contentValues.clear();
                contentValues.put(Entities.Media.COLUMN_FIELD_ART_ID, artId);

                database.update(Entities.Media.TABLE_NAME, contentValues, Entities.Media.COLUMN_FIELD_ALBUM_ID + " = ? ", whereAlbumId);
            }

            doNotifyLibraryChanges();
        }
    }

    protected void doRestoreAlbumCover(String albumId, boolean updateTracks) {
        SQLiteDatabase database = openHelper.getWritableDatabase();

        if (database != null) {
            final String whereAlbumId[] = new String[] {
                    albumId
            };

            database.execSQL(
                    "UPDATE " + Entities.Album.TABLE_NAME + " " +
                    "SET " + Entities.Album.COLUMN_FIELD_ALBUM_ART_ID + " = " + Entities.Album.COLUMN_FIELD_ORIGINAL_ALBUM_ART_ID + " " +
                    "WHERE " + Entities.Album._ID + " = ? ", whereAlbumId);


            if (updateTracks) {
                database.execSQL(
                    "UPDATE " + Entities.Media.TABLE_NAME + " " +
                    "SET " + Entities.Media.COLUMN_FIELD_ART_ID + " = " + Entities.Media.COLUMN_FIELD_ORIGINAL_ART_ID + " " +
                    "WHERE " + Entities.Media.COLUMN_FIELD_ALBUM_ID + " = ? ", whereAlbumId);
            }

            doNotifyLibraryChanges();
        }
    }











    protected static boolean isArtFile(SyncScanContext scanContext, File file) {
        String filePath = file.getAbsolutePath();

        if (scanContext.albumArtExtensions == null) {
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(PlayerApplication.context);

            Set<String> defaults = new HashSet<String>(Arrays.asList(PlayerApplication.context.getResources().getStringArray(R.array.cover_exts)));
            Set<String> extensionSet = SharedPreferencesCompat.getStringSet(sharedPrefs, PlayerApplication.context.getString(R.string.key_cover_exts), defaults);

            if(extensionSet.size() == 0) {
                extensionSet = defaults;
            }

            scanContext.albumArtExtensions = new ArrayList<String>(extensionSet);
        }

        for(String extension : scanContext.albumArtExtensions) {
            int extensionLength = extension.length();
            int extensionOffset = filePath.length() - extension.length();

            if(extension.regionMatches(true, 0, filePath, extensionOffset, extensionLength)) {
                return true;
            }
        }

        return false;
    }

    protected static boolean isAudioFile(SyncScanContext scanContext, File file) {
        if (file.isDirectory()) {
            return false;
        }

        String filePath = file.getAbsolutePath();

        if (scanContext.audioFilesExtensions == null) {
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(PlayerApplication.context);

            Set<String> defaults = new HashSet<String>(Arrays.asList(PlayerApplication.context.getResources().getStringArray(R.array.audio_exts)));
            Set<String> extensionSet = SharedPreferencesCompat.getStringSet(sharedPrefs, PlayerApplication.context.getString(R.string.key_audio_exts), defaults);

            if(extensionSet.size() == 0) {
                extensionSet = defaults;
            }

            scanContext.audioFilesExtensions = new ArrayList<String>(extensionSet);
        }

        for(String extension : scanContext.audioFilesExtensions) {
            int extensionLength = extension.length();
            int extensionOffset = filePath.length() - extension.length();

            if(extension.regionMatches(true, 0, filePath, extensionOffset, extensionLength)) {
                return true;
            }
        }

        return false;
    }

    protected boolean mediaExistsInDb(String mediaUri) {
        SQLiteDatabase database = openHelper.getReadableDatabase();

        if (database == null) {
            return true; // error, will be added in next scan.
        }

        Cursor cursor = database.query(
                Entities.Media.TABLE_NAME,
                new String[] { Entities.Media._ID},
                Entities.Media.COLUMN_FIELD_URI + " = ? ",
                new String[] { mediaUri },
                null,
                null,
                null);

        boolean exists = (cursor != null) && (cursor.getCount() > 0);
        if (cursor != null) {
            cursor.close();
        }

        return exists;
    }


    protected void doSyncStartScan(SyncScanContext scanContext) {
        if (scanning) {
            return;
        }

        for (OnLibraryChangeListener libraryChangeListener : scanListeners) {
            libraryChangeListener.libraryScanStarted();
        }

        scanning = true;
        scanContext.database = openHelper.getWritableDatabase();

        Cursor acceptCursor = null;
        Cursor discardCursor = null;

        try {
            if (scanContext.database != null) {
                /*
                    Firstly, checking for deleted files.
                */
                final String[] projection = new String[]{
                        Entities.Media._ID,
                        Entities.Media.COLUMN_FIELD_URI
                };

                Cursor cursor = scanContext.database.query(Entities.Media.TABLE_NAME, projection, null, null, null, null, null);

                if (cursor != null) {
                    int indexOfId = cursor.getColumnIndexOrThrow(Entities.Media._ID);
                    int indexOfFilePath = cursor.getColumnIndexOrThrow(Entities.Media.COLUMN_FIELD_URI);

                    while (cursor.moveToNext()) {
                        // Checking for deleted files
                        final File mediaFile = PlayerApplication.uriToFile(cursor.getString(indexOfFilePath));
                        if (!mediaFile.exists()) {
                            final String where = Entities.Media._ID + " = ? ";
                            final String[] selectionArgs = new String[]{
                                    String.valueOf(cursor.getInt(indexOfId))
                            };

                            LogUtils.LOGI(TAG, "!Media : " + cursor.getString(indexOfFilePath));
                            scanContext.database.delete(Entities.Media.TABLE_NAME, where, selectionArgs);
                        }
                    }
                    cursor.close();
                }

                /*
                    Next, we make a list of forbidden paths.
                */
                final String pathProjection[] = new String[]{
                        Entities.ScanDirectory.COLUMN_FIELD_SCAN_DIRECTORY_NAME
                };

                final String selection = Entities.ScanDirectory.COLUMN_FIELD_SCAN_DIRECTORY_IS_EXCLUDED + " = ? ";

                final String selectionAccept[] = new String[]{
                        "0"
                };

                final String selectionDiscard[] = new String[]{
                        "1"
                };

                ArrayList<File> acceptList = new ArrayList<File>();

                acceptCursor = scanContext.database.query(Entities.ScanDirectory.TABLE_NAME, pathProjection, selection, selectionAccept, null, null, null);
                discardCursor = scanContext.database.query(Entities.ScanDirectory.TABLE_NAME, pathProjection, selection, selectionDiscard, null, null, null);

                Map<String, Boolean> discardMap = new HashMap<String, Boolean>();
                if (discardCursor != null && discardCursor.getCount() > 0) {
                    scanContext.database.beginTransaction();

                    while (discardCursor.moveToNext()) {
                        final String discardPath = discardCursor.getString(0);
                        discardMap.put(discardPath, true);

                        scanContext.database.delete(
                                Entities.Media.TABLE_NAME,
                                Entities.Media.COLUMN_FIELD_URI + " LIKE ?",
                                new String[]{
                                        "file://" + discardPath + File.separator + "%"
                                }
                        );
                    }

                    scanContext.database.setTransactionSuccessful();
                    scanContext.database.endTransaction();
                }

                if (acceptCursor != null && acceptCursor.getCount() > 0) {
                    while (acceptCursor.moveToNext()) {
                        final String currentPath = acceptCursor.getString(0);
                        if (currentPath != null) {
                            acceptList.add(new File(currentPath));
                        }
                    }

                    doSyncDirectoryScan(acceptList, discardMap, scanContext);
                }

                scanContext.database.beginTransaction();

                LogUtils.LOGI(TAG, "Updating albums");
                scanContext.database.delete(
                        Entities.Album.TABLE_NAME,
                        Entities.Album._ID + " NOT IN ( " +
                                "SELECT " + Entities.Media.COLUMN_FIELD_ALBUM_ID + " " +
                                "FROM " + Entities.Media.TABLE_NAME + " " +
                                "GROUP BY " + Entities.Media.COLUMN_FIELD_ALBUM_ID +
                                ")",
                        null
                );

                LogUtils.LOGI(TAG, "Updating Album artists");
                scanContext.database.delete(
                        Entities.AlbumArtist.TABLE_NAME,
                        Entities.AlbumArtist._ID + " NOT IN ( " +
                                "SELECT " + Entities.Media.COLUMN_FIELD_ALBUM_ARTIST_ID + " " +
                                "FROM " + Entities.Media.TABLE_NAME + " " +
                                "GROUP BY " + Entities.Media.COLUMN_FIELD_ALBUM_ARTIST_ID +
                                ")",
                        null
                );

                LogUtils.LOGI(TAG, "Updating Artists");
                scanContext.database.delete(
                        Entities.Artist.TABLE_NAME,
                        Entities.Artist._ID + " NOT IN ( " +
                                "SELECT " + Entities.Media.COLUMN_FIELD_ARTIST_ID + " " +
                                "FROM " + Entities.Media.TABLE_NAME + " " +
                                "GROUP BY " + Entities.Media.COLUMN_FIELD_ARTIST_ID +
                                ")",
                        null
                );

                LogUtils.LOGI(TAG, "Updating Genres");
                scanContext.database.delete(
                        Entities.Genre.TABLE_NAME,
                        Entities.Genre._ID + " NOT IN ( " +
                                "SELECT " + Entities.Media.COLUMN_FIELD_GENRE_ID + " " +
                                "FROM " + Entities.Media.TABLE_NAME + " " +
                                "GROUP BY " + Entities.Media.COLUMN_FIELD_GENRE_ID +
                                ")",
                        null
                );
                LogUtils.LOGI(TAG, "updates done");
                scanContext.database.setTransactionSuccessful();
                scanContext.database.endTransaction();

                scanContext.database = null;
            }
        }
        catch (final Exception exception) {
            LogUtils.LOGException(TAG, "doSyncStartScan", 0, exception);
        }
        finally {
            if (scanContext.database != null && scanContext.database.inTransaction()) {
                scanContext.database.endTransaction();
            }

            if (acceptCursor != null) {
                acceptCursor.close();
            }

            if (discardCursor != null) {
                discardCursor.close();
            }
        }

        scanning = false;

        for (OnLibraryChangeListener libraryChangeListener : scanListeners) {
            libraryChangeListener.libraryScanFinished();
        }
    }

    protected Long getArtistId(String artist, SyncScanContext scanContext) {
        Long id = scanContext.artistIdMap.get(artist);

        if (TextUtils.isEmpty(artist)) {
            return null;
        }

        if (id == null) {
            Cursor artistCursor = scanContext.database.query(Entities.Artist.TABLE_NAME, new String[] {Entities.Artist._ID}, Entities.Artist.COLUMN_FIELD_ARTIST_NAME + " = ?", new String[] { artist }, null, null, null);

            if (artistCursor != null && artistCursor.getCount() > 0) {
                artistCursor.moveToPosition(0);
                id = artistCursor.getLong(0);
                scanContext.artistIdMap.put(artist, id);
            }
            else {
                ContentValues artistValues = new ContentValues();
                artistValues.put(Entities.Artist.COLUMN_FIELD_ARTIST_NAME, artist);
                artistValues.put(Entities.Artist.COLUMN_FIELD_USER_HIDDEN, false);
                artistValues.put(Entities.Artist.COLUMN_FIELD_VISIBLE, true);
                id = scanContext.database.insert(Entities.Artist.TABLE_NAME, null, artistValues);
                scanContext.artistIdMap.put(artist, id);
            }

            if (artistCursor != null) {
                artistCursor.close();
            }
        }

        return id;
    }

    protected Long getAlbumId(String album, SyncScanContext scanContext) {
        Long id = scanContext.albumIdMap.get(album);

        if (TextUtils.isEmpty(album)) {
            return null;
        }

        if (id == null) {
            Cursor albumCursor = scanContext.database.query(Entities.Album.TABLE_NAME, new String[]{Entities.Album._ID}, Entities.Album.COLUMN_FIELD_ALBUM_NAME + " = ?", new String[]{album}, null, null, null);

            if (albumCursor != null && albumCursor.getCount() > 0) {
                albumCursor.moveToPosition(0);
                id = albumCursor.getLong(0);
                scanContext.albumIdMap.put(album, id);
            }
            else {
                ContentValues albumValues = new ContentValues();
                albumValues.put(Entities.Album.COLUMN_FIELD_ALBUM_NAME, album);
                albumValues.put(Entities.Album.COLUMN_FIELD_USER_HIDDEN, false);
                id = scanContext.database.insert(Entities.Album.TABLE_NAME, null, albumValues);
                scanContext.albumIdMap.put(album, id);
            }

            if (albumCursor != null) {
                albumCursor.close();
            }
        }

        return id;
    }

    protected Long getGenreId(String genre, SyncScanContext scanContext) {
        Long id = scanContext.genreIdMap.get(genre);

        if (TextUtils.isEmpty(genre)) {
            return null;
        }

        if (id == null) {
            Cursor genreCursor = scanContext.database.query(Entities.Genre.TABLE_NAME, new String[] {Entities.Genre._ID}, Entities.Genre.COLUMN_FIELD_GENRE_NAME + " = ?", new String[] { genre }, null, null, null);

            if (genreCursor != null && genreCursor.getCount() > 0) {
                genreCursor.moveToPosition(0);
                id = genreCursor.getLong(0);
                scanContext.genreIdMap.put(genre, id);
            }
            else {
                ContentValues genreValues = new ContentValues();
                genreValues.put(Entities.Genre.COLUMN_FIELD_GENRE_NAME, genre);
                genreValues.put(Entities.Genre.COLUMN_FIELD_USER_HIDDEN, false);
                genreValues.put(Entities.Genre.COLUMN_FIELD_VISIBLE, true);
                id = scanContext.database.insert(Entities.Genre.TABLE_NAME, null, genreValues);
                scanContext.genreIdMap.put(genre, id);
            }

            if (genreCursor != null) {
                genreCursor.close();
            }
        }

        return id;
    }

    protected long getCoverForFile(File sourceFile, SyncScanContext scanContext, boolean hasEmbeddedTag) {
        if (sourceFile == null || sourceFile.getParentFile() == null) {
            return 0;
        }

        final ContentValues mediaCover = new ContentValues();

        long embeddedCoverId = 0;
        long coverId = 0;

        if (hasEmbeddedTag) {
            mediaCover.clear();
            mediaCover.put(Entities.Art.COLUMN_FIELD_URI, PlayerApplication.fileToUri(sourceFile));
            mediaCover.put(Entities.Art.COLUMN_FIELD_URI_IS_EMBEDDED, true);
            embeddedCoverId = scanContext.database.insert(Entities.Art.TABLE_NAME, null, mediaCover);
        }

        final Resources resources = PlayerApplication.context.getResources();
        final SharedPreferences sharedPrefs = PlayerApplication.context.getSharedPreferences("provider-" + mediaManager.getMediaManagerId(), Context.MODE_PRIVATE);

        if (sharedPrefs.getBoolean(resources.getString(R.string.preference_key_display_local_art), false)) {
            final File parentFile = sourceFile.getParentFile();

            if (scanContext.coverMap.containsKey(parentFile.getName())) {
                coverId = scanContext.coverMap.get(parentFile.getName());
            } else {
                List<File> fileList = new ArrayList<File>();
                fileList.add(parentFile);

                for (int pathIndex = 0; pathIndex < fileList.size(); ) {
                    if (cancelingScan) {
                        break;
                    }

                    File currentFile = fileList.get(pathIndex);
                    if (currentFile.isDirectory()) {
                        LogUtils.LOGI(TAG, "entering: " + currentFile.getName());
                        fileList.remove(pathIndex);

                        // directory content is not empty
                        File directoryContent[] = currentFile.listFiles();
                        if (directoryContent != null) {
                            // add directory content to scanlist
                            for (File subFile : directoryContent) {
                                fileList.add(pathIndex, subFile);
                            }
                        }
                    } else {
                        if (currentFile.length() == 0) {
                            fileList.remove(pathIndex);
                            continue;
                        }

                        if (isArtFile(scanContext, currentFile)) {
                            final String artUri = PlayerApplication.fileToUri(currentFile);
                            coverId = 0l;
                            if (scanContext.coverMap.containsKey(parentFile.getName())) {
                                coverId = scanContext.coverMap.get(parentFile.getName());
                            }

                            // update database
                            if (coverId == 0) {
                                mediaCover.clear();
                                mediaCover.put(Entities.Art.COLUMN_FIELD_URI, artUri);
                                mediaCover.put(Entities.Art.COLUMN_FIELD_URI_IS_EMBEDDED, false);
                                coverId = scanContext.database.insert(Entities.Art.TABLE_NAME, null, mediaCover);

                                mediaCover.clear();
                                mediaCover.put(Entities.Media.COLUMN_FIELD_ART_ID, artUri);
                                mediaCover.put(Entities.Media.COLUMN_FIELD_ORIGINAL_ART_ID, artUri);

                                int rows = scanContext.database.update(
                                        Entities.Media.TABLE_NAME,
                                        mediaCover,
                                        Entities.Media.COLUMN_FIELD_URI + " LIKE ?",
                                        new String[]{parentFile.getName() + File.separator + "%"});
                                LogUtils.LOGI(TAG, "Updated covers: " + rows + " rows (" + parentFile.getName() + File.separator + "%" + ") -> " + artUri);
                            }

                            LogUtils.LOGI(TAG, "Updated covers uri: " + parentFile.getName() + " -> " + artUri);
                            scanContext.coverMap.put(parentFile.getName(), coverId);
                        }
                        fileList.remove(pathIndex);
                    }
                }
            }
        }

        return embeddedCoverId != 0 ? embeddedCoverId : coverId;
    }

    protected void updateAlbumArtists(SyncScanContext scanContext) {
        scanContext.database.execSQL(
                "UPDATE " + Entities.Album.TABLE_NAME + " SET " +
                        Entities.Album.COLUMN_FIELD_ALBUM_ARTIST + " = ( " +
                        "SELECT " +
                        "CASE WHEN COUNT(DISTINCT " + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_ARTIST + ") = 1 THEN " + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_ARTIST + " " +
                        "ELSE '" + PlayerApplication.getVariousArtists() + "' " +
                        "END " +
                        "AS " + Entities.Media.COLUMN_FIELD_ARTIST + " " +
                        "FROM " + Entities.Media.TABLE_NAME + " " +
                        "WHERE (" + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_ALBUM_ID + " = " + Entities.Album.TABLE_NAME + "." + Entities.Album._ID + ") " +
                        " AND (" +
                        "(" + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_IS_QUEUE_FILE_ENTRY + " = 0) " +
                        "OR (" + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_IS_QUEUE_FILE_ENTRY + " IS NULL)" +
                        ") " +
                        "GROUP BY " + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_ALBUM_ID +
                        ")");

        scanContext.database.execSQL(
                "INSERT OR IGNORE INTO " + Entities.AlbumArtist.TABLE_NAME + " (" +
                        Entities.AlbumArtist.COLUMN_FIELD_ARTIST_NAME + ", " +
                        Entities.AlbumArtist.COLUMN_FIELD_VISIBLE + ", " +
                        Entities.AlbumArtist.COLUMN_FIELD_USER_HIDDEN +
                        ") " +
                        "SELECT " + Entities.Album.COLUMN_FIELD_ALBUM_ARTIST + ", -1, 0 " +
                        "FROM " + Entities.Album.TABLE_NAME + " GROUP BY " + Entities.Album.COLUMN_FIELD_ALBUM_ARTIST
        );

        scanContext.database.execSQL(
                "UPDATE " + Entities.Album.TABLE_NAME + " SET " + Entities.Album.COLUMN_FIELD_ALBUM_ARTIST_ID + " = (" +
                        "SELECT " + Entities.AlbumArtist._ID + " " +
                        "FROM " + Entities.AlbumArtist.TABLE_NAME + " " +
                        "WHERE " + Entities.AlbumArtist.COLUMN_FIELD_ARTIST_NAME + " = " + Entities.Album.COLUMN_FIELD_ALBUM_ARTIST + " " +
                        "GROUP BY " + Entities.AlbumArtist.COLUMN_FIELD_ARTIST_NAME +
                        ") " +
                        "WHERE " + Entities.Album.COLUMN_FIELD_ALBUM_ARTIST + " IN (" +
                        "SELECT " + Entities.AlbumArtist.COLUMN_FIELD_ARTIST_NAME + " " +
                        "FROM " + Entities.AlbumArtist.TABLE_NAME +
                        ") ");

        scanContext.database.execSQL(
                "UPDATE " + Entities.Media.TABLE_NAME + " SET " +
                        Entities.Media.COLUMN_FIELD_ALBUM_ARTIST_ID + " = (" +
                        "SELECT " + Entities.Album.COLUMN_FIELD_ALBUM_ARTIST_ID + " " +
                        "FROM " + Entities.Album.TABLE_NAME + " " +
                        "WHERE " + Entities.Album._ID + " = " + Entities.Media.COLUMN_FIELD_ALBUM_ID + " " +
                        "GROUP BY " + Entities.Album._ID +
                        "), " +
                        Entities.Media.COLUMN_FIELD_ALBUM_ARTIST + " = (" +
                        "SELECT " + Entities.Album.COLUMN_FIELD_ALBUM_ARTIST + " " +
                        "FROM " + Entities.Album.TABLE_NAME + " " +
                        "WHERE " + Entities.Album._ID + " = " + Entities.Media.COLUMN_FIELD_ALBUM_ID + " " +
                        "GROUP BY " + Entities.Album._ID +
                        ") " +
                        "WHERE " + Entities.Media.COLUMN_FIELD_ALBUM_ID + " IN (" +
                        "SELECT " + Entities.Album._ID + " " +
                        "FROM " + Entities.Album.TABLE_NAME +
                        ") "
        );

        scanContext.database.execSQL(
                "DELETE FROM " + Entities.AlbumArtist.TABLE_NAME + " WHERE " + Entities.AlbumArtist._ID + " NOT IN (" +
                        "SELECT " + Entities.Media.COLUMN_FIELD_ALBUM_ARTIST_ID + " FROM " + Entities.Media.TABLE_NAME + ")"
        );
    }

    protected void updateAlbumCovers(SyncScanContext scanContext) {
        scanContext.database.execSQL(
                "UPDATE " + Entities.Album.TABLE_NAME + " SET " +
                        Entities.Album.COLUMN_FIELD_ALBUM_ART_ID + " = ( " +
                        "SELECT " + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_ART_ID + " " +
                        "FROM " + Entities.Media.TABLE_NAME + " " +
                        "WHERE " + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_ALBUM_ID + " = " + Entities.Album.TABLE_NAME + "." + Entities.Album._ID + " " +
                        "  AND " + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_ART_ID + " IS NOT NULL " +
                        "UNION " +
                        "SELECT NULL " +
                        "ORDER BY " + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_ART_ID + " DESC " +
                        "LIMIT 1 " +
                        ") " +
                        "WHERE (" + Entities.Album.TABLE_NAME + "." + Entities.Album.COLUMN_FIELD_ALBUM_ART_ID + " IS NULL) OR (" + Entities.Album.TABLE_NAME + "." + Entities.Album.COLUMN_FIELD_ALBUM_ART_ID + " = '')"
        );

        scanContext.database.execSQL(
                "UPDATE " + Entities.Album.TABLE_NAME + " SET " +
                        Entities.Album.COLUMN_FIELD_ORIGINAL_ALBUM_ART_ID + " = " + Entities.Album.COLUMN_FIELD_ALBUM_ART_ID + " " +
                        "WHERE (" + Entities.Album.TABLE_NAME + "." + Entities.Album.COLUMN_FIELD_ORIGINAL_ALBUM_ART_ID + " IS NULL) OR (" + Entities.Album.TABLE_NAME + "." + Entities.Album.COLUMN_FIELD_ORIGINAL_ALBUM_ART_ID + " = '')"
        );
    }

    protected void doSyncDirectoryScan(List<File> fileList, Map<String, Boolean> discardMap, SyncScanContext scanContext) {
        ContentValues mediaTags = new ContentValues();

        scanContext.coverMap = new HashMap<String, Long>();
        scanContext.artistIdMap = new HashMap<String, Long>();
        scanContext.albumIdMap = new HashMap<String, Long>();
        scanContext.genreIdMap = new HashMap<String, Long>();

        int refreshThreshold = 0;
        for (int pathIndex = 0 ; pathIndex < fileList.size() ; ) {
            if (cancelingScan) {
                break;
            }

            File currentFile = fileList.get(pathIndex);
            if (currentFile.isDirectory()) {
                final String path = currentFile.getAbsolutePath();

                if (discardMap.get(path) == null || discardMap.get(path).equals(false)) {
                    LogUtils.LOGI(TAG, "+Path: " + currentFile.getName());
                    fileList.remove(pathIndex);

                    // directory content is not empty
                    File directoryContent[] = currentFile.listFiles();
                    if (directoryContent != null) {
                        // add directory content to scanlist
                        for (File subFile : directoryContent) {
                            fileList.add(pathIndex, subFile);
                        }
                    }
                }
                else {
                    LogUtils.LOGI(TAG, "-Path: " + currentFile.getName());
                    fileList.remove(pathIndex);
                }
            }
            else {
                if (currentFile.length() == 0) {
                    fileList.remove(pathIndex);
                    continue;
                }

                if (isAudioFile(scanContext, currentFile)) {
                    final String mediaUri = PlayerApplication.fileToUri(currentFile);
                    if (!mediaExistsInDb(mediaUri)) {
                        LogUtils.LOGI(TAG, "+Media : " + mediaUri);

                        JniMediaLib.doReadTags(currentFile, mediaTags);

                        boolean hasEmbeddedArt = mediaTags.getAsBoolean(Entities.Media.NOT_PERSISTANT_COLUMN_FIELD_HAS_EMBEDDED_ART);
                        long coverId = getCoverForFile(currentFile, scanContext, hasEmbeddedArt);

                        if (coverId != 0) {
                            mediaTags.put(Entities.Media.COLUMN_FIELD_ART_ID, coverId);
                            mediaTags.put(Entities.Media.COLUMN_FIELD_ORIGINAL_ART_ID, coverId);
                        }
                        else {
                            // cover not found, registering the directory for future potential update.
                            if (scanContext.coverMap.get(currentFile.getParent()) == null) {
                                scanContext.coverMap.put(currentFile.getParent(), 0l);
                            }
                            mediaTags.remove(Entities.Media.COLUMN_FIELD_ART_ID);
                            mediaTags.remove(Entities.Media.COLUMN_FIELD_ORIGINAL_ART_ID);
                        }

                        mediaTags.put(Entities.Media.COLUMN_FIELD_ARTIST_ID, getArtistId(mediaTags.getAsString(Entities.Media.COLUMN_FIELD_ARTIST), scanContext));
                        mediaTags.put(Entities.Media.COLUMN_FIELD_ALBUM_ID, getAlbumId(mediaTags.getAsString(Entities.Media.COLUMN_FIELD_ALBUM), scanContext));
                        mediaTags.put(Entities.Media.COLUMN_FIELD_GENRE_ID, getGenreId(mediaTags.getAsString(Entities.Media.COLUMN_FIELD_GENRE), scanContext));
                        mediaTags.put(Entities.Media.COLUMN_FIELD_VISIBLE, true);
                        mediaTags.put(Entities.Media.COLUMN_FIELD_USER_HIDDEN, false);


                        mediaTags.remove(Entities.Media.NOT_PERSISTANT_COLUMN_FIELD_HAS_EMBEDDED_ART);
                        scanContext.database.insert(Entities.Media.TABLE_NAME, null, mediaTags);

                        refreshThreshold++;
                        if (refreshThreshold >= 25) {
                            doNotifyLibraryChanges();
                            refreshThreshold = 0;
                        }
                    }
                    else {
                        LogUtils.LOGI(TAG, "=Media : " + mediaUri);
                    // nothing to be done.
                    }
                }
                fileList.remove(pathIndex);
            }
        }

        updateAlbumArtists(scanContext);
        updateAlbumCovers(scanContext);

        scanContext.coverMap.clear();
        scanContext.coverMap = null;
        scanContext.artistIdMap.clear();
        scanContext.artistIdMap = null;
        scanContext.albumIdMap.clear();
        scanContext.albumIdMap = null;
        scanContext.genreIdMap.clear();
        scanContext.genreIdMap = null;
        System.gc();

        doNotifyLibraryChanges();
    }

    protected boolean doPlaylistAddContent(String playlistId, int position, ContentType contentType, final String sourceId, int sortOrder, String filter) {
        SQLiteDatabase database = openHelper.getWritableDatabase();

        if (playlistId == null) {
            playlistId = "0";
        }

        if (database != null) {
            String selectStatement;
            String selectionArgs[] = null;

            String insertStatement =
                    "INSERT INTO " + Entities.PlaylistEntry.TABLE_NAME + " (" +
                            Entities.PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + ", " +
                            Entities.PlaylistEntry.COLUMN_FIELD_POSITION + ", " +
                            Entities.PlaylistEntry.COLUMN_FIELD_SONG_ID +
                            ") ";

            if (contentType == ContentType.CONTENT_TYPE_PLAYLIST) {
                insertStatement = insertStatement +
                        "SELECT " + playlistId + ", NULL, " + Entities.PlaylistEntry.COLUMN_FIELD_SONG_ID + " ";

                selectStatement =
                        "FROM " + Entities.PlaylistEntry.TABLE_NAME + " " +
                                "WHERE " + Entities.PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = " + sourceId + " " +
                                "ORDER BY " + Entities.PlaylistEntry.COLUMN_FIELD_POSITION;
            }
            else {
                insertStatement = insertStatement +
                        "SELECT " + playlistId + ", NULL, " + Entities.Media._ID + " ";

                selectStatement = "FROM " + Entities.Media.TABLE_NAME + " ";

                selectStatement = selectStatement + (MusicConnector.show_hidden ?
                        "WHERE (" + Entities.Media.COLUMN_FIELD_VISIBLE + " <> 0) " :
                        "WHERE (" + Entities.Media.COLUMN_FIELD_VISIBLE + " <> 0) AND (" + Entities.Media.COLUMN_FIELD_USER_HIDDEN + " = 0) ");

                switch (contentType) {
                    case CONTENT_TYPE_DEFAULT:
                        if (!TextUtils.isEmpty(filter)) {
                            selectStatement = selectStatement + " AND (" +
                                    "(" + Entities.Media.COLUMN_FIELD_ARTIST + " LIKE '%" + filter + "%') OR " +
                                    "(" + Entities.Media.COLUMN_FIELD_ALBUM + " LIKE '%" + filter + "%') OR " +
                                    "(" + Entities.Media.COLUMN_FIELD_ALBUM_ARTIST + " LIKE '%" + filter + "%') OR " +
                                    "(" + Entities.Media.COLUMN_FIELD_GENRE + " LIKE '%" + filter + "%') OR " +
                                    "(" + Entities.Media.COLUMN_FIELD_TITLE + " LIKE '%" + filter + "%') " + ")";
                        }

                        break;
                    case CONTENT_TYPE_MEDIA:
                        selectStatement = selectStatement +
                                "AND (" + Entities.Media._ID + " = " + sourceId + ") ";
                        break;
                    case CONTENT_TYPE_GENRE:
                        selectStatement = selectStatement +
                                "AND (" + Entities.Media.COLUMN_FIELD_GENRE_ID + " = " + sourceId + ") ";
                        break;
                    case CONTENT_TYPE_ARTIST:
                        selectStatement = selectStatement +
                                "AND (" + Entities.Media.COLUMN_FIELD_ARTIST_ID + " = " + sourceId + ") ";
                        break;
                    case CONTENT_TYPE_ALBUM:
                        selectStatement = selectStatement +
                                "AND (" + Entities.Media.COLUMN_FIELD_ALBUM_ID + " = " + sourceId + ") ";
                        break;
                    case CONTENT_TYPE_ALBUM_ARTIST:
                        selectStatement = selectStatement +
                                "AND (" + Entities.Media.COLUMN_FIELD_ALBUM_ARTIST_ID + " = " + sourceId + ") ";
                        break;
                    case CONTENT_TYPE_STORAGE:
                        String decodedSourceId;
                        try {
                            decodedSourceId = new String(Base64.decode(sourceId));
                        }
                        catch (final IOException exception) {
                            return false;
                        }
                        selectStatement = selectStatement +
                                "AND (" + Entities.Media.COLUMN_FIELD_URI + " = ?) ";

                        selectionArgs = new String[] {
                                decodedSourceId
                        };
                        break;
                    default:
                        return false;
                }

                switch (sortOrder) {
                    case SONG_URI:
                        selectStatement = selectStatement + "ORDER BY " + Entities.Media.COLUMN_FIELD_URI + " COLLATE NOCASE ASC";
                        break;
                    case -SONG_URI:
                        selectStatement = selectStatement + "ORDER BY " + Entities.Media.COLUMN_FIELD_URI + " COLLATE NOCASE DESC";
                        break;
                    case SONG_FIRST_PLAYED:
                        selectStatement = selectStatement + "ORDER BY " + Entities.Media.COLUMN_FIELD_FIRST_PLAYED + " COLLATE NOCASE ASC";
                        break;
                    case -SONG_FIRST_PLAYED:
                        selectStatement = selectStatement + "ORDER BY " + Entities.Media.COLUMN_FIELD_FIRST_PLAYED + " COLLATE NOCASE DESC";
                        break;
                    case SONG_LAST_PLAYED:
                        selectStatement = selectStatement + "ORDER BY " + Entities.Media.COLUMN_FIELD_LAST_PLAYED + " COLLATE NOCASE ASC";
                        break;
                    case -SONG_LAST_PLAYED:
                        selectStatement = selectStatement + "ORDER BY " + Entities.Media.COLUMN_FIELD_LAST_PLAYED + " COLLATE NOCASE DESC";
                        break;
                    case SONG_TITLE:
                        selectStatement = selectStatement + "ORDER BY " + Entities.Media.COLUMN_FIELD_TITLE + " COLLATE NOCASE ASC";
                        break;
                    case -SONG_TITLE:
                        selectStatement = selectStatement + "ORDER BY " + Entities.Media.COLUMN_FIELD_TITLE + " COLLATE NOCASE DESC";
                        break;
                    case SONG_ARTIST:
                        selectStatement = selectStatement + "ORDER BY " + Entities.Media.COLUMN_FIELD_ARTIST + " COLLATE NOCASE ASC";
                        break;
                    case -SONG_ARTIST:
                        selectStatement = selectStatement + "ORDER BY " + Entities.Media.COLUMN_FIELD_ARTIST + " COLLATE NOCASE DESC";
                        break;
                    case SONG_ALBUM_ARTIST:
                        selectStatement = selectStatement + "ORDER BY " + Entities.Media.COLUMN_FIELD_ALBUM_ARTIST + " COLLATE NOCASE ASC";
                        break;
                    case -SONG_ALBUM_ARTIST:
                        selectStatement = selectStatement + "ORDER BY " + Entities.Media.COLUMN_FIELD_ALBUM_ARTIST + " COLLATE NOCASE DESC";
                        break;
                    case SONG_ALBUM:
                        selectStatement = selectStatement + "ORDER BY " + Entities.Media.COLUMN_FIELD_ALBUM + " COLLATE NOCASE ASC";
                        break;
                    case -SONG_ALBUM:
                        selectStatement = selectStatement + "ORDER BY " + Entities.Media.COLUMN_FIELD_ALBUM + " COLLATE NOCASE DESC";
                        break;
                    case SONG_TRACK:
                        selectStatement = selectStatement + "ORDER BY " + Entities.Media.COLUMN_FIELD_TRACK + " COLLATE NOCASE ASC";
                        break;
                    case -SONG_TRACK:
                        selectStatement = selectStatement + "ORDER BY " + Entities.Media.COLUMN_FIELD_TRACK + " COLLATE NOCASE DESC";
                        break;
                    case SONG_DISC:
                        selectStatement = selectStatement + "ORDER BY " + Entities.Media.COLUMN_FIELD_DISC + " COLLATE NOCASE ASC";
                        break;
                    case -SONG_DISC:
                        selectStatement = selectStatement + "ORDER BY " + Entities.Media.COLUMN_FIELD_DISC + " COLLATE NOCASE DESC";
                        break;
                }
            }

            int addedCount = 0;
            Cursor cursor = database.rawQuery("SELECT COUNT(*) AS CNT " + selectStatement, selectionArgs);

            if (cursor != null) {
                if (cursor.getCount() == 1) {
                    cursor.moveToFirst();
                    addedCount = cursor.getInt(0);

                    database.execSQL(
                            "UPDATE " + Entities.PlaylistEntry.TABLE_NAME + " SET " +
                                    Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " = " + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " + " + addedCount + " " +
                                    "WHERE " + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " >= " + position + " " +
                                    "AND " + Entities.PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = " + playlistId);
                }

                cursor.close();
            }

            final String updateStatement =
                    "UPDATE " + Entities.PlaylistEntry.TABLE_NAME + " SET " + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " = (" +
                            "SELECT COUNT(*)" + " FROM " + Entities.PlaylistEntry.TABLE_NAME + " T1 " +
                            "WHERE (T1." + Entities.PlaylistEntry._ID + " < " + Entities.PlaylistEntry.TABLE_NAME + "." + Entities.PlaylistEntry._ID + ") " +
                            "AND (T1." + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " < " + (position + addedCount) +") " +
                            "AND (T1." + Entities.PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = " + playlistId + ") " +
                            ") " +
                            "WHERE (" + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " IS NULL) " +
                            "AND (" + Entities.PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = " + playlistId + ") ";

            database.beginTransaction();
            try {
                database.execSQL(insertStatement + selectStatement);
                database.execSQL(updateStatement);
                database.setTransactionSuccessful();
            }
            catch (final SQLException sqlException) {
                LogUtils.LOGException(TAG, "doPlaylistAddContent", 0, sqlException);
            }
            finally {
                database.endTransaction();
            }
            return true;
        }

        return false;
    }

    protected boolean doPlaylistAddContent(String playlistId, int position, List<File> fileList, boolean deleteFileMedias) {
        SQLiteDatabase database = openHelper.getWritableDatabase();

        if (playlistId == null) {
            playlistId = "0";
        }

        if (database != null && fileList != null) {
            int addedCount = fileList.size();

            SyncScanContext scanContext = new SyncScanContext();

            database.execSQL(
                    "UPDATE " + Entities.PlaylistEntry.TABLE_NAME + " SET " +
                            Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " = " + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " + " + addedCount + " " +
                            "WHERE " + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " >= " + position + " " +
                            "AND " + Entities.PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = " + playlistId);

            database.beginTransaction();
            try {
                if (deleteFileMedias) {
                    final String where = Entities.Media.COLUMN_FIELD_IS_QUEUE_FILE_ENTRY + " = ? ";

                    final String whereArgs[] = new String[]{
                            String.valueOf(1)
                    };

                    database.delete(Entities.Media.TABLE_NAME, where, whereArgs);
                }

                ContentValues contentValues = new ContentValues();
                for (File currentFile : fileList) {
                    if (isAudioFile(scanContext, currentFile)) {
                        contentValues.clear();
                        JniMediaLib.doReadTags(currentFile, contentValues);
                        contentValues.put(Entities.Media.COLUMN_FIELD_VISIBLE, 0);
                        contentValues.put(Entities.Media.COLUMN_FIELD_IS_QUEUE_FILE_ENTRY, 1);
                        contentValues.remove(Entities.Media.NOT_PERSISTANT_COLUMN_FIELD_HAS_EMBEDDED_ART);
                        long insertId = database.insert(Entities.Media.TABLE_NAME, null, contentValues);

                        contentValues.clear();
                        contentValues.put(Entities.PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID, playlistId);
                        contentValues.put(Entities.PlaylistEntry.COLUMN_FIELD_POSITION, position);
                        contentValues.put(Entities.PlaylistEntry.COLUMN_FIELD_SONG_ID, insertId);
                        database.insert(Entities.PlaylistEntry.TABLE_NAME, null, contentValues);
                        position++;
                    }
                }

                database.setTransactionSuccessful();
            }
            catch (final SQLException sqlException) {
                LogUtils.LOGException(TAG, "doPlaylistAddContent", 0, sqlException);
            }
            finally {
                database.endTransaction();
            }

            return true;
        }

        return false;
    }

    @Override
    public void databaseMaintain() {
        getWritableDatabase().rawQuery("VACUUM;", null);
    }

    protected List<File> getStorageFileList(SyncScanContext scanContext, String filter, int[] sortFields) {
        if (currentFolder == null) {
            return null;
        }

        final File[] currentFileList = currentFolder.listFiles(hiddenFilter);
        ArrayList<File> fileList = new ArrayList<File>();

        if (currentFileList != null) {
            storageSortOrder = sortFields;
            Arrays.sort(currentFileList, filenameComparator);
            Arrays.sort(currentFileList, filetypeComparator);

            for (File currentFile : currentFileList) {
                if (!TextUtils.isEmpty(filter) && !currentFile.getAbsolutePath().toLowerCase().contains(filter.toLowerCase())) {
                    continue;
                }

                if (isAudioFile(scanContext, currentFile)) {
                    fileList.add(currentFile);
                } else if (currentFile.isDirectory()) {
                    fileList.add(currentFile);
                }
            }
        }

        return fileList;
    }


    protected int[] storageSortOrder = null;

    protected final Comparator<File> filenameComparator = new Comparator<File>() {

        @Override
        public int compare(File lhs, File rhs) {
            final String lhsName = lhs.getName().toUpperCase(Locale.getDefault());
            final String rhsName = rhs.getName().toUpperCase(Locale.getDefault());

            if (storageSortOrder == null || storageSortOrder.length < 1) {
                return lhsName.compareTo(rhsName);
            }
            else {
                if (storageSortOrder[0] == AbstractMediaManager.Provider.STORAGE_DISPLAY_NAME) {
                    return lhsName.compareTo(rhsName);
                }
                else // if (MusicConnector.storage_sort_order == MusicConnector.SORT_Z_A)  {
                    return -lhsName.compareTo(rhsName);
                //}
            }
        }
    };

    protected static final Comparator<File> filetypeComparator = new Comparator<File>() {

        @Override
        public int compare(File lhs, File rhs) {
            final boolean lhsIsDirectory = lhs.isDirectory();
            final boolean rhsIsDirectory = rhs.isDirectory();

            if (lhsIsDirectory == rhsIsDirectory) {
                return 0;
            }
            else if (lhsIsDirectory) { // && !rhsIsDirectory) {   // "!rhsIsDirectory" is always true here
                return -1;
            }
            else {
                return 1;
            }
        }
    };

    protected static final FileFilter hiddenFilter = new FileFilter() {

        @Override
        public boolean accept(File pathname) {
            if (MusicConnector.show_hidden) {
                return pathname.canRead();
            }
            else {
                return pathname.canRead() && !pathname.isHidden();
            }
        }
    };



    public static class SyncScanContext {
        SQLiteDatabase database;

        ArrayList<String> albumArtExtensions = null;

        ArrayList<String> audioFilesExtensions = null;

        HashMap<String, Long> coverMap;

        HashMap<String, Long> artistIdMap;

        HashMap<String, Long> albumIdMap;

        HashMap<String, Long> genreIdMap;
    }

    public class LocationAction implements AbstractMediaManager.ProviderAction {

        @Override
        public int getDescription() {
            return R.string.preference_title_settings_location;
        }

        @Override
        public boolean isVisible() {
            return false;
        }

        @Override
        public void launch(Activity source) {
            final Intent intent = new Intent(PlayerApplication.context, SearchPathActivity.class);
            intent.putExtra(KEY_PROVIDER_ID, mediaManager.getMediaManagerId());

            source.startActivityForResult(intent, ACTIVITY_NEED_UI_REFRESH);
        }
    }

    public class SettingsAction implements AbstractMediaManager.ProviderAction {

        @Override
        public int getDescription() {
            return R.string.drawer_item_label_library_settings;
        }

        @Override
        public boolean isVisible() {
            return true;
        }

        @Override
        public void launch(Activity source) {
            final Intent intent = new Intent(PlayerApplication.context, SettingsActivity.class);
            intent.putExtra(KEY_PROVIDER_ID, mediaManager.getMediaManagerId());

            source.startActivityForResult(intent, ACTIVITY_NEED_UI_REFRESH);
        }
    }

    public class AlbumArtistEmptyAction extends LocationAction implements AbstractMediaManager.AbstractEmptyContentAction {

        @Override
        public int getDescription() {
            return R.string.ni_artists;
        }

        @Override
        public int getActionDescription() {
            return R.string.ni_artists_hint;
        }
    }

    public class AlbumEmptyAction extends LocationAction implements AbstractMediaManager.AbstractEmptyContentAction {

        @Override
        public int getDescription() {
            return R.string.ni_albums;
        }

        @Override
        public int getActionDescription() {
            return R.string.ni_albums_hint;
        }
    }

    public class ArtistEmptyAction extends LocationAction implements AbstractMediaManager.AbstractEmptyContentAction {

        @Override
        public int getDescription() {
            return R.string.ni_artists;
        }

        @Override
        public int getActionDescription() {
            return R.string.ni_artists_hint;
        }
    }

    public class GenreEmptyAction extends LocationAction implements AbstractMediaManager.AbstractEmptyContentAction {

        @Override
        public int getDescription() {
            return R.string.ni_genres;
        }

        @Override
        public int getActionDescription() {
            return R.string.ni_genres_hint;
        }
    }

    public class SongEmptyAction extends LocationAction implements AbstractMediaManager.AbstractEmptyContentAction {

        @Override
        public int getDescription() {
            return R.string.ni_songs;
        }

        @Override
        public int getActionDescription() {
            return R.string.ni_songs_hint;
        }
    }
}
