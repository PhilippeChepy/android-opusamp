package net.opusapp.player.ui.views;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RadioButton;

import net.opusapp.player.ui.utils.TypefaceCache;

public class CustomRadioButton extends RadioButton {

    public CustomRadioButton(Context context, AttributeSet attributeSet)
    {
        super(context, attributeSet);
        if (!isInEditMode()) {
            setTypeface(TypefaceCache.getTypeface("RobotoLight.ttf", context));
        }
    }
}
