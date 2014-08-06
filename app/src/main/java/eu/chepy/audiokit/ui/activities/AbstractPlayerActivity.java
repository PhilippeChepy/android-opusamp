/*
 * AbstractPlayerActivity.java
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

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.ActionBarActivity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import com.sothree.slidinguppanel.SlidingUpPanelLayout;

import eu.chepy.audiokit.core.service.IPlayerService;
import eu.chepy.audiokit.ui.utils.MusicConnector;
import eu.chepy.audiokit.ui.utils.PlayerViewHelper;

public abstract class AbstractPlayerActivity extends ActionBarActivity implements ServiceConnection {

    public static final String TAG = AbstractPlayerActivity.class.getSimpleName();

    private PlayerViewHelper playerViewHelper;

    private boolean usingActionBar;

    public PlayerViewHelper getPlayerView() {
        return playerViewHelper;
    }


    protected void initPlayerView(Bundle savedInstanceState) {
        initPlayerView(savedInstanceState, true);
    }

    protected void initPlayerView(Bundle savedInstanceState, boolean usesActionBar) {
        playerViewHelper = new PlayerViewHelper(this);

        usingActionBar = usesActionBar;
        if (usingActionBar) {
            if (playerViewHelper.restoreInstanceState(savedInstanceState)) {
                getSupportActionBar().show();
            } else {
                getSupportActionBar().hide();
            }
        }
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        MusicConnector.playerService = IPlayerService.Stub.asInterface(iBinder);
        playerViewHelper.registerServiceListener();
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        playerViewHelper.unregisterServiceListener();
        MusicConnector.playerService = null; // Used by app widgets...
    }

    @Override
    protected void onPause() {
        super.onPause();
        //ImageLoaderFactory.getInstance().pause();
    }

    @Override
    protected void onResume() {
        super.onResume();

        /*
        Double allocated = (double) (Debug.getNativeHeapAllocatedSize())/ (double)((1048576));
        Double available = (double) (Debug.getNativeHeapSize())/1048576.0;
        Double free = (double) (Debug.getNativeHeapFreeSize())/1048576.0;
        DecimalFormat df = new DecimalFormat();
        df.setMaximumFractionDigits(2);
        df.setMinimumFractionDigits(2);

        Log.d("tag", "debug. =================================");
        Log.d("tag", "debug.heap native: allocated " + df.format(allocated) + "MB of " + df.format(available) + "MB (" + df.format(free) + "MB free)");
        Log.d("tag", "debug.memory: allocated: " + df.format((double) (Runtime.getRuntime().totalMemory()/1048576)) + "MB of " + df.format((double) (Runtime.getRuntime().maxMemory()/1048576))+ "MB (" + df.format((double)(Runtime.getRuntime().freeMemory()/1048576)) +"MB free)");
        */

        //ImageLoaderFactory.getInstance().resume();
        playerViewHelper.refreshViews();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        //ImageLoaderFactory.getInstance().stop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (usingActionBar) {
            playerViewHelper.saveInstanceState(outState, getSupportActionBar().isShowing());
        }
    }

    protected void unbindDrawables(View view)
    {
        if (view.getBackground() != null) {
            view.getBackground().setCallback(null);
        }

        if (view instanceof ViewGroup && !(view instanceof AdapterView)) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            {
                unbindDrawables(((ViewGroup) view).getChildAt(i));
            }
            ((ViewGroup) view).removeAllViews();
        }
    }

    @Override
    public void onBackPressed() {
        final SlidingUpPanelLayout slidingUpPanelLayout = getPlayerView().getSlidingPanel();

        if (slidingUpPanelLayout != null && slidingUpPanelLayout.isPanelExpanded()) {
            slidingUpPanelLayout.collapsePanel();
            return;
        }

        super.onBackPressed();
    }
}
