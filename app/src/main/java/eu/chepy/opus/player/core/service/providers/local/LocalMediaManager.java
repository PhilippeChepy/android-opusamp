package eu.chepy.opus.player.core.service.providers.local;

import eu.chepy.opus.player.core.service.providers.AbstractMediaManager;
import eu.chepy.opus.player.core.service.providers.AbstractMediaPlayer;
import eu.chepy.opus.player.core.service.providers.AbstractMediaProvider;

public class LocalMediaManager implements AbstractMediaManager {

    private AbstractMediaPlayer mediaPlayer;

    private AbstractMediaProvider mediaProvider;

    private int providerId;

    public LocalMediaManager(int providerId) {
        this.providerId = providerId;
        mediaPlayer = new LocalMediaPlayer(this);
        mediaProvider = new LocalMediaProvider(this, providerId);
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
    public AbstractMediaProvider getMediaProvider() {
        return mediaProvider;
    }

    @Override
    public AbstractMediaPlayer getMediaPlayer() {
        return mediaPlayer;
    }
}