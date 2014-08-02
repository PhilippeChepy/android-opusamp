package eu.chepy.audiokit.ui.adapter.ux;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

import eu.chepy.audiokit.R;
import eu.chepy.audiokit.ui.drawer.AbstractNavigationDrawerItem;
import eu.chepy.audiokit.ui.drawer.NavigationMenuItem;
import eu.chepy.audiokit.ui.drawer.NavigationMenuSection;
import eu.chepy.audiokit.ui.utils.PlayerApplication;
import eu.chepy.audiokit.ui.views.CustomBoldTextView;
import eu.chepy.audiokit.ui.views.CustomTextView;

public class NavigationDrawerAdapter extends ArrayAdapter<AbstractNavigationDrawerItem> {

    private LayoutInflater inflater;
    
    public NavigationDrawerAdapter(Context context, int textViewResourceId, List<AbstractNavigationDrawerItem> objects ) {
        super(context, textViewResourceId, objects);
        inflater = LayoutInflater.from(PlayerApplication.context);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view;
        
        AbstractNavigationDrawerItem menuItem = this.getItem(position);
        if (menuItem.getType() == NavigationMenuItem.ITEM_TYPE) {
            view = getItemView(convertView, parent, menuItem );
        }
        else {
            view = getSectionView(convertView, parent, menuItem);
        }
        return view ;
    }
    
    public View getItemView( View convertView, ViewGroup parentView, AbstractNavigationDrawerItem navDrawerItem ) {
    	NavigationMenuItem menuItem = (NavigationMenuItem) navDrawerItem ;
        NavMenuItemHolder navMenuItemHolder = null;
        
        if (convertView == null) {
            convertView = inflater.inflate( R.layout.view_drawer_item_single_line, parentView, false);
            if (convertView != null) {
                CustomTextView labelView = (CustomTextView) convertView.findViewById(R.id.drawer_item_label);
                ImageView iconView = (ImageView) convertView.findViewById(R.id.drawer_item_icon);

                navMenuItemHolder = new NavMenuItemHolder();
                navMenuItemHolder.labelView = labelView;
                navMenuItemHolder.iconView = iconView;

                convertView.setTag(navMenuItemHolder);
            }
        }

        if ( navMenuItemHolder == null && convertView != null) {
            navMenuItemHolder = (NavMenuItemHolder) convertView.getTag();
        }

        if (navMenuItemHolder != null) {
            navMenuItemHolder.labelView.setText(menuItem.getLabel());
            navMenuItemHolder.iconView.setImageResource(menuItem.getIcon());
        }
        
        return convertView ;
    }

    public View getSectionView(View convertView, ViewGroup parentView, AbstractNavigationDrawerItem navDrawerItem) {
        
        NavigationMenuSection menuSection = (NavigationMenuSection) navDrawerItem ;
        NavMenuSectionHolder navMenuItemHolder = null;
        
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.view_drawer_item_separator, parentView, false);
            if (convertView != null) {
                CustomBoldTextView labelView = (CustomBoldTextView) convertView.findViewById(R.id.drawer_section_label);

                navMenuItemHolder = new NavMenuSectionHolder();
                navMenuItemHolder.labelView = labelView;
                convertView.setTag(navMenuItemHolder);
            }
        }

        if ( navMenuItemHolder == null ) {
            navMenuItemHolder = (NavMenuSectionHolder) convertView.getTag();
        }
                    
        navMenuItemHolder.labelView.setText(menuSection.getLabel());
        
        return convertView ;
    }
    
    @Override
    public int getViewTypeCount() {
        return 2;
    }
    
    @Override
    public int getItemViewType(int position) {
        return this.getItem(position).getType();
    }
    
    @Override
    public boolean isEnabled(int position) {
        return getItem(position).isEnabled();
    }
    
    
    private static class NavMenuItemHolder {
        private TextView labelView;
        private ImageView iconView;
    }
    
    private class NavMenuSectionHolder {
        private TextView labelView;
    }
}
