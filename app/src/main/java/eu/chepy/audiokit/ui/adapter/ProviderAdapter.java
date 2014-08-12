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
package eu.chepy.audiokit.ui.adapter;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import eu.chepy.audiokit.R;
import eu.chepy.audiokit.core.service.providers.MediaManagerFactory;
import eu.chepy.audiokit.ui.activities.SettingsLibrariesActivity;

public class ProviderAdapter extends SimpleCursorAdapter {

    public static final String TAG = ProviderAdapter.class.getSimpleName();



    private Context context;

    private static final int ITEM_VIEW = R.layout.view_item_double_line_dragable;


    @SuppressWarnings("deprecation")
    public ProviderAdapter(Context context) {
        super(context, ITEM_VIEW, null, new String[] {}, new int[] {});
        this.context = context;
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
            convertView = layoutInflater.inflate(ITEM_VIEW, parent, false);
            viewHolder = new LibraryHolder();

            if (convertView != null) {
                viewHolder.textViews1 = (TextView) convertView.findViewById(R.id.line_one);
                viewHolder.textViews2 = (TextView) convertView.findViewById(R.id.line_two);
                convertView.setTag(viewHolder);
            }
        } else {
            viewHolder = (LibraryHolder) convertView.getTag();
        }

        viewHolder.textViews1.setText(cursor.getString(SettingsLibrariesActivity.COLUMN_PROVIDER_NAME));
        viewHolder.textViews2.setText(MediaManagerFactory.getDescriptionFromType(cursor.getInt(SettingsLibrariesActivity.COLUMN_PROVIDER_TYPE)));

        return convertView;
    }



    private final class LibraryHolder {

        public TextView textViews1;

        public TextView textViews2;
    }
}
