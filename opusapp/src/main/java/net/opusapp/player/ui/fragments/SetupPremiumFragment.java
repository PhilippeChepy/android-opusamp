package net.opusapp.player.ui.fragments;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import net.opusapp.player.R;
import net.opusapp.player.core.service.providers.local.database.Entities;
import net.opusapp.player.core.service.providers.local.database.OpenHelper;
import net.opusapp.player.ui.activities.SetupActivity;
import net.opusapp.player.ui.utils.PlayerApplication;
import net.opusapp.player.ui.utils.StorageOptions;
import net.opusapp.player.ui.views.CustomLinkTextView;
import net.opusapp.player.ui.views.CustomRadioButton;

import java.io.File;

public class SetupPremiumFragment extends Fragment {

    public static final String TAG = SetupPremiumFragment.class.getSimpleName();



    private ImageView premiumChecking;

    private ImageView premiumValid;

    private ImageView premiumInvalid;

    private TextView premiumTitle;

    private TextView premiumDescription;

    private TextView locationDescription;

    private CustomRadioButton radioWhole;

    private CustomRadioButton radioIntended;


    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_premium, container, false);

        premiumChecking = (ImageView) view.findViewById(R.id.premium_checking);
        premiumValid = (ImageView) view.findViewById(R.id.premium_ok);
        premiumInvalid = (ImageView) view.findViewById(R.id.premium_failure);
        premiumTitle = (TextView) view.findViewById(R.id.premium_title);
        premiumDescription = (TextView) view.findViewById(R.id.premium_description);
        locationDescription = (TextView) view.findViewById(R.id.storage_locations);

        radioWhole = (CustomRadioButton) view.findViewById(R.id.storage_use_whole_sdcard);
        radioIntended = (CustomRadioButton) view.findViewById(R.id.storage_use_music_directory);

        radioWhole.setOnCheckedChangeListener(onRadioCheckedListener);
        radioIntended.setOnCheckedChangeListener(onRadioCheckedListener);

        onRadioCheckedListener.onCheckedChanged(radioIntended, true);

        final CustomLinkTextView privacyLink = (CustomLinkTextView) view.findViewById(R.id.privacy_link);
        privacyLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://opusamp.com/privacy"));
                startActivity(browserIntent);
            }
        });

        final View next = view.findViewById(R.id.next);
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final ContentValues contentValues = new ContentValues();
                final OpenHelper localOpenHelper = new OpenHelper(1);
                final SQLiteDatabase localDatabase = localOpenHelper.getWritableDatabase();
                final String[] directories = StorageOptions.getStorageDirectories();

                if (localDatabase != null) {
                    for (String directory : directories) {
                        contentValues.clear();

                        if (radioIntended.isChecked()) {
                            directory = directory + File.separator + "Music";
                        }

                        contentValues.put(Entities.ScanDirectory.COLUMN_FIELD_SCAN_DIRECTORY_NAME, directory);
                        contentValues.put(Entities.ScanDirectory.COLUMN_FIELD_SCAN_DIRECTORY_IS_EXCLUDED, 0);
                        localDatabase.insert(Entities.ScanDirectory.TABLE_NAME, null, contentValues);
                    }
                }
                localOpenHelper.close();


                final Activity activity = getActivity();
                if (activity != null) {
                    ((SetupActivity)activity).terminate();
                }
            }
        });

        if (PlayerApplication.isFreemium()) {
            PlayerApplication.iabStart(new Runnable() {
                @Override
                public void run() {
                    premiumChecking.setVisibility(View.GONE);
                    doUpdateStatus();
                    PlayerApplication.iabStop();
                }
            });
        }
        else {
            doUpdateStatus();
        }

        return view;
    }

    private void doUpdateStatus() {
        if (PlayerApplication.isFreemium()) {
            premiumInvalid.setVisibility(View.VISIBLE);
            premiumTitle.setText(R.string.label_setup_invalid_premium_title);
            premiumDescription.setText(R.string.label_setup_invalid_premium_description);
        }
        else {
            premiumValid.setVisibility(View.VISIBLE);
            premiumTitle.setText(R.string.label_setup_valid_premium_title);
            premiumDescription.setText(R.string.label_setup_valid_premium_description);
        }
    }

    private final CompoundButton.OnCheckedChangeListener onRadioCheckedListener= new CompoundButton.OnCheckedChangeListener() {

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (buttonView == radioWhole && isChecked) {
                locationDescription.setVisibility(View.VISIBLE);

                String[] storageOptionses = StorageOptions.getStorageDirectories();
                StringBuilder storageStringBuilder = new StringBuilder();
                for (String options : storageOptionses) {
                    storageStringBuilder.append(options);
                    storageStringBuilder.append("\n");
                }
                locationDescription.setText(storageStringBuilder.toString());
            }
            else if (buttonView == radioIntended && isChecked) {
                locationDescription.setVisibility(View.VISIBLE);

                String[] storageOptionses = StorageOptions.getStorageDirectories();
                StringBuilder storageStringBuilder = new StringBuilder();
                for (String options : storageOptionses) {
                    storageStringBuilder.append(options);
                    storageStringBuilder.append(File.separator);
                    storageStringBuilder.append("Music");
                    storageStringBuilder.append("\n");
                }
                locationDescription.setText(storageStringBuilder.toString());
            }
        }
    };
}
