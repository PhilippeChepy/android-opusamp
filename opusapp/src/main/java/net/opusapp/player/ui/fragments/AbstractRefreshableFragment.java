/*
 * AbstractRefreshableFragment.java
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
package net.opusapp.player.ui.fragments;

import android.support.v4.app.Fragment;
import android.text.SpannableString;
import android.text.style.UnderlineSpan;
import android.view.View;

import net.opusapp.player.core.service.providers.AbstractMediaManager;
import net.opusapp.player.ui.views.CustomLinkTextView;
import net.opusapp.player.ui.views.CustomTextView;

public abstract class AbstractRefreshableFragment extends Fragment {
	
	public static final String TAG = AbstractRefreshableFragment.class.getSimpleName();



    /*
        Empty actions
     */
    private AbstractMediaManager.AbstractEmptyContentAction emptyContentAction;



    public abstract void doRefresh();

    protected void setEmptyContentAction(final AbstractMediaManager.AbstractEmptyContentAction emptyContentAction) {
        this.emptyContentAction = emptyContentAction;
    }

    public void setEmptyAction(CustomTextView descriptionView, CustomLinkTextView actionView) {
        if (emptyContentAction != null) {
            descriptionView.setText(emptyContentAction.getDescription());

            SpannableString content = new SpannableString(getString(emptyContentAction.getActionDescription()));
            content.setSpan(new UnderlineSpan(), 0, content.length(), 0);

            actionView.setText(content);
            actionView.setClickable(true);
            actionView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    emptyContentAction.launch(getActivity());
                }
            });
        }
    }
}
