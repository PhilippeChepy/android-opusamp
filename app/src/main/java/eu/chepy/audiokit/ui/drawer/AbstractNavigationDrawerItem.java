package eu.chepy.audiokit.ui.drawer;

public interface AbstractNavigationDrawerItem {
    public int getId();
    public String getLabel();
    public int getType();
    public boolean isEnabled();
    public boolean updateActionBarTitle();
}
