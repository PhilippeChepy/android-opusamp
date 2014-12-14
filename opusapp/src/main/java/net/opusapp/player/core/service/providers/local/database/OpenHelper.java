/*
 * OpenHelper.java
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

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import net.opusapp.player.R;
import net.opusapp.player.ui.utils.PlayerApplication;
import net.opusapp.player.utils.LogUtils;

import java.io.File;

public class OpenHelper extends SQLiteOpenHelper {

    public static final String TAG = OpenHelper.class.getSimpleName();


    private final static int DATABASE_VERSION = 3;

    private int mProviderId;

    private String[] mDefaultExtensionList = new String[] {
            "3gp", "aac", "ac3", "ape", "asf", "flac", "m4a", "m4b", "m4p", "mka", "mks", "mkv",
            "mov", "mp+", "mp1", "mp2", "mp3", "mp4", "mpc", "mpp", "oga", "ogg", "ogv", "ogx",
            "opus", "tta", "wav", "wave", "wma", "wmv", "wv"
    };

    public OpenHelper(int providerId) {
        super(PlayerApplication.context, "provider-" + providerId + ".db", null, DATABASE_VERSION);
        mProviderId = providerId;
    }

    public void deleteDatabaseFile() {
        File databaseFile = PlayerApplication.context.getDatabasePath("provider-" + mProviderId + ".db");
        if (databaseFile != null) {
            boolean deleted = databaseFile.delete();
            LogUtils.LOGI(TAG, "deleting provider data (" + mProviderId + ") : " + deleted);
        }
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
		/*
		 * Library tables
		 */
        Entities.Album.createTable(database);
        Entities.AlbumArtist.createTable(database);
        Entities.Artist.createTable(database);
        Entities.Genre.createTable(database);
        Entities.Art.createTable(database);
        Entities.AlbumHasArts.createTable(database);
        Entities.Playlist.createTable(database);
        Entities.PlaylistEntry.createTable(database);
        Entities.Media.createTable(database);
        Entities.ScanDirectory.createTable(database);
        Entities.FileExtensions.createTable(database);



        /// Playlists
		// Current queue
        ContentValues contentValues = new ContentValues();
        contentValues.put(Entities.Playlist._ID, 0);
        contentValues.put(Entities.Playlist.COLUMN_FIELD_PLAYLIST_NAME, "");
        contentValues.put(Entities.Playlist.COLUMN_FIELD_VISIBLE, false);
        contentValues.put(Entities.Playlist.COLUMN_FIELD_USER_HIDDEN, false);
        database.insert(Entities.Playlist.TABLE_NAME, null, contentValues);

		// Favorite playlist
        contentValues.clear();
        contentValues.put(Entities.Playlist.COLUMN_FIELD_PLAYLIST_NAME, PlayerApplication.context.getString(R.string.label_favorites));
        contentValues.put(Entities.Playlist.COLUMN_FIELD_VISIBLE, true);
        contentValues.put(Entities.Playlist.COLUMN_FIELD_USER_HIDDEN, false);
        database.insert(Entities.Playlist.TABLE_NAME, null, contentValues);

        /// File extensions
        for (final String extension : mDefaultExtensionList) {
            contentValues.clear();
            contentValues.put(Entities.FileExtensions.COLUMN_FIELD_EXTENSION, extension);
            database.insert(Entities.FileExtensions.TABLE_NAME, null, contentValues);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        if (oldVersion == 1 && newVersion == 2) {
            LogUtils.LOGE(TAG, "upgrading local provider database (version 1 -> 2)");

            Entities.FileExtensions.createTable(database);

            ContentValues contentValues = new ContentValues();
            /// File extensions
            for (final String extension : mDefaultExtensionList) {
                contentValues.put(Entities.FileExtensions.COLUMN_FIELD_EXTENSION, extension);
                database.insert(Entities.FileExtensions.TABLE_NAME, null, contentValues);
                contentValues.clear();
            }
            oldVersion = 2;
        }

        if (oldVersion == 2 && newVersion == 3) {
            LogUtils.LOGE(TAG, "upgrading local provider database (version 2 -> 3)");

            database.execSQL(
                    "UPDATE " + Entities.Media.TABLE_NAME + " SET " +
                            Entities.Media.COLUMN_FIELD_ART_ID + " = NULL " +
                            "WHERE " + Entities.Media.COLUMN_FIELD_ART_ID + " IN (" +
                            " SELECT " + Entities.Art._ID +
                            " FROM " + Entities.Art.TABLE_NAME +
                            " WHERE " + Entities.Art.COLUMN_FIELD_URI_IS_EMBEDDED + " = 1)");

            database.execSQL(
                    "UPDATE " + Entities.Media.TABLE_NAME + " SET " +
                            Entities.Media.COLUMN_FIELD_ORIGINAL_ART_ID + " = NULL " +
                            "WHERE " + Entities.Media.COLUMN_FIELD_ORIGINAL_ART_ID + " IN (" +
                            " SELECT " + Entities.Art._ID +
                            " FROM " + Entities.Art.TABLE_NAME +
                            " WHERE " + Entities.Art.COLUMN_FIELD_URI_IS_EMBEDDED + " = 1)");

            database.execSQL(
                    "UPDATE " + Entities.Album.TABLE_NAME + " SET " +
                            Entities.Album.COLUMN_FIELD_ALBUM_ART_ID + " = NULL " +
                            "WHERE " + Entities.Album.COLUMN_FIELD_ALBUM_ART_ID + " IN (" +
                            " SELECT " + Entities.Art._ID +
                            " FROM " + Entities.Art.TABLE_NAME +
                            " WHERE " + Entities.Art.COLUMN_FIELD_URI_IS_EMBEDDED + " = 1)");

            database.execSQL(
                    "UPDATE " + Entities.Album.TABLE_NAME + " SET " +
                            Entities.Album.COLUMN_FIELD_ORIGINAL_ALBUM_ART_ID + " = NULL " +
                            "WHERE " + Entities.Album.COLUMN_FIELD_ORIGINAL_ALBUM_ART_ID + " IN (" +
                            " SELECT " + Entities.Art._ID +
                            " FROM " + Entities.Art.TABLE_NAME +
                            " WHERE " + Entities.Art.COLUMN_FIELD_URI_IS_EMBEDDED + " = 1)");

            oldVersion = 3;
        }

        if (oldVersion != newVersion) {
            Entities.Album.destroyTable(database);
            Entities.AlbumArtist.destroyTable(database);
            Entities.Artist.destroyTable(database);
            Entities.Genre.destroyTable(database);
            Entities.Art.destroyTable(database);
            Entities.AlbumHasArts.destroyTable(database);
            Entities.Playlist.destroyTable(database);
            Entities.PlaylistEntry.destroyTable(database);
            Entities.Media.destroyTable(database);
            Entities.ScanDirectory.destroyTable(database);

            Entities.FileExtensions.destroyTable(database);

            onCreate(database);
        }
    }

    @Override
    public void onDowngrade(SQLiteDatabase database, int oldVersion, int newVersion) {
		onUpgrade(database, oldVersion, newVersion);
    }
}
