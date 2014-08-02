package eu.chepy.audiokit.ui.adapter.ux;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

import eu.chepy.audiokit.R;
import eu.chepy.audiokit.core.service.providers.Metadata;

public class MetadataListAdapter extends BaseAdapter {

    private List<Metadata> metadataList;

    private Context context;

    public MetadataListAdapter(Context context, List<Metadata> metadataList) {
        this.metadataList = metadataList;
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
        return metadataList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return metadataList.get(position).index;
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

        final Metadata reviewItem = metadataList.get(position);
        if (viewHolder != null) {
            viewHolder.textView1.setText(reviewItem.description);
            viewHolder.textView2.setText(reviewItem.value);
        }
        return view;
    }

    @Override
    public int getCount() {
        return metadataList.size();
    }

    private final class Holder {

        public TextView textView1;

        public TextView textView2;
    }
}