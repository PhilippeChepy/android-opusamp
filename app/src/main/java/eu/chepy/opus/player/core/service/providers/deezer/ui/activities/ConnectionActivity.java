package eu.chepy.opus.player.core.service.providers.deezer.ui.activities;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;

import com.deezer.sdk.model.Permissions;
import com.deezer.sdk.network.connect.DeezerConnect;
import com.deezer.sdk.network.connect.SessionStore;
import com.deezer.sdk.network.connect.event.DialogListener;

import eu.chepy.opus.player.core.service.providers.AbstractMediaManager;
import eu.chepy.opus.player.core.service.providers.deezer.DeezerProvider;
import eu.chepy.opus.player.ui.utils.PlayerApplication;

public class ConnectionActivity extends ActionBarActivity {

    private int providerId = 0;

    private DeezerConnect deezerConnect;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        providerId = getIntent().getIntExtra(AbstractMediaManager.Provider.KEY_PROVIDER_ID, -1);
    }

    @Override
    protected void onResume() {
        super.onResume();

        for (int managerIndex = 0 ; managerIndex < PlayerApplication.mediaManagers.length ; managerIndex++) {
            if (PlayerApplication.mediaManagers[managerIndex].getMediaManagerId() == providerId) {
                final DeezerProvider provider = (DeezerProvider) PlayerApplication.mediaManagers[managerIndex].getProvider();

                deezerConnect = provider.getDeezerConnect();

                if (deezerConnect.isSessionValid()) {
                    deezerConnect.logout(this);
                    finish();
                }
                else {
                    deezerConnect.authorize(this, permissions, listener);
                }

                break;
            }
        }
    }

    String[] permissions = new String[] {
            Permissions.BASIC_ACCESS,
            Permissions.MANAGE_LIBRARY,
            Permissions.LISTENING_HISTORY
    };

    DialogListener listener = new DialogListener() {
        public void onComplete(Bundle values) {
            SessionStore sessionStore = new SessionStore();
            sessionStore.save(deezerConnect, PlayerApplication.context);
            finish();
        }

        public void onCancel() {
            finish();
        }

        public void onException(Exception e) {
            // TODO: show error message...
            finish();
        }
    };


}
