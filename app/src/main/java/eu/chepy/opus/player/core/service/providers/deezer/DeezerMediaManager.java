package eu.chepy.opus.player.core.service.providers.deezer;

import eu.chepy.opus.player.core.service.providers.AbstractMediaManager;

public class DeezerMediaManager implements AbstractMediaManager {

    private Player player;

    private Provider provider;

    private int providerId;

    public DeezerMediaManager(int providerId) {
        this.providerId = providerId;
        player = new DeezerPlayer(this);
        provider = new DeezerProvider(this);
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
