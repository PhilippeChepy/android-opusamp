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

import com.bumptech.glide.Glide;

import net.opusapp.player.R;
import net.opusapp.player.core.service.providers.local.LocalProvider;
import net.opusapp.player.core.service.providers.local.scanner.MediaScanner;
import net.opusapp.player.ui.adapter.holder.GridViewHolder;
import net.opusapp.player.ui.utils.PlayerApplication;
import net.opusapp.player.ui.views.CustomTextView;

import java.io.File;
import java.io.FileFilter;
import java.util.Set;

public class LocalCoverFileSelectionActivity extends ActionBarActivity implements OnItemClickListener {



    public static final String KEY_RESULT = "result";

    private static final int OPTION_MENUITEM_CANCEL = 2;



    private GridView mGridView;

    private TextView mPathTextView;

    private FileSelectAdapter mFileAdapter;

    private File mCurrentFolder;

    private boolean mIsRoot;

    private boolean mDoubleBackExit = false;

    private Set<String> mCoverExtensions;



    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        setContentView(R.layout.activity_directory_select);

        PlayerApplication.applyActionBar(this);


        mGridView = (GridView) findViewById(R.id.grid_view_base);
        mPathTextView = (TextView) findViewById(R.id.path);

        mGridView.setEmptyView(findViewById(R.id.grid_view_empty));
        final CustomTextView emptyDescription = (CustomTextView) findViewById(R.id.empty_description);
        emptyDescription.setText(R.string.ni_files);

        mGridView.setOnItemClickListener(this);
        
        mGridView.setOnCreateContextMenuListener(this);
        mGridView.setNumColumns(PlayerApplication.getListColumns() / 2);

        mCoverExtensions = MediaScanner.getCoverExtensions();

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
        final File selectedFile = mFileAdapter.getItem(position);

        if (selectedFile != null) {
            if (selectedFile.isDirectory()) {
                setFolder(selectedFile);
            }
            else {
                final Set<String> coverExtensions = MediaScanner.getCoverExtensions();

                if (LocalProvider.fileHasValidExtension(selectedFile, coverExtensions)) {
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
            if (mDoubleBackExit) {
                super.onBackPressed();
                return;
            }

            mDoubleBackExit = true;
            Toast.makeText(PlayerApplication.context, R.string.toast_press_back_again, Toast.LENGTH_SHORT).show();

            new Handler().postDelayed(new Runnable() {

                @Override
                public void run() {
                    mDoubleBackExit = false;
                }
            }, 2000);
        }
    }

    protected boolean handleBackButton() {
        if (mCurrentFolder != null) {
            final File parentFile = mCurrentFolder.getParentFile();
            if (parentFile != null) {
                setFolder(parentFile);
                return true;
            }
        }

        return false;
    }


    public void setFolder(File folder) {
        mCurrentFolder = folder;
        File filesInFolder[] = folder.listFiles(directoryFileFilter);

        if (folder.getParentFile() != null) {
            File[] fileList = new File[filesInFolder.length + 1];
            System.arraycopy(filesInFolder, 0, fileList, 1, filesInFolder.length);
            fileList[0] = folder.getParentFile();
            filesInFolder = fileList;
            mIsRoot = false;
        }
        else {
            mIsRoot = true;
        }

        mPathTextView.setText(folder.getAbsolutePath());

        mFileAdapter = new FileSelectAdapter(this, R.layout.view_item_double_line_thumbnailed, filesInFolder);
        mGridView.setAdapter(mFileAdapter);
    }

    protected final FileFilter directoryFileFilter = new FileFilter() {

        @Override
        public boolean accept(File pathname) {
        return (pathname.isDirectory() && pathname.canRead() || LocalProvider.fileHasValidExtension(pathname, mCoverExtensions));
        }
    };

    public class FileSelectAdapter extends ArrayAdapter<File> {

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

            if (!mIsRoot && position == 0) {
                viewHolder.image.setImageResource(R.drawable.ic_arrow_drop_up_grey600_48dp);
                viewHolder.lineOne.setText(R.string.fs_parent_directory);
                viewHolder.lineTwo.setVisibility(View.GONE);
            }
            else {
                viewHolder.lineOne.setText(file.getName());

                if (file.isDirectory()) {
                    viewHolder.image.setImageResource(R.drawable.ic_folder_grey600_48dp);
                    viewHolder.lineTwo.setVisibility(View.VISIBLE);
                    viewHolder.lineTwo.setText(R.string.fs_directory);
                }
                else {
                    Glide.with(LocalCoverFileSelectionActivity.this)
                            .load(PlayerApplication.fileToUri(file))
                            .centerCrop()
                            .placeholder(R.drawable.ic_insert_drive_file_grey600_48dp)
                            .crossFade()
                            .into(viewHolder.image);
                    viewHolder.lineTwo.setVisibility(View.GONE);
                }
            }

            return view;
        }
    }
}
