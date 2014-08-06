package eu.chepy.audiokit.core.service.providers.local.ui.activities;

import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

import eu.chepy.audiokit.R;
import eu.chepy.audiokit.core.service.providers.AbstractMediaManager;
import eu.chepy.audiokit.core.service.providers.AbstractMediaProvider;
import eu.chepy.audiokit.ui.utils.PlayerApplication;

public class SettingsActivity extends PreferenceActivity {

    private int providerId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        providerId = 0;

        if (intent != null) {
            providerId = intent.getIntExtra(AbstractMediaProvider.KEY_PROVIDER_ID, 0);
        }

        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        final PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setSharedPreferencesName("provider-" + providerId);
        preferenceManager.setSharedPreferencesMode(MODE_PRIVATE);

        addPreferencesFromResource(R.xml.local_preferences);

        setLocationHandler();
    }

    @SuppressWarnings("deprecation")
    private void setLocationHandler() {
        final Preference openSourceLicenses = findPreference("location");
        openSourceLicenses.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(final Preference preference) {
                final AbstractMediaManager mediaManager =
                        PlayerApplication.mediaManagers[PlayerApplication.getManagerIndex(providerId)];

                final AbstractMediaProvider mediaProvider = mediaManager.getMediaProvider();
                mediaProvider.getAbstractProviderAction(0).launch(SettingsActivity.this);
                return true;
            }
        });
    }
}
