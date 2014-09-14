package eu.chepy.opus.player.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;

import eu.chepy.opus.player.R;
import eu.chepy.opus.player.ui.fragments.SetupWelcomeFragment;
import eu.chepy.opus.player.ui.utils.PlayerApplication;

public class SetupActivity extends FragmentActivity {

    private static final String SETUP_FRAGMENT_MAIN = "setup_fragment";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_setup);

        Fragment currentFragment = getSupportFragmentManager().findFragmentByTag(SETUP_FRAGMENT_MAIN);
        if (currentFragment == null) {
            currentFragment = new SetupWelcomeFragment();
        }

        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container, currentFragment, SETUP_FRAGMENT_MAIN);
        fragmentTransaction.commit();
    }

    public void updateFragment(final Fragment fragment) {
        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container, fragment, SETUP_FRAGMENT_MAIN);
        fragmentTransaction.commit();
    }

    public void terminate() {
        finish();
        PlayerApplication.disableFirstRun();
        startActivity(new Intent(this, LibraryMainActivity.class));
    }
}
