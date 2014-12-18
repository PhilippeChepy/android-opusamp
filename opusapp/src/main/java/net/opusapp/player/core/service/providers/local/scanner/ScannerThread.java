package net.opusapp.player.core.service.providers.local.scanner;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import net.opusapp.player.R;
import net.opusapp.player.core.service.providers.local.LocalProvider;
import net.opusapp.player.core.service.providers.local.database.Entities;
import net.opusapp.player.core.service.utils.CursorUtils;
import net.opusapp.player.ui.utils.PlayerApplication;
import net.opusapp.player.utils.LogUtils;
import net.opusapp.player.utils.jni.JniMediaLib;

import java.io.File;
import java.util.ArrayList;
import java.util.Set;

public class ScannerThread extends Thread {

    public static final String TAG = ScannerThread.class.getSimpleName();



    private final MediaScanner mMediaScanner;

    private final int mMediaManagerId;

    private final SQLiteDatabase mDatabase;

    private final static int UPDATE_THRESHOLD = 25;



    private boolean mCancelling;

    private Set<String> mMediaExtensionSet;

    private Set<String> mCoverExtensionSet;

    private ArrayList<File> mAcceptList;

    private ArrayList<File> mDiscardList;

    private boolean mAcceptLocalCovers;


    public ScannerThread(final MediaScanner mediaScanner) {
        mMediaScanner = mediaScanner;
        mDatabase = mediaScanner.getDatabaseOpenHelper().getWritableDatabase();
        mMediaManagerId = mediaScanner.getManager().getMediaManagerId();
        mCancelling = false;
    }

    @Override
    public void run() {
        mMediaScanner.notifyScannerHasStarted();
        //setPriority(MIN_PRIORITY); // Don't burn our poor phone's CPU... to quickly

        // construction of extension set.
        mMediaExtensionSet = MediaScanner.getMediaExtensions(mMediaManagerId);
        mCoverExtensionSet = MediaScanner.getCoverExtensions();

        // accepting local covers has default arts.
        final Resources resources = PlayerApplication.context.getResources();
        final SharedPreferences sharedPrefs = PlayerApplication.context.getSharedPreferences("provider-" + mMediaManagerId, Context.MODE_PRIVATE);
        mAcceptLocalCovers = sharedPrefs.getBoolean(resources.getString(R.string.preference_key_display_local_art), false);

        // construction of accept/discard lists.
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

        final Cursor acceptCursor = mDatabase.query(Entities.ScanDirectory.TABLE_NAME, pathProjection, selection, selectionAccept, null, null, null);
        mAcceptList = new ArrayList<>();
        if (CursorUtils.ifNotEmpty(acceptCursor)) {
            while (acceptCursor.moveToNext()) {
                mAcceptList.add(new File(acceptCursor.getString(0)));
            }
            CursorUtils.free(acceptCursor);
        }

        final Cursor discardCursor = mDatabase.query(Entities.ScanDirectory.TABLE_NAME, pathProjection, selection, selectionDiscard, null, null, null);
        mDiscardList = new ArrayList<>();
        if (CursorUtils.ifNotEmpty(discardCursor)) {
            while (discardCursor.moveToNext()) {
                mDiscardList.add(new File(discardCursor.getString(0)));
            }
            CursorUtils.free(discardCursor);
        }

        // Now we are doing the scan itself.
        try {
            removeForbiddenMedias();
            removeOrphanedCovers();
            removeOrphanedMedias();

            deleteInvalidCategories();

            // Searching for new medias.
            for (File path : mAcceptList) {
                insertNewMedias(path);
            }

            deleteInvalidCategories();
            updateAllCategories();
        }
        catch (final CancellingException exception) {
            LogUtils.LOGI(TAG, "scan canceled by user");
        }

        mMediaScanner.notifyScannerHasFinished();
    }

    public void requestCancellation() {
        mCancelling = true;
    }




    protected void removeForbiddenMedias() throws CancellingException {
        try {
            mDatabase.beginTransaction();

            for (File path : mDiscardList) {
                if (mCancelling) {
                    throw new CancellingException();
                }

                mDatabase.delete(Entities.Media.TABLE_NAME,
                        Entities.Media.COLUMN_FIELD_URI + " LIKE ?",
                        new String[]{
                                "file://" + path.getAbsolutePath() + File.separator + "%"
                        }
                );
            }
            mDatabase.setTransactionSuccessful();
        }
        finally {
            mDatabase.endTransaction();
        }
    }

    protected void removeOrphanedCovers() throws CancellingException {

        final String artProjection[] = new String[] {
                Entities.Art._ID,
                Entities.Art.COLUMN_FIELD_URI,
        };

        final String selection = Entities.Art.COLUMN_FIELD_URI_IS_EMBEDDED + " <> 0 ";

        final int COLUMN_ART_ID = 0;
        final int COLUMN_ART_URI = 1;

        final Cursor cursor = mDatabase.query(Entities.Art.TABLE_NAME, artProjection, selection, null, null, null, null);

        try {
            mDatabase.beginTransaction();

            if (CursorUtils.ifNotEmpty(cursor)) {
                while (cursor.moveToNext()) {
                    if (mCancelling) {
                        throw new CancellingException();
                    }

                    final File artFile = PlayerApplication.uriToFile(cursor.getString(COLUMN_ART_URI));

                    if (!artFile.exists()) {
                        final String whereClause = Entities.Art._ID + " = ? ";
                        final String whereArgs[] = new String[] {
                                cursor.getString(COLUMN_ART_ID)
                        };

                        LogUtils.LOGI(TAG, "!Cover: " + artFile.getAbsolutePath());
                        mDatabase.delete(Entities.Art.TABLE_NAME, whereClause, whereArgs);
                    }
                }

                CursorUtils.free(cursor);

                updateCovers();
            }
            mDatabase.setTransactionSuccessful();
        }
        finally {
            mDatabase.endTransaction();
        }
    }

    protected void removeOrphanedMedias()  throws CancellingException {
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

        final int COLUMN_ID = 0;
        final int COLUMN_URI = 1;
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

        int refreshThreshold = 0;
        boolean checkEmbeddedArts = true;

        final ContentValues tags = new ContentValues();
        final Cursor cursor = mDatabase.query(Entities.Media.TABLE_NAME, mediaProjection, null, null, null, null, null);
        if (CursorUtils.ifNotEmpty(cursor)) {
            while (cursor.moveToNext()) {
                if (mCancelling) {
                    throw new CancellingException();
                }

                final File mediaFile = PlayerApplication.uriToFile(cursor.getString(COLUMN_URI));
                final String mediaAbsolutePath = mediaFile.getAbsolutePath();

                // Checking file existence
                if (!mediaFile.exists()) {
                    LogUtils.LOGI(TAG, "!Media (cause not existing) : " + mediaFile.getAbsolutePath());
                    deleteMediaById(cursor.getString(COLUMN_ID));
                    continue;
                }

                // Checking if is an audio file (criterion can change)
                if (!LocalProvider.fileHasValidExtension(mediaFile, mMediaExtensionSet)) {
                    LogUtils.LOGI(TAG, "!Media (cause not an audio file) : " + mediaFile.getAbsolutePath());
                    deleteMediaById(cursor.getString(COLUMN_ID));
                }

                // Checking presence in accept path.
                boolean needDeletion = true;
                for (File acceptPath : mAcceptList) {
                    if (mediaAbsolutePath.startsWith(acceptPath.getAbsolutePath())) {
                        needDeletion = false;
                        break;
                    }
                }
                if (needDeletion) {
                    LogUtils.LOGI(TAG, "!Media (cause not in accept path) : " + mediaFile.getAbsolutePath());
                    deleteMediaById(cursor.getString(COLUMN_ID));
                    continue;
                }

                // Test metadata changes...
                boolean needDbUpdate = false;

                tags.clear();
                JniMediaLib.readTags(mediaFile, tags);

                String title = tags.getAsString(Entities.Media.COLUMN_FIELD_TITLE);
                if (title != null && !title.equals(cursor.getString(COLUMN_TITLE))) {
                    needDbUpdate = true;
                }

                String artist = tags.getAsString(Entities.Media.COLUMN_FIELD_ARTIST);
                if (!needDbUpdate && artist != null && !artist.equals(cursor.getString(COLUMN_ARTIST))) {
                    needDbUpdate = true;
                }

                String albumArtist = tags.getAsString(Entities.Media.COLUMN_FIELD_ALBUM_ARTIST);
                if (!needDbUpdate && albumArtist != null && !albumArtist.equals(cursor.getString(COLUMN_ALBUM_ARTIST))) {
                    needDbUpdate = true;
                }

                String album = tags.getAsString(Entities.Media.COLUMN_FIELD_ALBUM);
                if (!needDbUpdate && album != null && !album.equals(cursor.getString(COLUMN_ALBUM))) {
                    needDbUpdate = true;
                }

                String genre = tags.getAsString(Entities.Media.COLUMN_FIELD_GENRE);
                if (!needDbUpdate && genre != null && !genre.equals(cursor.getString(COLUMN_GENRE))) {
                    needDbUpdate = true;
                }

                int year = tags.getAsInteger(Entities.Media.COLUMN_FIELD_YEAR);
                if (!needDbUpdate && year != cursor.getInt(COLUMN_YEAR)) {
                    needDbUpdate = true;
                }

                int track = tags.getAsInteger(Entities.Media.COLUMN_FIELD_TRACK);
                if (!needDbUpdate && track != cursor.getInt(COLUMN_TRACK)) {
                    needDbUpdate = true;
                }

                int disc = tags.getAsInteger(Entities.Media.COLUMN_FIELD_DISC);
                if (!needDbUpdate && disc != cursor.getInt(COLUMN_DISC)) {
                    needDbUpdate = true;
                }

                String comment = tags.getAsString(Entities.Media.COLUMN_FIELD_COMMENT);
                if (!needDbUpdate && comment != null && !comment.equals(cursor.getString(COLUMN_COMMENT))) {
                    needDbUpdate = true;
                }

                String lyrics = tags.getAsString(Entities.Media.COLUMN_FIELD_LYRICS);
                if (!needDbUpdate && lyrics != null && !lyrics.equals(cursor.getString(COLUMN_LYRICS))) {
                    needDbUpdate = true;
                }

                boolean hasEmbeddedArt = false;

                if (!needDbUpdate && checkEmbeddedArts) {
                    if (JniMediaLib.embeddedCoverCacheNeedCleanup()) {
                        checkEmbeddedArts = false;
                    }
                    else {
                        hasEmbeddedArt = tags.getAsBoolean(Entities.Media.NOT_PERSISTANT_COLUMN_FIELD_HAS_EMBEDDED_ART);

                        if (hasEmbeddedArt &&
                                (cursor.getInt(COLUMN_ORIGINAL_ART_ID) == 0 || cursor.isNull(COLUMN_ORIGINAL_ART_ID))) {
                            needDbUpdate = true;
                        }
                    }
                }

                if (needDbUpdate) {
                    LogUtils.LOGI(TAG, "~Media : " + mediaAbsolutePath);

                    if (hasEmbeddedArt) {
                        long coverId = searchEmbeddedCoversForMedia(mediaFile);

                        if (coverId != 0) {
                            if (cursor.getInt(COLUMN_ART_ID) == 0 || cursor.isNull(COLUMN_ART_ID)) {
                                tags.put(Entities.Media.COLUMN_FIELD_ART_ID, coverId);
                            }

                            tags.put(Entities.Media.COLUMN_FIELD_ORIGINAL_ART_ID, coverId);
                        }
                    }

                    tags.remove(Entities.Media.NOT_PERSISTANT_COLUMN_FIELD_HAS_EMBEDDED_ART);
                    mDatabase.update(Entities.Media.TABLE_NAME, tags, "_ID = ?", new String[]{ cursor.getString(COLUMN_ID) });

                    refreshThreshold++;
                    if (refreshThreshold >= UPDATE_THRESHOLD) {
                        updateAllCategories();
                        refreshThreshold = 0;
                    }
                }
            }

            // TODO: faire des transactions COURTES dans toute cette classe !
            CursorUtils.free(cursor);
            updateCovers();
        }
    }

    protected void insertNewMedias(final File sourcePath) {
        final ContentValues mediaTags = new ContentValues();

        final ArrayList<File> fileList = new ArrayList<>();
        fileList.add(sourcePath);

        int refreshThreshold = 0;

        while (fileList.size() > 0) {
            final File source = fileList.get(0);
            final String sourceAbsolutePath = source.getAbsolutePath();

            fileList.remove(0);

            if (source.isDirectory()) {
                boolean add = true;
                for (final File path : mDiscardList) {
                    if (sourceAbsolutePath.startsWith(path.getAbsolutePath())) {
                        add = false;
                        break;
                    }
                }

                if (add) {
                    LogUtils.LOGI(TAG, "+Path: " + source.getName());

                    File directoryContent[] = source.listFiles();
                    if (directoryContent != null && directoryContent.length > 0) {
                        for (File subFile : directoryContent) {
                            fileList.add(0, subFile);
                        }
                    }
                }
            }
            else {
                if (source.length() == 0) {
                    continue;
                }

                if (LocalProvider.fileHasValidExtension(source, mMediaExtensionSet)) {
                    final String mediaUri = PlayerApplication.fileToUri(source);

                    if (searchMediaIdByUri(mediaUri) <= 0) {
                        LogUtils.LOGI(TAG, "+Media : " + mediaUri);

                        mediaTags.clear();
                        JniMediaLib.readTags(source, mediaTags);

                        boolean hasEmbeddedArt = mediaTags.getAsBoolean(Entities.Media.NOT_PERSISTANT_COLUMN_FIELD_HAS_EMBEDDED_ART);
                        mediaTags.remove(Entities.Media.NOT_PERSISTANT_COLUMN_FIELD_HAS_EMBEDDED_ART);

                        long coverId = 0;
                        if (hasEmbeddedArt) {
                          coverId = searchEmbeddedCoversForMedia(source);
                        }

                        if (coverId == 0 && mAcceptLocalCovers) {
                            coverId = searchLocalCoversForMedia(source);
                        }
                        else {
                            searchLocalCoversForMedia(source);
                        }

                        if (coverId != 0) {
                            mediaTags.put(Entities.Media.COLUMN_FIELD_ART_ID, coverId);
                            mediaTags.put(Entities.Media.COLUMN_FIELD_ORIGINAL_ART_ID, coverId);
                        }
                        else {
                            mediaTags.remove(Entities.Media.COLUMN_FIELD_ART_ID);
                            mediaTags.remove(Entities.Media.COLUMN_FIELD_ORIGINAL_ART_ID);
                        }

                        mediaTags.putNull(Entities.Media.COLUMN_FIELD_ARTIST_ID);
                        mediaTags.putNull(Entities.Media.COLUMN_FIELD_ALBUM_ID);
                        mediaTags.putNull(Entities.Media.COLUMN_FIELD_GENRE_ID);
                        mediaTags.put(Entities.Media.COLUMN_FIELD_VISIBLE, true);
                        mediaTags.put(Entities.Media.COLUMN_FIELD_USER_HIDDEN, false);

                        mDatabase.insert(Entities.Media.TABLE_NAME, null, mediaTags);

                        refreshThreshold++;
                        if (refreshThreshold >= UPDATE_THRESHOLD) {
                            updateAllCategories();
                            refreshThreshold = 0;
                        }
                    }
                    else {
                        LogUtils.LOGI(TAG, "=Media : " + mediaUri);
                    }
                }
            }
        }
    }



    protected int searchMediaIdByUri(final String mediaUri) {
        final String tableName = Entities.Media.TABLE_NAME;

        final String requestedFields[] = new String[] {
                Entities.Media._ID
        };

        final String selection = Entities.Media.COLUMN_FIELD_URI + " = ? ";

        final String selectionArgs[] = new String[] {
                mediaUri
        };

        final Cursor cursor = mDatabase.query(tableName, requestedFields, selection, selectionArgs, null, null, null);

        int id = 0;
        if (CursorUtils.ifNotEmpty(cursor)) {
            cursor.moveToFirst();
            id = cursor.getInt(0);
            CursorUtils.free(cursor);
        }

        return id;
    }

    protected String searchMediaUriById(final String mediaId) {
        final String tableName = Entities.Media.TABLE_NAME;

        final String requestedFields[] = new String[] {
                Entities.Media.COLUMN_FIELD_URI
        };

        final String selection = Entities.Media._ID + " = ? ";

        final String selectionArgs[] = new String[] {
                mediaId
        };

        final Cursor cursor = mDatabase.query(tableName, requestedFields, selection, selectionArgs, null, null, null);

        String path = null;
        if (CursorUtils.ifNotEmpty(cursor)) {
            cursor.moveToFirst();
            path = cursor.getString(0);
            CursorUtils.free(cursor);
        }

        return path;
    }

    protected synchronized long searchEmbeddedCoversForMedia(final @NonNull File source) {
        final File cacheFile = JniMediaLib.embeddedCoverCacheFile(source);

        final String projection[] = new String[] {
                Entities.Art._ID
        };

        final String selection = Entities.Art.COLUMN_FIELD_URI + " = ? ";

        final String selectionArgs[] = new String[] {
                PlayerApplication.fileToUri(cacheFile)
        };

        long coverId;

        final Cursor coverCursor = mDatabase.query(Entities.Art.TABLE_NAME, projection, selection, selectionArgs, null, null, null);
        if (CursorUtils.ifNotEmpty(coverCursor)) {
            coverCursor.moveToFirst();
            coverId = coverCursor.getLong(0);
            CursorUtils.free(coverCursor);
        }
        else {
            final File coverFile = JniMediaLib.embeddedCoverDump(source);

            if (coverFile != null) {
                ContentValues values = new ContentValues();

                values.put(Entities.Art.COLUMN_FIELD_URI, PlayerApplication.fileToUri(coverFile));
                values.put(Entities.Art.COLUMN_FIELD_URI_IS_EMBEDDED, true);
                coverId = mDatabase.insert(Entities.Art.TABLE_NAME, null, values);
            }
            else {
                coverId = 0;
            }
        }

        return coverId;
    }

    protected synchronized long searchLocalCoversForMedia(final @NonNull File source) {
        final File parent = source.getParentFile();

        final String projection[] = new String[] {
                Entities.Art._ID
        };

        final String selection = Entities.Art.COLUMN_FIELD_URI + " LIKE ? ";

        final String selectionArgs[] = new String[] {
                PlayerApplication.fileToUri(parent) + "%"
        };

        long coverId;

        final Cursor coverCursor = mDatabase.query(Entities.Art.TABLE_NAME, projection, selection, selectionArgs, null, null, null, "1");
        if (CursorUtils.ifNotEmpty(coverCursor)) {
            coverCursor.moveToFirst();
            coverId = coverCursor.getLong(0);
            CursorUtils.free(coverCursor);
        }
        else {
            coverId = 0;

            final ContentValues values = new ContentValues();
            final File fileList[] = parent.listFiles();

            for (File file : fileList) {
                if (LocalProvider.fileHasValidExtension(file, mCoverExtensionSet)) {
                    values.clear();
                    values.put(Entities.Art.COLUMN_FIELD_URI, PlayerApplication.fileToUri(file));
                    values.put(Entities.Art.COLUMN_FIELD_URI_IS_EMBEDDED, true);

                    long lastInsertedCoverId = mDatabase.insert(Entities.Art.TABLE_NAME, null, values);
                    if (coverId == 0) {
                        coverId = lastInsertedCoverId;
                    }
                }
            }
        }

        return coverId;
    }



    protected void deleteMediaById(String mediaId) {
        // Deleting cover file cache.
        final String mediaAbsolutePath = searchMediaUriById(mediaId);
        if (!TextUtils.isEmpty(mediaAbsolutePath)) {
            final File mediaFile = new File(mediaAbsolutePath);
            final File cacheFile = JniMediaLib.embeddedCoverCacheFile(mediaFile);

            if (cacheFile.exists()) {
                if (!cacheFile.delete()) {
                    LogUtils.LOGW(TAG, "unable to delete cover cache file for " + mediaFile.getAbsolutePath());
                }
            }

            final String selection = Entities.Art.COLUMN_FIELD_URI + " = ? ";

            final String selectionArgs[] = new String[]{
                    PlayerApplication.fileToUri(cacheFile)
            };

            mDatabase.delete(Entities.Art.TABLE_NAME, selection, selectionArgs);
        }

        // Deleting media file.
        final String whereClause = Entities.Media._ID + " = ? ";
        final String whereArgs[] = new String[] {
                mediaId
        };

        mDatabase.delete(Entities.Media.TABLE_NAME, whereClause, whereArgs);
    }

    protected void deleteInvalidCategories() {
        deleteEmptyAlbums();
        deleteEmptyAlbumArtists();
        deleteEmptyArtists();
        deleteEmptyGenres();
    }

    protected void deleteEmptyAlbums() {
        mDatabase.delete(Entities.Album.TABLE_NAME,
                Entities.Album._ID + " NOT IN (" +
                        " SELECT " + Entities.Media.COLUMN_FIELD_ALBUM_ID +
                        " FROM " + Entities.Media.TABLE_NAME +
                        " GROUP BY " + Entities.Media.COLUMN_FIELD_ALBUM_ID + ")", null);
    }

    protected void deleteEmptyAlbumArtists() {
        mDatabase.delete(Entities.AlbumArtist.TABLE_NAME,
                Entities.AlbumArtist._ID + " NOT IN (" +
                        " SELECT " + Entities.Media.COLUMN_FIELD_ALBUM_ARTIST_ID +
                        " FROM " + Entities.Media.TABLE_NAME +
                        " GROUP BY " + Entities.Media.COLUMN_FIELD_ALBUM_ARTIST_ID + ")", null);
    }

    protected void deleteEmptyArtists() {
        mDatabase.delete(Entities.Artist.TABLE_NAME,
                Entities.Artist._ID + " NOT IN (" +
                        " SELECT " + Entities.Media.COLUMN_FIELD_ARTIST_ID +
                        " FROM " + Entities.Media.TABLE_NAME +
                        " GROUP BY " + Entities.Media.COLUMN_FIELD_ARTIST_ID + ")", null);
    }

    protected void deleteEmptyGenres() {
        mDatabase.delete(Entities.Genre.TABLE_NAME,
                Entities.Genre._ID + " NOT IN (" +
                        " SELECT " + Entities.Media.COLUMN_FIELD_GENRE_ID +
                        " FROM " + Entities.Media.TABLE_NAME +
                        " GROUP BY " + Entities.Media.COLUMN_FIELD_GENRE_ID + ")", null);
    }


    protected void updateAllCategories() {
        updateAlbums();
        updateAlbumArtists();
        updateArtists();
        updateGenres();

        updateCovers();

        mMediaScanner.notifyScannerHasUpdated();
    }

    protected void updateAlbums() {
        mDatabase.execSQL("INSERT OR IGNORE INTO " + Entities.Album.TABLE_NAME +
                " (" +
                Entities.Album.COLUMN_FIELD_ALBUM_NAME + ", " +
                Entities.Album.COLUMN_FIELD_USER_HIDDEN +
                ") " +
                " SELECT " + Entities.Media.COLUMN_FIELD_ALBUM + ", 0" +
                " FROM " + Entities.Media.TABLE_NAME +
                " WHERE (" + Entities.Media.COLUMN_FIELD_ALBUM + " IS NOT NULL) AND (" + Entities.Media.COLUMN_FIELD_ALBUM + " <> '') " +
                " GROUP BY " + Entities.Media.COLUMN_FIELD_ALBUM);

        mDatabase.execSQL("UPDATE " + Entities.Media.TABLE_NAME + " SET " +
                Entities.Media.COLUMN_FIELD_ALBUM_ID + " = (" +
                " SELECT " + Entities.Album._ID +
                " FROM " + Entities.Album.TABLE_NAME +
                " WHERE " + Entities.Album.TABLE_NAME + "." + Entities.Album.COLUMN_FIELD_ALBUM_NAME + " = " +
                Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_ALBUM +
                " LIMIT 1)");
    }

    protected void updateAlbumArtists() {

        mDatabase.execSQL("UPDATE " + Entities.Album.TABLE_NAME + " SET " +
                Entities.Album.COLUMN_FIELD_ALBUM_ARTIST + " = ( " +
                " SELECT " +
                " CASE WHEN COUNT(DISTINCT " + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_ARTIST + ") = 1 " +
                " THEN " + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_ARTIST + " " +
                " ELSE '" + PlayerApplication.getVariousArtists() + "' " +
                " END AS " + Entities.Media.COLUMN_FIELD_ARTIST + " " +
                "FROM " + Entities.Media.TABLE_NAME + " " +
                "WHERE (" + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_ALBUM_ID + " = " + Entities.Album.TABLE_NAME + "." + Entities.Album._ID + ") " +
                " AND ((" + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_IS_QUEUE_FILE_ENTRY + " = 0) " +
                    "OR (" + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_IS_QUEUE_FILE_ENTRY + " IS NULL)) " +
                "GROUP BY " + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_ALBUM_ID +
                ")");

        mDatabase.execSQL(
                "INSERT OR IGNORE INTO " + Entities.AlbumArtist.TABLE_NAME + " (" +
                    Entities.AlbumArtist.COLUMN_FIELD_ARTIST_NAME + ", " +
                    Entities.AlbumArtist.COLUMN_FIELD_VISIBLE + ", " +
                    Entities.AlbumArtist.COLUMN_FIELD_USER_HIDDEN +
                ") " +
                "SELECT " + Entities.Album.COLUMN_FIELD_ALBUM_ARTIST + ", -1, 0 " +
                "FROM " + Entities.Album.TABLE_NAME + " GROUP BY " + Entities.Album.COLUMN_FIELD_ALBUM_ARTIST
        );

        mDatabase.execSQL(
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

        mDatabase.execSQL(
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
    }

    protected void updateArtists() {
        mDatabase.execSQL("INSERT OR IGNORE INTO " + Entities.Artist.TABLE_NAME +
                " (" +
                Entities.Artist.COLUMN_FIELD_ARTIST_NAME + ", " +
                Entities.Artist.COLUMN_FIELD_VISIBLE + ", " +
                Entities.Artist.COLUMN_FIELD_USER_HIDDEN +
                ") " +
                " SELECT " + Entities.Media.COLUMN_FIELD_ARTIST + ", -1, 0" +
                " FROM " + Entities.Media.TABLE_NAME +
                " WHERE (" + Entities.Media.COLUMN_FIELD_ARTIST + " IS NOT NULL) AND (" + Entities.Media.COLUMN_FIELD_ARTIST + " <> '') " +
                " GROUP BY " + Entities.Media.COLUMN_FIELD_ARTIST);

        mDatabase.execSQL("UPDATE " + Entities.Media.TABLE_NAME + " SET " +
                Entities.Media.COLUMN_FIELD_ARTIST_ID + " = (" +
                " SELECT " + Entities.Artist._ID +
                " FROM " + Entities.Artist.TABLE_NAME +
                " WHERE " + Entities.Artist.TABLE_NAME + "." + Entities.Artist.COLUMN_FIELD_ARTIST_NAME + " = " +
                Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_ARTIST +
                " LIMIT 1)");
    }

    protected void updateGenres() {
        mDatabase.execSQL("INSERT OR IGNORE INTO " + Entities.Genre.TABLE_NAME +
                " (" +
                Entities.Genre.COLUMN_FIELD_GENRE_NAME + ", " +
                Entities.Genre.COLUMN_FIELD_VISIBLE + ", " +
                Entities.Genre.COLUMN_FIELD_USER_HIDDEN +
                ") " +
                " SELECT " + Entities.Media.COLUMN_FIELD_GENRE + ", -1, 0" +
                " FROM " + Entities.Media.TABLE_NAME +
                " WHERE (" + Entities.Media.COLUMN_FIELD_GENRE + " IS NOT NULL) AND (" + Entities.Media.COLUMN_FIELD_GENRE + " <> '') " +
                " GROUP BY " + Entities.Media.COLUMN_FIELD_GENRE);

        mDatabase.execSQL("UPDATE " + Entities.Media.TABLE_NAME + " SET " +
                Entities.Media.COLUMN_FIELD_GENRE_ID + " = (" +
                " SELECT " + Entities.Genre._ID +
                " FROM " + Entities.Genre.TABLE_NAME +
                " WHERE " + Entities.Genre.TABLE_NAME + "." + Entities.Genre.COLUMN_FIELD_GENRE_NAME + " = " +
                Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_GENRE +
                " LIMIT 1)");
    }

    protected void updateCovers() {
        // Update orphaned covers to NULL instead of invalid id.
        mDatabase.execSQL("UPDATE " + Entities.Album.TABLE_NAME + " SET " +
                Entities.Album.COLUMN_FIELD_ORIGINAL_ALBUM_ART_ID + " = NULL " +
                "WHERE " + Entities.Album.COLUMN_FIELD_ORIGINAL_ALBUM_ART_ID + " NOT IN (" +
                "SELECT " + Entities.Art._ID + " FROM " + Entities.Art.TABLE_NAME + ")");

        mDatabase.execSQL("UPDATE " + Entities.Media.TABLE_NAME + " SET " +
                Entities.Media.COLUMN_FIELD_ORIGINAL_ART_ID + " = NULL " +
                "WHERE " + Entities.Media.COLUMN_FIELD_ORIGINAL_ART_ID + " NOT IN (" +
                "SELECT " + Entities.Art._ID + " FROM " + Entities.Art.TABLE_NAME + ")");

        mDatabase.execSQL("UPDATE " + Entities.Album.TABLE_NAME + " SET " +
                Entities.Album.COLUMN_FIELD_ALBUM_ART_ID + " = " + Entities.Album.COLUMN_FIELD_ORIGINAL_ALBUM_ART_ID + " " +
                "WHERE " + Entities.Album.COLUMN_FIELD_ALBUM_ART_ID + " NOT IN (" +
                "SELECT " + Entities.Art._ID + " FROM " + Entities.Art.TABLE_NAME + ")");

        mDatabase.execSQL("UPDATE " + Entities.Media.TABLE_NAME + " SET " +
                Entities.Media.COLUMN_FIELD_ART_ID + " = " + Entities.Media.COLUMN_FIELD_ORIGINAL_ART_ID + " " +
                "WHERE " + Entities.Media.COLUMN_FIELD_ART_ID + " NOT IN (" +
                "SELECT " + Entities.Art._ID + " FROM " + Entities.Art.TABLE_NAME + ")");

        // Updating album arts by choosing it from album's tracks.
        mDatabase.execSQL("UPDATE " + Entities.Album.TABLE_NAME + " SET " +
                Entities.Album.COLUMN_FIELD_ALBUM_ART_ID + " = ( " +
                    "SELECT " + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_ART_ID +
                    " FROM " + Entities.Media.TABLE_NAME +
                    " WHERE " + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_ALBUM_ID + " = " + Entities.Album.TABLE_NAME + "." + Entities.Album._ID +
                        " AND " + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_ART_ID + " IS NOT NULL " +
                    "UNION " +
                    "SELECT NULL " +
                    "ORDER BY " + Entities.Media.TABLE_NAME + "." + Entities.Media.COLUMN_FIELD_ART_ID + " DESC " +
                    "LIMIT 1 " +
                ") " +
                " WHERE (" + Entities.Album.TABLE_NAME + "." + Entities.Album.COLUMN_FIELD_ALBUM_ART_ID + " IS NULL)" +
                    " OR (" + Entities.Album.TABLE_NAME + "." + Entities.Album.COLUMN_FIELD_ALBUM_ART_ID + " = '')");

        mDatabase.execSQL("UPDATE " + Entities.Album.TABLE_NAME + " SET " +
                Entities.Album.COLUMN_FIELD_ORIGINAL_ALBUM_ART_ID + " = " + Entities.Album.COLUMN_FIELD_ALBUM_ART_ID +
                " WHERE (" + Entities.Album.TABLE_NAME + "." + Entities.Album.COLUMN_FIELD_ORIGINAL_ALBUM_ART_ID + " IS NULL)" +
                    " OR (" + Entities.Album.TABLE_NAME + "." + Entities.Album.COLUMN_FIELD_ORIGINAL_ALBUM_ART_ID + " = '')");
    }
}
