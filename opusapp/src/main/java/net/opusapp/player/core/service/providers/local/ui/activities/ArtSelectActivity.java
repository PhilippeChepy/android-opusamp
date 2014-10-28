package net.opusapp.player.core.service.providers.local.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;

import com.astuetz.PagerSlidingTabStrip;

import net.opusapp.player.R;
import net.opusapp.player.core.service.providers.AbstractMediaManager;
import net.opusapp.player.core.service.providers.local.ui.fragments.ArtSelectionFragment;
import net.opusapp.player.ui.activities.UtilDirectorySelectActivity;
import net.opusapp.player.ui.adapter.ux.PagerAdapter;
import net.opusapp.player.ui.utils.PlayerApplication;

public class ArtSelectActivity extends ActionBarActivity {

    private ViewPager viewPager;

    private final static int REQUEST_CODE_EMBEDDED_TAGS = 1;

    private final static int REQUEST_CODE_LOCAL_FILESYSTEM = 2;

    private final static int REQUEST_CODE_INTERNET = 3;

    private PagerAdapter pagerAdapter;

    private int providerId;

    private MenuItem addMenuItem;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        providerId = getIntent().getIntExtra(AbstractMediaManager.Provider.KEY_PROVIDER_ID, -1);
        final String sourceId = getIntent().getStringExtra(AbstractMediaManager.Provider.KEY_SOURCE_ID);

        setContentView(R.layout.activity_tabbed_grids);
        PlayerApplication.applyActionBar(this);

        Bundle fromEmbeddedTagsBundle = new Bundle();
        fromEmbeddedTagsBundle.putInt(ArtSelectionFragment.CONTENT_TYPE_KEY, ArtSelectionFragment.CONTENT_EMBEDDED_TAGS);
        fromEmbeddedTagsBundle.putString(AbstractMediaManager.Provider.KEY_SOURCE_ID, sourceId);
        fromEmbeddedTagsBundle.putInt(AbstractMediaManager.Provider.KEY_PROVIDER_ID, providerId);

        Bundle fromLocalFileSystemBundle = new Bundle();
        fromLocalFileSystemBundle.putInt(ArtSelectionFragment.CONTENT_TYPE_KEY, ArtSelectionFragment.CONTENT_LOCAL_FILESYSTEM);
        fromLocalFileSystemBundle.putString(AbstractMediaManager.Provider.KEY_SOURCE_ID, sourceId);
        fromLocalFileSystemBundle.putInt(AbstractMediaManager.Provider.KEY_PROVIDER_ID, providerId);

        Bundle fromInternetBundle = new Bundle();
        fromInternetBundle.putInt(ArtSelectionFragment.CONTENT_TYPE_KEY, ArtSelectionFragment.CONTENT_INTERNET);
        fromInternetBundle.putString(AbstractMediaManager.Provider.KEY_SOURCE_ID, sourceId);
        fromInternetBundle.putInt(AbstractMediaManager.Provider.KEY_PROVIDER_ID, providerId);

        pagerAdapter = new PagerAdapter(this, getSupportFragmentManager());
        pagerAdapter.addFragment(new ArtSelectionFragment(), fromEmbeddedTagsBundle, R.string.tab_label_from_embedded_tag);
        pagerAdapter.addFragment(new ArtSelectionFragment(), fromLocalFileSystemBundle, R.string.tab_label_from_local_filesystem);
        // TODO: pagerAdapter.addFragment(new ArtSelectionFragment(), fromInternetBundle, R.string.tab_label_from_internet);

        viewPager = (ViewPager)findViewById(R.id.pager_viewpager);
        viewPager.setPageMargin(getResources().getInteger(R.integer.viewpager_margin_width));
        viewPager.setOffscreenPageLimit(pagerAdapter.getCount());
        viewPager.setAdapter(pagerAdapter);

        final PagerSlidingTabStrip scrollingTabs = (PagerSlidingTabStrip) findViewById(R.id.pager_tabs);
        scrollingTabs.setViewPager(viewPager);
        scrollingTabs.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int i, float v, int i2) {
                
            }

            @Override
            public void onPageSelected(int i) {
                if (addMenuItem != null) {
                    addMenuItem.setVisible(i == 1);
                }
            }

            @Override
            public void onPageScrollStateChanged(int i) {

            }
        });
        PlayerApplication.applyThemeOnPagerTabs(scrollingTabs);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        addMenuItem = menu.add(Menu.NONE, 0, 0, R.string.menuitem_label_add);
        addMenuItem.setIcon(PlayerApplication.iconsAreDark() ?  R.drawable.ic_action_add : R.drawable.ic_action_add_dark);
        addMenuItem.setVisible(viewPager != null && viewPager.getCurrentItem() == 1);
        MenuItemCompat.setShowAsAction(addMenuItem, MenuItemCompat.SHOW_AS_ACTION_ALWAYS | MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT);
        addMenuItem.setOnMenuItemClickListener(onAddMenuItemListener);
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (providerId < 0) {
            return;
        }

        switch(requestCode) {
            case REQUEST_CODE_LOCAL_FILESYSTEM:
                if (resultCode == RESULT_OK) {
                }
                break;
            case REQUEST_CODE_INTERNET:
                if (resultCode == RESULT_OK) {

                }
        }
        pagerAdapter.refresh();
    }

    private final MenuItem.OnMenuItemClickListener onAddMenuItemListener = new MenuItem.OnMenuItemClickListener() {

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            Intent intent = new Intent(ArtSelectActivity.this, UtilDirectorySelectActivity.class);
            startActivityForResult(intent, viewPager.getCurrentItem() == 0 ? REQUEST_CODE_EMBEDDED_TAGS : REQUEST_CODE_LOCAL_FILESYSTEM);
            return false;
        }
    };
}
