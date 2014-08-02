package eu.chepy.audiokit.ui.drawer;

public class NavigationMenuItem implements AbstractNavigationDrawerItem {

    public static final int ITEM_TYPE = 1 ;

    private int id ;
    private String label ;  
    private int icon ;
    private boolean updateActionBarTitle ;

    private NavigationMenuItem() {
    }

    public static NavigationMenuItem create( int id, String label, int icon, boolean updateActionBarTitle) {
    	NavigationMenuItem item = new NavigationMenuItem();
        item.setId(id);
        item.setLabel(label);
        item.setIcon(icon);
        item.setUpdateActionBarTitle(updateActionBarTitle);
        return item;
    }
    
    @Override
    public int getType() {
        return ITEM_TYPE;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public int getIcon() {
        return icon;
    }

    public void setIcon(int icon) {
        this.icon = icon;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean updateActionBarTitle() {
        return this.updateActionBarTitle;
    }

    public void setUpdateActionBarTitle(boolean updateActionBarTitle) {
        this.updateActionBarTitle = updateActionBarTitle;
    }
}
