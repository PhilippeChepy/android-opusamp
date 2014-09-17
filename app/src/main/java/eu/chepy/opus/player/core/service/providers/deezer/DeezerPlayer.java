package eu.chepy.opus.player.core.service.providers.deezer;

import eu.chepy.opus.player.core.service.providers.AbstractMediaManager;

public class DeezerPlayer implements AbstractMediaManager.Player {

    public DeezerPlayer(DeezerMediaManager mediaManager) {

    }

    @Override
    public boolean loadMedia(AbstractMediaManager.Media media) {
        return false;
    }

    @Override
    public void unloadMedia(AbstractMediaManager.Media media) {

    }

    @Override
    public void playerSetContent(AbstractMediaManager.Media context) {

    }

    @Override
    public void playerPlay() {

    }

    @Override
    public void playerPause(boolean setPaused) {

    }

    @Override
    public void playerStop() {

    }

    @Override
    public boolean playerIsPlaying() {
        return false;
    }

    @Override
    public void playerSeek(long position) {

    }

    @Override
    public long playerGetPosition() {
        return 0;
    }

    @Override
    public long playerGetDuration() {
        return 0;
    }

    @Override
    public boolean equalizerIsEnabled() {
        return false;
    }

    @Override
    public long equalizerSetEnabled(boolean enabled) {
        return 0;
    }

    @Override
    public long equalizerBandSetGain(int band, int gain) {
        return 0;
    }

    @Override
    public long equalizerBandGetGain(int band) {
        return 0;
    }

    @Override
    public void addCompletionListener(OnProviderCompletionListener listener) {

    }

    @Override
    public void removeCompletionListener(OnProviderCompletionListener listener) {

    }

    @Override
    public void resetListeners() {

    }
}
