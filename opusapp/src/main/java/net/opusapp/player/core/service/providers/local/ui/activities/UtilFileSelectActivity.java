/*
 * UtilDirectorySelectActivity.java
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
package net.opusapp.player.core.service.providers.local.ui.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import net.opusapp.player.R;
import net.opusapp.player.core.service.providers.local.LocalProvider;
import net.opusapp.player.ui.adapter.holder.GridViewHolder;
import net.opusapp.player.ui.utils.PlayerApplication;
import net.opusapp.player.ui.views.CustomTextView;

import java.io.File;
import java.io.FileFilter;

public class UtilFileSelectActivity extends ActionBarActivity implements OnItemClickListener {

	private GridView gridView;

    private TextView pathTextView;
	
	private FileSelectAdapter fileAdapter;

	public static final String KEY_RESULT = "result";
	
	private File currentFolder;
	
	public static boolean isAtRootLevel;

    private boolean doubleBackToExitPressedOnce = false;

    private LocalProvider.SyncScanContext scanContext;



    private static final int OPTION_MENUITEM_CANCEL = 2;



    @Override
	protected void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		
		setContentView(R.layout.activity_directory_select);

        PlayerApplication.applyActionBar(this);


		gridView = (GridView) findViewById(R.id.grid_view_base);
        pathTextView = (TextView) findViewById(R.id.path);

        gridView.setEmptyView(findViewById(R.id.grid_view_empty));
        final CustomTextView emptyDescription = (CustomTextView) findViewById(R.id.empty_description);
        emptyDescription.setText(R.string.ni_files);

        gridView.setOnItemClickListener(this);
        
        gridView.setOnCreateContextMenuListener(this);
        gridView.setNumColumns(PlayerApplication.getListColumns() / 2);

        scanContext = new LocalProvider.SyncScanContext();

        setFolder(Environment.getExternalStorageDirectory());
	}

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        final MenuItem cancelMenuItem = menu.add(Menu.NONE, OPTION_MENUITEM_CANCEL, 2, R.string.actionbar_confirmation_text_cancel);
        cancelMenuItem.setIcon(PlayerApplication.iconsAreDark() ?  R.drawable.ic_close_black_48dp : R.drawable.ic_close_white_48dp);
        MenuItemCompat.setShowAsAction(cancelMenuItem, MenuItemCompat.SHOW_AS_ACTION_ALWAYS | MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT);
        cancelMenuItem.setOnMenuItemClickListener(onCancelOptionMenuItemListener);
        return true;
    }

    final MenuItem.OnMenuItemClickListener onCancelOptionMenuItemListener = new MenuItem.OnMenuItemClickListener() {

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            Intent returnIntent = new Intent();
            setResult(RESULT_CANCELED, returnIntent);
            finish();
            return true;
        }
    };

	@Override
	public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
		File selectedFile = fileAdapter.getItem(position);

		if (selectedFile != null) {
			if (selectedFile.isDirectory()) {
				setFolder(selectedFile);
			}
            else {
                if (LocalProvider.isArtFile(scanContext, selectedFile)) {
                    Intent returnIntent = new Intent();
                    returnIntent.putExtra(KEY_RESULT, selectedFile.getAbsolutePath());
                    setResult(RESULT_OK, returnIntent);
                    finish();
                }
            }
		}
	}

    @Override
    public void onBackPressed() {
        if (!handleBackButton()) {
            if (doubleBackToExitPressedOnce) {
                super.onBackPressed();
                return;
            }

            doubleBackToExitPressedOnce = true;
            Toast.makeText(PlayerApplication.context, R.string.toast_press_back_again, Toast.LENGTH_SHORT).show();

            new Handler().postDelayed(new Runnable() {

                @Override
                public void run() {
                    doubleBackToExitPressedOnce = false;
                }
            }, 2000);
        }
    }

    protected boolean handleBackButton() {
        if (currentFolder != null) {
            final File parentFile = currentFolder.getParentFile();
            if (parentFile != null) {
                setFolder(parentFile);
                return true;
            }
        }

        return false;
    }

	
	public void setFolder(File folder) {
		currentFolder = folder;
		File filesInFolder[] = folder.listFiles(directoryFileFilter);
		
		if (folder.getParentFile() != null) {
			File[] fileList = new File[filesInFolder.length + 1];
			System.arraycopy(filesInFolder, 0, fileList, 1, filesInFolder.length);
			fileList[0] = folder.getParentFile();
			filesInFolder = fileList;
			isAtRootLevel = false;
		}
		else {
			isAtRootLevel = true;
		}

        pathTextView.setText(folder.getAbsolutePath());

		fileAdapter = new FileSelectAdapter(this, R.layout.view_item_double_line_thumbnailed, filesInFolder);
        gridView.setAdapter(fileAdapter);
	}
	
	protected final FileFilter directoryFileFilter = new FileFilter() {
		
		@Override
		public boolean accept(File pathname) {
			return (pathname.isDirectory() && pathname.canRead() || LocalProvider.isArtFile(scanContext, pathname));
		}
	};

	public class FileSelectAdapter extends ArrayAdapter<File> {
		
		public static final String TAG = "FileSelectAdapter";



		public FileSelectAdapter(Context context, int resource, File[] objects) {
			super(context, resource, R.id.line_one, objects);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
	        View view = super.getView(position, convertView, parent);
	        
	        final GridViewHolder viewHolder;

	        if (view != null) {
	            viewHolder = new GridViewHolder(view);
	            view.setTag(viewHolder);

	        } else {
	            viewHolder = (GridViewHolder)convertView.getTag();
	        }

			File file = getItem(position);

			if (!UtilFileSelectActivity.isAtRootLevel && position == 0) {
				viewHolder.image.setImageResource(R.drawable.ic_arrow_drop_up_grey600_48dp);
                viewHolder.lineOne.setText("Parent folder"); // TODO: translation @see UtilDirectorySelectActivity
                viewHolder.lineTwo.setVisibility(View.GONE);
			}
			else {
                viewHolder.lineOne.setText(file.getName());
		
				if (file.isDirectory()) {
                    viewHolder.image.setImageResource(R.drawable.ic_folder_grey600_48dp);
                    viewHolder.lineTwo.setVisibility(View.VISIBLE);
                    viewHolder.lineTwo.setText(R.string.fs_directory);
				} else {
                    PlayerApplication.thumbnailImageLoader.cancelDisplayTask(viewHolder.image);
                    viewHolder.image.setImageResource(R.drawable.ic_insert_drive_file_grey600_48dp);
                    PlayerApplication.thumbnailImageLoader.displayImage(PlayerApplication.fileToUri(file), viewHolder.image);

                    viewHolder.lineTwo.setVisibility(View.GONE);
				}
			}

			return view;
		}
	}
}
