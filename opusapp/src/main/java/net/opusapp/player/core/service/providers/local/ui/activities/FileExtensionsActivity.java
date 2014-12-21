package net.opusapp.player.core.service.providers.local.ui.activities;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.PopupMenu;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;

import net.opusapp.player.R;
import net.opusapp.player.core.service.providers.MediaManager;
import net.opusapp.player.core.service.providers.local.database.Entities;
import net.opusapp.player.core.service.providers.local.database.OpenHelper;
import net.opusapp.player.core.service.utils.AbstractSimpleCursorLoader;
import net.opusapp.player.ui.adapter.holder.GridViewHolder;
import net.opusapp.player.ui.dialogs.EditTextDialog;
import net.opusapp.player.ui.utils.PlayerApplication;
import net.opusapp.player.ui.views.CustomTextView;
import net.opusapp.player.ui.views.RefreshableView;

public class FileExtensionsActivity extends ActionBarActivity implements RefreshableView, LoaderCallbacks<Cursor>, AdapterView.OnItemClickListener {

    private GridView mGridView;

    private ExtensionAdapter mAdapter;

    private Cursor mCursor;

    private int mProviderId;



    public static final int MENUITEM_DELETE = 0;



    private static final int COLUMN_ID = 0;

    private static final int COLUMN_EXTENSION = 1;

    @Override
    public void refresh() {
        if( mGridView != null ) {
            getSupportLoaderManager().restartLoader(0, null, this);
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle bundle) {
        final String[] projection = new String[] {
                Entities.FileExtensions._ID,
                Entities.FileExtensions.COLUMN_FIELD_EXTENSION
        };

        return new AbstractSimpleCursorLoader(this) {
            @Override
            public Cursor loadInBackground() {
                final SQLiteDatabase database = OpenHelper.getInstance(mProviderId).getReadableDatabase();
                return database.query(Entities.FileExtensions.TABLE_NAME, projection, null, null, null, null, Entities.FileExtensions.COLUMN_FIELD_EXTENSION);
            }
        };
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (data == null) {
            return;
        }

        mAdapter.changeCursor(data);
        mGridView.invalidateViews();
        mCursor = data;
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        if (mAdapter != null) {
            mAdapter.changeCursor(null);
        }
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        mProviderId = getIntent().getIntExtra(MediaManager.Provider.KEY_PROVIDER_ID, -1);

        setContentView(R.layout.activity_grid);

        mGridView = (GridView) findViewById(R.id.grid_view_base);
        mGridView.setEmptyView(findViewById(R.id.grid_view_empty));

        final CustomTextView emptyDescription = (CustomTextView) findViewById(R.id.empty_description);
        emptyDescription.setText(R.string.ni_scan_file_extensions);

        PlayerApplication.applyActionBar(this);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mAdapter = new ExtensionAdapter(this);
        mGridView.setOnCreateContextMenuListener(this);
        mGridView.setOnItemClickListener(this);
        mGridView.setAdapter(mAdapter);
        mGridView.setNumColumns(PlayerApplication.getListColumns() * 2);

        mProviderId = getIntent().getIntExtra(MediaManager.Provider.KEY_PROVIDER_ID, -1);

        getSupportLoaderManager().initLoader(0, null, this);
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
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem addMenuItem = menu.add(Menu.NONE, 0, 0, R.string.menuitem_label_add);
        addMenuItem.setIcon(PlayerApplication.iconsAreDark() ?  R.drawable.ic_add_black_48dp : R.drawable.ic_add_white_48dp);
        MenuItemCompat.setShowAsAction(addMenuItem, MenuItemCompat.SHOW_AS_ACTION_ALWAYS | MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT);
        addMenuItem.setOnMenuItemClickListener(onAddMenuItemListener);
        return true;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        menu.add(Menu.NONE, MENUITEM_DELETE, 1, R.string.menuitem_label_delete);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
        openContextMenu(mGridView);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (item.getItemId() == MENUITEM_DELETE) {
            deleteExtensionMenuItemClick(mCursor.getInt(COLUMN_ID));
        }

        return super.onContextItemSelected(item);
    }


    protected void deleteExtensionMenuItemClick(int id) {
        final String selection = Entities.FileExtensions._ID + " = ? ";

        final String selectionArgs[] = new String[] {
                String.valueOf(id)
        };

        final SQLiteDatabase database = OpenHelper.getInstance(mProviderId).getWritableDatabase();
        database.delete(Entities.FileExtensions.TABLE_NAME, selection, selectionArgs);

        getSupportLoaderManager().restartLoader(0, null, this);
    }

    protected void notifyLibraryChanges() {
        refresh();

        final MediaManager mediaManager = PlayerApplication.mediaManager(mProviderId);
        final MediaManager.Provider provider = mediaManager.getProvider();

        if (provider != null) {
            provider.scanStart();
        }
    }

    private final MenuItem.OnMenuItemClickListener onAddMenuItemListener = new MenuItem.OnMenuItemClickListener() {

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            final EditTextDialog.ButtonClickListener extensionValidatoin = new EditTextDialog.ButtonClickListener() {
                @Override
                public void click(EditTextDialog dialog) {
                    final String extension = dialog.getText();

                    if (!TextUtils.isEmpty(extension)) {
                        final ContentValues contentValues = new ContentValues();
                        contentValues.put(Entities.FileExtensions.COLUMN_FIELD_EXTENSION, extension);

                        final SQLiteDatabase database = OpenHelper.getInstance(mProviderId).getWritableDatabase();
                        database.insert(Entities.FileExtensions.TABLE_NAME, null, contentValues);
                        notifyLibraryChanges();
                    }
                }
            };

            final EditTextDialog.ButtonClickListener extensionCancellation = new EditTextDialog.ButtonClickListener() {
                @Override
                public void click(EditTextDialog dialog) {
                    // Nothing to be done.
                }
            };

            final EditTextDialog editTextDialog = new EditTextDialog(FileExtensionsActivity.this, R.string.preference_title_settings_extensions);
            editTextDialog.setNegativeButtonRunnable(extensionCancellation);
            editTextDialog.setPositiveButtonRunnable(extensionValidatoin);
            editTextDialog.show();

            return true;
        }
    };

    public class ExtensionAdapter extends SimpleCursorAdapter {

        public ExtensionAdapter(Context context) {
            super(context, R.layout.view_item_single_line, null, new String[] {}, new int[] {}, 0);
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            final View view = super.getView(position, convertView, parent);
            final Cursor cursor = (Cursor) getItem(position);
            final GridViewHolder viewholder;

            if (view != null) {
                viewholder = new GridViewHolder(view);
                viewholder.customView = view.findViewById(R.id.card_layout);
                viewholder.contextMenuHandle = view.findViewById(R.id.context_menu_handle);
                view.setTag(viewholder);
            } else {
                viewholder = (GridViewHolder) convertView.getTag();
            }

            viewholder.contextMenuHandle.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View view) {
                    final PopupMenu popupMenu = new PopupMenu(FileExtensionsActivity.this, view);
                    final Menu menu = popupMenu.getMenu();

                    final MenuItem menuItem = menu.add(R.string.menuitem_label_delete);
                    menuItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            cursor.moveToPosition(position);
                            deleteExtensionMenuItemClick(cursor.getInt(0));
                            return true;
                        }
                    });

                    popupMenu.show();
                }
            });

            viewholder.lineOne.setText(cursor.getString(COLUMN_EXTENSION));
            return view;
        }
    }
}
