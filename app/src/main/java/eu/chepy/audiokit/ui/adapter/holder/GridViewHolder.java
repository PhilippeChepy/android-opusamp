/*
 * GridViewHolder.java
 *
 * Copyright (c) 2012, Philippe Chepy
 * All rights reserved.
 *
 * This software is the confidential and proprietary information
 * of Philippe Chepy.
 * You shall not disclose such Confidential Information.
 *
 * http://www.chepy.eu
 */
package eu.chepy.audiokit.ui.adapter.holder;

import android.view.View;
import android.widget.TextView;

import eu.chepy.audiokit.R;
import eu.chepy.audiokit.ui.views.SquareImageView;

public class GridViewHolder {
	
	public SquareImageView image;
	
	public TextView lineOne;
	
	public TextView lineTwo;
	
	public View customView;
	
	public GridViewHolder(View view) {
		image = (SquareImageView) view.findViewById(R.id.image);
        lineOne = (TextView) view.findViewById(R.id.line_one);
        lineTwo = (TextView) view.findViewById(R.id.line_two);
	}
}
