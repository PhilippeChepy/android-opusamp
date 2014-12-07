package net.opusapp.player.ui.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.widget.EditText;

import net.opusapp.player.ui.utils.TypefaceCache;

public class CustomFilteringEditText extends EditText {

    public CustomFilteringEditText(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);

        if (!isInEditMode()) {
            setTypeface(TypefaceCache.getTypeface("RobotoLight.ttf", context));
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }
}
