/*
 * MetadataListAdapter.java
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
package eu.chepy.opus.player.ui.adapter.ux;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

import eu.chepy.opus.player.R;
import eu.chepy.opus.player.core.service.providers.MediaMetadata;

public class MetadataListAdapter extends BaseAdapter {

    private List<MediaMetadata> mediaMetadataList;

    private Context context;

    public MetadataListAdapter(Context context, List<MediaMetadata> mediaMetadataList) {
        this.mediaMetadataList = mediaMetadataList;
        this.context = context;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public int getItemViewType(int position) {
        return 0;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return true;
    }

    @Override
    public Object getItem(int position) {
        return mediaMetadataList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return mediaMetadataList.get(position).index;
    }

    @Override
    public View getView(int position, View view, ViewGroup container) {
        Holder viewHolder;

        if (view == null) {
            viewHolder = new Holder();

            LayoutInflater inflater = LayoutInflater.from(context);
            view = inflater.inflate(R.layout.view_item_property, container, false);

            viewHolder.textView1 = (TextView) view.findViewById(android.R.id.text1);
            viewHolder.textView2 = (TextView) view.findViewById(android.R.id.text2);

            view.setTag(viewHolder);
        }
        else {
            viewHolder = (Holder) view.getTag();
        }

        final MediaMetadata reviewItem = mediaMetadataList.get(position);
        if (viewHolder != null) {
            viewHolder.textView1.setText(reviewItem.description);
            viewHolder.textView2.setText(reviewItem.value);
        }
        return view;
    }

    @Override
    public int getCount() {
        return mediaMetadataList.size();
    }

    private final class Holder {

        public TextView textView1;

        public TextView textView2;
    }
}