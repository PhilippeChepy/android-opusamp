/*
 * HeaderWrapperAdapter.java
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
package eu.chepy.audiokit.ui.adapter.ux;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.BaseAdapter;

public class HeaderWrapperAdapter extends BaseAdapter{

    protected Adapter wrappedAdapter;

    protected View headerView;


    public HeaderWrapperAdapter(Adapter baseAdapter, View headerView) {
        wrappedAdapter = baseAdapter;
        this.headerView = headerView;
    }

    @Override
    public int getCount() {
        return wrappedAdapter.getCount() + 1;
    }

    @Override
    public Object getItem(int i) {
        if (i == 0) {
            return null;
        }

        return wrappedAdapter.getItem(i);
    }

    @Override
    public long getItemId(int i) {
        return i == 0 ? -1 : wrappedAdapter.getItemId(i);
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? 1 : 0;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        if (i == 0) { // && getItemViewType(i) == 1) {
            return headerView;
        }

        return wrappedAdapter.getView(i - 1, view, viewGroup);
    }
}
