/*
 * PagerAdapter.java
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
package eu.chepy.audiokit.ui.adapter.ux;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.view.ViewGroup;

import java.util.ArrayList;

import eu.chepy.audiokit.ui.fragments.AbstractRefreshableFragment;

public class PagerAdapter extends FragmentStatePagerAdapter {

	public static final String TAG = "PagerAdapter";

    private final ArrayList<Fragment> pagedFragments = new ArrayList<Fragment>();

    private final ArrayList<String> pagedFragmentTitles = new ArrayList<String>();

    private Context context;
    
    public PagerAdapter(Context sourceContext, FragmentManager manager) {
        super(manager);
        context = sourceContext;
    }

    @Override
    public int getCount() {
        return pagedFragmentTitles.size();
    }

    @Override
    public Fragment getItem(int position) {
        return pagedFragments.get(position);
    }

    @Override
    public CharSequence getPageTitle(int position) {
    	if (position < pagedFragmentTitles.size()) {
    		final String title = pagedFragmentTitles.get(position);
    		
    		if (title == null) {
    			return "";
    		}
    		
    		return title;
    	}
    	
    	return "";
    }

    public void addFragment(Fragment fragment, Bundle bundle, int titleResId) {
        fragment.setArguments(bundle);
        pagedFragments.add(fragment);
        pagedFragmentTitles.add(context.getString(titleResId));
        notifyDataSetChanged();
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        Fragment fragment = (Fragment) super.instantiateItem(container, position);
        pagedFragments.set(position, fragment);
        return fragment;
    }

    public void doRefresh() {
        for (Fragment fragment : pagedFragments) {
            if (fragment instanceof AbstractRefreshableFragment) {
                if (fragment.isAdded()) {
                    ((AbstractRefreshableFragment) fragment).doRefresh();
                }
            }
        }
    }
}
