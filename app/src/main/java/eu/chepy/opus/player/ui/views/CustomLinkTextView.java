/*
 * CustomTextView.java
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
package eu.chepy.opus.player.ui.views;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.widget.TextView;

import eu.chepy.opus.player.R;
import eu.chepy.opus.player.ui.utils.TypefaceCache;

public class CustomLinkTextView extends TextView {

    public CustomLinkTextView(Context context, AttributeSet attributeSet)
    {
        super(context, attributeSet);
        if (!isInEditMode()) {
            setTypeface(TypefaceCache.getTypeface("RobotoLight.ttf", context));

            final Resources resources = getResources();
            if (resources != null) {
                setTextColor(getResources().getColor(R.color.link_blue));
            }
        }
    }
}