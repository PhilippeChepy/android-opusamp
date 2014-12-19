/*
 * SearchPathFragment.java
 *
 * Copyright (c) 2012, Philippe Chepy
 * All rights reserved.
 *
 * This software is the confidential and proprietary information
 * of Philippe Chepy.
 * You shall not disclose such Confidential Information.
 *
 * http://www.chepy.eu
 */
package net.opusapp.player.core.service.providers.local.ui.fragments;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.widget.PopupMenu;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;

import net.opusapp.player.R;
import net.opusapp.player.core.service.providers.AbstractMediaManager;
import net.opusapp.player.core.service.providers.local.LocalProvider;
import net.opusapp.player.core.service.providers.local.database.Entities;
import net.opusapp.player.core.service.utils.AbstractSimpleCursorLoader;
import net.opusapp.player.ui.adapter.holder.GridViewHolder;
import net.opusapp.player.ui.utils.PlayerApplication;
import net.opusapp.player.ui.views.CustomTextView;
import net.opusapp.player.ui.views.RefreshableView;

public class SearchPathFragment extends Fragment implements RefreshableView, LoaderCallbacks<Cursor>, OnItemClickListener {

	private GridView mGridView;
	
    private CollectionScannerPathAdapter mAdapter;
    
    private Cursor mCursor;

    private int mProviderId;



	public static final int MENUITEM_DELETE = 0;



	public static final int CONTENT_SEARCH_PATH = 0;
	
	public static final int CONTENT_EXCLUDE_PATH = 1;
	
	public static final String CONTENT_TYPE_KEY = "type_key";


    private static final int COLUMN_ID = 0;

    private static final int COLUMN_NAME = 1;



    protected SQLiteDatabase getReadableDatabase() {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManager(mProviderId);
        final AbstractMediaManager.Provider provider = mediaManager.getProvider();

        if (provider instanceof LocalProvider) {
            return ((LocalProvider) provider).getReadableDatabase();
        }

        return null;
    }

    protected SQLiteDatabase getWritableDatabase() {
        final AbstractMediaManager mediaManager = PlayerApplication.mediaManager(mProviderId);
        final AbstractMediaManager.Provider provider = mediaManager.getProvider();

        if (provider instanceof LocalProvider) {
            return ((LocalProvider) provider).getWritableDatabase();
        }

        return null;
    }

	
	@Override
	public void refresh() {
        if( mGridView != null ) {
            getLoaderManager().restartLoader(0, null, this);
        }
	}
        
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View rootView = inflater.inflate(R.layout.view_list_gridview, container, false);
        if (rootView != null) {
            mGridView = (GridView) rootView.findViewById(R.id.grid_view_base);
            mGridView.setEmptyView(rootView.findViewById(R.id.grid_view_empty));

            final CustomTextView emptyDescription = (CustomTextView) rootView.findViewById(R.id.empty_description);
            emptyDescription.setText(R.string.ni_scan_directories);
        }

        return rootView;
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mAdapter = new CollectionScannerPathAdapter(getActivity(), R.layout.view_item_single_line, null, new String[] {}, new int[] {}, 0);
        mGridView.setOnCreateContextMenuListener(this);
        mGridView.setOnItemClickListener(this);
        mGridView.setAdapter(mAdapter);
        mGridView.setNumColumns(PlayerApplication.getListColumns() / 2);

        Bundle arguments = getArguments();
        if (arguments != null) {
            mProviderId = arguments.getInt(AbstractMediaManager.Provider.KEY_PROVIDER_ID);
        }

        getLoaderManager().initLoader(0, null, this);
	}
	
	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle bundle) {
        final String[] projection = new String[] {
                Entities.ScanDirectory._ID,
                Entities.ScanDirectory.COLUMN_FIELD_SCAN_DIRECTORY_NAME
        };
        
		int pathType = CONTENT_SEARCH_PATH;
        Bundle arguments = getArguments();
        if (arguments != null) {
            pathType = arguments.getInt(CONTENT_TYPE_KEY, CONTENT_SEARCH_PATH);
        }

		final String selection = Entities.ScanDirectory.COLUMN_FIELD_SCAN_DIRECTORY_IS_EXCLUDED + " = ?";
		
		final String[] selectionArgs = new String[] {
			String.valueOf(pathType == CONTENT_SEARCH_PATH ? 0 : 1)	
		};
		
		final String sortOrder = Entities.ScanDirectory.COLUMN_FIELD_SCAN_DIRECTORY_NAME;

        return new AbstractSimpleCursorLoader(getActivity()) {
            @Override
            public Cursor loadInBackground() {
                SQLiteDatabase database = getReadableDatabase();
                if (database != null) {
                    return database.query(Entities.ScanDirectory.TABLE_NAME, projection, selection, selectionArgs, null, null, sortOrder);
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
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);

        menu.add(Menu.NONE, MENUITEM_DELETE, 1, R.string.menuitem_label_delete);
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		if (!getUserVisibleHint()) {
			return false;
		}

		if (item.getItemId() == MENUITEM_DELETE) {
            deletePathMenuItemClick(mCursor.getInt(COLUMN_ID));
		}

		return super.onContextItemSelected(item);
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
        getActivity().openContextMenu(mGridView);
	}

    protected void deletePathMenuItemClick(int id) {
        final String selection = Entities.ScanDirectory._ID + " = ? ";

        final String selectionArgs[] = new String[] {
                String.valueOf(id)
        };

        SQLiteDatabase database = getWritableDatabase();
        if (database != null) {
            database.delete(Entities.ScanDirectory.TABLE_NAME, selection, selectionArgs);
        }
        getLoaderManager().restartLoader(0, null, this);
    }
	
	public class CollectionScannerPathAdapter extends SimpleCursorAdapter {

	    public CollectionScannerPathAdapter(Context context, int layout, Cursor c, String[] from, int[] to, int flags) {
	        super(context, layout, c, from, to, flags);
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
	            viewholder = (GridViewHolder)convertView.getTag();
	        }



            viewholder.contextMenuHandle.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View view) {
                    final PopupMenu popupMenu = new PopupMenu(getActivity(), view);
                    final Menu menu = popupMenu.getMenu();

                    final MenuItem menuItem = menu.add(R.string.menuitem_label_delete);
                    menuItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            cursor.moveToPosition(position);
                            deletePathMenuItemClick(cursor.getInt(0));
                            return true;
                        }
                    });

                    popupMenu.show();
                }
            });

            viewholder.lineOne.setText(cursor.getString(COLUMN_NAME));
	        return view;
	    }
	}
}
