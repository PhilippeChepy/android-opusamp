package net.opusapp.player.core.service.providers.local;

import net.opusapp.player.core.service.providers.AbstractMediaManager;
import net.opusapp.player.core.service.providers.MediaManagerFactory;

public class LocalMediaManager implements AbstractMediaManager {

    private Player player;

    private Provider provider;

    private int mProviderId;

    private String mName;

    public LocalMediaManager(int providerId, String name) {
        mProviderId = providerId;
        mName = name;
        player = new LocalPlayer(this);
        provider = new LocalProvider(this);
    }

    @Override
    public int getMediaManagerType() {
        return LOCAL_MEDIA_MANAGER;
    }

    @Override
    public String getName() {
        return mName;
    }

    @Override
    public String getDescription() {
        return MediaManagerFactory.getDescriptionFromType(LOCAL_MEDIA_MANAGER);
    }

    @Override
    public int getId() {
        return mProviderId;
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