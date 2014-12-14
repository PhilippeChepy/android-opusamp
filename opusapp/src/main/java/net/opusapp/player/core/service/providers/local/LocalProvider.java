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
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import net.opusapp.player.R;
import net.opusapp.player.core.service.PlayerService;
import net.opusapp.player.core.service.providers.AbstractMediaManager;
import net.opusapp.player.core.service.providers.MediaMetadata;
import net.opusapp.player.core.service.providers.local.database.Entities;
import net.opusapp.player.core.service.providers.local.database.OpenHelper;
import net.opusapp.player.core.service.providers.local.ui.activities.ArtSelectActivity;
import net.opusapp.player.core.service.providers.local.ui.activities.FileExtensionsActivity;
import net.opusapp.player.core.service.providers.local.ui.activities.SearchPathActivity;
import net.opusapp.player.core.service.providers.local.ui.activities.SettingsActivity;
import net.opusapp.player.core.service.utils.CursorUtils;
import net.opusapp.player.ui.utils.MusicConnector;
import net.opusapp.player.ui.utils.PlayerApplication;
import net.opusapp.player.ui.views.RefreshableView;
import net.opusapp.player.utils.Base64;
import net.opusapp.player.utils.LogUtils;
import net.opusapp.player.utils.backport.android.content.SharedPreferencesCompat;
import net.opusapp.player.utils.jni.JniMediaLib;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
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



    public static final int ACTION_INDEX_LOCATION = 1;

    public static final int ACTION_INDEX_EXTENSIONS = 2;



    public AbstractMediaManager.ProviderAction ACTION_LIST[] = new AbstractMediaManager.ProviderAction[] {
            new SettingsAction(),
            new LocationAction(),
            new FileExtensionAction()
    };



    public LocalProvider(LocalMediaManager mediaManager) {
        this.mediaManager = mediaManager;

        openHelper = new OpenHelper(mediaManager.getMediaManagerId());
        scanListeners = new ArrayList<>();

        currentFolder = new File(Environment.getExternalStorageDirectory().getAbsolutePath());
        isAtRootLevel = true;

        final Resources resources = PlayerApplication.context.getResources();
        final SharedPreferences sharedPrefs = PlayerApplication.context.getSharedPreferences("provider-" + mediaManager.getMediaManagerId(), Context.MODE_PRIVATE);

        final String[] tabTitles = resources.getStringArray(R.array.preference_values_tab_visibility);

        Set<String> defaultTabs = new HashSet<>(Arrays.asList(tabTitles));
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
                return buildAlbumCursor(fields, sortFields, filter, source, sourceId);
            case CONTENT_TYPE_ALBUM_ARTIST:
                return buildAlbumArtistCursor(fields, sortFields, filter);
            case CONTENT_TYPE_ARTIST:
                return buildArtistCursor(fields, sortFields, filter);
            case CONTENT_TYPE_GENRE:
                return buildGenreCursor(fields, sortFields, filter);
            case CONTENT_TYPE_PLAYLIST:
                return buildPlaylistCursor(fields, sortFields, filter);
            case CONTENT_TYPE_MEDIA:
                return buildMediaCursor(fields, sortFields, filter, source, sourceId);
            case CONTENT_TYPE_STORAGE:
                return buildStorageCursor(fields, sortFields, filter);
        }

        return null;
    }

    @Override
    public boolean play(ContentType contentType, String sourceId, int sortOrder, int position, String filter) {
        SQLiteDatabase database = openHelper.getWritableDatabase();

        if (database != null) {
            if (PlayerApplication.playerService != null) {
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
        }

        return false;
    }

    @Override
    public boolean playNext(ContentType contentType, String sourceId, int sortOrder, String filter) {
        SQLiteDatabase database = openHelper.getWritableDatabase();

        if (database != null) {
            if (PlayerApplication.playerService != null) {
                int position = PlayerApplication.playerService.queueGetPosition();

                if (contentType == ContentType.CONTENT_TYPE_STORAGE) {
                    try {
                        final File selection = PlayerApplication.uriToFile(new String(Base64.decode(sourceId)));
                        final List<File> filePlaylist = new ArrayList<>();
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
        }

        return false;
    }

    @Override
    public AbstractMediaManager.Media[] getCurrentPlaylist(AbstractMediaManager.Player player) {

        int[] requestedFields = new int[] {
                AbstractMediaManager.Provider.SONG_URI,
                AbstractMediaManager.Provider.SONG_TITLE,
                AbstractMediaManager.Provider.SONG_ARTIST,
                AbstractMediaManager.Provider.SONG_ALBUM,
                AbstractMediaManager.Provider.SONG_DURATION,
                AbstractMediaManager.Provider.SONG_ART_URI
        };

        int[] sortOrder = new int[] {
                AbstractMediaManager.Provider.PLAYLIST_ENTRY_POSITION
        };

        final int COLUMN_SONG_URI = 0;

        final int COLUMN_SONG_TITLE = 1;

        final int COLUMN_SONG_ARTIST = 2;

        final int COLUMN_SONG_ALBUM = 3;

        final int COLUMN_SONG_DURATION = 4;

        final int COLUMN_SONG_ART_URI = 5;

        Cursor playlistCursor = buildCursor(
                AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_MEDIA,
                requestedFields,
                sortOrder,
                null,
                AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_PLAYLIST,
                null);

        AbstractMediaManager.Media[] playlist;
        if (CursorUtils.ifNotEmpty(playlistCursor)) {
            playlist = new LocalMedia[playlistCursor.getCount()];

            int i = -1;
            while (playlistCursor.moveToNext()) {
                i++;
                playlist[i] = new LocalMedia(player, playlistCursor.getString(COLUMN_SONG_URI));
                playlist[i].name = playlistCursor.getString(COLUMN_SONG_TITLE);
                playlist[i].album = playlistCursor.getString(COLUMN_SONG_ALBUM);
                playlist[i].artist = playlistCursor.getString(COLUMN_SONG_ARTIST);
                playlist[i].duration = playlistCursor.getLong(COLUMN_SONG_DURATION);
                playlist[i].artUri = playlistCursor.getString(COLUMN_SONG_ART_URI);
            }

            CursorUtils.free(playlistCursor);
        }
        else {
            playlist = PlayerService.EMPTY_PLAYLIST;
        }

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
                int position = 0;

                Cursor cursor = database.query(
                        Entities.PlaylistEntry.TABLE_NAME,
                        new String[] { "COUNT(*) AS CNT" },
                        Entities.PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = ? ",
                        new String[] {playlistId},
                        null,
                        null,
                        null);

                if (CursorUtils.ifNotEmpty(cursor)) {
                    cursor.moveToFirst();
                    position = cursor.getInt(0);
                    CursorUtils.free(cursor);
                }

                LogUtils.LOGW(TAG, "playlistAdd : position = " + position);

                if (contentType == ContentType.CONTENT_TYPE_STORAGE) {
                    try {
                        final File selection = PlayerApplication.uriToFile(new String(Base64.decode(sourceId)));
                        final List<File> filePlaylist = new ArrayList<>();
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
        switch (key) {
            case CONTENT_ART_URI:
                switch (contentType) {
                    case CONTENT_TYPE_ART:
                        return getArt((String) target);
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
            default:
        }
        return null;
    }


    @Override
    public List<MediaMetadata> getMetadataList(ContentType contentType, Object target) {
        final Resources resources = PlayerApplication.context.getResources();
        ArrayList<MediaMetadata> mediaMetadataList = new ArrayList<>();

        switch (contentType) {
            case CONTENT_TYPE_ALBUM:
                final SQLiteDatabase database = getReadableDatabase();

                final String albumSelection = Entities.Media.COLUMN_FIELD_ALBUM_ID + " = ? ";

                final String albumSelectionArgs[] = new String[] {
                        (String) target
                };

                // Album Name
                final Cursor albumNameCursor = database.rawQuery(
                        "SELECT " + Entities.Album.COLUMN_FIELD_ALBUM_NAME + " " +
                                "FROM " + Entities.Album.TABLE_NAME + " " +
                                "WHERE " + Entities.Album._ID + " = ? ", albumSelectionArgs);

                String albumName = "";
                if (CursorUtils.ifNotEmpty(albumNameCursor)) {
                    albumNameCursor.moveToFirst();
                    albumName = albumNameCursor.getString(0);
                    CursorUtils.free(albumNameCursor);
                }

                // Track count
                final Cursor trackCountCursor = database.rawQuery(
                        "SELECT COUNT(*) " +
                                "FROM " + Entities.Media.TABLE_NAME + " " +
                                "WHERE " + Entities.Media.COLUMN_FIELD_ALBUM_ID + " = ? ", albumSelectionArgs);

                long trackCount = 0;
                if (CursorUtils.ifNotEmpty(trackCountCursor)) {
                    trackCountCursor.moveToFirst();
                    trackCount = trackCountCursor.getInt(0);
                    CursorUtils.free(trackCountCursor);
                }

                // Total duration
                String totalDuration = PlayerApplication.context.getString(R.string.label_metadata_unknown);
                final Cursor totalCursor = database.rawQuery(
                        "SELECT SUM(" + Entities.Media.COLUMN_FIELD_DURATION + ") " +
                                "FROM " + Entities.Media.TABLE_NAME + " " +
                                "WHERE " + Entities.Media.COLUMN_FIELD_ALBUM_ID + " = ? ", albumSelectionArgs);

                if (CursorUtils.ifNotEmpty(totalCursor)) {
                    totalCursor.moveToFirst();
                    totalDuration = PlayerApplication.formatSecs(totalCursor.getInt(0));
                    CursorUtils.free(totalCursor);
                }

                // Album artist
                final String artistsColumns[] = new String[] {
                        Entities.Media.COLUMN_FIELD_ARTIST
                };

                final Cursor artistsCursor = database.query(Entities.Media.TABLE_NAME, artistsColumns, albumSelection, albumSelectionArgs, Entities.Media.COLUMN_FIELD_ARTIST, null, Entities.Media.COLUMN_FIELD_ARTIST);
                final ArrayList<String> artists = new ArrayList<>();

                if (CursorUtils.ifNotEmpty(artistsCursor)) {
                    artistsCursor.moveToFirst();
                    artists.add(artistsCursor.getString(0));
                    CursorUtils.free(artistsCursor);
                }

                // Genre
                final String genresColumns[] = new String[] {
                        Entities.Media.COLUMN_FIELD_GENRE
                };

                final Cursor genreCursor = database.query(Entities.Media.TABLE_NAME, genresColumns, albumSelection, albumSelectionArgs, Entities.Media.COLUMN_FIELD_GENRE, null, Entities.Media.COLUMN_FIELD_GENRE);
                final ArrayList<String> genres = new ArrayList<>();

                if (CursorUtils.ifNotEmpty(genreCursor)) {
                    genreCursor.moveToNext();
                    genres.add(genreCursor.getString(0));
                    CursorUtils.free(genreCursor);
                }


                final int requestedFields[] = new int[]{
                        SONG_URI,
                };

                final int COLUMN_URI = 0;

                final Cursor albumCursor = buildMediaCursor(requestedFields, new int[]{}, "", ContentType.CONTENT_TYPE_ALBUM, (String) target);
                boolean albumIsWritable = true;

                if (CursorUtils.ifNotEmpty(albumCursor)) {
                    while (albumCursor.moveToNext()) {
                        final File mediaFile = PlayerApplication.uriToFile(albumCursor.getString(COLUMN_URI));
                        if (!checkIfFileIsWritable(mediaFile)) {
                            albumIsWritable = false;
                        }
                    }

                    CursorUtils.free(albumCursor);
                }


                // Data compilation
                mediaMetadataList.add(new MediaMetadata(ALBUM_NAME,resources.getString(R.string.label_metadata_album_title), albumName, albumIsWritable ? MediaMetadata.EditType.TYPE_STRING : MediaMetadata.EditType.TYPE_READONLY));
                mediaMetadataList.add(new MediaMetadata(-1, resources.getString(R.string.label_metadata_album_track_count), String.valueOf(trackCount), MediaMetadata.EditType.TYPE_READONLY));
                mediaMetadataList.add(new MediaMetadata(-1, resources.getString(R.string.label_metadata_album_duration), totalDuration, MediaMetadata.EditType.TYPE_READONLY));
                mediaMetadataList.add(new MediaMetadata(-1, resources.getQuantityString(R.plurals.label_metadata_album_artists, artists.size()), TextUtils.join(", ", artists), MediaMetadata.EditType.TYPE_READONLY));
                mediaMetadataList.add(new MediaMetadata(-1, resources.getQuantityString(R.plurals.label_metadata_album_genres, genres.size()), TextUtils.join(", ", genres), MediaMetadata.EditType.TYPE_READONLY));

                break;
            case CONTENT_TYPE_MEDIA:
                final int mediaColumns[] = new int[] {
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
                        R.string.label_metadata_media_path,
                        R.string.label_metadata_media_filename,
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

                Cursor cursor = buildMediaCursor(mediaColumns, new int[]{}, "", ContentType.CONTENT_TYPE_MEDIA, (String) target);

                if (cursor != null && cursor.getCount() > 0) {
                    cursor.moveToFirst();

                    final File mediaFile = PlayerApplication.uriToFile(cursor.getString(0));
                    boolean fileIsWritable = checkIfFileIsWritable(mediaFile);

                    for (int columnIndex = 0; columnIndex < cursor.getColumnCount(); columnIndex++) {
                        if (!cursor.isNull(columnIndex)) {
                            MediaMetadata.EditType editType;

                            switch (mediaColumns[columnIndex]) {
                                case SONG_TITLE:
                                case SONG_ARTIST:
                                case SONG_ALBUM_ARTIST:
                                case SONG_ALBUM:
                                case SONG_GENRE:
                                    editType = MediaMetadata.EditType.TYPE_STRING;
                                    break;
                                case SONG_YEAR:
                                case SONG_TRACK:
                                case SONG_DISC:
                                    editType = MediaMetadata.EditType.TYPE_NUMERIC;
                                    break;
                                default:
                                    editType = MediaMetadata.EditType.TYPE_READONLY;
                                    break;
                            }

                            if (!fileIsWritable) {
                                editType = MediaMetadata.EditType.TYPE_READONLY;
                            }

                            String value = cursor.getString(columnIndex);
                            if (mediaColumns[columnIndex] == SONG_DURATION) {
                                value = PlayerApplication.formatSecs(cursor.getInt(columnIndex));
                            }

                            if (mediaColumns[columnIndex] == SONG_URI) {
                                mediaMetadataList.add(new MediaMetadata(-1, resources.getString(fieldLabelIds[columnIndex]), mediaFile.getParentFile().getAbsolutePath(), editType));
                                mediaMetadataList.add(new MediaMetadata(-1, resources.getString(fieldLabelIds[columnIndex + 1]), mediaFile.getName(), editType));
                            }
                            else {
                                mediaMetadataList.add(new MediaMetadata(mediaColumns[columnIndex], resources.getString(fieldLabelIds[columnIndex + 1]), value, editType));
                            }
                        }
                    }
                }
        }
        return mediaMetadataList;
    }

    @Override
    public void setMetadataList(ContentType contentType, Object target, List<MediaMetadata> metadataList) {
        final int requestedFields[] = new int[]{
                SONG_URI,
        };

        final int COLUMN_URI = 0;

        switch (contentType) {
            case CONTENT_TYPE_ALBUM:
                boolean albumChanged = false;
                final Cursor albumCursor = buildMediaCursor(requestedFields, new int[]{}, "", ContentType.CONTENT_TYPE_ALBUM, (String) target);

                if (CursorUtils.ifNotEmpty(albumCursor)) {
                    while (albumCursor.moveToNext()) {
                        final String mediaUri = albumCursor.getString(COLUMN_URI);

                        if (!TextUtils.isEmpty(mediaUri)) {
                            File mediaFile = PlayerApplication.uriToFile(mediaUri);
                            if (mediaFile.exists()) {
                                final ContentValues tags = new ContentValues();
                                JniMediaLib.readTags(mediaFile, tags);

                                boolean changed = false;
                                for (final MediaMetadata mediaMetadata : metadataList) {
                                    if (mediaMetadata.changed()) {
                                        LogUtils.LOGE(TAG, "album tag changed {desc=" + mediaMetadata.mDescription + ", val=" + mediaMetadata.mValue + "}");

                                        switch (mediaMetadata.mFieldType) {
                                            case ALBUM_NAME:
                                                if (!TextUtils.isEmpty(mediaMetadata.mValue)) {
                                                    tags.put(Entities.Media.COLUMN_FIELD_ALBUM, mediaMetadata.mValue);
                                                    changed = true;
                                                }
                                                break;
                                        }
                                    }
                                }

                                if (changed) {
                                    albumChanged = true;
                                    JniMediaLib.writeTags(mediaFile, tags);
                                }
                            }
                        }
                    }

                    if (albumChanged) {
                        scanStart();
                    }

                    CursorUtils.free(albumCursor);
                }

                break;
            case CONTENT_TYPE_MEDIA:
                final Cursor mediaCursor = buildMediaCursor(requestedFields, new int[]{}, "", ContentType.CONTENT_TYPE_MEDIA, (String) target);

                if (CursorUtils.ifNotEmpty(mediaCursor)) {
                    while (mediaCursor.moveToNext()) {
                        final String mediaUri = mediaCursor.getString(COLUMN_URI);

                        if (!TextUtils.isEmpty(mediaUri)) {
                            File mediaFile = PlayerApplication.uriToFile(mediaUri);
                            if (mediaFile.exists()) {

                                final ContentValues tags = new ContentValues();
                                JniMediaLib.readTags(mediaFile, tags);

                                boolean changed = false;
                                for (final MediaMetadata mediaMetadata : metadataList) {
                                    if (mediaMetadata.changed()) {
                                        LogUtils.LOGE(TAG, "media tag changed {desc=" + mediaMetadata.mDescription + ", val=" + mediaMetadata.mValue + "}");

                                        switch (mediaMetadata.mFieldType) {
                                            case SONG_TITLE:
                                                if (!TextUtils.isEmpty(mediaMetadata.mValue)) {
                                                    tags.put(Entities.Media.COLUMN_FIELD_TITLE, mediaMetadata.mValue);
                                                    changed = true;
                                                }
                                                break;
                                            case SONG_ARTIST:
                                                if (!TextUtils.isEmpty(mediaMetadata.mValue)) {
                                                    tags.put(Entities.Media.COLUMN_FIELD_ARTIST, mediaMetadata.mValue);
                                                    changed = true;
                                                }
                                                break;
                                            case SONG_ALBUM_ARTIST:
                                                if (!TextUtils.isEmpty(mediaMetadata.mValue)) {
                                                    tags.put(Entities.Media.COLUMN_FIELD_ALBUM_ARTIST, mediaMetadata.mValue);
                                                    changed = true;
                                                }
                                                break;
                                            case SONG_ALBUM:
                                                if (!TextUtils.isEmpty(mediaMetadata.mValue)) {
                                                    tags.put(Entities.Media.COLUMN_FIELD_ALBUM, mediaMetadata.mValue);
                                                    changed = true;
                                                }
                                                break;
                                            case SONG_GENRE:
                                                if (!TextUtils.isEmpty(mediaMetadata.mValue)) {
                                                    tags.put(Entities.Media.COLUMN_FIELD_GENRE, mediaMetadata.mValue);
                                                    changed = true;
                                                }
                                                break;
                                            case SONG_YEAR:
                                                if (!TextUtils.isEmpty(mediaMetadata.mValue)) {
                                                    tags.put(Entities.Media.COLUMN_FIELD_YEAR, Integer.parseInt(mediaMetadata.mValue));
                                                    changed = true;
                                                }
                                                break;
                                            case SONG_TRACK:
                                                if (!TextUtils.isEmpty(mediaMetadata.mValue)) {
                                                    tags.put(Entities.Media.COLUMN_FIELD_TRACK, Integer.parseInt(mediaMetadata.mValue));
                                                    changed = true;
                                                }
                                                break;
                                            case SONG_DISC:
                                                if (!TextUtils.isEmpty(mediaMetadata.mValue)) {
                                                    tags.put(Entities.Media.COLUMN_FIELD_DISC, Integer.parseInt(mediaMetadata.mValue));
                                                    changed = true;
                                                }
                                                break;
                                        }
                                    }
                                }

                                if (changed) {
                                    JniMediaLib.writeTags(mediaFile, tags);
                                    scanStart();
                                }
                            }
                        }
                    }

                    CursorUtils.free(mediaCursor);
                }
                break;
        }
    }

    @Override
    public boolean hasContentType(ContentType contentType) {
        final Resources resources = PlayerApplication.context.getResources();
        final SharedPreferences sharedPrefs = PlayerApplication.context.getSharedPreferences("provider-" + mediaManager.getMediaManagerId(), Context.MODE_PRIVATE);

        final String[] tabTitles = resources.getStringArray(R.array.preference_values_tab_visibility);

        Set<String> defaultTabs = new HashSet<>(Arrays.asList(tabTitles));
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
    public AbstractMediaManager.ProviderAction getAction(int index) {
        LogUtils.LOGE(TAG, "index = " + index);
        if (index >= ACTION_LIST.length) {
            return null;
        }

        return ACTION_LIST[index];
    }

    @Override
    public void changeAlbumArt(final Activity sourceActivity, final RefreshableView sourceRefreshable, final String albumId, boolean restore) {
        if (restore) {
            final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[PlayerApplication.libraryManagerIndex];
            final AbstractMediaManager.Provider provider = mediaManager.getProvider();

            final DialogInterface.OnClickListener artUpdateSongPositiveOnClickListener = new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    provider.setProperty(
                            AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM,
                            albumId,
                            AbstractMediaManager.Provider.ContentProperty.CONTENT_ART_ORIGINAL_URI,
                            null,
                            true);

                    sourceRefreshable.refresh();
                }
            };

            final DialogInterface.OnClickListener artUpdateSongNegativeOnClickListener = new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    provider.setProperty(
                            AbstractMediaManager.Provider.ContentType.CONTENT_TYPE_ALBUM,
                            albumId,
                            AbstractMediaManager.Provider.ContentProperty.CONTENT_ART_ORIGINAL_URI,
                            null,
                            false);

                    sourceRefreshable.refresh();
                }
            };

            new AlertDialog.Builder(sourceActivity)
                    .setTitle(R.string.alert_dialog_title_art_change_tracks)
                    .setMessage(R.string.alert_dialog_message_restore_art_change_tracks)
                    .setPositiveButton(R.string.label_yes, artUpdateSongPositiveOnClickListener)
                    .setNegativeButton(R.string.label_no, artUpdateSongNegativeOnClickListener)
                    .show();
        }
        else {
            final Intent intent = new Intent(sourceActivity, ArtSelectActivity.class);
            intent.putExtra(KEY_PROVIDER_ID, mediaManager.getMediaManagerId());
            intent.putExtra(KEY_SOURCE_ID, Long.parseLong(albumId));
            sourceActivity.startActivityForResult(intent, ACTIVITY_NEED_UI_REFRESH);
        }
    }

    public void notifyLibraryChanges() {
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

            notifyLibraryChanges();
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

            notifyLibraryChanges();
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

            notifyLibraryChanges();
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

            notifyLibraryChanges();
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

            notifyLibraryChanges();
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

            notifyLibraryChanges();
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

    protected Cursor buildAlbumArtistCursor(final int[] requestedFields, final int[] sortFields, String filter) {
        final ArrayList<String> columnsList = new ArrayList<>();

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

    protected Cursor buildAlbumCursor(final int[] requestedFields, final int[] sortFields, String filter, final ContentType source, final String sourceId) {
        final ArrayList<String> columnsList = new ArrayList<>();

        boolean usesArtTable = false;

        for (int field : requestedFields) {
            switch (field) {
                case AbstractMediaManager.Provider.ALBUM_ID:
                    columnsList.add(Entities.Album.TABLE_NAME + "." + Entities.Album._ID);
                    break;
                case AbstractMediaManager.Provider.ALBUM_NAME:
                    columnsList.add(Entities.Album.TABLE_NAME + "." + Entities.Album.COLUMN_FIELD_ALBUM_NAME);
                    break;
                case AbstractMediaManager.Provider.ALBUM_ART_URI:
                    usesArtTable = true;
                    columnsList.add(Entities.Art.TABLE_NAME + "." + Entities.Art.COLUMN_FIELD_URI);
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
                tableDescription = tableDescription + " LEFT JOIN " + Entities.Media.TABLE_NAME +
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
        }

        if (usesArtTable) {
            tableDescription = tableDescription + " LEFT JOIN " + Entities.Art.TABLE_NAME +
                                " ON " + Entities.Art.TABLE_NAME + "." + Entities.Art._ID + " = " + Entities.Album.COLUMN_FIELD_ALBUM_ART_ID;
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

    protected Cursor buildArtistCursor(final int[] requestedFields, final int[] sortFields, String filter) {
        final ArrayList<String> columnsList = new ArrayList<>();

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

    protected Cursor buildGenreCursor(final int[] requestedFields, final int[] sortFields, String filter) {
        final ArrayList<String> columnsList = new ArrayList<>();

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

    protected Cursor buildPlaylistCursor(int[] requestedFields, int[] sortFields, String filter) {

        final ArrayList<String> columnsList = new ArrayList<>();

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

    protected Cursor buildMediaCursor(int[] requestedFields, int[] sortFields, String filter, ContentType contentType, String sourceId) {
        final Resources resources = PlayerApplication.context.getResources();
        final SharedPreferences sharedPrefs = PlayerApplication.context.getSharedPreferences("provider-" + mediaManager.getMediaManagerId(), Context.MODE_PRIVATE);

        boolean manageMissingTags = sharedPrefs.getBoolean(resources.getString(R.string.preference_key_display_source_if_no_tags), true);


        boolean useArtTable = false;
        boolean usesSongTable = false;
        boolean usesPlaylistEntryTable = false;

        final ArrayList<String> columnsList = new ArrayList<>();

        for (int field : requestedFields) {
            switch (field) {
                case AbstractMediaManager.Provider.SONG_ID:
                    usesSongTable = true;
                    columnsList.add(Entities.Media.TABLE_NAME + "." + Entities.Media._ID + " AS " + "_id");
                    break;
                case AbstractMediaManager.Provider.SONG_URI:
                    usesSongTable = true;
                    columnsList.add(Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_URI);
                    break;
                case AbstractMediaManager.Provider.SONG_ART_URI:
                    usesSongTable = true;
                    useArtTable = true;
                    columnsList.add(Entities.Art.TABLE_NAME + "." + Entities.Art.COLUMN_FIELD_URI);
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
                                "CASE WHEN COALESCE(" + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_TITLE + ", '') = '' "+
                                        "THEN " + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_URI + " " +
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
                    columnsList.add(Entities.PlaylistEntry.TABLE_NAME + "." + Entities.PlaylistEntry._ID + " AS " + "_id");
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

        if (useArtTable) {
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

    protected Cursor buildStorageCursor(int[] requestedFields, int[] sortFields, String filter) {
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
                        currentRow[columnIndex] = "drawable://" + R.drawable.ic_arrow_drop_up_grey600_48dp;
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
                            currentRow[columnIndex] = getStorageArt(currentFile.getAbsolutePath());
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
                            currentRow[columnIndex] = "drawable://" + R.drawable.ic_folder_grey600_48dp;
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



    protected String getStorageArt(String media) {
        File mediaFile = PlayerApplication.uriToFile(media);
        File cacheFile = JniMediaLib.embeddedCoverCacheFile(mediaFile);

        if (cacheFile != null && !cacheFile.exists()) {
            cacheFile = JniMediaLib.embeddedCoverDump(mediaFile);
        }

        return cacheFile != null ? PlayerApplication.fileToUri(cacheFile) : null;
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
                Entities.Art.COLUMN_FIELD_URI
        };

        final String selection = Entities.Media.TABLE_NAME + "." + Entities.Media._ID + " = ? ";

        final String[] selectionArgs = new String[] {
                songId
        };

        final int COLUMN_ART_URI = 0;

        String songArtUri = null;
        Cursor cursor = database.query(tableName, columns, selection, selectionArgs, null, null, null);
        if (CursorUtils.ifNotEmpty(cursor)) {
            cursor.moveToFirst();
            songArtUri = cursor.getString(COLUMN_ART_URI);
            CursorUtils.free(cursor);
        }

        if (!TextUtils.isEmpty(songArtUri)) {
            try {
                return PlayerApplication.context.getContentResolver().openInputStream(Uri.parse(songArtUri));
            }
            catch (final FileNotFoundException fileNotFoundException) {
                return null;
            }
        }
        return null;
    }

    protected String getArt(String artId) {
        final SQLiteDatabase database = openHelper.getReadableDatabase();
        if (database == null) {
            return null;
        }

        final String[] columns = new String[]{
                Entities.Art.TABLE_NAME + "." + Entities.Art.COLUMN_FIELD_URI
        };

        final int COLUMN_ART_URI = 0;

        String selection = Entities.Art.TABLE_NAME + "." + Entities.Art._ID + " = ? ";
        final String[] selectionArgs = new String[]{ artId };

        String artUri = null;

        Cursor cursor = database.query(Entities.Art.TABLE_NAME, columns, selection, selectionArgs, null, null, null);
        if (CursorUtils.ifNotEmpty(cursor)) {
            cursor.moveToFirst();
            artUri = cursor.getString(COLUMN_ART_URI);

            CursorUtils.free(cursor);
        }

        return artUri;
    }

    protected String getAlbumArt(String albumId) {
        final SQLiteDatabase database = openHelper.getReadableDatabase();
        if (database == null) {
            return null;
        }

        final String tableName =
                Entities.Album.TABLE_NAME + " LEFT JOIN " + Entities.Art.TABLE_NAME + " ON " +
                        Entities.Art.TABLE_NAME + "." + Entities.Art._ID + " = " +
                        Entities.Album.TABLE_NAME + "." + Entities.Album.COLUMN_FIELD_ALBUM_ART_ID;


        final String[] columns = new String[]{
                Entities.Art.TABLE_NAME + "." + Entities.Art.COLUMN_FIELD_URI
        };

        final int COLUMN_ART_URI = 0;

        String selection = Entities.Album.TABLE_NAME + "." + Entities.Album._ID + " = ? ";
        final String[] selectionArgs = new String[]{ albumId };

        String albumArtUri = null;

        Cursor cursor = database.query(tableName, columns, selection, selectionArgs, null, null, null);
        if (CursorUtils.ifNotEmpty(cursor)) {
            cursor.moveToFirst();
            albumArtUri = cursor.getString(COLUMN_ART_URI);
            CursorUtils.free(cursor);
        }

        return albumArtUri;
    }

    @Override
    public String getAlbumArtUri(String albumId) {
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
        };

        final int COLUMN_ART_URI = 0;

        String selection = Entities.Album.TABLE_NAME + "." + Entities.Album._ID + " = ? ";
        final String[] selectionArgs = new String[]{ albumId };

        String albumArtUri = null;

        Cursor cursor = database.query(tableName, columns, selection, selectionArgs, null, null, null);
        if (CursorUtils.ifNotEmpty(cursor)) {
            cursor.moveToFirst();
            albumArtUri = cursor.getString(COLUMN_ART_URI);
            CursorUtils.free(cursor);
        }

        return albumArtUri;
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

            if (CursorUtils.ifNotEmpty(cursor)) {
                cursor.moveToFirst();
                artId = cursor.getLong(0);
                CursorUtils.free(cursor);
            }
            else {
                ContentValues artData = new ContentValues();
                artData.put(Entities.Art.COLUMN_FIELD_URI, uri);
                artData.put(Entities.Art.COLUMN_FIELD_URI_IS_EMBEDDED, false);
                artId = database.insert(Entities.Art.TABLE_NAME, null, artData);
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

            notifyLibraryChanges();
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

            notifyLibraryChanges();
        }
    }











    public static boolean isArtFile(SyncScanContext scanContext, File file) {
        String filePath = file.getAbsolutePath();

        if (scanContext.albumArtExtensions == null) {
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(PlayerApplication.context);

            Set<String> defaults = new HashSet<>(Arrays.asList(PlayerApplication.context.getResources().getStringArray(R.array.cover_exts)));
            Set<String> extensionSet = SharedPreferencesCompat.getStringSet(sharedPrefs, PlayerApplication.context.getString(R.string.key_cover_exts), defaults);

            if(extensionSet.size() == 0) {
                extensionSet = defaults;
            }

            scanContext.albumArtExtensions = new ArrayList<>(extensionSet);
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

    public boolean isAudioFile(SyncScanContext scanContext, File file) {
        if (file.isDirectory()) {
            return false;
        }

        final String filePath = file.getAbsolutePath();
        if (scanContext.audioFilesExtensions == null) {
            scanContext.audioFilesExtensions = new ArrayList<>();

            final SQLiteDatabase database = openHelper.getReadableDatabase();

            final String[] columns = new String[] {
                Entities.FileExtensions.COLUMN_FIELD_EXTENSION
            };

            final int COLUMN_EXTENSION = 0;

            final Cursor cursor = database.query(Entities.FileExtensions.TABLE_NAME, columns, null, null, null, null, null, null);
            if (CursorUtils.ifNotEmpty(cursor)) {
                while (cursor.moveToNext()) {
                    scanContext.audioFilesExtensions.add(cursor.getString(COLUMN_EXTENSION));
                }

                CursorUtils.free(cursor);
            }
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

        if (CursorUtils.ifNotEmpty(cursor)) {
            CursorUtils.free(cursor);
            return true;
        }

        return false;
    }


    protected void doSyncStartScan(SyncScanContext scanContext) {
        if (scanning) {
            return;
        }

        for (OnLibraryChangeListener libraryChangeListener : scanListeners) {
            libraryChangeListener.libraryScanStarted();
        }

        scanning = true;
        final SQLiteDatabase database = openHelper.getWritableDatabase();

        try {
            if (database != null) {
                // construction of a list of forbidden paths.
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

                ArrayList<File> acceptList = new ArrayList<>();

                Cursor acceptCursor = database.query(Entities.ScanDirectory.TABLE_NAME, pathProjection, selection, selectionAccept, null, null, null);
                Cursor discardCursor = database.query(Entities.ScanDirectory.TABLE_NAME, pathProjection, selection, selectionDiscard, null, null, null);

                Map<String, Boolean> discardMap = new HashMap<>();
                if (CursorUtils.ifNotEmpty(discardCursor)) {
                    database.beginTransaction();

                    while (discardCursor.moveToNext()) {
                        final String discardPath = discardCursor.getString(0);
                        discardMap.put(discardPath, true);

                        database.delete(
                                Entities.Media.TABLE_NAME,
                                Entities.Media.COLUMN_FIELD_URI + " LIKE ?",
                                new String[]{
                                        "file://" + discardPath + File.separator + "%"
                                }
                        );
                    }

                    database.setTransactionSuccessful();
                    database.endTransaction();
                    CursorUtils.free(discardCursor);
                }

                if (CursorUtils.ifNotEmpty(acceptCursor)) {
                    while (acceptCursor.moveToNext()) {
                        final String currentPath = acceptCursor.getString(0);
                        if (currentPath != null) {
                            acceptList.add(new File(currentPath));
                        }
                    }

                    CursorUtils.free(acceptCursor);
                }

                final String artProjection[] = new String[] {
                        Entities.Art._ID,
                        Entities.Art.COLUMN_FIELD_URI,
                };

                final int COLUMN_ART_PKID = 0;
                final int COLOMN_ART_URI = 1;

                Cursor arts = database.query(Entities.Art.TABLE_NAME, artProjection, null, null, null, null, null);
                if (CursorUtils.ifNotEmpty(arts)) {
                    while (arts.moveToNext()) {
                        final File artFile = PlayerApplication.uriToFile(arts.getString(COLOMN_ART_URI));

                        if (!artFile.exists()) {
                            final String whereClause = Entities.Art._ID + " = ? ";
                            final String whereArgs[] = new String[] {
                                    arts.getString(COLUMN_ART_PKID)
                            };

                            database.delete(Entities.Art.TABLE_NAME, whereClause, whereArgs);
                        }
                    }

                    CursorUtils.free(arts);
                }

                database.execSQL(
                        "UPDATE " + Entities.Media.TABLE_NAME + " SET " +
                        Entities.Media.COLUMN_FIELD_ART_ID + " = NULL " +
                        "WHERE " + Entities.Media.COLUMN_FIELD_ART_ID + " NOT IN (" +
                                " SELECT " + Entities.Art._ID +
                                " FROM " + Entities.Art.TABLE_NAME + ")");

                database.execSQL(
                        "UPDATE " + Entities.Media.TABLE_NAME + " SET " +
                        Entities.Media.COLUMN_FIELD_ORIGINAL_ART_ID + " = NULL " +
                        "WHERE " + Entities.Media.COLUMN_FIELD_ORIGINAL_ART_ID + " NOT IN (" +
                                " SELECT " + Entities.Art._ID +
                                " FROM " + Entities.Art.TABLE_NAME + ")");

                database.execSQL(
                        "UPDATE " + Entities.Album.TABLE_NAME + " SET " +
                        Entities.Album.COLUMN_FIELD_ALBUM_ART_ID + " = NULL " +
                        "WHERE " + Entities.Album.COLUMN_FIELD_ALBUM_ART_ID + " NOT IN (" +
                                " SELECT " + Entities.Art._ID +
                                " FROM " + Entities.Art.TABLE_NAME + ")");

                database.execSQL(
                        "UPDATE " + Entities.Album.TABLE_NAME + " SET " +
                        Entities.Album.COLUMN_FIELD_ORIGINAL_ALBUM_ART_ID + " = NULL " +
                        "WHERE " + Entities.Album.COLUMN_FIELD_ORIGINAL_ALBUM_ART_ID + " NOT IN (" +
                                " SELECT " + Entities.Art._ID +
                                " FROM " + Entities.Art.TABLE_NAME + ")");

                // checking accept/discard paths and deleted medias for already scanned medias.
                final String mediaProjection[] = new String[]{
                        Entities.Media._ID,
                        Entities.Media.COLUMN_FIELD_URI,

                        Entities.Media.COLUMN_FIELD_TITLE,
                        Entities.Media.COLUMN_FIELD_ARTIST,
                        Entities.Media.COLUMN_FIELD_ALBUM_ARTIST,
                        Entities.Media.COLUMN_FIELD_ALBUM,
                        Entities.Media.COLUMN_FIELD_GENRE,
                        Entities.Media.COLUMN_FIELD_YEAR,
                        Entities.Media.COLUMN_FIELD_TRACK,
                        Entities.Media.COLUMN_FIELD_DISC,

                        Entities.Media.COLUMN_FIELD_COMMENT,
                        Entities.Media.COLUMN_FIELD_LYRICS,
                        Entities.Media.COLUMN_FIELD_ORIGINAL_ART_ID,
                        Entities.Media.COLUMN_FIELD_ART_ID
                };

                final int COLUMN_TITLE = 2;
                final int COLUMN_ARTIST = 3;
                final int COLUMN_ALBUM_ARTIST = 4;
                final int COLUMN_ALBUM = 5;
                final int COLUMN_GENRE = 6;
                final int COLUMN_YEAR = 7;
                final int COLUMN_TRACK = 8;
                final int COLUMN_DISC = 9;
                final int COLUMN_COMMENT = 10;
                final int COLUMN_LYRICS = 11;
                final int COLUMN_ORIGINAL_ART_ID = 12;
                final int COLUMN_ART_ID = 13;

                Cursor medias = database.query(Entities.Media.TABLE_NAME, mediaProjection, null, null, null, null, null);
                if (CursorUtils.ifNotEmpty(medias)) {
                    while (medias.moveToNext()) {
                        final File currentMediaFile = PlayerApplication.uriToFile(medias.getString(1));
                        final long currentMediaId = medias.getLong(0);

                        if (currentMediaFile != null) {
                            final String currentMediaPath = currentMediaFile.getAbsolutePath();

                            boolean needDeletion = true;
                            boolean needDbUpdate = false;

                            if (currentMediaFile.exists()) {
                                needDeletion = false;
                            }

                            // Test for accept paths
                            if (!needDeletion) {
                                needDeletion = true;
                                for (File acceptPath : acceptList) {
                                    if (currentMediaPath.startsWith(acceptPath.getAbsolutePath())) {
                                        needDeletion = false;
                                        break;
                                    }
                                }
                            }

                            // Test for discard paths
                            if (!needDeletion) {
                                for (Map.Entry<String, Boolean> discardPath : discardMap.entrySet()) {
                                    if (currentMediaPath.startsWith(discardPath.getKey()) && discardPath.getValue()) {
                                        needDeletion = true;
                                        break;
                                    }
                                }
                            }

                            if (!needDeletion && !isAudioFile(scanContext, currentMediaFile)) {
                                needDeletion = true;
                            }

                            ContentValues tags = new ContentValues();
                            if (!needDeletion) {
                                JniMediaLib.readTags(currentMediaFile, tags);

                                String title = tags.getAsString(Entities.Media.COLUMN_FIELD_TITLE);
                                if (title != null && !title.equals(medias.getString(COLUMN_TITLE))) {
                                    needDbUpdate = true;
                                }

                                String artist = tags.getAsString(Entities.Media.COLUMN_FIELD_ARTIST);
                                if (!needDbUpdate && artist != null && !artist.equals(medias.getString(COLUMN_ARTIST))) {
                                    needDbUpdate = true;
                                }

                                String albumArtist = tags.getAsString(Entities.Media.COLUMN_FIELD_ALBUM_ARTIST);
                                if (!needDbUpdate && albumArtist != null && !albumArtist.equals(medias.getString(COLUMN_ALBUM_ARTIST))) {
                                    needDbUpdate = true;
                                }

                                String album = tags.getAsString(Entities.Media.COLUMN_FIELD_ALBUM);
                                if (!needDbUpdate && album != null && !album.equals(medias.getString(COLUMN_ALBUM))) {
                                    needDbUpdate = true;
                                }

                                String genre = tags.getAsString(Entities.Media.COLUMN_FIELD_GENRE);
                                if (!needDbUpdate && genre != null && !genre.equals(medias.getString(COLUMN_GENRE))) {
                                    needDbUpdate = true;
                                }

                                int year = tags.getAsInteger(Entities.Media.COLUMN_FIELD_YEAR);
                                if (!needDbUpdate && year != medias.getInt(COLUMN_YEAR)) {
                                    needDbUpdate = true;
                                }

                                int track = tags.getAsInteger(Entities.Media.COLUMN_FIELD_TRACK);
                                if (!needDbUpdate && track != medias.getInt(COLUMN_TRACK)) {
                                    needDbUpdate = true;
                                }

                                int disc = tags.getAsInteger(Entities.Media.COLUMN_FIELD_DISC);
                                if (!needDbUpdate && disc != medias.getInt(COLUMN_DISC)) {
                                    needDbUpdate = true;
                                }

                                String comment = tags.getAsString(Entities.Media.COLUMN_FIELD_COMMENT);
                                if (!needDbUpdate && comment != null && !comment.equals(medias.getString(COLUMN_COMMENT))) {
                                    needDbUpdate = true;
                                }

                                String lyrics = tags.getAsString(Entities.Media.COLUMN_FIELD_LYRICS);
                                if (!needDbUpdate && lyrics != null && !lyrics.equals(medias.getString(COLUMN_LYRICS))) {
                                    needDbUpdate = true;
                                }

                                boolean hasEmbeddedArt = tags.getAsBoolean(Entities.Media.NOT_PERSISTANT_COLUMN_FIELD_HAS_EMBEDDED_ART);
                                if (!needDbUpdate && hasEmbeddedArt &&
                                        (medias.getInt(COLUMN_ORIGINAL_ART_ID) == 0 || medias.isNull(COLUMN_ORIGINAL_ART_ID))) {
                                    needDbUpdate = true;
                                }
                            }

                            if (needDeletion) {
                                LogUtils.LOGI(TAG, "!Media : " + currentMediaPath);
                                database.delete(Entities.Media.TABLE_NAME, "_ID = ?", new String[]{String.valueOf(currentMediaId)});
                                database.delete(Entities.Art.TABLE_NAME, Entities.Art.COLUMN_FIELD_URI + " LIKE ?", new String[]{currentMediaPath + "%"});
                            }
                            else if (needDbUpdate) {
                                LogUtils.LOGI(TAG, "~Media : " + currentMediaPath);

                                tags.put(Entities.Media.COLUMN_FIELD_ARTIST_ID, getArtistId(tags.getAsString(Entities.Media.COLUMN_FIELD_ARTIST), scanContext));
                                tags.put(Entities.Media.COLUMN_FIELD_ALBUM_ID, getAlbumId(tags.getAsString(Entities.Media.COLUMN_FIELD_ALBUM), scanContext));
                                tags.put(Entities.Media.COLUMN_FIELD_GENRE_ID, getGenreId(tags.getAsString(Entities.Media.COLUMN_FIELD_GENRE), scanContext));


                                boolean hasEmbeddedArt = tags.getAsBoolean(Entities.Media.NOT_PERSISTANT_COLUMN_FIELD_HAS_EMBEDDED_ART);
                                if (hasEmbeddedArt) {
                                    long coverId = getCoverForFile(currentMediaFile, scanContext, hasEmbeddedArt);

                                    if (coverId != 0) {
                                        if (medias.getInt(COLUMN_ART_ID) == 0 || medias.isNull(COLUMN_ART_ID)) {
                                            tags.put(Entities.Media.COLUMN_FIELD_ART_ID, coverId);
                                        }

                                        tags.put(Entities.Media.COLUMN_FIELD_ORIGINAL_ART_ID, coverId);
                                    }
                                }

                                tags.remove(Entities.Media.NOT_PERSISTANT_COLUMN_FIELD_HAS_EMBEDDED_ART);
                                database.update(Entities.Media.TABLE_NAME, tags, "_ID = ?", new String[] { String.valueOf(currentMediaId) });
                            }
                        }
                    }

                    CursorUtils.free(medias);
                }


                if (acceptList.size() > 0) {
                    // Updating from filesystem
                    doSyncDirectoryScan(acceptList, discardMap, scanContext);
                }

                database.beginTransaction();

                LogUtils.LOGI(TAG, "Updating albums");
                database.delete(
                        Entities.Album.TABLE_NAME,
                        Entities.Album._ID + " NOT IN ( " +
                                "SELECT " + Entities.Media.COLUMN_FIELD_ALBUM_ID + " " +
                                "FROM " + Entities.Media.TABLE_NAME + " " +
                                "GROUP BY " + Entities.Media.COLUMN_FIELD_ALBUM_ID +
                                ")",
                        null
                );

                LogUtils.LOGI(TAG, "Updating Album artists");
                database.delete(
                        Entities.AlbumArtist.TABLE_NAME,
                        Entities.AlbumArtist._ID + " NOT IN ( " +
                                "SELECT " + Entities.Media.COLUMN_FIELD_ALBUM_ARTIST_ID + " " +
                                "FROM " + Entities.Media.TABLE_NAME + " " +
                                "GROUP BY " + Entities.Media.COLUMN_FIELD_ALBUM_ARTIST_ID +
                                ")",
                        null
                );

                LogUtils.LOGI(TAG, "Updating Artists");
                database.delete(
                        Entities.Artist.TABLE_NAME,
                        Entities.Artist._ID + " NOT IN ( " +
                                "SELECT " + Entities.Media.COLUMN_FIELD_ARTIST_ID + " " +
                                "FROM " + Entities.Media.TABLE_NAME + " " +
                                "GROUP BY " + Entities.Media.COLUMN_FIELD_ARTIST_ID +
                                ")",
                        null
                );

                LogUtils.LOGI(TAG, "Updating Genres");
                database.delete(
                        Entities.Genre.TABLE_NAME,
                        Entities.Genre._ID + " NOT IN ( " +
                                "SELECT " + Entities.Media.COLUMN_FIELD_GENRE_ID + " " +
                                "FROM " + Entities.Media.TABLE_NAME + " " +
                                "GROUP BY " + Entities.Media.COLUMN_FIELD_GENRE_ID +
                                ")",
                        null
                );

                LogUtils.LOGI(TAG, "Updating Arts");
                database.rawQuery(
                        "UPDATE " + Entities.Album.TABLE_NAME + " " +
                        "SET " + Entities.Album.COLUMN_FIELD_ORIGINAL_ALBUM_ART_ID + " = NULL " +
                        "WHERE " + Entities.Album.COLUMN_FIELD_ORIGINAL_ALBUM_ART_ID + " NOT IN (" +
                                "SELECT " + Entities.Art._ID + " FROM " + Entities.Art.TABLE_NAME +
                        ")",
                        null
                );

                database.rawQuery(
                        "UPDATE " + Entities.Media.TABLE_NAME + " " +
                        "SET " + Entities.Media.COLUMN_FIELD_ORIGINAL_ART_ID + " = NULL " +
                        "WHERE " + Entities.Media.COLUMN_FIELD_ORIGINAL_ART_ID + " NOT IN (" +
                                "SELECT " + Entities.Art._ID + " FROM " + Entities.Art.TABLE_NAME +
                        ")",
                        null
                );

                database.rawQuery(
                        "UPDATE " + Entities.Album.TABLE_NAME + " " +
                        "SET " + Entities.Album.COLUMN_FIELD_ALBUM_ART_ID + " = " + Entities.Album.COLUMN_FIELD_ORIGINAL_ALBUM_ART_ID + " " +
                        "WHERE " + Entities.Album.COLUMN_FIELD_ALBUM_ART_ID + " NOT IN (" +
                                "SELECT " + Entities.Art._ID + " FROM " + Entities.Art.TABLE_NAME +
                        ")",
                        null
                );

                database.rawQuery(
                        "UPDATE " + Entities.Media.TABLE_NAME + " " +
                        "SET " + Entities.Media.COLUMN_FIELD_ART_ID + " = " + Entities.Media.COLUMN_FIELD_ORIGINAL_ART_ID + " " +
                        "WHERE " + Entities.Media.COLUMN_FIELD_ART_ID + " NOT IN (" +
                                "SELECT " + Entities.Art._ID + " FROM " + Entities.Art.TABLE_NAME +
                        ")",
                        null
                );

                LogUtils.LOGI(TAG, "updates done");
                database.setTransactionSuccessful();
                database.endTransaction();
            }
        }
        catch (final Exception exception) {
            LogUtils.LOGException(TAG, "doSyncStartScan", 0, exception);
        }
        finally {
            if (database != null && database.inTransaction()) {
                database.endTransaction();
            }
        }

        scanning = false;

        for (OnLibraryChangeListener libraryChangeListener : scanListeners) {
            libraryChangeListener.libraryChanged();
            libraryChangeListener.libraryScanFinished();
        }
    }

    protected Long getArtistId(String artist, SyncScanContext scanContext) {
        Long id = scanContext.artistIdMap != null ? scanContext.artistIdMap.get(artist) : null;

        if (TextUtils.isEmpty(artist)) {
            return null;
        }

        final SQLiteDatabase database = openHelper.getWritableDatabase();

        if (id == null) {
            Cursor artistCursor = database.query(Entities.Artist.TABLE_NAME, new String[] {Entities.Artist._ID}, Entities.Artist.COLUMN_FIELD_ARTIST_NAME + " = ?", new String[] { artist }, null, null, null);

            if (CursorUtils.ifNotEmpty(artistCursor)) {
                artistCursor.moveToPosition(0);
                id = artistCursor.getLong(0);
                CursorUtils.free(artistCursor);
            }
            else {
                ContentValues artistValues = new ContentValues();
                artistValues.put(Entities.Artist.COLUMN_FIELD_ARTIST_NAME, artist);
                artistValues.put(Entities.Artist.COLUMN_FIELD_USER_HIDDEN, false);
                artistValues.put(Entities.Artist.COLUMN_FIELD_VISIBLE, true);
                id = database.insert(Entities.Artist.TABLE_NAME, null, artistValues);
            }

            if (scanContext.artistIdMap != null) {
                scanContext.artistIdMap.put(artist, id);
            }
        }

        return id;
    }

    protected Long getAlbumId(String album, SyncScanContext scanContext) {
        Long id = scanContext.albumIdMap != null ? scanContext.albumIdMap.get(album) : null;

        if (TextUtils.isEmpty(album)) {
            return null;
        }

        if (id == null) {
            final SQLiteDatabase database = openHelper.getWritableDatabase();

            Cursor albumCursor = database.query(Entities.Album.TABLE_NAME, new String[]{Entities.Album._ID}, Entities.Album.COLUMN_FIELD_ALBUM_NAME + " = ?", new String[]{album}, null, null, null);

            if (CursorUtils.ifNotEmpty(albumCursor)) {
                albumCursor.moveToFirst();
                id = albumCursor.getLong(0);
                CursorUtils.free(albumCursor);
            }
            else {
                ContentValues albumValues = new ContentValues();
                albumValues.put(Entities.Album.COLUMN_FIELD_ALBUM_NAME, album);
                albumValues.put(Entities.Album.COLUMN_FIELD_USER_HIDDEN, false);
                id = database.insert(Entities.Album.TABLE_NAME, null, albumValues);
            }

            if (scanContext.albumIdMap != null) {
                scanContext.albumIdMap.put(album, id);
            }
        }

        return id;
    }

    protected Long getGenreId(String genre, SyncScanContext scanContext) {
        Long id = scanContext.genreIdMap != null ? scanContext.genreIdMap.get(genre) : null;

        if (TextUtils.isEmpty(genre)) {
            return null;
        }

        if (id == null) {
            final SQLiteDatabase database = openHelper.getWritableDatabase();
            Cursor genreCursor = database.query(Entities.Genre.TABLE_NAME, new String[]{Entities.Genre._ID}, Entities.Genre.COLUMN_FIELD_GENRE_NAME + " = ?", new String[]{genre}, null, null, null);

            if (CursorUtils.ifNotEmpty(genreCursor)) {
                genreCursor.moveToPosition(0);
                id = genreCursor.getLong(0);
                CursorUtils.free(genreCursor);
            }
            else {
                ContentValues genreValues = new ContentValues();
                genreValues.put(Entities.Genre.COLUMN_FIELD_GENRE_NAME, genre);
                genreValues.put(Entities.Genre.COLUMN_FIELD_USER_HIDDEN, false);
                genreValues.put(Entities.Genre.COLUMN_FIELD_VISIBLE, true);
                id = database.insert(Entities.Genre.TABLE_NAME, null, genreValues);
            }

            if (scanContext.genreIdMap != null) {
                scanContext.genreIdMap.put(genre, id);
            }
        }

        return id;
    }

    protected synchronized long getCoverForFile(File sourceFile, SyncScanContext scanContext, boolean hasEmbeddedTag) {
        if (sourceFile == null || sourceFile.getParentFile() == null) {
            return 0;
        }

        final ContentValues mediaCover = new ContentValues();

        long embeddedCoverId = 0;
        long coverId = 0;

        if (hasEmbeddedTag) {
            final File cacheFile = JniMediaLib.embeddedCoverCacheFile(sourceFile);

            if (cacheFile == null) {
                return 0;
            }

            final String projection[] = new String[] {
                    Entities.Art._ID
            };

            final String selection = Entities.Art.COLUMN_FIELD_URI + " = ? ";

            final String selectionArgs[] = new String[] {
                    PlayerApplication.fileToUri(cacheFile)
            };

            final SQLiteDatabase database = openHelper.getWritableDatabase();
            Cursor coverCursor = database.query(Entities.Art.TABLE_NAME, projection, selection, selectionArgs, null, null, null);
            if (CursorUtils.ifNotEmpty(coverCursor)) {
                coverCursor.moveToFirst();
                embeddedCoverId = coverCursor.getLong(0);
                CursorUtils.free(coverCursor);
            }
            else {
                final File coverFile = JniMediaLib.embeddedCoverDump(sourceFile);

                if (coverFile != null) {
                    mediaCover.clear();
                    mediaCover.put(Entities.Art.COLUMN_FIELD_URI, PlayerApplication.fileToUri(coverFile));
                    mediaCover.put(Entities.Art.COLUMN_FIELD_URI_IS_EMBEDDED, true);
                    embeddedCoverId = database.insert(Entities.Art.TABLE_NAME, null, mediaCover);
                }
                else {
                    embeddedCoverId = 0;
                }
            }
        }

        final Resources resources = PlayerApplication.context.getResources();
        final SharedPreferences sharedPrefs = PlayerApplication.context.getSharedPreferences("provider-" + mediaManager.getMediaManagerId(), Context.MODE_PRIVATE);

        if (sharedPrefs.getBoolean(resources.getString(R.string.preference_key_display_local_art), false)) {
            final File parentFile = sourceFile.getParentFile();

            if (scanContext.coverMap.containsKey(parentFile.getName())) {
                coverId = scanContext.coverMap.get(parentFile.getName());
            } else {
                List<File> fileList = new ArrayList<>();
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
                            final SQLiteDatabase database = openHelper.getWritableDatabase();

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
                                coverId = database.insert(Entities.Art.TABLE_NAME, null, mediaCover);

                                mediaCover.clear();
                                mediaCover.put(Entities.Media.COLUMN_FIELD_ART_ID, artUri);
                                mediaCover.put(Entities.Media.COLUMN_FIELD_ORIGINAL_ART_ID, artUri);

                                int rows = database.update(
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

    protected void updateAlbumArtists() {
        final SQLiteDatabase database = openHelper.getWritableDatabase();

        database.execSQL(
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

        database.execSQL(
                "INSERT OR IGNORE INTO " + Entities.AlbumArtist.TABLE_NAME + " (" +
                        Entities.AlbumArtist.COLUMN_FIELD_ARTIST_NAME + ", " +
                        Entities.AlbumArtist.COLUMN_FIELD_VISIBLE + ", " +
                        Entities.AlbumArtist.COLUMN_FIELD_USER_HIDDEN +
                        ") " +
                        "SELECT " + Entities.Album.COLUMN_FIELD_ALBUM_ARTIST + ", -1, 0 " +
                        "FROM " + Entities.Album.TABLE_NAME + " GROUP BY " + Entities.Album.COLUMN_FIELD_ALBUM_ARTIST
        );

        database.execSQL(
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

        database.execSQL(
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

        database.execSQL(
                "DELETE FROM " + Entities.AlbumArtist.TABLE_NAME + " WHERE " + Entities.AlbumArtist._ID + " NOT IN (" +
                        "SELECT " + Entities.Media.COLUMN_FIELD_ALBUM_ARTIST_ID + " FROM " + Entities.Media.TABLE_NAME + ")"
        );
    }

    protected void updateAlbumCovers() {
        final SQLiteDatabase database = openHelper.getWritableDatabase();

        database.execSQL(
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

        database.execSQL(
                "UPDATE " + Entities.Album.TABLE_NAME + " SET " +
                        Entities.Album.COLUMN_FIELD_ORIGINAL_ALBUM_ART_ID + " = " + Entities.Album.COLUMN_FIELD_ALBUM_ART_ID + " " +
                        "WHERE (" + Entities.Album.TABLE_NAME + "." + Entities.Album.COLUMN_FIELD_ORIGINAL_ALBUM_ART_ID + " IS NULL) OR (" + Entities.Album.TABLE_NAME + "." + Entities.Album.COLUMN_FIELD_ORIGINAL_ALBUM_ART_ID + " = '')"
        );
    }

    protected void doSyncDirectoryScan(List<File> fileList, Map<String, Boolean> discardMap, SyncScanContext scanContext) {
        final ContentValues mediaTags = new ContentValues();
        final SQLiteDatabase database = openHelper.getWritableDatabase();

        scanContext.coverMap = new HashMap<>();
        scanContext.artistIdMap = new HashMap<>();
        scanContext.albumIdMap = new HashMap<>();
        scanContext.genreIdMap = new HashMap<>();

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

                        JniMediaLib.readTags(currentFile, mediaTags);

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
                        database.insert(Entities.Media.TABLE_NAME, null, mediaTags);

                        refreshThreshold++;
                        if (refreshThreshold >= 25) {
                            notifyLibraryChanges();
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

        updateAlbumArtists();
        updateAlbumCovers();

        scanContext.coverMap.clear();
        scanContext.coverMap = null;
        scanContext.artistIdMap.clear();
        scanContext.artistIdMap = null;
        scanContext.albumIdMap.clear();
        scanContext.albumIdMap = null;
        scanContext.genreIdMap.clear();
        scanContext.genreIdMap = null;
        System.gc();

        notifyLibraryChanges();
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

            if (CursorUtils.ifNotEmpty(cursor)) {
                cursor.moveToFirst();
                addedCount = cursor.getInt(0);

                database.execSQL(
                        "UPDATE " + Entities.PlaylistEntry.TABLE_NAME + " SET " +
                                Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " = " + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " + " + addedCount + " " +
                                "WHERE " + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " >= " + position + " " +
                                "AND " + Entities.PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = " + playlistId);

                CursorUtils.free(cursor);
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
                        JniMediaLib.readTags(currentFile, contentValues);
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
        ArrayList<File> fileList = new ArrayList<>();

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
        ArrayList<String> albumArtExtensions = null;

        ArrayList<String> audioFilesExtensions = null;

        HashMap<String, Long> coverMap;

        HashMap<String, Long> artistIdMap;

        HashMap<String, Long> albumIdMap;

        HashMap<String, Long> genreIdMap;
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
    
    public class FileExtensionAction implements AbstractMediaManager.ProviderAction {

        @Override
        public int getDescription() {
            return R.string.preference_title_settings_extensions;
        }

        @Override
        public boolean isVisible() {
            return false;
        }

        @Override
        public void launch(Activity source) {
            final Intent intent = new Intent(PlayerApplication.context, FileExtensionsActivity.class);
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




    protected boolean checkIfFileIsWritable(final File file) {
        try {
            FileOutputStream outputStream = new FileOutputStream(file, true);
            outputStream.close();
            return true;
        } catch (final Exception exception) {
            return false;
        }
    }
}
