package net.opusapp.player.core.service.providers.local;

import net.opusapp.player.core.service.providers.AbstractMediaManager;

public class LocalMediaManager implements AbstractMediaManager {

    private Player player;

    private Provider provider;

    private int providerId;

    public LocalMediaManager(int providerId) {
        this.providerId = providerId;
        player = new LocalPlayer(this);
        provider = new LocalProvider(this);
    }

    @Override
    public int getMediaManagerType() {
        return LOCAL_MEDIA_MANAGER;
    }

    @Override
    public int getMediaManagerId() {
        return providerId;
    }

    @Override
    public Provider getProvider() {
        return provider;
    }

    @Override
    public Player getPlayer() {
        return player;
    }
}