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
package net.opusapp.player.ui.activities;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.TextView;

import com.mobeta.android.dslv.DragSortListView;

import net.opusapp.player.R;
import net.opusapp.player.core.service.providers.AbstractMediaManager;
import net.opusapp.player.core.service.providers.MediaManagerFactory;
import net.opusapp.player.core.service.providers.MediaManagerFactory.MediaManagerDescription;
import net.opusapp.player.core.service.providers.index.database.Entities;
import net.opusapp.player.core.service.utils.AbstractSimpleCursorLoader;
import net.opusapp.player.ui.adapter.ProviderAdapter;
import net.opusapp.player.ui.utils.PlayerApplication;
import net.opusapp.player.utils.LogUtils;

import java.util.ArrayList;

public class SettingsLibrariesActivity extends ActionBarActivity implements
        LoaderManager.LoaderCallbacks<Cursor>,
        AdapterView.OnItemClickListener,
        DragSortListView.DropListener,
        DragSortListView.DragScrollProfile {

    public final static String TAG = SettingsLibrariesActivity.class.getSimpleName();



    /*

     */
    private static final int OPTION_MENUITEM_ADD = 1;

    private static final int CONTEXT_MENUITEM_EDIT = 1;

    private static final int CONTEXT_MENUITEM_DELETE = 2;



    /*
        ContentType UI
     */
    private DragSortListView listView;

    private ProviderAdapter adapter;

    private Cursor cursor;



    /*
        ContentType Data
     */
    private final static String[] requestedFields = new String[] {
            Entities.Provider._ID,
            Entities.Provider.COLUMN_FIELD_PROVIDER_NAME,
            Entities.Provider.COLUMN_FIELD_PROVIDER_TYPE
    };

    public static final int COLUMN_PROVIDER_ID = 0;

    public static final int COLUMN_PROVIDER_NAME = 1;

    public static final int COLUMN_PROVIDER_TYPE = 2;

    public void doRefresh() {
        getSupportLoaderManager().restartLoader(0, null, this);
        PlayerApplication.allocateMediaManagers();
    }

    @Override
    public float getSpeed(float w, long t) {
        if (w > 0.8F) {
            return adapter.getCount() / 0.001F;
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

        setContentView(R.layout.activity_library_settings);
        final Toolbar toolbar = (Toolbar) findViewById(R.id.main_toolbar);
        setSupportActionBar(toolbar);

        listView = (DragSortListView) findViewById(R.id.dragable_list_base);

        TextView emptyView = (TextView) findViewById(R.id.dragable_list_empty);
        emptyView.setText(R.string.ni_providers);
        listView.setEmptyView(emptyView);

        adapter = new ProviderAdapter(this, R.layout.view_item_double_line_dragable, new int[] { COLUMN_PROVIDER_NAME, COLUMN_PROVIDER_TYPE });

        listView.setOnCreateContextMenuListener(this);
        listView.setOnItemClickListener(this);
        listView.setAdapter(adapter);
        listView.setDragScrollProfile(this);
        listView.setDropListener(this);

        getSupportLoaderManager().initLoader(0, null, this);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {

        return new AbstractSimpleCursorLoader(this) {
            @Override
            public Cursor loadInBackground() {
                final SQLiteDatabase database = PlayerApplication.getDatabaseOpenHelper().getReadableDatabase();
                if (database != null) {
                    final String orderBy = Entities.Provider.COLUMN_FIELD_PROVIDER_POSITION;

                    return database.query(Entities.Provider.TABLE_NAME, requestedFields, null, null, null, null, orderBy);
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

        cursor = data;
        adapter.changeCursor(data);
        listView.invalidateViews();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        if (adapter != null) {
            adapter.changeCursor(null);
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        menu.setHeaderTitle(R.string.drawer_item_label_manage_libraries);
        menu.add(Menu.NONE, CONTEXT_MENUITEM_EDIT, 1, R.string.menuitem_label_edit);

        if (cursor.getInt(COLUMN_PROVIDER_ID) != 1) {
            menu.add(Menu.NONE, CONTEXT_MENUITEM_DELETE, 2, R.string.menuitem_label_delete);
        }
    }

    @Override
    public boolean onContextItemSelected(android.view.MenuItem item) {
        final int providerId = cursor.getInt(COLUMN_PROVIDER_ID);

        switch (item.getItemId()) {
            case CONTEXT_MENUITEM_EDIT:
                final EditText nameEditText = new EditText(this);
                nameEditText.setText(cursor.getString(COLUMN_PROVIDER_NAME));

                final DialogInterface.OnClickListener newLibraryOnClickListener = new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        SQLiteDatabase database = PlayerApplication.getDatabaseOpenHelper().getWritableDatabase();
                        final Editable collectionName = nameEditText.getText();

                        if (database != null && collectionName != null) {
                            int mediaProviderType = cursor.getInt(COLUMN_PROVIDER_TYPE);

                            ContentValues contentValues = new ContentValues();
                            contentValues.put(Entities.Provider.COLUMN_FIELD_PROVIDER_NAME, collectionName.toString());
                            contentValues.put(Entities.Provider.COLUMN_FIELD_PROVIDER_TYPE, mediaProviderType);

                            database.update(Entities.Provider.TABLE_NAME, contentValues, Entities.Provider._ID + " = ? ",
                                    new String[] {
                                            String.valueOf(providerId)
                                    });

                            doLibraryConfiguration(providerId, mediaProviderType);
                            doRefresh();
                        }
                    }
                };

                doLibraryEdition(nameEditText, newLibraryOnClickListener);
                return true;
            case CONTEXT_MENUITEM_DELETE:
                if (providerId != 1) {
                    int itemIndex = PlayerApplication.getManagerIndex(providerId);
                    final SQLiteDatabase database = PlayerApplication.getDatabaseOpenHelper().getWritableDatabase();

                    if (database != null && itemIndex >= 0) {
                        final AbstractMediaManager mediaManager = PlayerApplication.mediaManagers[itemIndex];
                        /*
                            Delete database registration
                         */
                        database.delete(Entities.Provider.TABLE_NAME, Entities.Provider._ID + " = ? ",
                                new String[] {
                                        String.valueOf(mediaManager.getMediaManagerId())
                                });

                        /*
                            Delete provider specific content
                         */
                        mediaManager.getProvider().erase();
                        doRefresh();
                    }

                    return true;
                }
        }

        return super.onContextItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        final MenuItem addMenuItem = menu.add(Menu.NONE, OPTION_MENUITEM_ADD, 1, R.string.menuitem_label_add_library);
        addMenuItem.setIcon(R.drawable.ic_action_add_dark);
        MenuItemCompat.setShowAsAction(addMenuItem, MenuItemCompat.SHOW_AS_ACTION_ALWAYS | MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT);
        addMenuItem.setOnMenuItemClickListener(onAddOptionMenuItemListener);
        return true;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
        doLibraryConfiguration(cursor.getInt(COLUMN_PROVIDER_ID), cursor.getInt(COLUMN_PROVIDER_TYPE));
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    protected void doLibraryConfiguration(int mediaProviderId, int mediaProviderType) {
        LogUtils.LOGD(TAG, "providerId : " + mediaProviderId + " providerType : " + mediaProviderType);

        final AbstractMediaManager localLibraryProvider = MediaManagerFactory.buildMediaManager(mediaProviderType, mediaProviderId);
        final AbstractMediaManager.Provider provider = localLibraryProvider.getProvider();
        final AbstractMediaManager.ProviderAction providerAction = provider.getAbstractProviderAction(0);

        if (providerAction != null) {
            /* launch activity */ providerAction.launch(this);
        }
    }

    protected void doLibraryEdition(final EditText nameEditText, final DialogInterface.OnClickListener newPlaylistPositiveOnClickListener) {
        final InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        final DialogInterface.OnClickListener positiveClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                newPlaylistPositiveOnClickListener.onClick(dialogInterface, i);
                inputMethodManager.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
            }
        };

        final DialogInterface.OnClickListener negativeClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                inputMethodManager.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
            }
        };

        new AlertDialog.Builder(SettingsLibrariesActivity.this)
                .setTitle(R.string.label_new_library)
                .setView(nameEditText)
                .setPositiveButton(android.R.string.ok, positiveClickListener)
                .setNegativeButton(android.R.string.cancel, negativeClickListener)
                .show();

        nameEditText.requestFocus();
        inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0);
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

            doRefresh();
        }
    }


    private static int lastLibraryTypeIndex;

    private MenuItem.OnMenuItemClickListener onAddOptionMenuItemListener = new MenuItem.OnMenuItemClickListener() {

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            final ArrayList<Integer> managerItemIds = new ArrayList<Integer>();
            final ArrayList<String> managerItemDescriptions = new ArrayList<String>();

            final MediaManagerDescription managerList[] = MediaManagerFactory.getMediaManagerList();


            for (MediaManagerDescription mediaManager : managerList) {
                if (mediaManager != null && mediaManager.isEnabled) {
                    managerItemIds.add(mediaManager.typeId);
                    managerItemDescriptions.add(mediaManager.description);
                }
            }

            final EditText nameEditText = new EditText(SettingsLibrariesActivity.this);

            final DialogInterface.OnClickListener newLibraryOnClickListener = new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialogInterface, int which) {
                    // nothing to be done.
                    SQLiteDatabase database = PlayerApplication.getDatabaseOpenHelper().getWritableDatabase();
                    final Editable collectionName = nameEditText.getText();

                    int mediaProviderType = managerItemIds.get(lastLibraryTypeIndex);

                    if (database != null && collectionName != null) {
                        ContentValues contentValues = new ContentValues();
                        contentValues.put(Entities.Provider.COLUMN_FIELD_PROVIDER_NAME, collectionName.toString());
                        contentValues.put(Entities.Provider.COLUMN_FIELD_PROVIDER_TYPE, mediaProviderType);
                        contentValues.put(Entities.Provider.COLUMN_FIELD_PROVIDER_POSITION, cursor.getCount());

                        long rowId = database.insert(Entities.Provider.TABLE_NAME, null, contentValues);
                        if (rowId < 0) {
                            LogUtils.LOGW(TAG, "new library: database insertion failure.");
                        } else {
                            doLibraryConfiguration((int) rowId, mediaProviderType);
                            doRefresh();
                        }
                    }
                }
            };

            final DialogInterface.OnClickListener onMediaManagerTypeSelection = new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialogInterface, int which) {
                    lastLibraryTypeIndex = which;
                    doLibraryEdition(nameEditText, newLibraryOnClickListener);
                }
            };

            new AlertDialog.Builder(SettingsLibrariesActivity.this)
                    .setTitle(R.string.alert_dialog_title_type_of_library)
                    .setItems(managerItemDescriptions.toArray(new String[managerItemDescriptions.size()]), onMediaManagerTypeSelection)
                    .show();

            return true;
        }
    };
}
