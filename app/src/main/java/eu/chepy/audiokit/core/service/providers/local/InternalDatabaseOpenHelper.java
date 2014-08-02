package eu.chepy.audiokit.core.service.providers.local;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.io.File;

import eu.chepy.audiokit.R;
import eu.chepy.audiokit.core.service.providers.local.entities.Album;
import eu.chepy.audiokit.core.service.providers.local.entities.AlbumArtist;
import eu.chepy.audiokit.core.service.providers.local.entities.Artist;
import eu.chepy.audiokit.core.service.providers.local.entities.Genre;
import eu.chepy.audiokit.core.service.providers.local.entities.Media;
import eu.chepy.audiokit.core.service.providers.local.entities.Playlist;
import eu.chepy.audiokit.core.service.providers.local.entities.PlaylistEntry;
import eu.chepy.audiokit.core.service.providers.local.entities.ScanDirectory;

public class InternalDatabaseOpenHelper extends SQLiteOpenHelper {

    public static final String TAG = InternalDatabaseOpenHelper.class.getSimpleName();


    private final static int DATABASE_VERSION = 1;

    private Context context = null;

    private int providerId;

    public InternalDatabaseOpenHelper(Context context, int providerId) {
        super(context, "provDb" + providerId + ".db", null, DATABASE_VERSION);
        this.context = context;
        this.providerId = providerId;
    }

    public void deleteDatabaseFile() {
        File databaseFile = context.getDatabasePath("provDb" + providerId + ".db");
        if (databaseFile != null) {
            boolean deleted = databaseFile.delete();
            Log.w(TAG, "deleting provider data (" + providerId + ") : " + deleted);
        }
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
		/*
		 * Library tables
		 */
        Album.createTable(database);
        AlbumArtist.createTable(database);
        Artist.createTable(database);
        Genre.createTable(database);
        Playlist.createTable(database);
        PlaylistEntry.createTable(database);
        Media.createTable(database);
        ScanDirectory.createTable(database);

		/* Current queue */
        ContentValues contentValues = new ContentValues();
        contentValues.put(Playlist.COLUMN_FIELD_PLAYLIST_ID, 0);
        contentValues.put(Playlist.COLUMN_FIELD_PLAYLIST_NAME, "");
        contentValues.put(Playlist.COLUMN_FIELD_VISIBLE, false);
        contentValues.put(Playlist.COLUMN_FIELD_USER_HIDDEN, false);
        database.insert(Playlist.TABLE_NAME, null, contentValues);

		/* Favorite playlist */
        contentValues.clear();
        //contentValues.put(Playlist.COLUMN_FIELD_PLAYLIST_ID, 1);
        contentValues.put(Playlist.COLUMN_FIELD_PLAYLIST_NAME, context.getString(R.string.label_favorites));
        contentValues.put(Playlist.COLUMN_FIELD_VISIBLE, true);
        contentValues.put(Playlist.COLUMN_FIELD_USER_HIDDEN, false);
        database.insert(Playlist.TABLE_NAME, null, contentValues);
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
		/*
		 * Library tables
		 */
        Album.destroyTable(database);
        AlbumArtist.destroyTable(database);
        Artist.destroyTable(database);
        Genre.destroyTable(database);
        Playlist.destroyTable(database);
        PlaylistEntry.destroyTable(database);
        Media.destroyTable(database);
        ScanDirectory.destroyTable(database);

        onCreate(database);
    }

    @Override
    public void onDowngrade(SQLiteDatabase database, int oldVersion, int newVersion) {
		/*
		 * Library tables
		 */
        Album.destroyTable(database);
        AlbumArtist.destroyTable(database);
        Artist.destroyTable(database);
        Genre.destroyTable(database);
        Playlist.destroyTable(database);
        PlaylistEntry.destroyTable(database);
        Media.destroyTable(database);
        ScanDirectory.destroyTable(database);

        onCreate(database);
    }
}
