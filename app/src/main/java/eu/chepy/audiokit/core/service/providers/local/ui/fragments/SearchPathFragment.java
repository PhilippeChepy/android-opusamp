/*
 * CollectionScannerPathFragment.java
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
package eu.chepy.audiokit.core.service.providers.local.ui.fragments;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.util.Log;
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

import eu.chepy.audiokit.R;
import eu.chepy.audiokit.core.service.providers.AbstractMediaProvider;
import eu.chepy.audiokit.core.service.providers.local.LocalMediaProvider;
import eu.chepy.audiokit.core.service.providers.local.database.Entities;
import eu.chepy.audiokit.core.service.utils.AbstractSimpleCursorLoader;
import eu.chepy.audiokit.ui.adapter.holder.GridViewHolder;
import eu.chepy.audiokit.ui.fragments.AbstractRefreshableFragment;
import eu.chepy.audiokit.ui.utils.PlayerApplication;
import eu.chepy.audiokit.ui.views.CustomTextView;

public class SearchPathFragment extends AbstractRefreshableFragment implements LoaderCallbacks<Cursor>, OnItemClickListener {

	private static final String TAG = SearchPathFragment.class.getSimpleName();



	private GridView gridView;
	
    private CollectionScannerPathAdapter songAdapter;
    
    private Cursor cursor;

    private int providerId;



	public static final int MENUITEM_DELETE = 0;
	
	public static final int CONTENT_SEARCH_PATH = 0;
	
	public static final int CONTENT_EXCLUDE_PATH = 1;
	
	public static final String CONTENT_TYPE_KEY = "type_key";


    private static final int COLUMN_ID = 0;

    private static final int COLUMN_NAME = 1;



    protected SQLiteDatabase getReadableDatabase() {
        int index = PlayerApplication.getManagerIndex(providerId);

        final AbstractMediaProvider mediaProvider = PlayerApplication.mediaManagers[index].getMediaProvider();
        if (mediaProvider instanceof LocalMediaProvider) {
            return ((LocalMediaProvider) mediaProvider).getReadableDatabase();
        }

        return null;
    }

    protected SQLiteDatabase getWritableDatabase() {
        int index = PlayerApplication.getManagerIndex(providerId);

        final AbstractMediaProvider mediaProvider = PlayerApplication.mediaManagers[index].getMediaProvider();
        if (mediaProvider instanceof LocalMediaProvider) {
            return ((LocalMediaProvider) mediaProvider).getWritableDatabase();
        }

        return null;
    }

	
	@Override
	public void doRefresh() {
		Log.d(TAG, "doRefresh()");

        if( gridView != null ) {
            getLoaderManager().restartLoader(0, null, this);
        }
	}
        
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		Log.d(TAG, "onCreateView()");
		
		View rootView = inflater.inflate(R.layout.view_list_gridview, container, false);
        if (rootView != null) {
            gridView = (GridView) rootView.findViewById(R.id.grid_view_base);
            gridView.setEmptyView(rootView.findViewById(R.id.grid_view_empty));

            final CustomTextView emptyDescription = (CustomTextView) rootView.findViewById(R.id.empty_description);
            emptyDescription.setText(R.string.ni_scan_directories);
        }

        return rootView;
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		Log.d(TAG, "onActivityCreated()");
        super.onActivityCreated(savedInstanceState);

        this.songAdapter = new CollectionScannerPathAdapter(getActivity(), R.layout.view_item_single_line, null, new String[] {}, new int[] {}, 0);
        this.gridView.setOnCreateContextMenuListener(this);
        this.gridView.setOnItemClickListener(this);
        this.gridView.setAdapter(songAdapter);
        gridView.setNumColumns(PlayerApplication.getListColumns());

        Bundle arguments = getArguments();
        if (arguments != null) {
            providerId = arguments.getInt(AbstractMediaProvider.KEY_PROVIDER_ID);
        }

        getLoaderManager().initLoader(0, null, this);
	}
	
	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle bundle) {
		Log.d(TAG, "onCreateLoader()");

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
		Log.d(TAG, "onLoadFinished()");
		
        if (data == null) {
            return;
        }

        this.songAdapter.changeCursor(data);
        this.gridView.invalidateViews();
        this.cursor = data;
	}
	
	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		Log.d(TAG, "onLoaderReset()");
		
        if (this.songAdapter != null) {
        	this.songAdapter.changeCursor(null);
        }
	}
	
	@Override
	public void onStart() {
		Log.d(TAG, "onStart()");
        super.onStart();
	}
	
	@Override
	public void onStop() {
		Log.d(TAG, "onStop()");
        super.onStop();
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		Log.d(TAG, "onCreateContextMenu()");
		
		menu.add(Menu.NONE, MENUITEM_DELETE, 1, R.string.context_menu_delete);
		
		super.onCreateContextMenu(menu, v, menuInfo);
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		if (!getUserVisibleHint()) {
			return false;
		}
		
		Log.d(TAG, "onContextItemSelected()");

		if (item.getItemId() == MENUITEM_DELETE) {
            final String selection = Entities.ScanDirectory._ID + " = ? ";
			
			final String selectionArgs[] = new String[] {
					cursor.getString(COLUMN_ID)
			};

            SQLiteDatabase database = getWritableDatabase();
            if (database != null) {
                database.delete(Entities.ScanDirectory.TABLE_NAME, selection, selectionArgs);
            }
			// TODO: delete associated content (already scanned)

            getLoaderManager().restartLoader(0, null, this);
		}

		return super.onContextItemSelected(item);
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
		Log.d(TAG, "onItemClick()");

        getActivity().openContextMenu(gridView);
	}
	
	public class CollectionScannerPathAdapter extends SimpleCursorAdapter {
		public static final String TAG = "CollectionScannerPathAdapter";
	    
	    public CollectionScannerPathAdapter(Context context, int layout, Cursor c, String[] from, int[] to, int flags) {
	        super(context, layout, c, from, to, flags);
	    }

	    @Override
	    public View getView(final int position, View convertView, ViewGroup parent) {
	        final View view = super.getView(position, convertView, parent);

	        Cursor cursor = (Cursor) getItem(position);
	        final GridViewHolder viewholder;

	        if (view != null) {
	            viewholder = new GridViewHolder(view);
	            viewholder.customView = view.findViewById(R.id.card_layout);
	            view.setTag(viewholder);
	        } else {
	            viewholder = (GridViewHolder)convertView.getTag();
	        }

            viewholder.lineOne.setText(cursor.getString(COLUMN_NAME));
	        return view;
	    }
	}
}
