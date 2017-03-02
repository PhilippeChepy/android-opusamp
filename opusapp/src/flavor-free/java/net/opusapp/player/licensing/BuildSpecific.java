package net.opusapp.player.licensing;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.provider.Settings;

import com.google.android.vending.licensing.AESObfuscator;

import net.opusapp.licensing.LicenseChecker;
import net.opusapp.licensing.LicenseCheckerCallback;
import net.opusapp.licensing.PlaystoreAccountType;
import net.opusapp.licensing.ServerManagedPolicy;
import net.opusapp.player.ui.utils.PlayerApplication;
import net.opusapp.player.utils.LogUtils;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

public class BuildSpecific {

    public static final String TAG = BuildSpecific.class.getSimpleName();



    public static void initApp() {
        if (isExpired()) {
            trialMode = false;
        }
    }

    @SuppressWarnings("deprecated")
    public static void managePremiumPreference(final PreferenceActivity activity, final Preference buyPremiumPreference) {
        buyPremiumPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                buyPremium(activity);
                return true;
            }
        });
    }



    private static final String CONFIG_FILE_FREEMIUM = "global-config";

    private static final String CONFIG_NO_DISPLAY = "noDisplayCounter";

    private static final String CONFIG_EXPIRED = "isTrialExpired";

    private static final String CONFIG_PREMIUM_HINT = "premiumHintFlag";

    private static final String BASE64_PUBLIC_KEY =
            "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQC/uBXD8ItJQg" +
                    "Y15Yqw4jcCdzUTJ3L+QG7RSuMjbAJOa8isY/hVyGjnPlTn+S9A" +
                    "/IsjtSx7s3oc86X0HOVEOV2O3a8S9MYKvrjNUVMUVDVyxYA3bg" +
                    "HZuaXU722pn6FewE1merjWbt0rtsMcJMI7uNpIh/3LjeQ65J2K" +
                    "XEWtuFBw1QIDAQAB";

    private static final byte SALT[] = new byte[] {-101, -88, 61, 94, -112, -71, -4, 4, 39, 3, 27, 59, -30, -103, 123, 69, 115, 54, 84, -87};

    private static final String TRIAL_SERVER_URL = "https://opusapp.net:3000/";

    private static boolean trialMode = true;

    public static void doTrialCheck(Activity context, LicenseCheckerCallback trialLicenseCheckerCallback) {
        //Create an url object to the MobileTrial server
        URL trialServerUrl = null;
        try {
            trialServerUrl = URI.create(TRIAL_SERVER_URL).toURL();
        } catch (MalformedURLException exception) {
            LogUtils.LOGException(TAG, "doPrepareTrialCheck", 0, exception);
        }

        final String deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);

        // Construct the LicenseChecker with a ServerManaged Policy
        final LicenseChecker trialChecker = new LicenseChecker(
                context, new ServerManagedPolicy(context,
                new AESObfuscator(SALT, context.getPackageName(), deviceId)),
                BASE64_PUBLIC_KEY,
                trialServerUrl,
                new PlaystoreAccountType());

        trialChecker.checkAccess(trialLicenseCheckerCallback);
    }

    public static boolean isTrial() {
        return trialMode;
    }

    public static void setTrial(boolean trial) {
        trialMode = trial;
    }

    public static void setExpired() {
        final SharedPreferences sharedPreferences = PlayerApplication.context.getSharedPreferences(CONFIG_FILE_FREEMIUM, Context.MODE_PRIVATE);
        final SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(CONFIG_EXPIRED, true);
        editor.apply();
    }

    public static boolean isExpired() {
        final SharedPreferences sharedPreferences = PlayerApplication.context.getSharedPreferences(CONFIG_FILE_FREEMIUM, Context.MODE_PRIVATE);
        return sharedPreferences.getBoolean(CONFIG_EXPIRED, false);
    }

    public static void setPremiumHintDialogFlag() {
        final SharedPreferences sharedPreferences = PlayerApplication.context.getSharedPreferences(CONFIG_FILE_FREEMIUM, Context.MODE_PRIVATE);
        final SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(CONFIG_PREMIUM_HINT, true);
        editor.apply();
    }

    public static boolean hasPremiumHintDialogFlag() {
        final SharedPreferences sharedPreferences = PlayerApplication.context.getSharedPreferences(CONFIG_FILE_FREEMIUM, Context.MODE_PRIVATE);
        return sharedPreferences.getBoolean(CONFIG_PREMIUM_HINT, false);
    }

    public static void buyPremium(Context context) {
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=net.opusapp.player.premium")));
        } catch (android.content.ActivityNotFoundException anfe) {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/apps/details?id=net.opusapp.player.premium")));
        }
    }

    public static boolean canShowInterstitial() {
        return !isTrial();
    }

    public static int adDisplayGetCounter() {
        final SharedPreferences sharedPreferences = PlayerApplication.context.getSharedPreferences(CONFIG_FILE_FREEMIUM, Context.MODE_PRIVATE);
        return sharedPreferences.getInt(CONFIG_NO_DISPLAY, 0) + 1;
    }

    public static void adDisplayInc() {
        final SharedPreferences sharedPreferences = PlayerApplication.context.getSharedPreferences(CONFIG_FILE_FREEMIUM, Context.MODE_PRIVATE);
        int noDisplayCounter = sharedPreferences.getInt(CONFIG_NO_DISPLAY, 0) + 1;

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(CONFIG_NO_DISPLAY, noDisplayCounter);
        editor.apply();
    }

    public static void adDisplayReset() {
        final SharedPreferences sharedPreferences = PlayerApplication.context.getSharedPreferences(CONFIG_FILE_FREEMIUM, Context.MODE_PRIVATE);

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(CONFIG_NO_DISPLAY, 0);
        editor.apply();
    }
}
