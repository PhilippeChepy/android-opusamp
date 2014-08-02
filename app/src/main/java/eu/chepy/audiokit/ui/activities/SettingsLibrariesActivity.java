package eu.chepy.audiokit.ui.activities;

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
import android.text.Editable;
import android.util.Log;
import android.view.ContextMenu;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.mobeta.android.dslv.DragSortListView;

import java.util.ArrayList;

import eu.chepy.audiokit.R;
import eu.chepy.audiokit.core.service.providers.AbstractMediaManager;
import eu.chepy.audiokit.core.service.providers.AbstractMediaProvider;
import eu.chepy.audiokit.core.service.providers.AbstractProviderAction;
import eu.chepy.audiokit.core.service.providers.MediaManagerFactory;
import eu.chepy.audiokit.core.service.providers.MediaManagerFactory.MediaManagerDescription;
import eu.chepy.audiokit.core.service.providers.index.entities.Provider;
import eu.chepy.audiokit.core.service.utils.AbstractSimpleCursorLoader;
import eu.chepy.audiokit.ui.adapter.ProviderAdapter;
import eu.chepy.audiokit.ui.utils.PlayerApplication;
import eu.chepy.audiokit.utils.LogUtils;

public class SettingsLibrariesActivity extends SherlockFragmentActivity implements
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
            Provider.COLUMN_FIELD_PROVIDER_ID,
            Provider.COLUMN_FIELD_PROVIDER_NAME,
            Provider.COLUMN_FIELD_PROVIDER_TYPE
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

        getSupportActionBar().show();

        setContentView(R.layout.activity_library_settings);
        listView = (DragSortListView) findViewById(R.id.dragable_list_base);

        TextView emptyView = (TextView) findViewById(R.id.dragable_list_empty);
        emptyView.setText(R.string.ni_providers);
        listView.setEmptyView(emptyView);


        adapter = new ProviderAdapter(this);

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
                    final String orderBy = Provider.COLUMN_FIELD_PROVIDER_POSITION;

                    return database.query(Provider.TABLE_NAME, requestedFields, null, null, null, null, orderBy);
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
        Log.d(TAG, "onCreateContextMenu()");

        menu.setHeaderTitle(R.string.label_library_settings);
        menu.add(Menu.NONE, CONTEXT_MENUITEM_EDIT, 1, R.string.context_menu_edit);

        if (cursor.getInt(COLUMN_PROVIDER_ID) != 1) {
            menu.add(Menu.NONE, CONTEXT_MENUITEM_DELETE, 2, R.string.context_menu_delete);
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
                            contentValues.put(Provider.COLUMN_FIELD_PROVIDER_NAME, collectionName.toString());
                            contentValues.put(Provider.COLUMN_FIELD_PROVIDER_TYPE, mediaProviderType);

                            database.update(Provider.TABLE_NAME, contentValues, Provider.COLUMN_FIELD_PROVIDER_ID + " = ? ",
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
                        database.delete(Provider.TABLE_NAME, Provider.COLUMN_FIELD_PROVIDER_ID + " = ? ",
                                new String[] {
                                        String.valueOf(mediaManager.getMediaManagerId())
                                });

                        /*
                            Delete provider specific content
                         */
                        mediaManager.getMediaProvider().erase();
                        doRefresh();
                    }

                    return true;
                }
        }

        return super.onContextItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        final MenuItem addMenuItem = menu.add(Menu.NONE, OPTION_MENUITEM_ADD, 1, R.string.menu_label_add_library);
        addMenuItem.setIcon(R.drawable.ic_action_add_dark);
        addMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
        addMenuItem.setOnMenuItemClickListener(onAddOptionMenuItemListener);

        /*
        TODO:
        new ShowcaseView.Builder(this, true)
                .setTarget(new ActionItemTarget(this, OPTION_MENUITEM_ADD))
                .setContentTitle(R.string.tutor_library_management_title)
                .setContentText(R.string.tutor_library_management_detail)
                .setStyle(R.style.ShowcaseTheme)
                .singleShot(PlayerApplication.SHOWCASE_LIBRARY_MANAGEMENT)
                .build();
         */
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
        final AbstractMediaManager localLibraryProvider = MediaManagerFactory.buildMediaManager(mediaProviderType, mediaProviderId);
        final AbstractMediaProvider mediaProvider = localLibraryProvider.getMediaProvider();
        final AbstractProviderAction providerAction = mediaProvider.getAbstractProviderAction(0);

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
        Log.w(TAG, "from = " + indexFrom + " to = " + indexTo);

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
                            "UPDATE " + Provider.TABLE_NAME + " " +
                            "SET " + Provider.COLUMN_FIELD_PROVIDER_POSITION + " = -1 " +
                            "WHERE " + Provider.COLUMN_FIELD_PROVIDER_POSITION + " = " + String.valueOf(lowerIndex)
                    );

                    // Provider is -1, 0, 1, 2, 3, 4, ..., indexFrom + 1, indexFrom + 2, ...
                    database.execSQL(
                            "UPDATE " + Provider.TABLE_NAME + " " +
                            "SET " + Provider.COLUMN_FIELD_PROVIDER_POSITION + " = " + Provider.COLUMN_FIELD_PROVIDER_POSITION + " - 1 " +
                            "WHERE " + Provider.COLUMN_FIELD_PROVIDER_POSITION +
                                " BETWEEN " + String.valueOf(lowerIndex) +
                                    " AND " + String.valueOf(upperIndex)
                    );


                    // Provider is 0, 1, 2, 3, 4, ..., indexFrom + 1, indexFrom + 2, ..., indexTo - 1, indexTo, ...
                    database.execSQL(
                            "UPDATE " + Provider.TABLE_NAME + " " +
                            "SET " + Provider.COLUMN_FIELD_PROVIDER_POSITION + " = " + String.valueOf(upperIndex) + " " +
                            "WHERE " + Provider.COLUMN_FIELD_PROVIDER_POSITION + " = -1"
                    );
                } else {
                    int lowerIndex = Math.min(indexFrom, indexTo);
                    int upperIndex = Math.max(indexFrom, indexTo);

                    database.execSQL(
                            "UPDATE " + Provider.TABLE_NAME + " " +
                            "SET " + Provider.COLUMN_FIELD_PROVIDER_POSITION + " = -1 " +
                            "WHERE " + Provider.COLUMN_FIELD_PROVIDER_POSITION + " = " + String.valueOf(upperIndex)
                    );

                    database.execSQL(
                            "UPDATE " + Provider.TABLE_NAME + " " +
                            "SET " + Provider.COLUMN_FIELD_PROVIDER_POSITION + " = " + Provider.COLUMN_FIELD_PROVIDER_POSITION + " + 1 " +
                            "WHERE " + Provider.COLUMN_FIELD_PROVIDER_POSITION +
                                " BETWEEN " + String.valueOf(lowerIndex) +
                                    " AND " + String.valueOf(upperIndex)
                    );

                    database.execSQL(
                            "UPDATE " + Provider.TABLE_NAME + " " +
                            "SET " + Provider.COLUMN_FIELD_PROVIDER_POSITION + " = " + String.valueOf(lowerIndex) + " " +
                            "WHERE " + Provider.COLUMN_FIELD_PROVIDER_POSITION + " = -1"
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
                if (mediaManager.isEnabled) {
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
                        contentValues.put(Provider.COLUMN_FIELD_PROVIDER_NAME, collectionName.toString());
                        contentValues.put(Provider.COLUMN_FIELD_PROVIDER_TYPE, mediaProviderType);
                        contentValues.put(Provider.COLUMN_FIELD_PROVIDER_POSITION, cursor.getCount());

                        long rowId = database.insert(Provider.TABLE_NAME, null, contentValues);
                        if (rowId < 0) {
                            Log.w(TAG, "new library: database insertion failure.");
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
                    .setTitle(R.string.mi_add_media_manager)
                    .setItems(managerItemDescriptions.toArray(new String[managerItemDescriptions.size()]), onMediaManagerTypeSelection)
                    .show();

            return true;
        }
    };
}
