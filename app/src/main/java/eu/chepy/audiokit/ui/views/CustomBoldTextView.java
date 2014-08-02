package eu.chepy.audiokit.ui.views;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import eu.chepy.audiokit.ui.utils.TypefaceCache;

public class CustomBoldTextView extends TextView {

    public CustomBoldTextView(Context context, AttributeSet attributeSet)
    {
        super(context, attributeSet);
        if (!isInEditMode()) {
            setTypeface(TypefaceCache.getTypeface("RobotoRegular.ttf", context));
        }
    }
}
