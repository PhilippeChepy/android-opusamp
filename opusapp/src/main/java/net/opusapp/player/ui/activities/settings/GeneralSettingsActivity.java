/*
 * SettingsLibrariesActivity.java
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
package net.opusapp.player.ui.activities.settings;

import android.content.Intent;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.app.ActionBarActivity;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.TextView;

import com.mobeta.android.dslv.DragSortListView;

import net.opusapp.player.R;
import net.opusapp.player.core.service.providers.AbstractMediaManager;
import net.opusapp.player.core.service.providers.index.database.Entities;
import net.opusapp.player.core.service.utils.AbstractSimpleCursorLoader;
import net.opusapp.player.ui.adapter.ProviderAdapter;
import net.opusapp.player.ui.utils.MusicConnector;
import net.opusapp.player.ui.utils.PlayerApplication;
import net.opusapp.player.utils.LogUtils;

public class GeneralSettingsActivity extends ActionBarActivity implements
        LoaderManager.LoaderCallbacks<Cursor>,
        AdapterView.OnItemClickListener,
        DragSortListView.DropListener,
        DragSortListView.DragScrollProfile {

    public final static String TAG = GeneralSettingsActivity.class.getSimpleName();



    // Menu items
    private static final int CONTEXT_MENUITEM_EDIT = 1;

    private static final int CONTEXT_MENUITEM_DELETE = 2;



    // Content UI
    private DragSortListView mListView;

    private ProviderAdapter mAdapter;

    private Cursor mCursor;



    // ContentType Data
    private final static String[] mRequestedFields = new String[] {
            Entities.Provider._ID,
            Entities.Provider.COLUMN_FIELD_PROVIDER_NAME,
            Entities.Provider.COLUMN_FIELD_PROVIDER_TYPE
    };

    public static final int COLUMN_PROVIDER_ID = 0;

    public static final int COLUMN_PROVIDER_NAME = 1;

    public static final int COLUMN_PROVIDER_TYPE = 2;

    public void refresh() {
        getSupportLoaderManager().restartLoader(0, null, this);
        PlayerApplication.allocateMediaManagers();
    }

    @Override
    public float getSpeed(float w, long t) {
        if (w > 0.8F) {
            return mAdapter.getCount() / 0.001F;
        }
        return 10.0F * w;
    }

    @Override
    public void drop(int from, int to) {
        doMoveProviders(from, to);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_settings);

        mListView = (DragSortListView) findViewById(R.id.dragable_list_base);

        TextView emptyView = (TextView) findViewById(R.id.dragable_list_empty);
        emptyView.setText(R.string.ni_providers);
        mListView.setEmptyView(emptyView);

        // Header view
        View globalSettingsView = getLayoutInflater().inflate(R.layout.view_item_settings, mListView, false);
        globalSettingsView.setFocusable(true);
        globalSettingsView.setClickable(true);
        globalSettingsView.setOnClickListener(mHeaderViewClickListener);

        TextView headerTextView = (TextView) globalSettingsView.findViewById(R.id.line_one);
        headerTextView.setText(R.string.drawer_item_label_application_settings);

        // Footer view
        View addLibraryView = getLayoutInflater().inflate(R.layout.view_item_settings, mListView, false);
        addLibraryView.setFocusable(true);
        addLibraryView.setClickable(true);
        addLibraryView.setOnClickListener(mFooterViewClickListener);

        TextView footerTextView = (TextView) addLibraryView.findViewById(R.id.line_one);
        footerTextView.setText(R.string.menuitem_label_add_provider);

        mAdapter = new ProviderAdapter(this, R.layout.view_item_double_line_dragable, new int[] { COLUMN_PROVIDER_NAME, COLUMN_PROVIDER_TYPE });

        mListView.setOnCreateContextMenuListener(this);
        mListView.setOnItemClickListener(this);
        mListView.addHeaderView(globalSettingsView);
        mListView.addFooterView(addLibraryView);
        mListView.setAdapter(mAdapter);
        mListView.setDragScrollProfile(this);
        mListView.setDropListener(this);

        getSupportLoaderManager().initLoader(0, null, this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        PlayerApplication.applyActionBar(this);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {

        return new AbstractSimpleCursorLoader(this) {
            @Override
            public Cursor loadInBackground() {
                final SQLiteDatabase database = PlayerApplication.getDatabaseOpenHelper().getReadableDatabase();
                if (database != null) {
                    final String orderBy = Entities.Provider.COLUMN_FIELD_PROVIDER_POSITION;

                    return database.query(Entities.Provider.TABLE_NAME, mRequestedFields, null, null, null, null, orderBy);
                }
                return null;
            }
        };
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (data == null) {
            return;
        }

        mCursor = data;
        mAdapter.changeCursor(data);
        mListView.invalidateViews();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        if (mAdapter != null) {
            mAdapter.changeCursor(null);
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        menu.setHeaderTitle(R.string.drawer_item_label_manage_libraries);
        menu.add(Menu.NONE, CONTEXT_MENUITEM_EDIT, 1, R.string.menuitem_label_edit);

        if (mCursor.getInt(COLUMN_PROVIDER_ID) != 1) {
            menu.add(Menu.NONE, CONTEXT_MENUITEM_DELETE, 2, R.string.menuitem_label_delete);
        }
    }

    @Override
    public boolean onContextItemSelected(android.view.MenuItem item) {
        final int providerId = mCursor.getInt(COLUMN_PROVIDER_ID);
        final String providerName = mCursor.getString(COLUMN_PROVIDER_NAME);

        switch (item.getItemId()) {
            case CONTEXT_MENUITEM_EDIT:

                final Runnable completionAction = new Runnable() {
                    @Override
                    public void run() {
                        refresh();
                    }
                };

                MusicConnector.editLibrary(this, providerId, providerName, completionAction);
                return true;
            case CONTEXT_MENUITEM_DELETE:
                if (providerId != 1) {
                    int itemIndex = PlayerApplication.getManagerIndex(providerId);
                    final SQLiteDatabase database = PlayerApplication.getDatabaseOpenHelper().getWritableDatabase();

                    if (database != null && itemIndex >= 0) {
                        // Delete database registration
                        database.delete(Entities.Provider.TABLE_NAME, Entities.Provider._ID + " = ? ",
                                new String[] {
                                        String.valueOf(providerId)
                                });

                        // Delete provider specific content
                        final AbstractMediaManager mediaManager = PlayerApplication.mediaManager(providerId);
                        mediaManager.getProvider().erase();
                        refresh();
                    }

                    return true;
                }
        }

        return super.onContextItemSelected(item);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
        MusicConnector.configureLibrary(this, mCursor.getInt(COLUMN_PROVIDER_ID));
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    protected void doMoveProviders(int indexFrom, int indexTo) {
        LogUtils.LOGD(TAG, "from = " + indexFrom + " to = " + indexTo);

        if (indexFrom == indexTo) {
            return; // done.
        }

        SQLiteDatabase database = PlayerApplication.getDatabaseOpenHelper().getWritableDatabase();
        if (database != null) {
            database.beginTransaction();
            try {
                if (indexFrom < indexTo) {
                    int lowerIndex = Math.min(indexFrom, indexTo);
                    int upperIndex = Math.max(indexFrom, indexTo);

                    // Provider is 0, 1, 2, 3, 4, ..., indexFrom, indexFrom + 1, indexFrom + 2, ..., indexTo, ...
                    database.execSQL(
                            "UPDATE " + Entities.Provider.TABLE_NAME + " " +
                            "SET " + Entities.Provider.COLUMN_FIELD_PROVIDER_POSITION + " = -1 " +
                            "WHERE " + Entities.Provider.COLUMN_FIELD_PROVIDER_POSITION + " = " + String.valueOf(lowerIndex)
                    );

                    // Provider is -1, 0, 1, 2, 3, 4, ..., indexFrom + 1, indexFrom + 2, ...
                    database.execSQL(
                            "UPDATE " + Entities.Provider.TABLE_NAME + " " +
                            "SET " + Entities.Provider.COLUMN_FIELD_PROVIDER_POSITION + " = " + Entities.Provider.COLUMN_FIELD_PROVIDER_POSITION + " - 1 " +
                            "WHERE " + Entities.Provider.COLUMN_FIELD_PROVIDER_POSITION +
                                " BETWEEN " + String.valueOf(lowerIndex) +
                                    " AND " + String.valueOf(upperIndex)
                    );


                    // Provider is 0, 1, 2, 3, 4, ..., indexFrom + 1, indexFrom + 2, ..., indexTo - 1, indexTo, ...
                    database.execSQL(
                            "UPDATE " + Entities.Provider.TABLE_NAME + " " +
                            "SET " + Entities.Provider.COLUMN_FIELD_PROVIDER_POSITION + " = " + String.valueOf(upperIndex) + " " +
                            "WHERE " + Entities.Provider.COLUMN_FIELD_PROVIDER_POSITION + " = -1"
                    );
                } else {
                    int lowerIndex = Math.min(indexFrom, indexTo);
                    int upperIndex = Math.max(indexFrom, indexTo);

                    database.execSQL(
                            "UPDATE " + Entities.Provider.TABLE_NAME + " " +
                            "SET " + Entities.Provider.COLUMN_FIELD_PROVIDER_POSITION + " = -1 " +
                            "WHERE " + Entities.Provider.COLUMN_FIELD_PROVIDER_POSITION + " = " + String.valueOf(upperIndex)
                    );

                    database.execSQL(
                            "UPDATE " + Entities.Provider.TABLE_NAME + " " +
                            "SET " + Entities.Provider.COLUMN_FIELD_PROVIDER_POSITION + " = " + Entities.Provider.COLUMN_FIELD_PROVIDER_POSITION + " + 1 " +
                            "WHERE " + Entities.Provider.COLUMN_FIELD_PROVIDER_POSITION +
                                " BETWEEN " + String.valueOf(lowerIndex) +
                                    " AND " + String.valueOf(upperIndex)
                    );

                    database.execSQL(
                            "UPDATE " + Entities.Provider.TABLE_NAME + " " +
                            "SET " + Entities.Provider.COLUMN_FIELD_PROVIDER_POSITION + " = " + String.valueOf(lowerIndex) + " " +
                            "WHERE " + Entities.Provider.COLUMN_FIELD_PROVIDER_POSITION + " = -1"
                    );
                }
                database.setTransactionSuccessful();
            }
            catch (final SQLException sqlException) {
                LogUtils.LOGException(TAG, "doMoveProviders", 0, sqlException);
            }
            finally {
                database.endTransaction();
            }

            refresh();
        }
    }

    private View.OnClickListener mHeaderViewClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            LogUtils.LOGW(TAG, "header view");
            final Intent intent = new Intent(getApplicationContext(), ApplicationSettingsActivity.class);
            startActivity(intent);
        }
    };

    private View.OnClickListener mFooterViewClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            LogUtils.LOGW(TAG, "footer view");
            MusicConnector.addLibrary(GeneralSettingsActivity.this, new Runnable() {
                @Override
                public void run() {
                    refresh();
                }
            });
        }
    };

}
