package net.opusapp.player.core.service.providers.local.scanner;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.preference.PreferenceManager;

import net.opusapp.player.R;
import net.opusapp.player.core.service.providers.AbstractMediaManager;
import net.opusapp.player.core.service.providers.local.LocalMediaManager;
import net.opusapp.player.core.service.providers.local.LocalProvider;
import net.opusapp.player.core.service.providers.local.database.Entities;
import net.opusapp.player.core.service.providers.local.database.OpenHelper;
import net.opusapp.player.core.service.utils.CursorUtils;
import net.opusapp.player.ui.utils.PlayerApplication;
import net.opusapp.player.utils.LogUtils;
import net.opusapp.player.utils.backport.android.content.SharedPreferencesCompat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class MediaScanner {

    public static final String TAG = MediaScanner.class.getSimpleName();



    private boolean mIsRunning;

    private ScannerThread mScannerThread;



    private LocalMediaManager mManager;

    private OpenHelper mDatabaseOpenHelper;



    public MediaScanner(final LocalMediaManager parent, final OpenHelper databaseOpenHelper) {
        mIsRunning = false;
        mScannerThread = null;
        mManager = parent;
        mDatabaseOpenHelper = databaseOpenHelper;
    }

    public static Set<String> getCoverExtensions() {
        final SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(PlayerApplication.context);

        final Set<String> defaults = new HashSet<>(Arrays.asList(PlayerApplication.context.getResources().getStringArray(R.array.cover_exts)));
        final Set<String> extensionSet = SharedPreferencesCompat.getStringSet(sharedPrefs, PlayerApplication.context.getString(R.string.key_cover_exts), defaults);

        if(extensionSet.size() == 0) {
            return defaults;
        }

        return extensionSet;
    }

    public static Set<String> getMediaExtensions(int providerId) {
        final OpenHelper databaseOpenHelper = new OpenHelper(providerId);
        final SQLiteDatabase database = databaseOpenHelper.getReadableDatabase();

        final Set<String> audioFilesExtensions = new HashSet<>();

        final String[] columns = new String[] {
                Entities.FileExtensions.COLUMN_FIELD_EXTENSION
        };

        final int COLUMN_EXTENSION = 0;
        final Cursor cursor = database.query(Entities.FileExtensions.TABLE_NAME, columns, null, null, null, null, null, null);

        if (CursorUtils.ifNotEmpty(cursor)) {
            while (cursor.moveToNext()) {
                audioFilesExtensions.add(cursor.getString(COLUMN_EXTENSION).toLowerCase());
            }
            CursorUtils.free(cursor);
        }

        return audioFilesExtensions;
    }


    public synchronized void start() {
        if (mScannerThread == null) {
            mScannerThread = new ScannerThread(this);
            mScannerThread.start();
        }
    }

    public synchronized void stop() {
        if (mScannerThread == null) {
            mIsRunning = false;
        }
        else {
            mScannerThread.requestCancellation();
        }
    }

    public AbstractMediaManager getManager() {
        return mManager;
    }

    public OpenHelper getDatabaseOpenHelper() {
        return mDatabaseOpenHelper;
    }

    public synchronized boolean isRunning() {
        return mIsRunning;
    }

    public synchronized void notifyScannerHasStarted() {
        LogUtils.LOGI(TAG, "scan started");

        mIsRunning = true;

        final LocalProvider provider = (LocalProvider) mManager.getProvider();
        provider.notifyLibraryScanStarted();
    }

    public synchronized void notifyScannerHasUpdated() {
        LogUtils.LOGI(TAG, "scan update");

        final LocalProvider provider = (LocalProvider) mManager.getProvider();
        provider.notifyLibraryChanges();
    }

    public synchronized void notifyScannerHasFinished() {
        LogUtils.LOGI(TAG, "scan finished");

        mScannerThread = null;
        mIsRunning = false;

        final LocalProvider provider = (LocalProvider) mManager.getProvider();
        provider.notifyLibraryScanStopped();
    }
}
