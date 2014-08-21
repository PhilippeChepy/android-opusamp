/*
 * NavigationMenuSection.java
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
package eu.chepy.opus.player.ui.drawer;

public class NavigationMenuSection implements AbstractNavigationDrawerItem {

    public static final int SECTION_TYPE = 0;
    private int id;
    private String label;

    private NavigationMenuSection() {
    }
    
    public static NavigationMenuSection create( int id, String label ) {
    	NavigationMenuSection section = new NavigationMenuSection();
        section.setId(id);
        section.setLabel(label);
        return section;
    }
    
    @Override
    public int getType() {
        return SECTION_TYPE;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    @Override
    public boolean isEnabled() {
        return false;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @Override
    public boolean updateActionBarTitle() {
        return false;
    }
}