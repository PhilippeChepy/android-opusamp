package eu.chepy.audiokit.core.service.providers;

import android.app.Activity;

public interface AbstractProviderAction {

    public String getDescription();

    public boolean isVisible();

    public void launch(Activity source);
}
