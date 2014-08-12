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
package eu.chepy.audiokit.core.service.providers.local.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.File;

import eu.chepy.audiokit.R;
import eu.chepy.audiokit.utils.LogUtils;

public class OpenHelper extends SQLiteOpenHelper {

    public static final String TAG = OpenHelper.class.getSimpleName();


    private final static int DATABASE_VERSION = 1;

    private Context context = null;

    private int providerId;

    public OpenHelper(Context context, int providerId) {
        super(context, "provider-" + providerId + ".db", null, DATABASE_VERSION);
        this.context = context;
        this.providerId = providerId;
    }

    public void deleteDatabaseFile() {
        File databaseFile = context.getDatabasePath("provDb" + providerId + ".db");
        if (databaseFile != null) {
            boolean deleted = databaseFile.delete();
            LogUtils.LOGI(TAG, "deleting provider data (" + providerId + ") : " + deleted);
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
        Entities.Playlist.createTable(database);
        Entities.PlaylistEntry.createTable(database);
        Entities.Media.createTable(database);
        Entities.ScanDirectory.createTable(database);

		/* Current queue */
        ContentValues contentValues = new ContentValues();
        contentValues.put(Entities.Playlist._ID, 0);
        contentValues.put(Entities.Playlist.COLUMN_FIELD_PLAYLIST_NAME, "");
        contentValues.put(Entities.Playlist.COLUMN_FIELD_VISIBLE, false);
        contentValues.put(Entities.Playlist.COLUMN_FIELD_USER_HIDDEN, false);
        database.insert(Entities.Playlist.TABLE_NAME, null, contentValues);

		/* Favorite playlist */
        contentValues.clear();
        //contentValues.put(Playlist._ID, 1);
        contentValues.put(Entities.Playlist.COLUMN_FIELD_PLAYLIST_NAME, context.getString(R.string.label_favorites));
        contentValues.put(Entities.Playlist.COLUMN_FIELD_VISIBLE, true);
        contentValues.put(Entities.Playlist.COLUMN_FIELD_USER_HIDDEN, false);
        database.insert(Entities.Playlist.TABLE_NAME, null, contentValues);
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
		/*
		 * Library tables
		 */
        Entities.Album.destroyTable(database);
        Entities.AlbumArtist.destroyTable(database);
        Entities.Artist.destroyTable(database);
        Entities.Genre.destroyTable(database);
        Entities.Playlist.destroyTable(database);
        Entities.PlaylistEntry.destroyTable(database);
        Entities.Media.destroyTable(database);
        Entities.ScanDirectory.destroyTable(database);

        onCreate(database);
    }

    @Override
    public void onDowngrade(SQLiteDatabase database, int oldVersion, int newVersion) {
		/*
		 * Library tables
		 */
        Entities.Album.destroyTable(database);
        Entities.AlbumArtist.destroyTable(database);
        Entities.Artist.destroyTable(database);
        Entities.Genre.destroyTable(database);
        Entities.Playlist.destroyTable(database);
        Entities.PlaylistEntry.destroyTable(database);
        Entities.Media.destroyTable(database);
        Entities.ScanDirectory.destroyTable(database);

        onCreate(database);
    }
}
