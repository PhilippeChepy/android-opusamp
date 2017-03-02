package net.opusapp.player.ui.activities;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.vending.licensing.Policy;

import net.opusapp.licensing.LicenseCheckerCallback;
import net.opusapp.player.R;
import net.opusapp.player.licensing.BuildSpecific;
import net.opusapp.player.utils.LogUtils;

public class OpusActivity extends ActionBarActivity implements LicenseCheckerCallback {

    public static final String TAG = OpusActivity.class.getSimpleName();



    private InterstitialAd interstitial = null;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (BuildSpecific.canShowInterstitial()) {
            int noDisplayCounter = BuildSpecific.adDisplayGetCounter();

            if (noDisplayCounter >= 5) {
                interstitial = new InterstitialAd(this);
                interstitial.setAdUnitId("ca-app-pub-3216044483473621/6665880790");

                AdRequest adRequest = new AdRequest.Builder()
                        .addTestDevice("2A8AFDBBC128894B872A1F3DAE11358D") // Nexus 5
                        .addTestDevice("EA2776551264A5F012EAD8016CCAFD67") // LG GPad
                        .build();

                interstitial.loadAd(adRequest);
                interstitial.setAdListener(new AdListener() {
                    @Override
                    public void onAdLoaded() {
                        super.onAdLoaded();
                        BuildSpecific.adDisplayReset();
                    }
                });
            } else {
                BuildSpecific.adDisplayInc();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (interstitial != null && interstitial.isLoaded() && BuildSpecific.canShowInterstitial()) {
            interstitial.show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!BuildSpecific.isExpired()) {
            BuildSpecific.doTrialCheck(this, this);
        }
    }

    @Override
    public void allow(int reason) {
        LogUtils.LOGI(TAG, "License checking - Licensed");
        BuildSpecific.setTrial(true);
    }

    @Override
    public void dontAllow(int policyReason) {
        BuildSpecific.setTrial(false);

        if (policyReason != Policy.RETRY) {
            LogUtils.LOGI(TAG, "License checking - Not licensed (!RETRY) (reason=" + policyReason +")");

            BuildSpecific.setExpired();

            if (!BuildSpecific.hasPremiumHintDialogFlag()) {
                BuildSpecific.setPremiumHintDialogFlag();

                new AlertDialog.Builder(this)
                        .setTitle(R.string.alert_dialog_title_premium_hint)
                        .setMessage(R.string.alert_dialog_message_premium_hint)
                        .setPositiveButton(R.string.alert_dialog_title_premium_positive_button, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();

                                BuildSpecific.buyPremium(OpusActivity.this);
                            }
                        })
                        .setNegativeButton(R.string.alert_dialog_title_premium_negative_button, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        })
                        .show();
            }
        }
        else {
            LogUtils.LOGI(TAG, "License checking - Not licensed (RETRY)");
        }
    }

    @Override
    public void applicationError(int errorCode) {
        LogUtils.LOGI(TAG, "License checking - applicationError : " + errorCode);
        BuildSpecific.setTrial(true);
    }
}
