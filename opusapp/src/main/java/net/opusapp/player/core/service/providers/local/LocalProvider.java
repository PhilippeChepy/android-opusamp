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
import android.text.TextUtils;

import net.opusapp.player.R;
import net.opusapp.player.core.service.PlayerEventBus;
import net.opusapp.player.core.service.PlayerService;
import net.opusapp.player.core.service.providers.AbstractMediaManager;
import net.opusapp.player.core.service.providers.MediaMetadata;
import net.opusapp.player.core.service.providers.event.LibraryContentChangedEvent;
import net.opusapp.player.core.service.providers.local.database.Entities;
import net.opusapp.player.core.service.providers.local.database.OpenHelper;
import net.opusapp.player.core.service.providers.local.scanner.MediaScanner;
import net.opusapp.player.core.service.providers.local.ui.activities.CoverSelectionActivity;
import net.opusapp.player.core.service.providers.local.ui.activities.FileExtensionsActivity;
import net.opusapp.player.core.service.providers.local.ui.activities.SearchPathActivity;
import net.opusapp.player.core.service.providers.local.ui.activities.SettingsActivity;
import net.opusapp.player.core.service.utils.CursorUtils;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class LocalProvider implements AbstractMediaManager.Provider {



    public static final String TAG = LocalProvider.class.getSimpleName();



    // Internal content & media scanner
    private SQLiteDatabase mDatabase;

    private MediaScanner mMediaScanner;

    private LocalMediaManager mediaManager;




    // Storage category.
    private File mStorageCurrentDir = null;

    private List<File> fileList;

    public boolean mStorageIsRoot;



    // Empty content custom actions.
    public final AlbumArtistEmptyAction EMPTY_ACTION_ALBUM_ARTIST = new AlbumArtistEmptyAction();

    public final AlbumEmptyAction EMPTY_ACTION_ALBUM = new AlbumEmptyAction();

    public final ArtistEmptyAction EMPTY_ACTION_ARTIST = new ArtistEmptyAction();

    public final GenreEmptyAction EMPTY_ACTION_GENRE = new GenreEmptyAction();

    public final SongEmptyAction EMPTY_ACTION_SONG = new SongEmptyAction();


    // Other actions.
    public static final int ACTION_INDEX_LOCATION = 1;

    public static final int ACTION_INDEX_EXTENSIONS = 2;



    public AbstractMediaManager.ProviderAction ACTION_LIST[] = new AbstractMediaManager.ProviderAction[] {
            new SettingsAction(),
            new LocationAction(),
            new FileExtensionAction()
    };



    public LocalProvider(LocalMediaManager mediaManager) {
        this.mediaManager = mediaManager;

        mDatabase = OpenHelper.getInstance(mediaManager.getId()).getWritableDatabase();
        mMediaScanner = new MediaScanner(mediaManager);

        mStorageCurrentDir = new File(Environment.getExternalStorageDirectory().getAbsolutePath());
        mStorageIsRoot = true;

        final Resources resources = PlayerApplication.context.getResources();
        final SharedPreferences sharedPrefs = PlayerApplication.context.getSharedPreferences("provider-" + mediaManager.getId(), Context.MODE_PRIVATE);

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

    public void setLastPlayed(String mediaUri) {
        ContentValues values = new ContentValues();
        values.put(Entities.Media.COLUMN_FIELD_LAST_PLAYED, new Date().getTime());

        final String where = Entities.Media.COLUMN_FIELD_URI + " = ?";
        final String whereArgs[] = new String[] {
                mediaUri
        };

        mDatabase.update(Entities.Media.TABLE_NAME, values, where, whereArgs);
    }

    protected void doEraseProviderData() {
        final AbstractMediaManager playerMediaManager = PlayerApplication.playerMediaManager();

        if (mediaManager.getId() == playerMediaManager.getId()) {
            playerMediaManager.getPlayer().playerStop();
        }

        OpenHelper databaseOpenHelper = OpenHelper.getInstance(mediaManager.getId());
        databaseOpenHelper.deleteDatabaseFile();

        File filePath = PlayerApplication.context.getFilesDir();
        if (filePath != null) {
            File providerPrefs = new File(filePath.getPath() + "/shared_prefs/provider-" + mediaManager.getId() + ".xml");
            if (!providerPrefs.delete()) {
                LogUtils.LOGE(TAG, "deleting provider-" + mediaManager.getId() + " preferences failed");
            }
        }
    }

    @Override
    public void erase() {
        scanCancel();
        doEraseProviderData();
    }

    @Override
    public synchronized boolean scanStart() {
        mMediaScanner.start();
        return true;
    }

    @Override
    public boolean scanCancel() {
        mMediaScanner.stop();
        return true;
    }

    @Override
    public boolean scanIsRunning() {
        return mMediaScanner.isRunning();
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
        if (PlayerApplication.playerService != null) {
            mDatabase.delete(Entities.PlaylistEntry.TABLE_NAME, Entities.PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = ?", new String[]{"0"});
            if (contentType == ContentType.CONTENT_TYPE_STORAGE) {
                if (mStorageCurrentDir.getParentFile() != null) {
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

        return false;
    }

    @Override
    public boolean playNext(ContentType contentType, String sourceId, int sortOrder, String filter) {
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
        ContentValues contentValues = new ContentValues();
        contentValues.put(Entities.Playlist.COLUMN_FIELD_PLAYLIST_NAME, playlistName);
        contentValues.put(Entities.Playlist.COLUMN_FIELD_VISIBLE, true);
        contentValues.put(Entities.Playlist.COLUMN_FIELD_USER_HIDDEN, false);

        return String.valueOf(mDatabase.insert(Entities.Playlist.TABLE_NAME, null, contentValues));
    }

    @Override
    public boolean playlistDelete(String playlistId) {
        if (playlistId == null) {
            playlistId = "0";
        }

        return mDatabase.delete(Entities.Playlist.TABLE_NAME, Entities.Playlist._ID + " = ?", new String[] { playlistId}) > 0 &&
                mDatabase.delete(Entities.PlaylistEntry.TABLE_NAME, Entities.PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = ? ", new String[] { playlistId}) > 0;
    }

    @Override
    public boolean playlistAdd(String playlistId, ContentType contentType, String sourceId, int sortOrder, String filter) {
        if (playlistId == null) {
            playlistId = "0";
        }

        if (PlayerApplication.playerService != null) {
            int position = 0;

            Cursor cursor = mDatabase.query(
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

        try {
            mDatabase.beginTransaction();
            if (moveFrom < moveTo) {
                int lowerIndex = Math.min(moveFrom, moveTo);
                int upperIndex = Math.max(moveFrom, moveTo);

                // Playlist is 0, 1, 2, 3, 4, ..., indexFrom, indexFrom + 1, indexFrom + 2, ..., indexTo, ...
                mDatabase.execSQL(
                        "UPDATE " + Entities.PlaylistEntry.TABLE_NAME + " SET " + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " = -1 " +
                                "WHERE " +
                                "(" + Entities.PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = " + playlistId + ") AND " +
                                "(" + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " = " + lowerIndex + ")"
                );

                // Playlist is -1, 0, 1, 2, 3, 4, ..., indexFrom + 1, indexFrom + 2, ...
                mDatabase.execSQL(
                        "UPDATE " + Entities.PlaylistEntry.TABLE_NAME + " " +
                                "SET " + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " = " + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " - 1 " +
                                "WHERE " +
                                "(" + Entities.PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = " + playlistId + ") AND " +
                                "(" + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " BETWEEN " + lowerIndex + " AND " + upperIndex + ")"
                );


                // Playlist is 0, 1, 2, 3, 4, ..., indexFrom + 1, indexFrom + 2, ..., indexTo - 1, indexTo, ...
                mDatabase.execSQL(
                        "UPDATE " + Entities.PlaylistEntry.TABLE_NAME + " SET " + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " = " + upperIndex + " " +
                                "WHERE " +
                                "(" + Entities.PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = " + playlistId + ") AND " +
                                "(" + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " = -1)"
                );
            } else {
                int lowerIndex = Math.min(moveFrom, moveTo);
                int upperIndex = Math.max(moveFrom, moveTo);

                mDatabase.execSQL(
                        "UPDATE " + Entities.PlaylistEntry.TABLE_NAME + " SET " + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " = -1 " +
                                "WHERE " +
                                "(" + Entities.PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = " + playlistId + ") AND " +
                                "(" + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " = " + upperIndex + ")"
                );

                mDatabase.execSQL(
                        "UPDATE " + Entities.PlaylistEntry.TABLE_NAME + " " +
                                "SET " + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " = " + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " + 1 " +
                                "WHERE " +
                                "(" + Entities.PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = " + playlistId + ") AND " +
                                "(" + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " BETWEEN " + lowerIndex + " AND " + upperIndex + ")"
                );

                mDatabase.execSQL(
                        "UPDATE " + Entities.PlaylistEntry.TABLE_NAME + " " +
                                "SET " + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " = " + lowerIndex + " " +
                                "WHERE " +
                                "(" + Entities.PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = " + playlistId + ") AND " +
                                "(" + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " = -1)"
                );
            }
            mDatabase.setTransactionSuccessful();
        }
        catch (final SQLException sqlException) {
            LogUtils.LOGException(TAG, "playlistMove", 0, sqlException);
        }
        finally {
            mDatabase.endTransaction();
        }
    }

    @Override
    public void playlistRemove(String playlistId, int position) {
        if (playlistId == null) {
            playlistId = "0";
        }

        try {
            mDatabase.beginTransaction();
            mDatabase.delete(
                    Entities.PlaylistEntry.TABLE_NAME,
                    Entities.PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = ? AND " + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " = ? ",
                    new String[]{
                            playlistId,
                            String.valueOf(position),
                    }
            );

            mDatabase.execSQL(
                    "UPDATE " + Entities.PlaylistEntry.TABLE_NAME + " " +
                            "SET " + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " = " + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " - 1 " +
                            "WHERE (" + Entities.PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = ?) AND (" + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " >= ?) ",
                    new String[]{
                            playlistId,
                            String.valueOf(position),
                    }
            );
            mDatabase.setTransactionSuccessful();
        }
        catch (final SQLException sqlException) {
            LogUtils.LOGException(TAG, "playlistRemove", 0, sqlException);
        }
        finally {
            mDatabase.endTransaction();
        }
    }

    @Override
    public void playlistClear(String playlistId) {
        if (playlistId == null) {
            playlistId = "0";
        }

        mDatabase.delete(Entities.PlaylistEntry.TABLE_NAME, Entities.PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = ? ", new String[]{playlistId});
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

                if (mStorageCurrentDir.getParentFile() != null) {
                    if (targetIndex == 0) {
                        mStorageCurrentDir = mStorageCurrentDir.getParentFile();
                        break;
                    }
                    else {
                        targetIndex--;
                    }
                }

                File fileTarget = fileList.get(targetIndex);
                if (fileTarget.isDirectory()) {
                    mStorageCurrentDir = fileTarget;
                }

                break;
            case CONTENT_STORAGE_CURRENT_LOCATION:
                mStorageCurrentDir = new File((String) target);
                fileList = getStorageFileList(null, null);
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

                if (mStorageCurrentDir.getParentFile() != null) {
                    if (targetIndex != 0) {
                        targetIndex--;
                    }
                    else {
                        return true; // current folder has childs !
                    }
                }
                return fileList.get(targetIndex).isDirectory();
            case CONTENT_STORAGE_HAS_PARENT:
                return mStorageCurrentDir.getParentFile() != null;
            case CONTENT_STORAGE_CURRENT_LOCATION:
                return mStorageCurrentDir.getAbsolutePath();
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
                final String albumSelection = Entities.Media.COLUMN_FIELD_ALBUM_ID + " = ? ";

                final String albumSelectionArgs[] = new String[] {
                        (String) target
                };

                // Album Name
                final Cursor albumNameCursor = mDatabase.rawQuery(
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
                final Cursor trackCountCursor = mDatabase.rawQuery(
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
                final Cursor totalCursor = mDatabase.rawQuery(
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

                final Cursor artistsCursor = mDatabase.query(Entities.Media.TABLE_NAME, artistsColumns, albumSelection, albumSelectionArgs, Entities.Media.COLUMN_FIELD_ARTIST, null, Entities.Media.COLUMN_FIELD_ARTIST);
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

                final Cursor genreCursor = mDatabase.query(Entities.Media.TABLE_NAME, genresColumns, albumSelection, albumSelectionArgs, Entities.Media.COLUMN_FIELD_GENRE, null, Entities.Media.COLUMN_FIELD_GENRE);
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
        final SharedPreferences sharedPrefs = PlayerApplication.context.getSharedPreferences("provider-" + mediaManager.getId(), Context.MODE_PRIVATE);

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
            final AbstractMediaManager mediaManager = PlayerApplication.libraryMediaManager();
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
            final Intent intent = new Intent(sourceActivity, CoverSelectionActivity.class);
            intent.putExtra(KEY_PROVIDER_ID, mediaManager.getId());
            intent.putExtra(KEY_SOURCE_ID, Long.parseLong(albumId));
            sourceActivity.startActivityForResult(intent, ACTIVITY_NEED_UI_REFRESH);
        }
    }

    protected boolean doToggleAlbumVisibility(String albumId) {
        mDatabase.execSQL(
                "UPDATE " + Entities.Album.TABLE_NAME +
                        "   SET " + Entities.Album.COLUMN_FIELD_USER_HIDDEN + " = ~" + Entities.Album.COLUMN_FIELD_USER_HIDDEN + " " +
                        "WHERE " + Entities.Album._ID + " = " + albumId
        );

        PlayerEventBus.getInstance().post(new LibraryContentChangedEvent());
        return true;
    }

    protected boolean doToggleAlbumArtistVisibility(String albumArtistId) {
        mDatabase.execSQL(
                "UPDATE " + Entities.AlbumArtist.TABLE_NAME +
                        "   SET " + Entities.AlbumArtist.COLUMN_FIELD_USER_HIDDEN + " = ~" + Entities.AlbumArtist.COLUMN_FIELD_USER_HIDDEN + " " +
                        "WHERE " + Entities.AlbumArtist._ID + " = " + albumArtistId
        );

        PlayerEventBus.getInstance().post(new LibraryContentChangedEvent());
        return true;
    }

    protected boolean doToggleArtistVisibility(String artistId) {
        mDatabase.execSQL(
                "UPDATE " + Entities.Artist.TABLE_NAME +
                        "   SET " + Entities.Artist.COLUMN_FIELD_USER_HIDDEN + " = ~" + Entities.Artist.COLUMN_FIELD_USER_HIDDEN + " " +
                        "WHERE " + Entities.Artist._ID + " = " + artistId
        );

        PlayerEventBus.getInstance().post(new LibraryContentChangedEvent());
        return true;
    }

    protected boolean doToggleGenreVisibility(String genreId) {
        mDatabase.execSQL(
                "UPDATE " + Entities.Genre.TABLE_NAME +
                        "   SET " + Entities.Genre.COLUMN_FIELD_USER_HIDDEN + " = ~" + Entities.Genre.COLUMN_FIELD_USER_HIDDEN + " " +
                        "WHERE " + Entities.Genre._ID + " = " + genreId
        );

        PlayerEventBus.getInstance().post(new LibraryContentChangedEvent());
        return true;
    }

    protected boolean doToggleMediaVisibility(String mediaId) {
        mDatabase.execSQL(
                "UPDATE " + Entities.Media.TABLE_NAME +
                        "   SET " + Entities.Media.COLUMN_FIELD_USER_HIDDEN + " = ~" + Entities.Media.COLUMN_FIELD_USER_HIDDEN + " " +
                        "WHERE " + Entities.Media._ID + " = " + mediaId
        );

        PlayerEventBus.getInstance().post(new LibraryContentChangedEvent());
        return true;
    }

    protected boolean doTogglePlaylistVisibility(String playlistId) {
        mDatabase.execSQL(
                "UPDATE " + Entities.Playlist.TABLE_NAME +
                        "   SET " + Entities.Playlist.COLUMN_FIELD_USER_HIDDEN + " = ~" + Entities.Playlist.COLUMN_FIELD_USER_HIDDEN + " " +
                        "WHERE " + Entities.Playlist._ID + " = " + playlistId
        );

        PlayerEventBus.getInstance().post(new LibraryContentChangedEvent());
        return true;
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

        String where = PlayerApplication.library_show_hidden ? null : "(" + Entities.AlbumArtist.COLUMN_FIELD_USER_HIDDEN + " = 0) ";

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
        return mDatabase.query(Entities.AlbumArtist.TABLE_NAME, columns, where, null, null, null, orderBy);
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
        String selection = PlayerApplication.library_show_hidden ? "" : "(" + Entities.Album.TABLE_NAME + "." + Entities.Album.COLUMN_FIELD_USER_HIDDEN + " = 0) ";
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
        return mDatabase.query(tableDescription, columns, selection, selectionArgs, groupBy, null, orderBy);
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

        String selection = PlayerApplication.library_show_hidden ? null : Entities.AlbumArtist.COLUMN_FIELD_USER_HIDDEN + " = 0 ";

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
        return mDatabase.query(Entities.Artist.TABLE_NAME, columns, selection, null, null, null, orderBy);
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

        String selection = PlayerApplication.library_show_hidden ? null : Entities.Genre.COLUMN_FIELD_USER_HIDDEN + " = 0 ";

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
        return mDatabase.query(Entities.Genre.TABLE_NAME, columns, selection, null, null, null, orderBy);
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

        String selection = PlayerApplication.library_show_hidden ?
                "(" + Entities.Playlist.COLUMN_FIELD_VISIBLE + " <> 0) AND (" + Entities.Playlist._ID + " <> 0)" :
                "(" + Entities.Playlist.COLUMN_FIELD_USER_HIDDEN + " = 0) AND " +
                        "(" + Entities.Playlist.COLUMN_FIELD_VISIBLE + " <> 0) AND (" + Entities.Playlist._ID + " <> 0)";

        if (!TextUtils.isEmpty(filter)) {
            selection = selection + " AND (" +
                    "(" + Entities.Playlist.COLUMN_FIELD_PLAYLIST_NAME + " LIKE '%" + filter + "%') " + ")";
        }

        // query.
        return mDatabase.query(Entities.Playlist.TABLE_NAME, columns, selection, null, null, null, orderBy);
    }

    protected Cursor buildMediaCursor(int[] requestedFields, int[] sortFields, String filter, ContentType contentType, String sourceId) {
        final Resources resources = PlayerApplication.context.getResources();
        final SharedPreferences sharedPrefs = PlayerApplication.context.getSharedPreferences("provider-" + mediaManager.getId(), Context.MODE_PRIVATE);

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

        String selection = PlayerApplication.library_show_hidden ?
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
                    " LEFT JOIN " + Entities.Art.TABLE_NAME +
                            " ON " +
                            Entities.Art.TABLE_NAME + "." + Entities.Art._ID + " = " +
                            Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_ART_ID;
        }

        return mDatabase.query(tableDescription, columns, selection, selectionArgs, null, null, orderBy);
    }

    protected Cursor buildStorageCursor(int[] requestedFields, int[] sortFields, String filter) {
        final String[] columns = new String[requestedFields.length + 1];
        final Object[] currentRow = new Object[requestedFields.length + 1];

        columns[requestedFields.length] = "_id";
        for (int columnIndex = 0 ; columnIndex < requestedFields.length ; columnIndex++) {
            columns[columnIndex] = String.valueOf(requestedFields[columnIndex]);
        }

        final MatrixCursor cursor = new MatrixCursor(columns);

        if (mStorageCurrentDir.getParentFile() != null) {
            currentRow[requestedFields.length] = 0;

            for (int columnIndex = 0; columnIndex < requestedFields.length; columnIndex++) {
                switch (requestedFields[columnIndex]) {
                    case SONG_ID:
                        currentRow[columnIndex] = 0;
                        break;
                    case SONG_URI:
                        currentRow[columnIndex] = PlayerApplication.fileToUri(mStorageCurrentDir.getParentFile());
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
                        currentRow[columnIndex] = Base64.encodeBytes(PlayerApplication.fileToUri(mStorageCurrentDir.getParentFile()).getBytes());
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

        fileList = getStorageFileList(filter, sortFields);

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
        Cursor cursor = mDatabase.query(tableName, columns, selection, selectionArgs, null, null, null);
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
        final String[] columns = new String[]{
                Entities.Art.TABLE_NAME + "." + Entities.Art.COLUMN_FIELD_URI
        };

        final int COLUMN_ART_URI = 0;

        String selection = Entities.Art.TABLE_NAME + "." + Entities.Art._ID + " = ? ";
        final String[] selectionArgs = new String[]{ artId };

        String artUri = null;

        Cursor cursor = mDatabase.query(Entities.Art.TABLE_NAME, columns, selection, selectionArgs, null, null, null);
        if (CursorUtils.ifNotEmpty(cursor)) {
            cursor.moveToFirst();
            artUri = cursor.getString(COLUMN_ART_URI);

            CursorUtils.free(cursor);
        }

        return artUri;
    }

    protected String getAlbumArt(String albumId) {
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

        Cursor cursor = mDatabase.query(tableName, columns, selection, selectionArgs, null, null, null);
        if (CursorUtils.ifNotEmpty(cursor)) {
            cursor.moveToFirst();
            albumArtUri = cursor.getString(COLUMN_ART_URI);
            CursorUtils.free(cursor);
        }

        return albumArtUri;
    }

    @Override
    public String getAlbumArtUri(String albumId) {
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

        Cursor cursor = mDatabase.query(tableName, columns, selection, selectionArgs, null, null, null);
        if (CursorUtils.ifNotEmpty(cursor)) {
            cursor.moveToFirst();
            albumArtUri = cursor.getString(COLUMN_ART_URI);
            CursorUtils.free(cursor);
        }

        return albumArtUri;
    }

    protected void doUpdateAlbumCover(String albumId, String uri, boolean updateTracks) {
        long artId;

        final String columns[] = new String[] {
            Entities.Art._ID
        };
        final String selection = Entities.Art.COLUMN_FIELD_URI + " = ? ";
        final String selectionArgs[] = new String[] {
            uri
        };

        Cursor cursor = mDatabase.query(Entities.Art.TABLE_NAME, columns, selection, selectionArgs, null, null, null);

        if (CursorUtils.ifNotEmpty(cursor)) {
            cursor.moveToFirst();
            artId = cursor.getLong(0);
            CursorUtils.free(cursor);
        }
        else {
            ContentValues artData = new ContentValues();
            artData.put(Entities.Art.COLUMN_FIELD_URI, uri);
            artData.put(Entities.Art.COLUMN_FIELD_URI_IS_EMBEDDED, false);
            artId = mDatabase.insert(Entities.Art.TABLE_NAME, null, artData);
        }

        final String whereAlbumId[] = new String[] {
                albumId
        };

        ContentValues contentValues = new ContentValues();
        contentValues.put(Entities.Album.COLUMN_FIELD_ALBUM_ART_ID, artId);

        mDatabase.update(Entities.Album.TABLE_NAME, contentValues, Entities.Album._ID + " = ? ", whereAlbumId);

        if (updateTracks) {
            contentValues.clear();
            contentValues.put(Entities.Media.COLUMN_FIELD_ART_ID, artId);

            mDatabase.update(Entities.Media.TABLE_NAME, contentValues, Entities.Media.COLUMN_FIELD_ALBUM_ID + " = ? ", whereAlbumId);
        }

        PlayerEventBus.getInstance().post(new LibraryContentChangedEvent());
    }

    protected void doRestoreAlbumCover(String albumId, boolean updateTracks) {
        final String whereAlbumId[] = new String[] {
                albumId
        };

        mDatabase.execSQL(
                "UPDATE " + Entities.Album.TABLE_NAME + " " +
                        "SET " + Entities.Album.COLUMN_FIELD_ALBUM_ART_ID + " = " + Entities.Album.COLUMN_FIELD_ORIGINAL_ALBUM_ART_ID + " " +
                        "WHERE " + Entities.Album._ID + " = ? ", whereAlbumId);


        if (updateTracks) {
            mDatabase.execSQL(
                    "UPDATE " + Entities.Media.TABLE_NAME + " " +
                            "SET " + Entities.Media.COLUMN_FIELD_ART_ID + " = " + Entities.Media.COLUMN_FIELD_ORIGINAL_ART_ID + " " +
                            "WHERE " + Entities.Media.COLUMN_FIELD_ALBUM_ID + " = ? ", whereAlbumId);
        }

        PlayerEventBus.getInstance().post(new LibraryContentChangedEvent());
    }

    public static boolean fileHasValidExtension(File file, Set<String> extensionSet) {
        if (file.isDirectory()) {
            return false;
        }

        final String filePath = file.getAbsolutePath().toLowerCase();

        for(String extension : extensionSet) {
            if (filePath.endsWith("." + extension)) {
                return true;
            }
        }

        return false;
    }
    protected boolean doPlaylistAddContent(String playlistId, int position, ContentType contentType, final String sourceId, int sortOrder, String filter) {
        if (playlistId == null) {
            playlistId = "0";
        }

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

            selectStatement = selectStatement + (PlayerApplication.library_show_hidden ?
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
        Cursor cursor = mDatabase.rawQuery("SELECT COUNT(*) AS CNT " + selectStatement, selectionArgs);

        if (CursorUtils.ifNotEmpty(cursor)) {
            cursor.moveToFirst();
            addedCount = cursor.getInt(0);

            mDatabase.execSQL(
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

        try {
            mDatabase.beginTransaction();
            mDatabase.execSQL(insertStatement + selectStatement);
            mDatabase.execSQL(updateStatement);
            mDatabase.setTransactionSuccessful();
        }
        catch (final SQLException sqlException) {
            LogUtils.LOGException(TAG, "doPlaylistAddContent", 0, sqlException);
        }
        finally {
            mDatabase.endTransaction();
        }
        return true;
    }

    protected boolean doPlaylistAddContent(String playlistId, int position, List<File> fileList, boolean deleteFileMedias) {
        if (playlistId == null) {
            playlistId = "0";
        }

        if (fileList != null && fileList.size() > 0) {
            final Set<String> audioExtensions = MediaScanner.getMediaExtensions(mediaManager.getId());

            int addedCount = fileList.size();

            try {
                mDatabase.beginTransaction();
                mDatabase.execSQL(
                        "UPDATE " + Entities.PlaylistEntry.TABLE_NAME + " SET " +
                                Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " = " + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " + " + addedCount + " " +
                                "WHERE " + Entities.PlaylistEntry.COLUMN_FIELD_POSITION + " >= " + position + " " +
                                "AND " + Entities.PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = " + playlistId);

                if (deleteFileMedias) {
                    final String where = Entities.Media.COLUMN_FIELD_IS_QUEUE_FILE_ENTRY + " = ? ";

                    final String whereArgs[] = new String[]{
                            String.valueOf(1)
                    };

                    mDatabase.delete(Entities.Media.TABLE_NAME, where, whereArgs);
                }

                ContentValues contentValues = new ContentValues();
                for (File currentFile : fileList) {
                    if (fileHasValidExtension(currentFile, audioExtensions)) {
                        contentValues.clear();
                        JniMediaLib.readTags(currentFile, contentValues);
                        contentValues.put(Entities.Media.COLUMN_FIELD_VISIBLE, 0);
                        contentValues.put(Entities.Media.COLUMN_FIELD_IS_QUEUE_FILE_ENTRY, 1);
                        contentValues.remove(Entities.Media.NOT_PERSISTANT_COLUMN_FIELD_HAS_EMBEDDED_ART);
                        long insertId = mDatabase.insert(Entities.Media.TABLE_NAME, null, contentValues);

                        contentValues.clear();
                        contentValues.put(Entities.PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID, playlistId);
                        contentValues.put(Entities.PlaylistEntry.COLUMN_FIELD_POSITION, position);
                        contentValues.put(Entities.PlaylistEntry.COLUMN_FIELD_SONG_ID, insertId);
                        mDatabase.insert(Entities.PlaylistEntry.TABLE_NAME, null, contentValues);
                        position++;
                    }
                }

                mDatabase.setTransactionSuccessful();
            }
            catch (final SQLException sqlException) {
                LogUtils.LOGException(TAG, "doPlaylistAddContent", 0, sqlException);
            }
            finally {
                mDatabase.endTransaction();
            }

            return true;
        }

        return false;
    }

    @Override
    public void databaseMaintain() {
        mDatabase.rawQuery("VACUUM;", null);
    }

    protected List<File> getStorageFileList(String filter, int[] sortFields) {
        if (mStorageCurrentDir == null) {
            return null;
        }

        final File[] sourceFileList = mStorageCurrentDir.listFiles(hiddenFilter);
        ArrayList<File> queryedFileList = new ArrayList<>();

        if (sourceFileList != null) {
            final Set<String> audioExtensions = MediaScanner.getMediaExtensions(mediaManager.getId());

            storageSortOrder = sortFields;
            Arrays.sort(sourceFileList, filenameComparator);
            Arrays.sort(sourceFileList, filetypeComparator);

            for (File file : sourceFileList) {
                if (!TextUtils.isEmpty(filter) && !file.getAbsolutePath().toLowerCase().contains(filter.toLowerCase())) {
                    continue;
                }

                if (fileHasValidExtension(file, audioExtensions)) {
                    queryedFileList.add(file);
                } else if (file.isDirectory()) {
                    queryedFileList.add(file);
                }
            }
        }

        return queryedFileList;
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
                else // if (MusicConnector.library_storage_sort_order == MusicConnector.SORT_Z_A)  {
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
            if (PlayerApplication.library_show_hidden) {
                return pathname.canRead();
            }
            else {
                return pathname.canRead() && !pathname.isHidden();
            }
        }
    };


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
            intent.putExtra(KEY_PROVIDER_ID, mediaManager.getId());

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
            intent.putExtra(KEY_PROVIDER_ID, mediaManager.getId());

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
            intent.putExtra(KEY_PROVIDER_ID, mediaManager.getId());

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
