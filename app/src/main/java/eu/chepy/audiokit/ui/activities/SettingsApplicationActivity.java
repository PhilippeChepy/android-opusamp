/*
 * SettingsApplicationActivity.java
 *
 * Copyright (c) 2012, Philippe Chepy
 * All rights reserved.
 *
 * This software is the confidential and proprietary information
 * of Philippe Chepy.
 * You shall not disclose such Confidential Information.
 *
 * http://www.chepy.eu
 */
package eu.chepy.audiokit.ui.activities;

import android.app.ProgressDialog;
import android.content.Intent;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;

import java.io.File;
import java.text.DecimalFormat;

import eu.chepy.audiokit.BuildConfig;
import eu.chepy.audiokit.R;
import eu.chepy.audiokit.ui.utils.PlayerApplication;
import eu.chepy.audiokit.ui.utils.uil.NormalImageLoader;
import eu.chepy.audiokit.ui.utils.uil.ThumbnailImageLoader;
import eu.chepy.audiokit.utils.LogUtils;
import eu.chepy.audiokit.utils.iab.IabHelper;
import eu.chepy.audiokit.utils.iab.IabResult;
import eu.chepy.audiokit.utils.iab.Inventory;
import eu.chepy.audiokit.utils.iab.Purchase;

public class SettingsApplicationActivity extends PreferenceActivity {

	public static final String TAG = SettingsApplicationActivity.class.getSimpleName();



    private IabHelper iabHelper;

    static final String ITEM_DEBUG_SKU = "android.test.purchased";

    static final String ITEM_RELEASE_SKU = "eu.chepy.audiokit.noads";

    static String ITEM_SKU;

    static final String PURCHASE_TOKEN = "purchase-token-noads";



	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
		addPreferencesFromResource(R.xml.preferences);

        setCacheCleanupListener();
        setDatabaseOptimizationListener();

        setOnlineHelpListener();
		setOpenSourceLicensesListener();



        if (BuildConfig.DEBUG) {
            ITEM_SKU = ITEM_DEBUG_SKU;
        }
        else {
            ITEM_SKU = ITEM_RELEASE_SKU;
        }


        String base64EncodedPublicKey =
                "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAjLhj4p4ORoh54q1RGf4HwZTngM0LNNFR6Dpw2k+nfiys4S" +
                "S70WceuQTB1g+I0HqwSOn7P+R+Zw4HVAks+w4p/AlDpKd5eilIfztxP77gFBOBGIbSMhfZNkASBrAyiqBeTM8I8cwY" +
                "QAbIOd9H/j/xAg3vz4iNwWbB6PUFXiJSeo2nQB5VwSZYlwCb8xOGpnQbrzg8ZfP1MzWXZ9gOFjCvViczaCL5xN5WRg" +
                "V4NQgQ+n7ecuVFYHxeu/VNxy3/m9AeqQcA1P4+7c0YYoMZN34uRIDWlXsQuohIPG6COfcfnYOqoCk3d9Htp+QzCqVK" +
                "jSFN8loHWiDKvSEWf0+ffxpWuQIDAQAB";

        iabHelper = new IabHelper(this, base64EncodedPublicKey);

        iabHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {

            public void onIabSetupFinished(IabResult result)
            {
                if (!result.isSuccess()) {
                    LogUtils.LOGE(TAG, "In-app Billing setup failed: " + result);
                }
                else {
                    LogUtils.LOGI(TAG, "In-app Billing is set up OK");
                }
            }
        });
	}


    @Override
    public void onDestroy() {
        super.onDestroy();

        if (iabHelper != null) {
            iabHelper.dispose();
        }
        iabHelper = null;
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (!iabHelper.handleActivityResult(requestCode, resultCode, data)) {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @SuppressWarnings("deprecation")
	private void setCacheCleanupListener() {
        final Preference cacheCleanup = findPreference("cache_cleanup");
        setDiscCacheSummary(cacheCleanup);
        cacheCleanup.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(final Preference preference) {
                NormalImageLoader.getInstance().clearDiskCache();
                ThumbnailImageLoader.getInstance().clearDiskCache();
                setDiscCacheSummary(cacheCleanup);
                return true;
            }
        });
    }

    @SuppressWarnings("deprecation")
    private void setDatabaseOptimizationListener() {
        final Preference databaseOptimization = findPreference("database_optimization");
        databaseOptimization.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(final Preference preference) {
                final ProgressDialog progressDialog = new ProgressDialog(SettingsApplicationActivity.this);

                final AsyncTask<Void, Integer, Void> optimizationTask = new AsyncTask<Void, Integer, Void>() {

                    @Override
                    protected void onPreExecute() {
                        super.onPreExecute();
                        progressDialog.setTitle(R.string.dialog_title_database_optimization);
                        progressDialog.setIndeterminate(true);
                        progressDialog.show();
                    }

                    @Override
                    protected Void doInBackground(Void... params) {
                        publishProgress(0);
                        PlayerApplication.getDatabaseOpenHelper().getWritableDatabase().rawQuery("VACUUM;", null);

                        for (int index = 0 ; index < PlayerApplication.mediaManagers.length ; index++) {
                            publishProgress(index + 1);
                            PlayerApplication.mediaManagers[index].getMediaProvider().databaseMaintain();
                        }

                        return null;
                    }

                    @Override
                    protected void onProgressUpdate(Integer... values) {
                        super.onProgressUpdate(values);

                        if (values[0] == 0) {
                            progressDialog.setMessage(getString(R.string.progress_dialog_global));
                        }
                        else {
                            progressDialog.setMessage(String.format(getString(R.string.progress_dialog_pct), values[0], PlayerApplication.mediaManagers.length));
                        }
                    }

                    @Override
                    protected void onPostExecute(Void aVoid) {
                        super.onPostExecute(aVoid);
                        progressDialog.dismiss();
                    }
                };

                optimizationTask.execute();

                return true;
            }
        });
    }

    @SuppressWarnings("deprecation")
    private void setOnlineHelpListener() {
        final Preference onlineHelp = findPreference("online_help");
        onlineHelp.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(final Preference preference) {

                return true;
            }
        });
    }

    @SuppressWarnings("deprecation")
    private void setOpenSourceLicensesListener() {
        final Preference openSourceLicenses = findPreference("open_source");
        openSourceLicenses.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(final Preference preference) {
                PlayerApplication.showOpenSourceDialog(SettingsApplicationActivity.this).show();
                return true;
            }
        });
    }

    @SuppressWarnings("deprecation")
    private void setBuyInAppListener() {
        final Preference buyPremium = findPreference("buy_premium_mode");
        buyPremium.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                iabHelper.launchPurchaseFlow(SettingsApplicationActivity.this, ITEM_SKU, 10001, purchaseFinishedListener, PURCHASE_TOKEN);
                return true;
            }
        });
    }


    public void consumeItem() {
        iabHelper.queryInventoryAsync(receivedInventoryListener);
    }

    IabHelper.OnIabPurchaseFinishedListener purchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
        public void onIabPurchaseFinished(IabResult result, Purchase purchase)
        {
            if (result.isFailure()) {
                // TODO: Handle error
                return;
            }
            else if (purchase.getSku().equals(ITEM_SKU)) {
                consumeItem();
                // TODO: cannot buy anymore
            }

        }
    };

    IabHelper.QueryInventoryFinishedListener receivedInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
        public void onQueryInventoryFinished(IabResult result, Inventory inventory) {

            if (result.isFailure()) {
                // TODO: Handle failure
            }
            else {
                iabHelper.consumeAsync(inventory.getPurchase(ITEM_SKU), consumeFinishedListener);
            }
        }
    };

    IabHelper.OnConsumeFinishedListener consumeFinishedListener = new IabHelper.OnConsumeFinishedListener() {

        public void onConsumeFinished(Purchase purchase, IabResult result) {

            if (result.isSuccess()) {
                // TODO: inapp billing is purchased
            }
            else {
                // handle error
            }
        }
    };

    private static long folderSize(File directory) {
        long length = 0;
        for (File file : directory.listFiles()) {
            if (file.isFile())
                length += file.length();
            else
                length += folderSize(file);
        }
        return length;
    }

    private static void setDiscCacheSummary(Preference cacheCleanup) {
        File normalCacheDirectory = NormalImageLoader.getInstance().getDiskCache().getDirectory();
        File thumbnailCacheDirectory = ThumbnailImageLoader.getInstance().getDiskCache().getDirectory();

        final Double allocated = (double)(folderSize(normalCacheDirectory) + folderSize(thumbnailCacheDirectory))/ (double)(1048576);

        final DecimalFormat decimalFormat = new DecimalFormat();
        decimalFormat.setMaximumFractionDigits(2);
        decimalFormat.setMinimumFractionDigits(2);

        cacheCleanup.setSummary(decimalFormat.format(allocated) + " MB");
    }
}
