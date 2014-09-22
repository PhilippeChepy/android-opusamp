/*
 * SquareView.java
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
package net.opusapp.player.ui.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

public class SquareView extends ViewGroup {
	
	public static final String TAG = SquareView.class.getSimpleName();
	
	public SquareView(Context context, AttributeSet attributeSet) {
		super(context, attributeSet);
	}

	protected void onLayout(boolean paramBoolean, int paramInt1, int paramInt2, int paramInt3, int paramInt4) {
		getChildAt(0)
				.layout(0, 0, paramInt3 - paramInt1, paramInt4 - paramInt2);
	}

	protected void onMeasure(int paramInt1, int paramInt2) {
		View localView = getChildAt(0);
		localView.measure(paramInt1, paramInt1);
		int i = resolveSize(localView.getMeasuredWidth(), paramInt1);
		localView.measure(i, i);
		setMeasuredDimension(i, i);
	}

	public void requestLayout() {
		forceLayout();
	}
}
