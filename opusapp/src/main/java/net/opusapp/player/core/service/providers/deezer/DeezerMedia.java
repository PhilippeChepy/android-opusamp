package net.opusapp.player.core.service.providers.deezer;

import net.opusapp.player.core.service.providers.AbstractMediaManager;

public class DeezerMedia extends AbstractMediaManager.Media {

    @Override
    public String getUri() {
        return null;
    }

    @Override
    public boolean isLoaded() {
        return false;
    }

    @Override
    public void load() {

    }

    @Override
    public void unload() {

    }
}
