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


    private final static int DATABASE_VERSION = 2;

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

        if (DATABASE_VERSION >= 2) {
            Entities.FileExtensions.createTable(database);
        }


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
            Entities.FileExtensions.createTable(database);

            ContentValues contentValues = new ContentValues();
            /// File extensions
            for (final String extension : mDefaultExtensionList) {
                contentValues.put(Entities.FileExtensions.COLUMN_FIELD_EXTENSION, extension);
                database.insert(Entities.FileExtensions.TABLE_NAME, null, contentValues);
                contentValues.clear();
            }
        }
        else {
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

            if (DATABASE_VERSION >= 2) {
                Entities.FileExtensions.destroyTable(database);
            }

            onCreate(database);
        }
    }

    @Override
    public void onDowngrade(SQLiteDatabase database, int oldVersion, int newVersion) {
		onUpgrade(database, oldVersion, newVersion);
    }
}
