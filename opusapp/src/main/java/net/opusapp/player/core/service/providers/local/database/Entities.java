/*
 * Entities.java
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
package net.opusapp.player.core.service.providers.local.database;

import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;

public class Entities {

    public static abstract class Album implements BaseColumns {

        public static final String TAG = Album.class.getSimpleName();



        public final static String TABLE_NAME = "library_album";



        public static final String COLUMN_FIELD_ALBUM_NAME      = "album_name";

        public static final String COLUMN_FIELD_ALBUM_ARTIST    = "album_artist";

        public static final String COLUMN_FIELD_ALBUM_ARTIST_ID = "album_artist_id";

        public static final String COLUMN_FIELD_ALBUM_ART       = "album_art";

        public static final String COLUMN_FIELD_ORIGINAL_ALBUM_ART = "original_album_art";

        public static final String COLUMN_FIELD_USER_HIDDEN     = "user_hidden";



        public static void createTable(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE " + TABLE_NAME + " ("
                    + _ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + COLUMN_FIELD_ALBUM_NAME + " TEXT UNIQUE ON CONFLICT IGNORE, "
                    + COLUMN_FIELD_ALBUM_ARTIST + " TEXT, "
                    + COLUMN_FIELD_ALBUM_ARTIST_ID + " INTEGER, "
                    + COLUMN_FIELD_ALBUM_ART + " TEXT, "
                    + COLUMN_FIELD_ORIGINAL_ALBUM_ART + " TEXT, "
                    + COLUMN_FIELD_USER_HIDDEN + " BOOLEAN);");
        }

        public static void destroyTable(SQLiteDatabase database) {
            database.execSQL("DROP TABLE " + TABLE_NAME + ";");
        }
    }

    public static abstract class AlbumArtist implements BaseColumns {

        public static final String TAG = AlbumArtist.class.getSimpleName();



        public final static String TABLE_NAME = "library_album_artist";



        public static final String COLUMN_FIELD_ARTIST_NAME      = "artist_name";

        public static final String COLUMN_FIELD_VISIBLE          = "visible";

        public static final String COLUMN_FIELD_USER_HIDDEN      = "user_hidden";



        public static void createTable(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE " + TABLE_NAME + " ("
                    + _ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + COLUMN_FIELD_ARTIST_NAME + " TEXT UNIQUE ON CONFLICT IGNORE, "
                    + COLUMN_FIELD_VISIBLE + " BOOLEAN, "
                    + COLUMN_FIELD_USER_HIDDEN + " BOOLEAN);");
        }

        public static void destroyTable(SQLiteDatabase database) {
            database.execSQL("DROP TABLE " + TABLE_NAME + ";");
        }

    }

    public static abstract class Artist implements BaseColumns {

        public static final String TAG = Artist.class.getSimpleName();



        public final static String TABLE_NAME = "library_artist";



        public static final String COLUMN_FIELD_ARTIST_NAME      = "artist_name";

        public static final String COLUMN_FIELD_VISIBLE          = "visible";

        public static final String COLUMN_FIELD_USER_HIDDEN      = "user_hidden";



        public static void createTable(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE " + TABLE_NAME + " ("
                    + _ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + COLUMN_FIELD_ARTIST_NAME + " TEXT UNIQUE ON CONFLICT IGNORE, "
                    + COLUMN_FIELD_VISIBLE + " BOOLEAN, "
                    + COLUMN_FIELD_USER_HIDDEN + " BOOLEAN);");
        }

        public static void destroyTable(SQLiteDatabase database) {
            database.execSQL("DROP TABLE " + TABLE_NAME + ";");
        }

    }

    public static abstract class Genre implements BaseColumns {

        public static final String TAG = Genre.class.getSimpleName();



        public final static String TABLE_NAME = "library_genre";



        public static final String COLUMN_FIELD_GENRE_NAME      = "genre_name";

        public static final String COLUMN_FIELD_VISIBLE         = "visible";

        public static final String COLUMN_FIELD_USER_HIDDEN     = "user_hidden";



        public static void createTable(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE " + TABLE_NAME + " ("
                    + _ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + COLUMN_FIELD_GENRE_NAME + " TEXT UNIQUE ON CONFLICT IGNORE, "
                    + COLUMN_FIELD_VISIBLE + " BOOLEAN, "
                    + COLUMN_FIELD_USER_HIDDEN + " BOOLEAN);");
        }

        public static void destroyTable(SQLiteDatabase database) {
            database.execSQL("DROP TABLE " + TABLE_NAME + ";");
        }

    }

    public static abstract class Media implements BaseColumns {

        public static final String TAG = Media.class.getSimpleName();



        public static final String TABLE_NAME = "media_audio";



        public static final String COLUMN_FIELD_URI             = "uri";

        public static final String COLUMN_FIELD_ART             = "art";

        public static final String COLUMN_FIELD_ORIGINAL_ART    = "original_art";

        public static final String COLUMN_FIELD_DURATION        = "duration";

        public static final String COLUMN_FIELD_BITRATE         = "bitrate";

        public static final String COLUMN_FIELD_SAMPLE_RATE     = "sample_rate";

        public static final String COLUMN_FIELD_CODEC           = "codec";

        public static final String COLUMN_FIELD_SCORE           = "score";

        public static final String COLUMN_FIELD_NOTE            = "note";

        public static final String COLUMN_FIELD_FIRST_PLAYED    = "first_played";

        public static final String COLUMN_FIELD_LAST_PLAYED     = "last_played";

        public static final String COLUMN_FIELD_TITLE           = "title";

        public static final String COLUMN_FIELD_ARTIST          = "artist";

        public static final String COLUMN_FIELD_ARTIST_ID       = "artist_id";

        public static final String COLUMN_FIELD_ALBUM_ARTIST    = "album_artist";

        public static final String COLUMN_FIELD_ALBUM_ARTIST_ID = "album_artist_id";

        public static final String COLUMN_FIELD_ALBUM           = "album";

        public static final String COLUMN_FIELD_ALBUM_ID        = "album_id";

        public static final String COLUMN_FIELD_GENRE           = "genre";

        public static final String COLUMN_FIELD_GENRE_ID        = "genre_id";

        public static final String COLUMN_FIELD_YEAR            = "year";

        public static final String COLUMN_FIELD_TRACK           = "track";

        public static final String COLUMN_FIELD_DISC            = "disc";

        public static final String COLUMN_FIELD_BPM             = "bpm";

        public static final String COLUMN_FIELD_COMMENT         = "comment";

        public static final String COLUMN_FIELD_LYRICS          = "lyrics";

        public static final String COLUMN_FIELD_VISIBLE         = "visible";

        public static final String COLUMN_FIELD_HAS_EMBEDDED_ART = "has_embedded_art";

        public static final String COLUMN_FIELD_USE_EMBEDDED_ART = "uses_embedded_art";

        public static final String COLUMN_FIELD_ORIGINALLY_USE_EMBEDDED_ART = "originally_uses_embedded_art";

        public static final String COLUMN_FIELD_IS_QUEUE_FILE_ENTRY   = "queue_file_entry"; /* queue entry is file from StorageFragment */

        public static final String COLUMN_FIELD_USER_HIDDEN           = "user_hidden";



        public static void createTable(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE " + TABLE_NAME + " ("
                    + _ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + COLUMN_FIELD_URI + " TEXT, "
                    + COLUMN_FIELD_ART + " TEXT, "
                    + COLUMN_FIELD_ORIGINAL_ART + " TEXT, "
                    + COLUMN_FIELD_DURATION + " INTEGER, "
                    + COLUMN_FIELD_BITRATE + " TEXT, "
                    + COLUMN_FIELD_SAMPLE_RATE + " INTEGER, "
                    + COLUMN_FIELD_CODEC + " TEXT, "
                    + COLUMN_FIELD_SCORE + " INTEGER, "
                    + COLUMN_FIELD_NOTE + " INTEGER, "
                    + COLUMN_FIELD_FIRST_PLAYED + " INTEGER, "
                    + COLUMN_FIELD_LAST_PLAYED + " INTEGER, "
                    + COLUMN_FIELD_TITLE + " TEXT, "
                    + COLUMN_FIELD_ARTIST + " TEXT, "
                    + COLUMN_FIELD_ARTIST_ID + " INTEGER, "
                    + COLUMN_FIELD_ALBUM_ARTIST + " TEXT, "
                    + COLUMN_FIELD_ALBUM_ARTIST_ID + " INTEGER, "
                    + COLUMN_FIELD_ALBUM + " TEXT, "
                    + COLUMN_FIELD_ALBUM_ID + " INTEGER, "
                    + COLUMN_FIELD_GENRE + " TEXT, "
                    + COLUMN_FIELD_GENRE_ID + " INTEGER, "
                    + COLUMN_FIELD_YEAR + " INTEGER, "
                    + COLUMN_FIELD_TRACK + " INTEGER, "
                    + COLUMN_FIELD_DISC + " INTEGER, "
                    + COLUMN_FIELD_BPM + " INTEGER, "
                    + COLUMN_FIELD_COMMENT + " TEXT, "
                    + COLUMN_FIELD_LYRICS + " TEXT, "
                    + COLUMN_FIELD_VISIBLE + " BOOLEAN, "
                    + COLUMN_FIELD_HAS_EMBEDDED_ART + " BOOLEAN, "
                    + COLUMN_FIELD_USE_EMBEDDED_ART + " BOOLEAN, "
                    + COLUMN_FIELD_ORIGINALLY_USE_EMBEDDED_ART + " BOOLEAN, "
                    + COLUMN_FIELD_IS_QUEUE_FILE_ENTRY + " BOOLEAN, "
                    + COLUMN_FIELD_USER_HIDDEN + " BOOLEAN);");
        }

        public static void destroyTable(SQLiteDatabase database) {
            database.execSQL("DROP TABLE " + TABLE_NAME + ";");
        }
    }

    public static abstract class Playlist implements BaseColumns {

        public static final String TAG = Playlist.class.getSimpleName();



        public final static String TABLE_NAME = "library_playlist";



        public static final String COLUMN_FIELD_PLAYLIST_NAME      = "playlist_name";

        public static final String COLUMN_FIELD_REPEAT_STATE       = "repeat_mode";

        public static final String COLUMN_FIELD_SHUFFLE_STATE      = "shuffle_mode";

        public static final String COLUMN_FIELD_VISIBLE            = "visible";

        public static final String COLUMN_FIELD_USER_HIDDEN     = "user_hidden";



        public static void createTable(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE " + TABLE_NAME + " ("
                    + _ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + COLUMN_FIELD_PLAYLIST_NAME + " TEXT UNIQUE ON CONFLICT FAIL, "
                    + COLUMN_FIELD_REPEAT_STATE + " INTEGER, "
                    + COLUMN_FIELD_SHUFFLE_STATE + " INTEGER, "
                    + COLUMN_FIELD_VISIBLE + " BOOLEAN, "
                    + COLUMN_FIELD_USER_HIDDEN + " BOOLEAN);");
        }

        public static void destroyTable(SQLiteDatabase database) {
            database.execSQL("DROP TABLE " + TABLE_NAME + ";");
        }

    }

    public static abstract class PlaylistEntry implements BaseColumns {

        public static final String TAG = PlaylistEntry.class.getSimpleName();



        public final static String TABLE_NAME = "library_playlist_entry";



        public static final String COLUMN_FIELD_PLAYLIST_ID     = "playlist_id";

        public static final String COLUMN_FIELD_POSITION        = "position";

        public static final String COLUMN_FIELD_SONG_ID         = "song_id";



        public static void createTable(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE " + TABLE_NAME + " ("
                    + _ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + COLUMN_FIELD_PLAYLIST_ID + " INTEGER, "
                    + COLUMN_FIELD_POSITION + " INTEGER, "
                    + COLUMN_FIELD_SONG_ID + " INTEGER);");
        }

        public static void destroyTable(SQLiteDatabase database) {
            database.execSQL("DROP TABLE " + TABLE_NAME + ";");
        }

    }

    public static abstract class ScanDirectory implements BaseColumns {

        public static final String TAG = ScanDirectory.class.getSimpleName();



        public final static String TABLE_NAME = "library_scan_paths";



        public static final String COLUMN_FIELD_SCAN_DIRECTORY_NAME      = "directory_path";

        public static final String COLUMN_FIELD_SCAN_DIRECTORY_IS_EXCLUDED = "exclude";



        public static void createTable(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE " + TABLE_NAME + " ("
                    + _ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + COLUMN_FIELD_SCAN_DIRECTORY_NAME + " TEXT UNIQUE ON CONFLICT FAIL, "
                    + COLUMN_FIELD_SCAN_DIRECTORY_IS_EXCLUDED + " BOOLEAN);");
        }

        public static void destroyTable(SQLiteDatabase database) {
            database.execSQL("DROP TABLE " + TABLE_NAME + ";");
        }
    }
}
