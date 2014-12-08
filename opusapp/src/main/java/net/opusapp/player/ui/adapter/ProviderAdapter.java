/*
 * ProviderAdapter.java
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
package net.opusapp.player.ui.adapter;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import net.opusapp.player.R;
import net.opusapp.player.core.service.providers.MediaManagerFactory;

public class ProviderAdapter extends SimpleCursorAdapter {

    public static final String TAG = ProviderAdapter.class.getSimpleName();



    private Context context;

    private int itemView = R.layout.view_item_double_line_dragable;

    private int columnProviderName = 1;

    private int columnProviderType = 2;



    public ProviderAdapter(Context context, int itemView, int columns[]) {
        super(context, itemView, null, new String[] {}, new int[] {}, 0);
        this.context = context;

        this.itemView = itemView;

        if (columns != null) {
            columnProviderName = columns[0];
            columnProviderType = columns[1];
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final Cursor cursor = (Cursor) getItem(position);
        LibraryHolder viewHolder;

        if (cursor == null) {
            return convertView;
        }

        if (convertView == null) {
            final LayoutInflater layoutInflater = ((Activity) context).getLayoutInflater();
            convertView = layoutInflater.inflate(itemView, parent, false);
            viewHolder = new LibraryHolder();

            if (convertView != null) {
                viewHolder.textViews1 = (TextView) convertView.findViewById(R.id.line_one);
                viewHolder.textViews2 = (TextView) convertView.findViewById(R.id.line_two);
                viewHolder.menuHandle = convertView.findViewById(R.id.context_menu_handle);

                if (viewHolder.menuHandle != null) {
                    // TODO: install menu handler instead of masking it.
                    viewHolder.menuHandle.setVisibility(View.GONE);
                }

                convertView.setTag(viewHolder);
            }
        } else {
            viewHolder = (LibraryHolder) convertView.getTag();
        }

        viewHolder.textViews1.setText(cursor.getString(columnProviderName));
        viewHolder.textViews2.setText(MediaManagerFactory.getDescriptionFromType(cursor.getInt(columnProviderType)));
        return convertView;
    }



    private final class LibraryHolder {

        public TextView textViews1;

        public TextView textViews2;

        public View menuHandle;
    }
}
