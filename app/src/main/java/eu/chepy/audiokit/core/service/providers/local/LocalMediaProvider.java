package eu.chepy.audiokit.core.service.providers.local;

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
import android.util.Log;

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

import eu.chepy.audiokit.R;
import eu.chepy.audiokit.core.service.providers.AbstractEmptyContentAction;
import eu.chepy.audiokit.core.service.providers.AbstractMediaProvider;
import eu.chepy.audiokit.core.service.providers.AbstractProviderAction;
import eu.chepy.audiokit.core.service.providers.Metadata;
import eu.chepy.audiokit.core.service.providers.local.entities.Album;
import eu.chepy.audiokit.core.service.providers.local.entities.AlbumArtist;
import eu.chepy.audiokit.core.service.providers.local.entities.Artist;
import eu.chepy.audiokit.core.service.providers.local.entities.Genre;
import eu.chepy.audiokit.core.service.providers.local.entities.Media;
import eu.chepy.audiokit.core.service.providers.local.entities.Playlist;
import eu.chepy.audiokit.core.service.providers.local.entities.PlaylistEntry;
import eu.chepy.audiokit.core.service.providers.local.entities.ScanDirectory;
import eu.chepy.audiokit.ui.activities.SoundEffectsActivity;
import eu.chepy.audiokit.ui.utils.MusicConnector;
import eu.chepy.audiokit.ui.utils.PlayerApplication;
import eu.chepy.audiokit.ui.utils.ProviderStreamImageDownloader;
import eu.chepy.audiokit.utils.Base64;
import eu.chepy.audiokit.utils.LogUtils;
import eu.chepy.audiokit.utils.jni.JniMediaLib;
import eu.chepy.audiokit.utils.support.android.content.SharedPreferencesCompat;

public class LocalMediaProvider implements AbstractMediaProvider {



    public static final String TAG = LocalMediaProvider.class.getSimpleName();



    private InternalDatabaseOpenHelper databaseOpenHelper;

    private int providerId;

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
    public final LocationAction ACTION_LOCATION = new LocationAction();

    public final AudioFxAction ACTION_AUDIO_FX = new AudioFxAction();

    public final SettingsAction ACTION_SETTINGS = new SettingsAction();



    public final AlbumArtistEmptyAction EMPTY_ACTION_ALBUM_ARTIST = new AlbumArtistEmptyAction();

    public final AlbumEmptyAction EMPTY_ACTION_ALBUM = new AlbumEmptyAction();

    public final ArtistEmptyAction EMPTY_ACTION_ARTIST = new ArtistEmptyAction();

    public final GenreEmptyAction EMPTY_ACTION_GENRE = new GenreEmptyAction();

    public final SongEmptyAction EMPTY_ACTION_SONG = new SongEmptyAction();



    public AbstractProviderAction ACTION_LIST[] = new AbstractProviderAction[] {
            ACTION_LOCATION,
            ACTION_AUDIO_FX,
            ACTION_SETTINGS
    };



    public LocalMediaProvider(LocalMediaManager mediaManager, int providerId) {
        this.mediaManager = mediaManager;
        this.providerId = providerId;

        databaseOpenHelper = new InternalDatabaseOpenHelper(PlayerApplication.context, providerId);
        scanListeners = new ArrayList<OnLibraryChangeListener>();

        currentFolder = new File(Environment.getExternalStorageDirectory().getAbsolutePath());
        isAtRootLevel = true;
    }

    public SQLiteDatabase getWritableDatabase() {
        return databaseOpenHelper.getWritableDatabase();
    }

    public SQLiteDatabase getReadableDatabase() {
        return databaseOpenHelper.getReadableDatabase();
    }

    public void setLastPlayed(String mediaUri) {
        SQLiteDatabase database = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(Media.COLUMN_FIELD_LAST_PLAYED, new Date().getTime());

        final String where = Media.COLUMN_FIELD_URI + " = ?";
        final String whereArgs[] = new String[] {
                mediaUri
        };

        database.update(Media.TABLE_NAME, values, where, whereArgs);
    }

    @Override
    public void erase() {
        databaseOpenHelper.deleteDatabaseFile();

        File filePath = PlayerApplication.context.getFilesDir();
        if (filePath != null) {
            File providerPrefs = new File(filePath.getPath() + "/shared_prefs/provider-" + providerId + ".xml");
            if (!providerPrefs.delete()) {
                Log.w(TAG, "deleting provider-" + providerId + " preferences failed");
            }
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
    public Cursor buildCursor(ContentType contentType, int[] fields, int[] sortFields, String filter) {
        switch (contentType) {
            case CONTENT_TYPE_ALBUM:
                return doBuildAlbumCursor(fields, sortFields, filter, ContentType.CONTENT_TYPE_DEFAULT, null);
            case CONTENT_TYPE_ALBUM_ARTIST:
                return doBuildAlbumArtistCursor(fields, sortFields, filter);
            case CONTENT_TYPE_ARTIST:
                return doBuildArtistCursor(fields, sortFields, filter);
            case CONTENT_TYPE_GENRE:
                return doBuildGenreCursor(fields, sortFields, filter);
            case CONTENT_TYPE_PLAYLIST:
                return doBuildPlaylistCursor(fields, sortFields, filter);
            case CONTENT_TYPE_MEDIA:
                return doBuildMediaCursor(fields, sortFields, filter, ContentType.CONTENT_TYPE_DEFAULT, null);
            case CONTENT_TYPE_STORAGE:
                return doBuildStorageCursor(fields, sortFields, filter);
        }

        return null;
    }

    @Override
    public Cursor buildCursor(ContentType contentType, int[] fields, int[] sortFields, String filter, ContentType source, String sourceId) {
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
        SQLiteDatabase database = databaseOpenHelper.getWritableDatabase();

        if (database != null) {
            if (MusicConnector.playerService != null) {
                try {
                    database.delete(PlaylistEntry.TABLE_NAME, PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = ?", new String[]{"0"});
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

                    MusicConnector.playerService.queueReload();

                    if (MusicConnector.playerService.queueGetSize() > position) {
                        MusicConnector.playerService.queueSetPosition(position);

                        if (!MusicConnector.playerService.isPlaying()) {
                            MusicConnector.playerService.play();
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
        SQLiteDatabase database = databaseOpenHelper.getWritableDatabase();

        if (database != null) {
            if (MusicConnector.playerService != null) {
                try {
                    int position = MusicConnector.playerService.queueGetPosition();

                    if (contentType == ContentType.CONTENT_TYPE_STORAGE) {
                        File selection = fileList.get(position);
                        fileList.clear();
                        fileList.add(selection);

                        if (!doPlaylistAddContent(null, position + 1, fileList, false)) {
                            return false;
                        }
                    }
                    if (!doPlaylistAddContent(null, position + 1, contentType, sourceId, sortOrder, filter)) {
                        return false;
                    }

                    MusicConnector.playerService.queueReload();
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
    public String playlistNew(String playlistName) {
        SQLiteDatabase database = databaseOpenHelper.getWritableDatabase();

        if (database != null) {
            ContentValues contentValues = new ContentValues();
            contentValues.put(Playlist.COLUMN_FIELD_PLAYLIST_NAME, playlistName);
            contentValues.put(Playlist.COLUMN_FIELD_VISIBLE, true);
            contentValues.put(Playlist.COLUMN_FIELD_USER_HIDDEN, false);

            return String.valueOf(database.insert(Playlist.TABLE_NAME, null, contentValues));
        }

        return null;
    }

    @Override
    public boolean playlistDelete(String playlistId) {
        SQLiteDatabase database = databaseOpenHelper.getWritableDatabase();

        if (playlistId == null) {
            playlistId = "0";
        }

        return database != null &&
                database.delete(Playlist.TABLE_NAME, Playlist.COLUMN_FIELD_PLAYLIST_ID + " = ?", new String[] { playlistId}) > 0 &&
                database.delete(PlaylistEntry.TABLE_NAME, PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = ? ", new String[] { playlistId}) > 0;
    }

    @Override
    public boolean playlistAdd(String playlistId, ContentType contentType, String sourceId, int sortOrder, String filter) {
        SQLiteDatabase database = databaseOpenHelper.getReadableDatabase();

        if (playlistId == null) {
            playlistId = "0";
        }

        if (database != null) {
            if (MusicConnector.playerService != null) {
                try {
                    int position = 0;

                    Cursor cursor = database.query(
                            PlaylistEntry.TABLE_NAME,
                            new String[] { "COUNT(*) AS CNT" },
                            PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = ? ",
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
                    Log.w(TAG, "playlistAdd : position = " + position);

                    if (contentType == ContentType.CONTENT_TYPE_STORAGE) {
                        File selection = fileList.get(position);
                        fileList.clear();
                        fileList.add(selection);

                        if (!doPlaylistAddContent(null, position, fileList, false)) {
                            return false;
                        }
                    }
                    else if (!doPlaylistAddContent(playlistId, position, contentType, sourceId, sortOrder, filter)) {
                        return false;
                    }

                    MusicConnector.playerService.queueReload();
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

        SQLiteDatabase database = databaseOpenHelper.getWritableDatabase();
        if (database != null) {
            database.beginTransaction();
            try {
                if (moveFrom < moveTo) {
                    int lowerIndex = Math.min(moveFrom, moveTo);
                    int upperIndex = Math.max(moveFrom, moveTo);

                    // Playlist is 0, 1, 2, 3, 4, ..., indexFrom, indexFrom + 1, indexFrom + 2, ..., indexTo, ...
                    database.execSQL(
                            "UPDATE " + PlaylistEntry.TABLE_NAME + " SET " + PlaylistEntry.COLUMN_FIELD_POSITION + " = -1 " +
                                    "WHERE " +
                                    "(" + PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = " + playlistId + ") AND " +
                                    "(" + PlaylistEntry.COLUMN_FIELD_POSITION + " = " + lowerIndex + ")"
                    );

                    // Playlist is -1, 0, 1, 2, 3, 4, ..., indexFrom + 1, indexFrom + 2, ...
                    database.execSQL(
                            "UPDATE " + PlaylistEntry.TABLE_NAME + " " +
                                    "SET " + PlaylistEntry.COLUMN_FIELD_POSITION + " = " + PlaylistEntry.COLUMN_FIELD_POSITION + " - 1 " +
                                    "WHERE " +
                                    "(" + PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = " + playlistId + ") AND " +
                                    "(" + PlaylistEntry.COLUMN_FIELD_POSITION + " BETWEEN " + lowerIndex + " AND " + upperIndex + ")"
                    );


                    // Playlist is 0, 1, 2, 3, 4, ..., indexFrom + 1, indexFrom + 2, ..., indexTo - 1, indexTo, ...
                    database.execSQL(
                            "UPDATE " + PlaylistEntry.TABLE_NAME + " SET " + PlaylistEntry.COLUMN_FIELD_POSITION + " = " + upperIndex + " " +
                                    "WHERE " +
                                    "(" + PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = " + playlistId + ") AND " +
                                    "(" + PlaylistEntry.COLUMN_FIELD_POSITION + " = -1)"
                    );
                } else {
                    int lowerIndex = Math.min(moveFrom, moveTo);
                    int upperIndex = Math.max(moveFrom, moveTo);

                    database.execSQL(
                            "UPDATE " + PlaylistEntry.TABLE_NAME + " SET " + PlaylistEntry.COLUMN_FIELD_POSITION + " = -1 " +
                                    "WHERE " +
                                    "(" + PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = " + playlistId + ") AND " +
                                    "(" + PlaylistEntry.COLUMN_FIELD_POSITION + " = " + upperIndex + ")"
                    );

                    database.execSQL(
                            "UPDATE " + PlaylistEntry.TABLE_NAME + " " +
                                    "SET " + PlaylistEntry.COLUMN_FIELD_POSITION + " = " + PlaylistEntry.COLUMN_FIELD_POSITION + " + 1 " +
                                    "WHERE " +
                                    "(" + PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = " + playlistId + ") AND " +
                                    "(" + PlaylistEntry.COLUMN_FIELD_POSITION + " BETWEEN " + lowerIndex + " AND " + upperIndex + ")"
                    );

                    database.execSQL(
                            "UPDATE " + PlaylistEntry.TABLE_NAME + " " +
                                    "SET " + PlaylistEntry.COLUMN_FIELD_POSITION + " = " + lowerIndex + " " +
                                    "WHERE " +
                                    "(" + PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = " + playlistId + ") AND " +
                                    "(" + PlaylistEntry.COLUMN_FIELD_POSITION + " = -1)"
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
        SQLiteDatabase database = databaseOpenHelper.getWritableDatabase();
        if (database != null) {
            database.beginTransaction();
            try {
                database.delete(
                        PlaylistEntry.TABLE_NAME,
                        PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = ? AND " + PlaylistEntry.COLUMN_FIELD_POSITION + " = ? ",
                        new String[]{
                                playlistId,
                                String.valueOf(position),
                        }
                );

                database.execSQL(
                        "UPDATE " + PlaylistEntry.TABLE_NAME + " " +
                                "SET " + PlaylistEntry.COLUMN_FIELD_POSITION + " = " + PlaylistEntry.COLUMN_FIELD_POSITION + " - 1 " +
                                "WHERE (" + PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = ?) AND (" + PlaylistEntry.COLUMN_FIELD_POSITION + " >= ?) ",
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
        SQLiteDatabase database = databaseOpenHelper.getWritableDatabase();

        if (playlistId == null) {
            playlistId = "0";
        }

        if (database != null) {
            database.delete(PlaylistEntry.TABLE_NAME, PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = ? ", new String[]{playlistId});
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
                        // TODO:
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
        }
    }

    @Override
    public Object getProperty(ContentType contentType, Object target, ContentProperty key) {
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
            case CONTENT_METADATA_LIST:
                ArrayList<Metadata> metadataList = new ArrayList<Metadata>();
                // TODO: add metadatas.
                switch (contentType) {
                    case CONTENT_TYPE_ALBUM:
                        // Track count
                        // Album artist
                        // Genre
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

                        final int fieldIds[] = new int[] {
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

                                    metadataList.add(new Metadata(columnIndex, fieldIds[columnIndex], cursor.getString(columnIndex), editable));
                                }
                            }
                        }
                }
                return metadataList;
            default:
        }
        return null;
    }


    @Override
    public boolean hasContentType(ContentType contentType) {
        final Resources resources = PlayerApplication.context.getResources();
        final SharedPreferences sharedPrefs = PlayerApplication.context.getSharedPreferences("provider-" + providerId, Context.MODE_PRIVATE);

        // TODO: use specific preferences !
        final String[] tabTitles = resources.getStringArray(R.array.tab_titles);

        Set<String> defaultTabs = new HashSet<String>(Arrays.asList(tabTitles));
        Set<String> userEnabledTabs = SharedPreferencesCompat.getStringSet(
                sharedPrefs, resources.getString(R.string.key_tabs_enabled), defaultTabs);

        if (userEnabledTabs.size() == 0) {
            userEnabledTabs = defaultTabs;
        }

        switch (contentType) {
            case CONTENT_TYPE_PLAYLIST:
                return  userEnabledTabs.contains(resources.getString(R.string.tab_playlists));
            case CONTENT_TYPE_ARTIST:
                return  userEnabledTabs.contains(resources.getString(R.string.tab_artists));
            case CONTENT_TYPE_ALBUM_ARTIST:
                return  userEnabledTabs.contains(resources.getString(R.string.tab_album_artists));
            case CONTENT_TYPE_ALBUM:
                return  userEnabledTabs.contains(resources.getString(R.string.tab_albums));
            case CONTENT_TYPE_MEDIA:
                return  userEnabledTabs.contains(resources.getString(R.string.tab_songs));
            case CONTENT_TYPE_GENRE:
                return  userEnabledTabs.contains(resources.getString(R.string.tab_genres));
            case CONTENT_TYPE_STORAGE:
                return  userEnabledTabs.contains(resources.getString(R.string.tab_storage));
        }

        return false;
    }

    @Override
    public AbstractEmptyContentAction getEmptyContentAction(ContentType contentType) {
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
    public AbstractProviderAction getAbstractProviderAction(int index) {
        return ACTION_LIST[index];
    }

    @Override
    public AbstractProviderAction[] getAbstractProviderActionList() {
        return ACTION_LIST;
    }



    protected void doNotifyLibraryChanges() {
        for (OnLibraryChangeListener libraryChangeListener : scanListeners) {
            libraryChangeListener.libraryChanged();
        }
    }

    protected boolean doToggleAlbumVisibility(String albumId) {
        final SQLiteDatabase database = databaseOpenHelper.getWritableDatabase();

        if (database != null) {
            database.execSQL(
                    "UPDATE " + Album.TABLE_NAME +
                            "   SET " + Album.COLUMN_FIELD_USER_HIDDEN + " = ~" + Album.COLUMN_FIELD_USER_HIDDEN + " " +
                            "WHERE " + Album.COLUMN_FIELD_ALBUM_ID + " = " + albumId
            );

            doNotifyLibraryChanges();
            return true;
        }

        return false;
    }

    protected boolean doToggleAlbumArtistVisibility(String albumArtistId) {
        final SQLiteDatabase database = databaseOpenHelper.getWritableDatabase();

        if (database != null) {
            database.execSQL(
                    "UPDATE " + AlbumArtist.TABLE_NAME +
                            "   SET " + AlbumArtist.COLUMN_FIELD_USER_HIDDEN + " = ~" + AlbumArtist.COLUMN_FIELD_USER_HIDDEN + " " +
                            "WHERE " + AlbumArtist.COLUMN_FIELD_ARTIST_ID + " = " + albumArtistId
            );

            doNotifyLibraryChanges();
            return true;
        }

        return false;
    }

    protected boolean doToggleArtistVisibility(String artistId) {
        final SQLiteDatabase database = databaseOpenHelper.getWritableDatabase();

        if (database != null) {
            database.execSQL(
                    "UPDATE " + Artist.TABLE_NAME +
                            "   SET " + Artist.COLUMN_FIELD_USER_HIDDEN + " = ~" + Artist.COLUMN_FIELD_USER_HIDDEN + " " +
                            "WHERE " + Artist.COLUMN_FIELD_ARTIST_ID + " = " + artistId
            );

            doNotifyLibraryChanges();
            return true;
        }

        return false;
    }

    protected boolean doToggleGenreVisibility(String genreId) {

        final SQLiteDatabase database = databaseOpenHelper.getWritableDatabase();

        if (database != null) {
            database.execSQL(
                    "UPDATE " + Genre.TABLE_NAME +
                            "   SET " + Genre.COLUMN_FIELD_USER_HIDDEN + " = ~" + Genre.COLUMN_FIELD_USER_HIDDEN + " " +
                            "WHERE " + Genre.COLUMN_FIELD_GENRE_ID + " = " + genreId
            );

            doNotifyLibraryChanges();
            return true;
        }

        return false;
    }

    protected boolean doToggleMediaVisibility(String mediaId) {
        final SQLiteDatabase database = databaseOpenHelper.getWritableDatabase();

        if (database != null) {
            database.execSQL(
                    "UPDATE " + Media.TABLE_NAME +
                            "   SET " + Media.COLUMN_FIELD_USER_HIDDEN + " = ~" + Media.COLUMN_FIELD_USER_HIDDEN + " " +
                            "WHERE " + Media.COLUMN_FIELD_ID + " = " + mediaId
            );

            doNotifyLibraryChanges();
            return true;
        }

        return false;
    }

    protected boolean doTogglePlaylistVisibility(String playlistId) {
        final SQLiteDatabase database = databaseOpenHelper.getWritableDatabase();

        if (database != null) {
            database.execSQL(
                    "UPDATE " + Playlist.TABLE_NAME +
                            "   SET " + Playlist.COLUMN_FIELD_USER_HIDDEN + " = ~" + Playlist.COLUMN_FIELD_USER_HIDDEN + " " +
                            "WHERE " + Playlist.COLUMN_FIELD_PLAYLIST_ID + " = " + playlistId
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
                    columnsList.add(AlbumArtist.COLUMN_FIELD_ARTIST_ID);
                    break;
                case ALBUM_ARTIST_NAME:
                    columnsList.add(AlbumArtist.COLUMN_FIELD_ARTIST_NAME);
                    break;
                case ALBUM_ARTIST_VISIBLE:
                    columnsList.add("(" + AlbumArtist.COLUMN_FIELD_USER_HIDDEN + "= 0)");
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
                    orderBy = orderBy + AlbumArtist.COLUMN_FIELD_ARTIST_NAME + " COLLATE NOCASE ASC";
                    break;
                case -ALBUM_ARTIST_NAME:
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + AlbumArtist.COLUMN_FIELD_ARTIST_NAME + " COLLATE NOCASE DESC";
                    break;
            }
        }

        if (TextUtils.isEmpty(orderBy)) {
            orderBy = AlbumArtist.COLUMN_FIELD_ARTIST_NAME + " COLLATE NOCASE ASC";
        }

        String where = MusicConnector.show_hidden ? null : "(" + AlbumArtist.COLUMN_FIELD_USER_HIDDEN + " = 0) ";

        if (!TextUtils.isEmpty(filter)) {
            if (where != null) {
                where = where + " AND ";
            }
            else {
                where = "";
            }

            where = where + "(" + AlbumArtist.COLUMN_FIELD_ARTIST_NAME + " LIKE '%" + filter + "%')";
        }

        // query.
        final SQLiteDatabase database = databaseOpenHelper.getReadableDatabase();
        if (database != null) {
            return database.query(AlbumArtist.TABLE_NAME, columns, where, null, null, null, orderBy);
        }
        return null;
    }

    protected Cursor doBuildAlbumCursor(final int[] requestedFields, final int[] sortFields, String filter, final ContentType source, final String sourceId) {
        final ArrayList<String> columnsList = new ArrayList<String>();

        for (int field : requestedFields) {
            switch (field) {
                case AbstractMediaProvider.ALBUM_ID:
                    columnsList.add(Album.TABLE_NAME + "." + Album.COLUMN_FIELD_ALBUM_ID);
                    break;
                case AbstractMediaProvider.ALBUM_NAME:
                    columnsList.add(Album.TABLE_NAME + "." + Album.COLUMN_FIELD_ALBUM_NAME);
                    break;
                case AbstractMediaProvider.ALBUM_ARTIST:
                    columnsList.add(Album.TABLE_NAME + "." + Album.COLUMN_FIELD_ALBUM_ARTIST);
                    break;
                case AbstractMediaProvider.ALBUM_VISIBLE:
                    columnsList.add("(" + Album.TABLE_NAME + "." + Album.COLUMN_FIELD_USER_HIDDEN + "= 0)");
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
                    orderBy = orderBy + Album.COLUMN_FIELD_ALBUM_NAME + " COLLATE NOCASE ASC";
                    break;
                case -ALBUM_NAME:
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Album.COLUMN_FIELD_ALBUM_NAME + " COLLATE NOCASE DESC";
                    break;
                case ALBUM_ARTIST:
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Album.COLUMN_FIELD_ALBUM_ARTIST + " COLLATE NOCASE ASC";
                    break;
                case -ALBUM_ARTIST:
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Album.COLUMN_FIELD_ALBUM_ARTIST + " COLLATE NOCASE DESC";
                    break;
            }
        }

        if (TextUtils.isEmpty(orderBy)) {
            orderBy = Album.COLUMN_FIELD_ALBUM_NAME + " COLLATE NOCASE ASC";
        }

        // setting details arguments
        String selection = MusicConnector.show_hidden ? "" : "(" + Album.TABLE_NAME + "." + Album.COLUMN_FIELD_USER_HIDDEN + " = 0) ";
        String[] selectionArgs = null;
        String groupBy = null;

        String tableDescription = Album.TABLE_NAME;

        String localSourceId = sourceId;
        if (localSourceId == null) {
            localSourceId = "0"; // null playlist is default playlist. (e.g. id = 0)
        }
        switch (source) {
            case CONTENT_TYPE_ALBUM_ARTIST:
                if (!TextUtils.isEmpty(selection)) {
                    selection = selection + " AND ";
                }
                selection = selection + Album.COLUMN_FIELD_ALBUM_ARTIST_ID + " = ? ";
                selectionArgs = new String[] {
                        localSourceId
                };
                break;
            case CONTENT_TYPE_GENRE:
                tableDescription =
                        Album.TABLE_NAME + " JOIN " + Media.TABLE_NAME +
                                " ON " + Media.TABLE_NAME + "." + Media.COLUMN_FIELD_ALBUM_ID + " = " + Album.TABLE_NAME + "." + Album.COLUMN_FIELD_ALBUM_ID;

                if (!TextUtils.isEmpty(selection)) {
                    selection = selection + " AND ";
                }

                selection = selection + Media.TABLE_NAME + "." + Media.COLUMN_FIELD_GENRE_ID + " = ? ";
                selectionArgs = new String[] {
                        localSourceId
                };
                groupBy = Media.TABLE_NAME + "." + Media.COLUMN_FIELD_ALBUM_ID;
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
                    "(" + Album.TABLE_NAME + "." + Album.COLUMN_FIELD_ALBUM_ARTIST + " LIKE '%" + filter + "%') OR " +
                    "(" + Album.TABLE_NAME + "." + Album.COLUMN_FIELD_ALBUM_NAME + " LIKE '%" + filter + "%') " + ")";
        }

        // query.
        final SQLiteDatabase database = databaseOpenHelper.getReadableDatabase();
        if (database != null) {
            return database.query(tableDescription, columns, selection, selectionArgs, groupBy, null, orderBy);
        }
        return null;
    }

    protected Cursor doBuildArtistCursor(final int[] requestedFields, final int[] sortFields, String filter) {
        final ArrayList<String> columnsList = new ArrayList<String>();

        for (int field : requestedFields) {
            switch (field) {
                case AbstractMediaProvider.ARTIST_ID:
                    columnsList.add(Artist.COLUMN_FIELD_ARTIST_ID);
                    break;
                case AbstractMediaProvider.ARTIST_NAME:
                    columnsList.add(Artist.COLUMN_FIELD_ARTIST_NAME);
                    break;
                case AbstractMediaProvider.ARTIST_VISIBLE:
                    columnsList.add("(" + Artist.COLUMN_FIELD_USER_HIDDEN + "= 0)");
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
                    orderBy = orderBy + Artist.COLUMN_FIELD_ARTIST_NAME + " COLLATE NOCASE ASC";
                    break;
                case -ARTIST_NAME:
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Artist.COLUMN_FIELD_ARTIST_NAME + " COLLATE NOCASE DESC";
                    break;
            }
        }

        if (TextUtils.isEmpty(orderBy)) {
            orderBy = Artist.COLUMN_FIELD_ARTIST_NAME + " COLLATE NOCASE ASC";
        }

        String selection = MusicConnector.show_hidden ? null : AlbumArtist.COLUMN_FIELD_USER_HIDDEN + " = 0 ";

        if (!TextUtils.isEmpty(filter)) {
            if (selection != null) {
                selection = selection + " AND ";
            }
            else {
                selection = "";
            }

            selection = selection + "(" +
                    "(" + Artist.COLUMN_FIELD_ARTIST_NAME + " LIKE '%" + filter + "%') " + ")";
        }

        // query.
        final SQLiteDatabase database = databaseOpenHelper.getReadableDatabase();
        if (database != null) {
            return database.query(Artist.TABLE_NAME, columns, selection, null, null, null, orderBy);
        }
        return null;
    }

    protected Cursor doBuildGenreCursor(final int[] requestedFields, final int[] sortFields, String filter) {
        final ArrayList<String> columnsList = new ArrayList<String>();

        for (int field : requestedFields) {
            switch (field) {
                case AbstractMediaProvider.GENRE_ID:
                    columnsList.add(Genre.COLUMN_FIELD_GENRE_ID);
                    break;
                case AbstractMediaProvider.GENRE_NAME:
                    columnsList.add(Genre.COLUMN_FIELD_GENRE_NAME);
                    break;
                case AbstractMediaProvider.GENRE_VISIBLE:
                    columnsList.add("(" + Genre.COLUMN_FIELD_USER_HIDDEN + "= 0)");
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
                    orderBy = orderBy + Genre.COLUMN_FIELD_GENRE_NAME + " COLLATE NOCASE ASC";
                    break;
                case -GENRE_NAME:
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Genre.COLUMN_FIELD_GENRE_NAME + " COLLATE NOCASE DESC";
                    break;
            }
        }

        if (TextUtils.isEmpty(orderBy)) {
            orderBy = Genre.COLUMN_FIELD_GENRE_NAME + " COLLATE NOCASE ASC";
        }

        String selection = MusicConnector.show_hidden ? null : Genre.COLUMN_FIELD_USER_HIDDEN + " = 0 ";

        if (!TextUtils.isEmpty(filter)) {
            if (selection != null) {
                selection = selection + " AND ";
            }
            else {
                selection = "";
            }

            selection = selection + "(" +
                    "(" + Genre.COLUMN_FIELD_GENRE_NAME + " LIKE '%" + filter + "%') " + ")";
        }

        // query.
        final SQLiteDatabase database = databaseOpenHelper.getReadableDatabase();
        if (database != null) {
            return database.query(Genre.TABLE_NAME, columns, selection, null, null, null, orderBy);
        }
        return null;
    }

    protected Cursor doBuildPlaylistCursor(int[] requestedFields, int[] sortFields, String filter) {

        final ArrayList<String> columnsList = new ArrayList<String>();

        for (int field : requestedFields) {
            switch (field) {
                case AbstractMediaProvider.PLAYLIST_ID:
                    columnsList.add(Playlist.COLUMN_FIELD_PLAYLIST_ID);
                    break;
                case AbstractMediaProvider.PLAYLIST_NAME:
                    columnsList.add(Playlist.COLUMN_FIELD_PLAYLIST_NAME);
                    break;
                case AbstractMediaProvider.PLAYLIST_VISIBLE:
                    columnsList.add("(" + Playlist.COLUMN_FIELD_VISIBLE + "<> 0) AND (" + Playlist.COLUMN_FIELD_USER_HIDDEN + "= 0)");
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
                    orderBy = orderBy + Playlist.COLUMN_FIELD_PLAYLIST_NAME + " COLLATE NOCASE ASC";
                    break;
                case -PLAYLIST_NAME:
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Playlist.COLUMN_FIELD_PLAYLIST_NAME + " COLLATE NOCASE DESC";
                    break;
            }
        }

        if (TextUtils.isEmpty(orderBy)) {
            orderBy = Playlist.COLUMN_FIELD_PLAYLIST_NAME + " COLLATE NOCASE ASC";
        }

        String selection = MusicConnector.show_hidden ?
                "(" + Playlist.COLUMN_FIELD_VISIBLE + " <> 0) AND (" + Playlist.COLUMN_FIELD_PLAYLIST_ID + " <> 0)" :
                "(" + Playlist.COLUMN_FIELD_USER_HIDDEN + " = 0) AND " +
                        "(" + Playlist.COLUMN_FIELD_VISIBLE + " <> 0) AND (" + Playlist.COLUMN_FIELD_PLAYLIST_ID + " <> 0)";

        if (!TextUtils.isEmpty(filter)) {
            selection = selection + " AND (" +
                    "(" + Playlist.COLUMN_FIELD_PLAYLIST_NAME + " LIKE '%" + filter + "%') " + ")";
        }

        // query.
        final SQLiteDatabase database = databaseOpenHelper.getReadableDatabase();
        if (database != null) {
            return database.query(Playlist.TABLE_NAME, columns, selection, null, null, null, orderBy);
        }
        return null;
    }

    protected Cursor doBuildMediaCursor(int[] requestedFields, int[] sortFields, String filter, ContentType contentType, String sourceId) {
        boolean usesSongTable = false;
        boolean usesPlaylistEntryTable = false;

        final ArrayList<String> columnsList = new ArrayList<String>();

        for (int field : requestedFields) {
            switch (field) {
                case AbstractMediaProvider.SONG_ID:
                    usesSongTable = true;
                    columnsList.add(Media.TABLE_NAME + "." + Media.COLUMN_FIELD_ID);
                    break;
                case AbstractMediaProvider.SONG_URI:
                    usesSongTable = true;
                    columnsList.add(Media.TABLE_NAME + "." + Media.COLUMN_FIELD_URI);
                    break;
                case AbstractMediaProvider.SONG_ART:
                    usesSongTable = true;
                    columnsList.add(Media.TABLE_NAME + "." + Media.COLUMN_FIELD_ART);
                    break;
                case AbstractMediaProvider.SONG_DURATION:
                    usesSongTable = true;
                    columnsList.add(Media.TABLE_NAME + "." + Media.COLUMN_FIELD_DURATION);
                    break;
                case AbstractMediaProvider.SONG_BITRATE:
                    usesSongTable = true;
                    columnsList.add(Media.TABLE_NAME + "." + Media.COLUMN_FIELD_BITRATE);
                    break;
                case AbstractMediaProvider.SONG_SAMPLE_RATE:
                    usesSongTable = true;
                    columnsList.add(Media.TABLE_NAME + "." + Media.COLUMN_FIELD_SAMPLE_RATE);
                    break;
                case AbstractMediaProvider.SONG_CODEC:
                    usesSongTable = true;
                    columnsList.add(Media.TABLE_NAME + "." + Media.COLUMN_FIELD_CODEC);
                    break;
                case AbstractMediaProvider.SONG_SCORE:
                    usesSongTable = true;
                    columnsList.add(Media.TABLE_NAME + "." + Media.COLUMN_FIELD_SCORE);
                    break;
                case AbstractMediaProvider.SONG_FIRST_PLAYED:
                    usesSongTable = true;
                    columnsList.add(Media.TABLE_NAME + "." + Media.COLUMN_FIELD_FIRST_PLAYED);
                    break;
                case AbstractMediaProvider.SONG_LAST_PLAYED:
                    usesSongTable = true;
                    columnsList.add(Media.TABLE_NAME + "." + Media.COLUMN_FIELD_LAST_PLAYED);
                    break;
                case AbstractMediaProvider.SONG_TITLE:
                    usesSongTable = true;
                    columnsList.add(Media.TABLE_NAME + "." + Media.COLUMN_FIELD_TITLE);
                    break;
                case AbstractMediaProvider.SONG_ARTIST:
                    usesSongTable = true;
                    columnsList.add(Media.TABLE_NAME + "." + Media.COLUMN_FIELD_ARTIST);
                    break;
                case AbstractMediaProvider.SONG_ARTIST_ID:
                    usesSongTable = true;
                    columnsList.add(Media.TABLE_NAME + "." + Media.COLUMN_FIELD_ARTIST_ID);
                    break;
                case AbstractMediaProvider.SONG_ALBUM_ARTIST:
                    usesSongTable = true;
                    columnsList.add(Media.TABLE_NAME + "." + Media.COLUMN_FIELD_ALBUM_ARTIST);
                    break;
                case AbstractMediaProvider.SONG_ALBUM_ARTIST_ID:
                    usesSongTable = true;
                    columnsList.add(Media.TABLE_NAME + "." + Media.COLUMN_FIELD_ALBUM_ARTIST_ID);
                    break;
                case AbstractMediaProvider.SONG_ALBUM:
                    usesSongTable = true;
                    columnsList.add(Media.TABLE_NAME + "." + Media.COLUMN_FIELD_ALBUM);
                    break;
                case AbstractMediaProvider.SONG_ALBUM_ID:
                    usesSongTable = true;
                    columnsList.add(Media.TABLE_NAME + "." + Media.COLUMN_FIELD_ALBUM_ID);
                    break;
                case AbstractMediaProvider.SONG_GENRE:
                    usesSongTable = true;
                    columnsList.add(Media.TABLE_NAME + "." + Media.COLUMN_FIELD_GENRE);
                    break;
                case AbstractMediaProvider.SONG_GENRE_ID:
                    usesSongTable = true;
                    columnsList.add(Media.TABLE_NAME + "." + Media.COLUMN_FIELD_GENRE_ID);
                    break;
                case AbstractMediaProvider.SONG_YEAR:
                    usesSongTable = true;
                    columnsList.add(Media.TABLE_NAME + "." + Media.COLUMN_FIELD_YEAR);
                    break;
                case AbstractMediaProvider.SONG_TRACK:
                    usesSongTable = true;
                    columnsList.add(Media.TABLE_NAME + "." + Media.COLUMN_FIELD_TRACK);
                    break;
                case AbstractMediaProvider.SONG_DISC:
                    usesSongTable = true;
                    columnsList.add(Media.TABLE_NAME + "." + Media.COLUMN_FIELD_DISC);
                    break;
                case AbstractMediaProvider.SONG_BPM:
                    usesSongTable = true;
                    columnsList.add(Media.TABLE_NAME + "." + Media.COLUMN_FIELD_BPM);
                    break;
                case AbstractMediaProvider.SONG_COMMENT:
                    usesSongTable = true;
                    columnsList.add(Media.TABLE_NAME + "." + Media.COLUMN_FIELD_COMMENT);
                    break;
                case AbstractMediaProvider.SONG_LYRICS:
                    usesSongTable = true;
                    columnsList.add(Media.TABLE_NAME + "." + Media.COLUMN_FIELD_LYRICS);
                    break;
                case AbstractMediaProvider.SONG_VISIBLE:
                    usesSongTable = true;
                    columnsList.add(
                            "(" + Media.TABLE_NAME + "." + Media.COLUMN_FIELD_USER_HIDDEN + "= 0) AND (" + Media.TABLE_NAME + "." + Media.COLUMN_FIELD_VISIBLE + " <> 0)"
                    );
                    break;
//                case AbstractMediaProvider.SONG_HAS_EMBEDDED_ART:
                case AbstractMediaProvider.PLAYLIST_ENTRY_ID:
                    usesPlaylistEntryTable = true;
                    columnsList.add(PlaylistEntry.TABLE_NAME + "." + PlaylistEntry.COLUMN_FIELD_ENTRY_ID);
                    break;
                case AbstractMediaProvider.PLAYLIST_ENTRY_POSITION:
                    usesPlaylistEntryTable = true;
                    columnsList.add(PlaylistEntry.TABLE_NAME + "." + PlaylistEntry.COLUMN_FIELD_POSITION);
                    break;
            }
        }

        final String[] columns = columnsList.toArray(new String[columnsList.size()]);// sort order
        String orderBy = "";
        for (int field : sortFields) {
            switch (field) {
                case SONG_URI:
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Media.TABLE_NAME + "." + Media.COLUMN_FIELD_URI + " COLLATE NOCASE ASC";
                    break;
                case -SONG_URI:
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Media.TABLE_NAME + "." + Media.COLUMN_FIELD_URI + " COLLATE NOCASE DESC";
                    break;
                case SONG_FIRST_PLAYED:
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Media.TABLE_NAME + "." + Media.COLUMN_FIELD_FIRST_PLAYED + " COLLATE NOCASE ASC";
                    break;
                case -SONG_FIRST_PLAYED:
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Media.TABLE_NAME + "." + Media.COLUMN_FIELD_FIRST_PLAYED + " COLLATE NOCASE DESC";
                    break;
                case SONG_LAST_PLAYED:
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Media.TABLE_NAME + "." + Media.COLUMN_FIELD_LAST_PLAYED + " COLLATE NOCASE ASC";
                    break;
                case -SONG_LAST_PLAYED:
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Media.TABLE_NAME + "." + Media.COLUMN_FIELD_LAST_PLAYED + " COLLATE NOCASE DESC";
                    break;
                case SONG_TITLE:
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Media.TABLE_NAME + "." + Media.COLUMN_FIELD_TITLE + " COLLATE NOCASE ASC";
                    break;
                case -SONG_TITLE:
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Media.TABLE_NAME + "." + Media.COLUMN_FIELD_TITLE + " COLLATE NOCASE DESC";
                    break;
                case SONG_ARTIST:
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Media.TABLE_NAME + "." + Media.COLUMN_FIELD_ARTIST + " COLLATE NOCASE ASC";
                    break;
                case -SONG_ARTIST:
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Media.TABLE_NAME + "." + Media.COLUMN_FIELD_ARTIST + " COLLATE NOCASE DESC";
                    break;
                case SONG_ALBUM_ARTIST:
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Media.TABLE_NAME + "." + Media.COLUMN_FIELD_ALBUM_ARTIST + " COLLATE NOCASE ASC";
                    break;
                case -SONG_ALBUM_ARTIST:
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Media.TABLE_NAME + "." + Media.COLUMN_FIELD_ALBUM_ARTIST + " COLLATE NOCASE DESC";
                    break;
                case SONG_ALBUM:
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Media.TABLE_NAME + "." + Media.COLUMN_FIELD_ALBUM + " COLLATE NOCASE ASC";
                    break;
                case -SONG_ALBUM:
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Media.TABLE_NAME + "." + Media.COLUMN_FIELD_ALBUM + " COLLATE NOCASE DESC";
                    break;
                case SONG_TRACK:
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Media.TABLE_NAME + "." + Media.COLUMN_FIELD_TRACK + " COLLATE NOCASE ASC";
                    break;
                case -SONG_TRACK:
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Media.TABLE_NAME + "." + Media.COLUMN_FIELD_TRACK + " COLLATE NOCASE DESC";
                    break;
                case SONG_DISC:
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Media.TABLE_NAME + "." + Media.COLUMN_FIELD_DISC + " COLLATE NOCASE ASC";
                    break;
                case -SONG_DISC:
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + Media.TABLE_NAME + "." + Media.COLUMN_FIELD_DISC + " COLLATE NOCASE DESC";
                    break;
                case PLAYLIST_ENTRY_POSITION:
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + PlaylistEntry.TABLE_NAME + "." + PlaylistEntry.COLUMN_FIELD_POSITION + " COLLATE NOCASE ASC";
                    break;
                case -PLAYLIST_ENTRY_POSITION:
                    if (!TextUtils.isEmpty(orderBy)) {
                        orderBy = orderBy + ", ";
                    }
                    orderBy = orderBy + PlaylistEntry.TABLE_NAME + "." + PlaylistEntry.COLUMN_FIELD_POSITION + " COLLATE NOCASE DESC";
                    break;
            }
        }

        if (TextUtils.isEmpty(orderBy)) {
            orderBy = Media.COLUMN_FIELD_TITLE + " COLLATE NOCASE ASC";
        }

        String showFiles = usesPlaylistEntryTable ? " OR (" + Media.COLUMN_FIELD_IS_QUEUE_FILE_ENTRY + " <> 0)" : "";

        String selection = MusicConnector.show_hidden ?
                "((" + Media.COLUMN_FIELD_VISIBLE + " <> 0) " + showFiles + ")" :
                "(((" + Media.COLUMN_FIELD_VISIBLE + " <> 0) AND (" + Media.COLUMN_FIELD_USER_HIDDEN + " = 0))" +
                        showFiles + ")";
        String[] selectionArgs = null;

        if (sourceId == null) {
            sourceId = "0"; // null playlist is default playlist. (e.g. id = 0)
        }

        switch (contentType) {
            case CONTENT_TYPE_MEDIA:
                if (!TextUtils.isEmpty(selection)) {
                    selection = selection + " AND ";
                }
                selection = selection + "(" + Media.COLUMN_FIELD_ID + " = ?) ";
                selectionArgs = new String[] {
                        sourceId
                };
                break;
            case CONTENT_TYPE_ARTIST:
                if (!TextUtils.isEmpty(selection)) {
                    selection = selection + " AND ";
                }
                selection = selection + "(" + Media.COLUMN_FIELD_ARTIST_ID + " = ?) ";
                selectionArgs = new String[] {
                        sourceId
                };
                break;
            case CONTENT_TYPE_ALBUM:
                if (!TextUtils.isEmpty(selection)) {
                    selection = selection + " AND ";
                }
                selection = selection + "(" + Media.COLUMN_FIELD_ALBUM_ID + " = ?) ";
                selectionArgs = new String[] {
                        sourceId
                };
                break;
            case CONTENT_TYPE_PLAYLIST:
                if (!TextUtils.isEmpty(selection)) {
                    selection = selection + " AND ";
                }

                usesPlaylistEntryTable = true;
                selection = selection + "(" + PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = ?) ";
                selectionArgs = new String[] {
                        sourceId
                };
                break;
            case CONTENT_TYPE_ALBUM_ARTIST:
                if (!TextUtils.isEmpty(selection)) {
                    selection = selection + " AND ";
                }
                selection = selection + "(" + Media.COLUMN_FIELD_ALBUM_ARTIST_ID + " = ?) ";
                selectionArgs = new String[] {
                        sourceId
                };
                break;
            case CONTENT_TYPE_GENRE:
                if (!TextUtils.isEmpty(selection)) {
                    selection = selection + " AND ";
                }
                selection = selection + "(" + Media.COLUMN_FIELD_GENRE_ID + " = ?) ";
                selectionArgs = new String[] {
                        sourceId
                };
                break;
        }

        if (!TextUtils.isEmpty(filter)) {
            selection = selection + "AND (" +
                    "(" + Media.COLUMN_FIELD_ARTIST + " LIKE '%" + filter + "%') OR " +
                    "(" + Media.COLUMN_FIELD_ALBUM + " LIKE '%" + filter + "%') OR " +
                    "(" + Media.COLUMN_FIELD_ALBUM_ARTIST + " LIKE '%" + filter + "%') OR " +
                    "(" + Media.COLUMN_FIELD_GENRE + " LIKE '%" + filter + "%') OR " +
                    "(" + Media.COLUMN_FIELD_TITLE + " LIKE '%" + filter + "%') " + ")";
        }

        // query.
        String tableDescription = Media.TABLE_NAME;
        if (usesPlaylistEntryTable) {
            if (usesSongTable) {
                tableDescription =

                        PlaylistEntry.TABLE_NAME + " JOIN " + Media.TABLE_NAME +
                                " ON " + Media.TABLE_NAME + "." + Media.COLUMN_FIELD_ID + " = " +
                                PlaylistEntry.TABLE_NAME + "." + PlaylistEntry.COLUMN_FIELD_SONG_ID;
            }
            else {
                tableDescription = PlaylistEntry.TABLE_NAME;
            }
        }

        final SQLiteDatabase database = databaseOpenHelper.getReadableDatabase();
        if (database != null) {
            return database.query(tableDescription, columns, selection, selectionArgs, null, null, orderBy);
        }
        return null;
    }

    protected Cursor doBuildStorageCursor(int[] requestedFields, int[] sortFields, String filter) {
        final String[] columns = new String[requestedFields.length + 1];
        final Object[] currentRow = new Object[requestedFields.length + 1];

        int sort = STORAGE_DISPLAY_NAME;
        if (sortFields != null && sortFields.length >= 1) {
            sort = sortFields[0];
        }

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
                    case SONG_ART:
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

        fileList = getStorageFileList(scanContext, sort, filter);

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
                        case SONG_ART:
                            currentRow[columnIndex] = ProviderStreamImageDownloader.SCHEME_URI_PREFIX +
                                    ProviderStreamImageDownloader.SUBTYPE_STORAGE + "/" +
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
                        case SONG_ART:
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
        final SQLiteDatabase database = databaseOpenHelper.getReadableDatabase();
        if (database == null) {
            return null;
        }

        final String[] columns = new String[] {
                Media.COLUMN_FIELD_URI,
                Media.COLUMN_FIELD_ART,
                Media.COLUMN_FIELD_HAS_EMBEDDED_ART,
                Media.COLUMN_FIELD_USE_EMBEDDED_ART
        };

        final String selection = Media.COLUMN_FIELD_ID + " = ? ";

        final String[] selectionArgs = new String[] {
                songId
        };

        final int COLUMN_URI = 0;
        final int COLUMN_ART = 1;
        final int COLUMN_HAS_EMBEDDED_ART = 2;
        final int COLUMN_USE_EMBEDDED_ART = 3;

        boolean usesEmbeddedArt = false;


        String songArtUri = null;
        Cursor cursor = database.query(Media.TABLE_NAME, columns, selection, selectionArgs, null, null, null);
        if (cursor != null && cursor.getCount() > 0) {
            cursor.moveToFirst();

            if (cursor.getInt(COLUMN_USE_EMBEDDED_ART) != 0) {
                if (cursor.getInt(COLUMN_HAS_EMBEDDED_ART) != 0) {
                    songArtUri = cursor.getString(COLUMN_URI);
                    usesEmbeddedArt = true;
                }
            }
            else {
                songArtUri = cursor.getString(COLUMN_ART);
            }
        }

        if (cursor != null) {
            cursor.close();
        }

        if (songArtUri != null) {
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
        final SQLiteDatabase database = databaseOpenHelper.getReadableDatabase();
        if (database == null) {
            return null;
        }

        final String[] columns = new String[] { Album.COLUMN_FIELD_ALBUM_ART };
        final String selection = Album.COLUMN_FIELD_ALBUM_ID + " = ? ";
        final String[] selectionArgs = new String[] { albumId };

        String albumArtUri = null;
        Cursor cursor = database.query(Album.TABLE_NAME, columns, selection, selectionArgs, null, null, null);
        if (cursor != null && cursor.getCount() > 0) {
            cursor.moveToFirst();
            albumArtUri = cursor.getString(0);
        }

        if (cursor != null) {
            cursor.close();
        }

        try {
            if (!TextUtils.isEmpty(albumArtUri)) {
                return PlayerApplication.context.getContentResolver().openInputStream(Uri.parse(albumArtUri));
            }
            return null;
        }
        catch (final FileNotFoundException fileNotFoundException) {
            return null;
        }
    }

    protected void doUpdateAlbumCover(String albumId, String uri, boolean updateTracks) {
        SQLiteDatabase database = databaseOpenHelper.getWritableDatabase();

        if (database != null) {
            final String whereAlbumId[] = new String[] {
                    albumId
            };

            ContentValues contentValues = new ContentValues();
            contentValues.put(Album.COLUMN_FIELD_ALBUM_ART, uri);

            database.update(Album.TABLE_NAME, contentValues, Album.COLUMN_FIELD_ALBUM_ID + " = ? ", whereAlbumId);


            if (updateTracks) {
                contentValues.clear();
                contentValues.put(Media.COLUMN_FIELD_ART, uri);
                contentValues.put(Media.COLUMN_FIELD_USE_EMBEDDED_ART, false);

                database.update(Media.TABLE_NAME, contentValues, Media.COLUMN_FIELD_ALBUM_ID + " = ? ", whereAlbumId);
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
        SQLiteDatabase database = databaseOpenHelper.getReadableDatabase();

        if (database == null) {
            return true; // error, will be added in next scan.
        }

        Cursor cursor = database.query(
                Media.TABLE_NAME,
                new String[] { Media.COLUMN_FIELD_ID },
                Media.COLUMN_FIELD_URI + " = ? ",
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
        scanContext.scannerDatabaseHandle = databaseOpenHelper.getWritableDatabase();

        if (scanContext.scannerDatabaseHandle != null) {
            /*
                Firstly, checking for deleted files.
             */
            final String[] projection = new String[] {
                    Media.COLUMN_FIELD_ID,
                    Media.COLUMN_FIELD_URI
            };

            Cursor cursor = scanContext.scannerDatabaseHandle.query(Media.TABLE_NAME, projection, null, null, null, null, null);
            if (cursor != null) {
                int indexOfId = cursor.getColumnIndexOrThrow(Media.COLUMN_FIELD_ID);
                int indexOfFilePath = cursor.getColumnIndexOrThrow(Media.COLUMN_FIELD_URI);

                while (cursor.moveToNext()) {
                    //Log.i(TAG, "scan : " + PlayerApplication.uriToFile(cursor.getString(indexOfFilePath)));
                    if (!PlayerApplication.uriToFile(cursor.getString(indexOfFilePath)).exists()) {
                        final String where = Media.COLUMN_FIELD_ID + " = ? ";
                        final String[] selectionArgs = new String[] {
                                String.valueOf(cursor.getInt(indexOfId))
                        };

                        Log.i(TAG, "!Media : " + cursor.getString(indexOfFilePath));
                        scanContext.scannerDatabaseHandle.delete(Media.TABLE_NAME, where, selectionArgs);
                    }
                }
                cursor.close();
            }

            /*
                Next, we make a list of forbidden paths.
             */
            final String pathProjection[] = new String[] {
                    ScanDirectory.COLUMN_FIELD_SCAN_DIRECTORY_NAME
            };

            final String selection = ScanDirectory.COLUMN_FIELD_SCAN_DIRECTORY_IS_EXCLUDED + " = ? ";

            final String selectionAccept[] = new String[] {
                    "0"
            };

            final String selectionDiscard[] = new String[] {
                    "1"
            };

            ArrayList<File> acceptList = new ArrayList<File>();

            SQLiteDatabase database = databaseOpenHelper.getReadableDatabase();

            if (database != null) {
                Cursor acceptCursor = database.query(ScanDirectory.TABLE_NAME, pathProjection, selection, selectionAccept, null, null, null);
                Cursor discardCursor = database.query(ScanDirectory.TABLE_NAME, pathProjection, selection, selectionDiscard, null, null, null);

                Map<String, Boolean> discardMap = new HashMap<String, Boolean>();
                if (discardCursor != null && discardCursor.getCount() > 0) {
                    while (discardCursor.moveToNext()) {
                        discardMap.put(discardCursor.getString(0), true);
                    }
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

                if (acceptCursor != null) {
                    acceptCursor.close();
                }

                if (discardCursor != null) {
                    discardCursor.close();
                }
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
            Cursor artistCursor = scanContext.scannerDatabaseHandle.query(Artist.TABLE_NAME, new String[] {Artist.COLUMN_FIELD_ARTIST_ID}, Artist.COLUMN_FIELD_ARTIST_NAME + " = ?", new String[] { artist }, null, null, null);

            if (artistCursor != null && artistCursor.getCount() > 0) {
                artistCursor.moveToPosition(0);
                id = artistCursor.getLong(0);
                scanContext.artistIdMap.put(artist, id);
            }
            else {
                ContentValues artistValues = new ContentValues();
                artistValues.put(Artist.COLUMN_FIELD_ARTIST_NAME, artist);
                artistValues.put(Artist.COLUMN_FIELD_USER_HIDDEN, false);
                artistValues.put(Artist.COLUMN_FIELD_VISIBLE, true);
                id = scanContext.scannerDatabaseHandle.insert(Artist.TABLE_NAME, null, artistValues);
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
            Cursor albumCursor = scanContext.scannerDatabaseHandle.query(Album.TABLE_NAME, new String[]{Album.COLUMN_FIELD_ALBUM_ID}, Album.COLUMN_FIELD_ALBUM_NAME + " = ?", new String[]{album}, null, null, null);

            if (albumCursor != null && albumCursor.getCount() > 0) {
                albumCursor.moveToPosition(0);
                id = albumCursor.getLong(0);
                scanContext.albumIdMap.put(album, id);
            }
            else {
                ContentValues albumValues = new ContentValues();
                albumValues.put(Album.COLUMN_FIELD_ALBUM_NAME, album);
                albumValues.put(Album.COLUMN_FIELD_USER_HIDDEN, false);
                id = scanContext.scannerDatabaseHandle.insert(Album.TABLE_NAME, null, albumValues);
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
            Cursor genreCursor = scanContext.scannerDatabaseHandle.query(Genre.TABLE_NAME, new String[] {Genre.COLUMN_FIELD_GENRE_ID}, Genre.COLUMN_FIELD_GENRE_NAME + " = ?", new String[] { genre }, null, null, null);

            if (genreCursor != null && genreCursor.getCount() > 0) {
                genreCursor.moveToPosition(0);
                id = genreCursor.getLong(0);
                scanContext.genreIdMap.put(genre, id);
            }
            else {
                ContentValues genreValues = new ContentValues();
                genreValues.put(Genre.COLUMN_FIELD_GENRE_NAME, genre);
                genreValues.put(Genre.COLUMN_FIELD_USER_HIDDEN, false);
                genreValues.put(Genre.COLUMN_FIELD_VISIBLE, true);
                id = scanContext.scannerDatabaseHandle.insert(Genre.TABLE_NAME, null, genreValues);
                scanContext.genreIdMap.put(genre, id);
            }

            if (genreCursor != null) {
                genreCursor.close();
            }
        }

        return id;
    }

    protected String getCoverForFile(File file, SyncScanContext scanContext) {
        if (file == null) {
            return null;
        }

        String coverUri = scanContext.coverMap.get(file.getName()); // firstly, checking if coverUri is already cached.
        if (!TextUtils.isEmpty(coverUri)) {
            return coverUri;
        }

        List<File> fileList = new ArrayList<File>();
        fileList.add(file);

        for (int pathIndex = 0 ; pathIndex < fileList.size() ; ) {
            if (cancelingScan) {
                break;
            }

            File currentFile = fileList.get(pathIndex);
            if (currentFile.isDirectory()) {
                Log.w(TAG, "entering: " + currentFile.getName());
                fileList.remove(pathIndex);

                // directory content is not empty
                File directoryContent[] = currentFile.listFiles();
                if(directoryContent != null) {
                    // add directory content to scanlist
                    for(File subFile : directoryContent) {
                        fileList.add(pathIndex, subFile);
                    }
                }
            }
            else {
                if (currentFile.length() == 0) {
                    fileList.remove(pathIndex);
                    continue;
                }

                if (isArtFile(scanContext, currentFile)) {
                    final String artUri = PlayerApplication.fileToUri(currentFile);
                    coverUri = scanContext.coverMap.get(file.getName());
                    // never found a media for this art, updating cover map only
                    if (coverUri == null) {
                        Log.w(TAG, "Updated covers uri: " + file.getName() + " -> " + artUri);
                        scanContext.coverMap.put(file.getName(), artUri);
                        return artUri; // done.
                    }
                    // found a media for this art, updating cover map AND database
                    else if (coverUri.equals("")) {
                        scanContext.coverMap.put(file.getName(), artUri);

                        scanContext.mediaCover.put(Media.COLUMN_FIELD_ART, artUri);
                        int rows = scanContext.scannerDatabaseHandle.update(
                                Media.TABLE_NAME,
                                scanContext.mediaCover,
                                Media.COLUMN_FIELD_URI + " LIKE ?",
                                new String[]{file.getName() + File.separator + "%"});
                        Log.w(TAG, "Updated covers: " + rows + " rows (" + file.getName() + File.separator + "%" + ") -> " + artUri);
                        return artUri; // done.
                    }
                }
                fileList.remove(pathIndex);
            }
        }
        return null;
    }

    protected void updateAlbumArtists(SyncScanContext scanContext) {
        scanContext.scannerDatabaseHandle.execSQL(
                "UPDATE " + Album.TABLE_NAME + " SET " +
                        Album.COLUMN_FIELD_ALBUM_ARTIST + " = ( " +
                        "SELECT " +
                        "CASE WHEN COUNT(DISTINCT " + Media.TABLE_NAME + "." + Media.COLUMN_FIELD_ARTIST + ") = 1 THEN " + Media.TABLE_NAME + "." + Media.COLUMN_FIELD_ARTIST + " " +
                        "ELSE '" + PlayerApplication.getVariousArtists() + "' " +
                        "END " +
                        "AS " + Media.COLUMN_FIELD_ARTIST + " " +
                        "FROM " + Media.TABLE_NAME + " " +
                        "WHERE (" + Media.TABLE_NAME + "." + Media.COLUMN_FIELD_ALBUM_ID + " = " + Album.TABLE_NAME + "." + Album.COLUMN_FIELD_ALBUM_ID + ") " +
                        " AND (" +
                        "(" + Media.TABLE_NAME + "." + Media.COLUMN_FIELD_IS_QUEUE_FILE_ENTRY + " = 0) " +
                        "OR (" + Media.TABLE_NAME + "." + Media.COLUMN_FIELD_IS_QUEUE_FILE_ENTRY + " IS NULL)" +
                        ") " +
                        "GROUP BY " + Media.TABLE_NAME + "." + Media.COLUMN_FIELD_ALBUM_ID +
                        ")");

        scanContext.scannerDatabaseHandle.execSQL(
                "INSERT OR IGNORE INTO " + AlbumArtist.TABLE_NAME + " (" +
                        AlbumArtist.COLUMN_FIELD_ARTIST_NAME + ", " +
                        AlbumArtist.COLUMN_FIELD_VISIBLE + ", " +
                        AlbumArtist.COLUMN_FIELD_USER_HIDDEN +
                        ") " +
                        "SELECT " + Album.COLUMN_FIELD_ALBUM_ARTIST + ", -1, 0 " +
                        "FROM " + Album.TABLE_NAME + " GROUP BY " + Album.COLUMN_FIELD_ALBUM_ARTIST);

        scanContext.scannerDatabaseHandle.execSQL(
                "UPDATE " + Album.TABLE_NAME + " SET " + Album.COLUMN_FIELD_ALBUM_ARTIST_ID + " = (" +
                        "SELECT " + AlbumArtist.COLUMN_FIELD_ARTIST_ID + " " +
                        "FROM " + AlbumArtist.TABLE_NAME + " " +
                        "WHERE " + AlbumArtist.COLUMN_FIELD_ARTIST_NAME + " = " + Album.COLUMN_FIELD_ALBUM_ARTIST + " " +
                        "GROUP BY " + AlbumArtist.COLUMN_FIELD_ARTIST_NAME +
                        ") " +
                        "WHERE " + Album.COLUMN_FIELD_ALBUM_ARTIST + " IN (" +
                        "SELECT " + AlbumArtist.COLUMN_FIELD_ARTIST_NAME + " " +
                        "FROM " + AlbumArtist.TABLE_NAME +
                        ") ");

        scanContext.scannerDatabaseHandle.execSQL(
                "UPDATE " + Media.TABLE_NAME + " SET " +
                        Media.COLUMN_FIELD_ALBUM_ARTIST_ID + " = (" +
                        "SELECT " + Album.COLUMN_FIELD_ALBUM_ARTIST_ID + " " +
                        "FROM " + Album.TABLE_NAME + " " +
                        "WHERE " + Album.COLUMN_FIELD_ALBUM_ID + " = " + Media.COLUMN_FIELD_ALBUM_ID + " " +
                        "GROUP BY " + Album.COLUMN_FIELD_ALBUM_ID +
                        "), " +
                        Media.COLUMN_FIELD_ALBUM_ARTIST + " = (" +
                        "SELECT " + Album.COLUMN_FIELD_ALBUM_ARTIST + " " +
                        "FROM " + Album.TABLE_NAME + " " +
                        "WHERE " + Album.COLUMN_FIELD_ALBUM_ID + " = " + Media.COLUMN_FIELD_ALBUM_ID + " " +
                        "GROUP BY " + Album.COLUMN_FIELD_ALBUM_ID +
                        ") " +
                        "WHERE " + Media.COLUMN_FIELD_ALBUM_ID + " IN (" +
                        "SELECT " + Album.COLUMN_FIELD_ALBUM_ID + " " +
                        "FROM " + Album.TABLE_NAME +
                        ") ");

        scanContext.scannerDatabaseHandle.execSQL(
                "DELETE FROM " + AlbumArtist.TABLE_NAME + " WHERE " + AlbumArtist.COLUMN_FIELD_ARTIST_ID + " NOT IN (" +
                        "SELECT " + Media.COLUMN_FIELD_ALBUM_ARTIST_ID + " FROM " + Media.TABLE_NAME + ")"
        );
    }

    protected void updateAlbumCovers(SyncScanContext scanContext) {
        scanContext.scannerDatabaseHandle.execSQL(
                "UPDATE " + Album.TABLE_NAME + " SET " +
                        Album.COLUMN_FIELD_ALBUM_ART + " = ( " +
                        "SELECT " + Media.TABLE_NAME + "." + Media.COLUMN_FIELD_ART  + " " +
                        "FROM " + Media.TABLE_NAME + " " +
                        "WHERE " + Media.TABLE_NAME + "." + Media.COLUMN_FIELD_ALBUM_ID + " = " + Album.TABLE_NAME + "." + Album.COLUMN_FIELD_ALBUM_ID + " " +
                        "  AND " + Media.TABLE_NAME + "." + Media.COLUMN_FIELD_ART + " IS NOT NULL " +
                        "UNION " +
                        "SELECT NULL " +
                        "ORDER BY " + Media.TABLE_NAME + "." + Media.COLUMN_FIELD_ART + " DESC " +
                        "LIMIT 1 " +
                        ") " +
                        "WHERE (" + Album.TABLE_NAME + "." + Album.COLUMN_FIELD_ALBUM_ART + " IS NULL) OR (" + Album.TABLE_NAME + "." + Album.COLUMN_FIELD_ALBUM_ART + " = '')");
    }

    protected void doSyncDirectoryScan(List<File> fileList, Map<String, Boolean> discardMap, SyncScanContext scanContext) {
        ContentValues mediaTags = new ContentValues();

        scanContext.mediaCover = new ContentValues();
        scanContext.coverMap = new HashMap<String, String>();
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

                if ((discardMap.get(path) == null) || (!discardMap.get(path).equals(true))) {
//                    Log.d(TAG, "entering: " + currentFile.getName());
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
            }
            else {
                if (currentFile.length() == 0) {
                    fileList.remove(pathIndex);
                    continue;
                }

                if (isAudioFile(scanContext, currentFile)) {
                    final String mediaUri = PlayerApplication.fileToUri(currentFile);
                    if (!mediaExistsInDb(mediaUri)) {
                        Log.i(TAG, "+Media : " + mediaUri);

                        JniMediaLib.doReadTags(currentFile, mediaTags);

                        String coverUri = getCoverForFile(currentFile.getParentFile(), scanContext);
                        if (!TextUtils.isEmpty(coverUri)) {
                            mediaTags.put(Media.COLUMN_FIELD_ART, coverUri);
                        }
                        else {
                            if (coverUri == null) {
                                // cover not found, registering the directory for future potential update.
                                if (scanContext.coverMap.get(currentFile.getParent()) == null) {
                                    scanContext.coverMap.put(currentFile.getParent(), "");
                                }
                            }
                            mediaTags.remove(Media.COLUMN_FIELD_ART);
                        }


                        mediaTags.put(Media.COLUMN_FIELD_ARTIST_ID, getArtistId(mediaTags.getAsString(Media.COLUMN_FIELD_ARTIST), scanContext));
                        mediaTags.put(Media.COLUMN_FIELD_ALBUM_ID, getAlbumId(mediaTags.getAsString(Media.COLUMN_FIELD_ALBUM), scanContext));
                        mediaTags.put(Media.COLUMN_FIELD_GENRE_ID, getGenreId(mediaTags.getAsString(Media.COLUMN_FIELD_GENRE), scanContext));
                        mediaTags.put(Media.COLUMN_FIELD_VISIBLE, true);
                        mediaTags.put(Media.COLUMN_FIELD_USER_HIDDEN, false);

                        scanContext.scannerDatabaseHandle.insert(Media.TABLE_NAME, null, mediaTags);

                        refreshThreshold++;
                        if (refreshThreshold >= 25) {
                            doNotifyLibraryChanges();
                            refreshThreshold = 0;
                        }
                    }
//                    else {
//                        Log.i(TAG, "=Media : " + mediaUri);
                    // nothing to be done.
//                    }
                }
                fileList.remove(pathIndex);
            }
        }

        updateAlbumArtists(scanContext);
        updateAlbumCovers(scanContext);

        scanContext.mediaCover.clear();
        scanContext.mediaCover = null;
        scanContext.coverMap.clear();
        scanContext.coverMap = null;
        scanContext.artistIdMap.clear();
        scanContext.artistIdMap = null;
        scanContext.albumIdMap.clear();
        scanContext.albumIdMap = null;
        scanContext.genreIdMap.clear();
        scanContext.genreIdMap = null;
        scanContext.scannerDatabaseHandle = null;

        doNotifyLibraryChanges();
    }

    protected boolean doPlaylistAddContent(String playlistId, int position, ContentType contentType, final String sourceId, int sortOrder, String filter) {
        SQLiteDatabase database = databaseOpenHelper.getWritableDatabase();

        if (playlistId == null) {
            playlistId = "0";
        }

        if (database != null) {
            String selectStatement;
            String selectionArgs[] = null;

            String insertStatement =
                    "INSERT INTO " + PlaylistEntry.TABLE_NAME + " (" +
                            PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + ", " +
                            PlaylistEntry.COLUMN_FIELD_POSITION + ", " +
                            PlaylistEntry.COLUMN_FIELD_SONG_ID +
                            ") ";

            if (contentType == ContentType.CONTENT_TYPE_PLAYLIST) {
                insertStatement = insertStatement +
                        "SELECT " + playlistId + ", NULL, " + PlaylistEntry.COLUMN_FIELD_SONG_ID + " ";

                selectStatement =
                        "FROM " + PlaylistEntry.TABLE_NAME + " " +
                                "WHERE " + PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = " + sourceId + " " +
                                "ORDER BY " + PlaylistEntry.COLUMN_FIELD_POSITION;
            }
            else {
                insertStatement = insertStatement +
                        "SELECT " + playlistId + ", NULL, " + Media.COLUMN_FIELD_ID + " ";

                selectStatement = "FROM " + Media.TABLE_NAME + " ";

                selectStatement = selectStatement + (MusicConnector.show_hidden ?
                        "WHERE (" + Media.COLUMN_FIELD_VISIBLE + " <> 0) " :
                        "WHERE (" + Media.COLUMN_FIELD_VISIBLE + " <> 0) AND (" + Media.COLUMN_FIELD_USER_HIDDEN + " = 0) ");

                switch (contentType) {
                    case CONTENT_TYPE_DEFAULT:
                        if (!TextUtils.isEmpty(filter)) {
                            selectStatement = selectStatement + " AND (" +
                                    "(" + Media.COLUMN_FIELD_ARTIST + " LIKE '%" + filter + "%') OR " +
                                    "(" + Media.COLUMN_FIELD_ALBUM + " LIKE '%" + filter + "%') OR " +
                                    "(" + Media.COLUMN_FIELD_ALBUM_ARTIST + " LIKE '%" + filter + "%') OR " +
                                    "(" + Media.COLUMN_FIELD_GENRE + " LIKE '%" + filter + "%') OR " +
                                    "(" + Media.COLUMN_FIELD_TITLE + " LIKE '%" + filter + "%') " + ")";
                        }

                        break;
                    case CONTENT_TYPE_MEDIA:
                        selectStatement = selectStatement +
                                "AND (" + Media.COLUMN_FIELD_ID + " = " + sourceId + ") ";
                        break;
                    case CONTENT_TYPE_GENRE:
                        selectStatement = selectStatement +
                                "AND (" + Media.COLUMN_FIELD_GENRE_ID + " = " + sourceId + ") ";
                        break;
                    case CONTENT_TYPE_ARTIST:
                        selectStatement = selectStatement +
                                "AND (" + Media.COLUMN_FIELD_ARTIST_ID + " = " + sourceId + ") ";
                        break;
                    case CONTENT_TYPE_ALBUM:
                        selectStatement = selectStatement +
                                "AND (" + Media.COLUMN_FIELD_ALBUM_ID + " = " + sourceId + ") ";
                        break;
                    case CONTENT_TYPE_ALBUM_ARTIST:
                        selectStatement = selectStatement +
                                "AND (" + Media.COLUMN_FIELD_ALBUM_ARTIST_ID + " = " + sourceId + ") ";
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
                                "AND (" + Media.COLUMN_FIELD_URI + " = ?) ";

                        selectionArgs = new String[] {
                                decodedSourceId
                        };
                        break;
                    default:
                        return false;
                }

                switch (sortOrder) {
                    case SONG_URI:
                        selectStatement = selectStatement + "ORDER BY " + Media.COLUMN_FIELD_URI + " COLLATE NOCASE ASC";
                        break;
                    case -SONG_URI:
                        selectStatement = selectStatement + "ORDER BY " + Media.COLUMN_FIELD_URI + " COLLATE NOCASE DESC";
                        break;
                    case SONG_FIRST_PLAYED:
                        selectStatement = selectStatement + "ORDER BY " + Media.COLUMN_FIELD_FIRST_PLAYED + " COLLATE NOCASE ASC";
                        break;
                    case -SONG_FIRST_PLAYED:
                        selectStatement = selectStatement + "ORDER BY " + Media.COLUMN_FIELD_FIRST_PLAYED + " COLLATE NOCASE DESC";
                        break;
                    case SONG_LAST_PLAYED:
                        selectStatement = selectStatement + "ORDER BY " + Media.COLUMN_FIELD_LAST_PLAYED + " COLLATE NOCASE ASC";
                        break;
                    case -SONG_LAST_PLAYED:
                        selectStatement = selectStatement + "ORDER BY " + Media.COLUMN_FIELD_LAST_PLAYED + " COLLATE NOCASE DESC";
                        break;
                    case SONG_TITLE:
                        selectStatement = selectStatement + "ORDER BY " + Media.COLUMN_FIELD_TITLE + " COLLATE NOCASE ASC";
                        break;
                    case -SONG_TITLE:
                        selectStatement = selectStatement + "ORDER BY " + Media.COLUMN_FIELD_TITLE + " COLLATE NOCASE DESC";
                        break;
                    case SONG_ARTIST:
                        selectStatement = selectStatement + "ORDER BY " + Media.COLUMN_FIELD_ARTIST + " COLLATE NOCASE ASC";
                        break;
                    case -SONG_ARTIST:
                        selectStatement = selectStatement + "ORDER BY " + Media.COLUMN_FIELD_ARTIST + " COLLATE NOCASE DESC";
                        break;
                    case SONG_ALBUM_ARTIST:
                        selectStatement = selectStatement + "ORDER BY " + Media.COLUMN_FIELD_ALBUM_ARTIST + " COLLATE NOCASE ASC";
                        break;
                    case -SONG_ALBUM_ARTIST:
                        selectStatement = selectStatement + "ORDER BY " + Media.COLUMN_FIELD_ALBUM_ARTIST + " COLLATE NOCASE DESC";
                        break;
                    case SONG_ALBUM:
                        selectStatement = selectStatement + "ORDER BY " + Media.COLUMN_FIELD_ALBUM + " COLLATE NOCASE ASC";
                        break;
                    case -SONG_ALBUM:
                        selectStatement = selectStatement + "ORDER BY " + Media.COLUMN_FIELD_ALBUM + " COLLATE NOCASE DESC";
                        break;
                    case SONG_TRACK:
                        selectStatement = selectStatement + "ORDER BY " + Media.COLUMN_FIELD_TRACK + " COLLATE NOCASE ASC";
                        break;
                    case -SONG_TRACK:
                        selectStatement = selectStatement + "ORDER BY " + Media.COLUMN_FIELD_TRACK + " COLLATE NOCASE DESC";
                        break;
                    case SONG_DISC:
                        selectStatement = selectStatement + "ORDER BY " + Media.COLUMN_FIELD_DISC + " COLLATE NOCASE ASC";
                        break;
                    case -SONG_DISC:
                        selectStatement = selectStatement + "ORDER BY " + Media.COLUMN_FIELD_DISC + " COLLATE NOCASE DESC";
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
                            "UPDATE " + PlaylistEntry.TABLE_NAME + " SET " +
                                    PlaylistEntry.COLUMN_FIELD_POSITION + " = " + PlaylistEntry.COLUMN_FIELD_POSITION + " + " + addedCount + " " +
                                    "WHERE " + PlaylistEntry.COLUMN_FIELD_POSITION + " >= " + position + " " +
                                    "AND " + PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = " + playlistId);
                }

                cursor.close();
            }

            final String updateStatement =
                    "UPDATE " + PlaylistEntry.TABLE_NAME + " SET " + PlaylistEntry.COLUMN_FIELD_POSITION + " = (" +
                            "SELECT COUNT(*)" + " FROM " + PlaylistEntry.TABLE_NAME + " T1 " +
                            "WHERE (T1." + PlaylistEntry.COLUMN_FIELD_ENTRY_ID + " < " + PlaylistEntry.TABLE_NAME + "." + PlaylistEntry.COLUMN_FIELD_ENTRY_ID + ") " +
                            "AND (T1." + PlaylistEntry.COLUMN_FIELD_POSITION + " < " + (position + addedCount) +") " +
                            "AND (T1." + PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = " + playlistId + ") " +
                            ") " +
                            "WHERE (" + PlaylistEntry.COLUMN_FIELD_POSITION + " IS NULL) " +
                            "AND (" + PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = " + playlistId + ") ";

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
        SQLiteDatabase database = databaseOpenHelper.getWritableDatabase();

        if (playlistId == null) {
            playlistId = "0";
        }

        if (database != null && fileList != null) {
            int addedCount = fileList.size();

            SyncScanContext scanContext = new SyncScanContext();

            database.execSQL(
                    "UPDATE " + PlaylistEntry.TABLE_NAME + " SET " +
                            PlaylistEntry.COLUMN_FIELD_POSITION + " = " + PlaylistEntry.COLUMN_FIELD_POSITION + " + " + addedCount + " " +
                            "WHERE " + PlaylistEntry.COLUMN_FIELD_POSITION + " >= " + position + " " +
                            "AND " + PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID + " = " + playlistId);

            database.beginTransaction();
            try {
                // Suppression des anciens medias.
                // TODO: optimization, remove "deleteFileMedias" and delete unused medias in playlists
                if (deleteFileMedias) {
                    final String where = Media.COLUMN_FIELD_IS_QUEUE_FILE_ENTRY + " = ? ";

                    final String whereArgs[] = new String[]{
                            String.valueOf(1)
                    };

                    database.delete(Media.TABLE_NAME, where, whereArgs);
                }

                ContentValues contentValues = new ContentValues();
                for (File currentFile : fileList) {
                    if (isAudioFile(scanContext, currentFile)) {
                        contentValues.clear();
                        JniMediaLib.doReadTags(currentFile, contentValues);
                        contentValues.put(Media.COLUMN_FIELD_VISIBLE, 0);
                        contentValues.put(Media.COLUMN_FIELD_IS_QUEUE_FILE_ENTRY, 1);
                        long insertId = database.insert(Media.TABLE_NAME, null, contentValues);

                        contentValues.clear();
                        contentValues.put(PlaylistEntry.COLUMN_FIELD_PLAYLIST_ID, playlistId);
                        contentValues.put(PlaylistEntry.COLUMN_FIELD_POSITION, position);
                        contentValues.put(PlaylistEntry.COLUMN_FIELD_SONG_ID, insertId);
                        database.insert(PlaylistEntry.TABLE_NAME, null, contentValues);
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

    protected List<File> getStorageFileList(SyncScanContext scanContext, int sort, String filter) {
        if (currentFolder == null) {
            return null;
        }

        final File[] currentFileList = currentFolder.listFiles(hiddenFilter);
        ArrayList<File> fileList = new ArrayList<File>();

        if (currentFileList != null) {
            Arrays.sort(currentFileList, filenameComparator);
            Arrays.sort(currentFileList, filetypeComparator);

            for (File currentFile : currentFileList) {
                if (isAudioFile(scanContext, currentFile)) {
                    fileList.add(currentFile);
                } else if (currentFile.isDirectory()) {
                    fileList.add(currentFile);
                }
            }
        }

        return fileList;
    }



    protected static final Comparator<File> filenameComparator = new Comparator<File>() {

        @Override
        public int compare(File lhs, File rhs) {
            final String lhsName = lhs.getName().toUpperCase(Locale.getDefault());
            final String rhsName = rhs.getName().toUpperCase(Locale.getDefault());

            if (MusicConnector.storage_sort_order == AbstractMediaProvider.STORAGE_DISPLAY_NAME) {
                return lhsName.compareTo(rhsName);
            }
            else // if (MusicConnector.storage_sort_order == MusicConnector.SORT_Z_A)  {
                return -lhsName.compareTo(rhsName);
            //}
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
        SQLiteDatabase scannerDatabaseHandle;

        ArrayList<String> albumArtExtensions = null;

        ArrayList<String> audioFilesExtensions = null;

        ContentValues mediaCover;

        HashMap<String, String> coverMap;

        HashMap<String, Long> artistIdMap;

        HashMap<String, Long> albumIdMap;

        HashMap<String, Long> genreIdMap;
    }

    public class LocationAction implements AbstractProviderAction {

        @Override
        public String getDescription() {
            return PlayerApplication.context.getString(R.string.label_library_settings_location);
        }

        @Override
        public boolean isVisible() {
            return false;
        }

        @Override
        public void launch(Activity source) {
            final Intent intent = new Intent(PlayerApplication.context, UISearchPathsSettingsActivity.class);
            intent.putExtra(KEY_PROVIDER_ID, providerId);

            source.startActivityForResult(intent, ACTIVITY_NEED_UI_REFRESH);
        }
    }

    public class AudioFxAction implements AbstractProviderAction {

        @Override
        public String getDescription() {
            return PlayerApplication.context.getString(R.string.label_library_settings_soundfx);
        }

        @Override
        public boolean isVisible() {
            return false;
        }

        @Override
        public void launch(Activity source) {
            source.startActivity(new Intent(PlayerApplication.context, SoundEffectsActivity.class));
        }
    }

    public class SettingsAction implements AbstractProviderAction {

        @Override
        public String getDescription() {
            return PlayerApplication.context.getString(R.string.label_library_settings_general);
        }

        @Override
        public boolean isVisible() {
            return true;
        }

        @Override
        public void launch(Activity source) {
            final Intent intent = new Intent(PlayerApplication.context, UILocalContentSettingsActivity.class);
            intent.putExtra(KEY_PROVIDER_ID, providerId);

            source.startActivityForResult(intent, ACTIVITY_NEED_UI_REFRESH);
        }
    }

    public class AlbumArtistEmptyAction implements AbstractEmptyContentAction {

        @Override
        public String getDescription() {
            return PlayerApplication.context.getString(R.string.ni_artists);
        }

        @Override
        public String getActionDescription() {
            return PlayerApplication.context.getString(R.string.ni_artists_hint);
        }

        @Override
        public AbstractProviderAction getAction() {
            return ACTION_LOCATION;
        }
    }

    public class AlbumEmptyAction implements AbstractEmptyContentAction {

        @Override
        public String getDescription() {
            return PlayerApplication.context.getString(R.string.ni_albums);
        }

        @Override
        public String getActionDescription() {
            return PlayerApplication.context.getString(R.string.ni_albums_hint);
        }

        @Override
        public AbstractProviderAction getAction() {
            return ACTION_LOCATION;
        }
    }

    public class ArtistEmptyAction implements AbstractEmptyContentAction {

        @Override
        public String getDescription() {
            return PlayerApplication.context.getString(R.string.ni_artists);
        }

        @Override
        public String getActionDescription() {
            return PlayerApplication.context.getString(R.string.ni_artists_hint);
        }

        @Override
        public AbstractProviderAction getAction() {
            return ACTION_LOCATION;
        }
    }

    public class GenreEmptyAction implements AbstractEmptyContentAction {

        @Override
        public String getDescription() {
            return PlayerApplication.context.getString(R.string.ni_genres);
        }

        @Override
        public String getActionDescription() {
            return PlayerApplication.context.getString(R.string.ni_genres_hint);
        }

        @Override
        public AbstractProviderAction getAction() {
            return ACTION_LOCATION;
        }
    }

    public class SongEmptyAction implements AbstractEmptyContentAction {

        @Override
        public String getDescription() {
            return PlayerApplication.context.getString(R.string.ni_songs);
        }

        @Override
        public String getActionDescription() {
            return PlayerApplication.context.getString(R.string.ni_songs_hint);
        }

        @Override
        public AbstractProviderAction getAction() {
            return ACTION_LOCATION;
        }
    }
}
