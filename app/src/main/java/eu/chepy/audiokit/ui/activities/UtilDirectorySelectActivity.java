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
package eu.chepy.audiokit.ui.activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileFilter;

import eu.chepy.audiokit.R;
import eu.chepy.audiokit.ui.adapter.holder.GridViewHolder;
import eu.chepy.audiokit.ui.utils.PlayerApplication;
import eu.chepy.audiokit.ui.views.CustomTextView;

public class UtilDirectorySelectActivity extends ActionBarActivity implements OnItemClickListener {

	private GridView gridView;

    private TextView pathTextView;
	
	private DirectorySelectAdapter fileAdapter;

	public static final String KEY_RESULT = "result";
	
	private File currentFolder;
	
	public static boolean isAtRootLevel;

    private boolean doubleBackToExitPressedOnce = false;
	
	@Override
	protected void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		
		setContentView(R.layout.fragment_storage);
		gridView = (GridView) findViewById(R.id.grid_view_base);

        pathTextView = (TextView) findViewById(R.id.path);

        gridView.setEmptyView(findViewById(R.id.grid_view_empty));
        final CustomTextView emptyDescription = (CustomTextView) findViewById(R.id.empty_description);
        emptyDescription.setText(R.string.ni_files);

        gridView.setOnItemClickListener(this);
        
        gridView.setOnCreateContextMenuListener(this);
        gridView.setNumColumns(PlayerApplication.getListColumns() / 2);
        
     // BEGIN_INCLUDE (inflate_set_custom_view)
     // Inflate a "Done/Cancel" custom action bar view.
		final LayoutInflater inflater = (LayoutInflater) getSupportActionBar()
				.getThemedContext().getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
		
		final View customActionBarView = inflater.inflate(R.layout.actionbar_confirmation, null);

        if (customActionBarView != null) {
            customActionBarView.findViewById(R.id.actionbar_done)
                    .setOnClickListener(new View.OnClickListener() {

                        @Override
                        public void onClick(View v) {
                            Intent returnIntent = new Intent();
                            returnIntent.putExtra(KEY_RESULT, currentFolder.getAbsolutePath());
                            setResult(RESULT_OK, returnIntent);
                            finish();
                        }
                    });

            customActionBarView.findViewById(R.id.actionbar_discard)
                    .setOnClickListener(new View.OnClickListener() {

                        @Override
                        public void onClick(View v) {
                            Intent returnIntent = new Intent();
                            setResult(RESULT_CANCELED, returnIntent);
                            finish();
                        }
                    });
        }

        getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM, ActionBar.DISPLAY_SHOW_CUSTOM | ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_SHOW_TITLE);
        getSupportActionBar().setCustomView(customActionBarView, new ActionBar.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        
        setFolder(Environment.getExternalStorageDirectory());
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
		File selectedFile = fileAdapter.getItem(position);
		
		if (selectedFile != null) {
			if (selectedFile.isDirectory()) {
				setFolder(selectedFile);
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

		fileAdapter = new DirectorySelectAdapter(this, R.layout.view_item_double_line_thumbnailed, filesInFolder);
        gridView.setAdapter(fileAdapter);
	}
	
	protected static final FileFilter directoryFileFilter = new FileFilter() {
		
		@Override
		public boolean accept(File pathname) {
			return pathname.isDirectory() && pathname.canRead();
		}
	};

	public class DirectorySelectAdapter extends ArrayAdapter<File> {
		
		public static final String TAG = "CollectionStorageAdapter";



		public DirectorySelectAdapter(Context context, int resource, File[] objects) {
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

			if (!UtilDirectorySelectActivity.isAtRootLevel && position == 0) {
				viewHolder.image.setImageResource(R.drawable.ic_action_arrow_left_top);
                viewHolder.lineOne.setText("Parent folder");
                viewHolder.lineTwo.setVisibility(View.GONE);
			}
			else {
                viewHolder.lineOne.setText(file.getName());
		
				if (file.isDirectory()) {
                    viewHolder.image.setImageResource(R.drawable.ic_action_folder_closed);
                    viewHolder.lineTwo.setVisibility(View.VISIBLE);
                    viewHolder.lineTwo.setText(R.string.fs_directory);
				} else { /* should never happen */
                    viewHolder.image.setImageResource(R.drawable.ic_action_document);
                    viewHolder.lineTwo.setVisibility(View.GONE);
				}
			}

			return view;
		}
	}
}
