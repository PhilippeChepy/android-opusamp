/*
 * SearchPathActivity.java
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

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;

import com.astuetz.PagerSlidingTabStrip;

import net.opusapp.player.R;
import net.opusapp.player.core.service.providers.MediaManager;
import net.opusapp.player.core.service.providers.local.database.Entities;
import net.opusapp.player.core.service.providers.local.database.OpenHelper;
import net.opusapp.player.core.service.providers.local.ui.fragments.SearchPathFragment;
import net.opusapp.player.ui.activities.UtilDirectorySelectActivity;
import net.opusapp.player.ui.adapter.ux.PagerAdapter;
import net.opusapp.player.ui.utils.PlayerApplication;

public class SearchPathActivity extends ActionBarActivity {


	
	private final static int REQUEST_CODE_SEARCH_PATH = 1;
	
	private final static int REQUEST_CODE_EXCLUDE_PATH = 2;



    private ViewPager mViewPager;

    private PagerAdapter mPagerAdapter;

    private int mProviderId;



	@Override
	protected void onCreate(Bundle bundle) {
		super.onCreate(bundle);

        mProviderId = getIntent().getIntExtra(MediaManager.Provider.KEY_PROVIDER_ID, -1);

		setContentView(R.layout.activity_tabbed_grids);

        PlayerApplication.applyActionBar(this);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		
		Bundle searchBundle = new Bundle();
		searchBundle.putInt(SearchPathFragment.CONTENT_TYPE_KEY, SearchPathFragment.CONTENT_SEARCH_PATH);
        searchBundle.putInt(MediaManager.Provider.KEY_PROVIDER_ID, mProviderId);
		
		Bundle excludedBundle = new Bundle();
		excludedBundle.putInt(SearchPathFragment.CONTENT_TYPE_KEY, SearchPathFragment.CONTENT_EXCLUDE_PATH);
        excludedBundle.putInt(MediaManager.Provider.KEY_PROVIDER_ID, mProviderId);
		
		mPagerAdapter = new PagerAdapter(this, getSupportFragmentManager());
		mPagerAdapter.addFragment(new SearchPathFragment(), searchBundle, R.string.tab_label_accept_path);
		mPagerAdapter.addFragment(new SearchPathFragment(), excludedBundle, R.string.tab_label_exclude_path);
		
        mViewPager = (ViewPager)findViewById(R.id.pager_viewpager);
        mViewPager.setPageMargin(getResources().getInteger(R.integer.viewpager_margin_width));
        mViewPager.setOffscreenPageLimit(mPagerAdapter.getCount());
        mViewPager.setAdapter(mPagerAdapter);


        final PagerSlidingTabStrip scrollingTabs = (PagerSlidingTabStrip) findViewById(R.id.pager_tabs);
		scrollingTabs.setViewPager(mViewPager);
        PlayerApplication.applyThemeOnPagerTabs(scrollingTabs);
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (mProviderId < 0) {
            return;
        }

        switch(requestCode) {
        case REQUEST_CODE_SEARCH_PATH:
            if (resultCode == RESULT_OK) {
                OpenHelper openHelper = OpenHelper.getInstance(mProviderId);

                SQLiteDatabase database = openHelper.getWritableDatabase();
                if (database != null) {
                    final ContentValues values = new ContentValues();
                    values.put(Entities.ScanDirectory.COLUMN_FIELD_SCAN_DIRECTORY_IS_EXCLUDED, 0);
                    values.put(Entities.ScanDirectory.COLUMN_FIELD_SCAN_DIRECTORY_NAME, data.getStringExtra(UtilDirectorySelectActivity.KEY_RESULT));

                    try {
                        database.insertOrThrow(Entities.ScanDirectory.TABLE_NAME, null, values);
                        notifyLibraryChanges();
                    }
                    catch (final SQLiteConstraintException exception) {
                        new AlertDialog.Builder(this)
                                .setTitle(R.string.alert_dialog_title_search_path_exists)
                                .setMessage(R.string.alert_dialog_message_search_path_exists)
                                .setPositiveButton(android.R.string.ok, null)
                                .show();
                    }
                }
            }
            break;
        case REQUEST_CODE_EXCLUDE_PATH:
            if (resultCode == RESULT_OK) {
                SQLiteDatabase database = OpenHelper.getInstance(mProviderId).getWritableDatabase();
                if (database != null) {
                    final ContentValues values = new ContentValues();
                    values.put(Entities.ScanDirectory.COLUMN_FIELD_SCAN_DIRECTORY_IS_EXCLUDED, 1);
                    values.put(Entities.ScanDirectory.COLUMN_FIELD_SCAN_DIRECTORY_NAME, data.getStringExtra(UtilDirectorySelectActivity.KEY_RESULT));

                    try {
                        database.insertOrThrow(Entities.ScanDirectory.TABLE_NAME, null, values);
                        notifyLibraryChanges();
                    }
                    catch (final SQLiteConstraintException exception) {
                        new AlertDialog.Builder(this)
                                .setTitle(R.string.alert_dialog_title_search_path_exists)
                                .setMessage(R.string.alert_dialog_message_search_path_exists)
                                .setPositiveButton(android.R.string.ok, null)
                                .show();
                    }
                }
            }
            break;
        }
        mPagerAdapter.refresh();
	}

    protected void notifyLibraryChanges() {
        mPagerAdapter.refresh();

        final MediaManager mediaManager = PlayerApplication.mediaManager(mProviderId);
        final MediaManager.Provider provider = mediaManager.getProvider();
        if (provider != null) {
            provider.scanStart();
        }
    }
	
	private final MenuItem.OnMenuItemClickListener onAddMenuItemListener = new MenuItem.OnMenuItemClickListener() {

		@Override
		public boolean onMenuItemClick(MenuItem item) {
        Intent intent = new Intent(SearchPathActivity.this, UtilDirectorySelectActivity.class);
        startActivityForResult(intent, mViewPager.getCurrentItem() == 0 ? REQUEST_CODE_SEARCH_PATH : REQUEST_CODE_EXCLUDE_PATH);
        return false;
		}
	};
}
