package net.opusapp.player.ui.widgets;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;

import net.opusapp.player.R;
import net.opusapp.player.core.service.PlayerService;
import net.opusapp.player.ui.activities.LibraryMainActivity;
import net.opusapp.player.ui.utils.PlayerApplication;

public class AppWidget4x1 extends AbstractAppWidget {

    public static final String TAG = AppWidget4x1.class.getSimpleName();



    private static AppWidget4x1 instance;

    public static synchronized AppWidget4x1 getInstance() {
        if (instance == null) {
            instance = new AppWidget4x1();
        }

        return instance;
    }


    @Override
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    public void applyUpdate(Context context) {
        final PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, new Intent(context, LibraryMainActivity.class), 0);

        if (hasPlaylist) {
            final RemoteViews view = new RemoteViews(context.getPackageName(), R.layout.app_widget4x1);

            // set widget touch events
            view.setOnClickPendingIntent(R.id.four_by_one_info, pendingIntent);
            view.setOnClickPendingIntent(R.id.four_by_one_albumart, pendingIntent);

            view.setOnClickPendingIntent(R.id.four_by_one_control_play, PlayerService.APPWIDGET_PAUSE_INTENT);
            view.setOnClickPendingIntent(R.id.four_by_one_control_next, PlayerService.APPWIDGET_NEXT_INTENT);
            view.setOnClickPendingIntent(R.id.four_by_one_control_prev, PlayerService.APPWIDGET_PREV_INTENT);

            // set widget content
            view.setTextViewText(R.id.four_by_one_trackname, track);
            view.setTextViewText(R.id.four_by_one_separator, " - ");
            view.setTextViewText(R.id.four_by_one_artistname, artist);

            if (albumImage != null) {
                view.setImageViewBitmap(R.id.four_by_one_albumart, albumImage);
            }
            else {
                view.setImageViewResource(R.id.four_by_one_albumart, R.drawable.no_art_small);
            }

            if (isPlaying) {
                view.setImageViewResource(R.id.four_by_one_control_play, R.drawable.ic_action_playback_pause_dark);
                if (PlayerApplication.hasICS_MR1()) {
                    view.setContentDescription(R.id.four_by_one_control_play, context.getString(R.string.imageview_content_description_play));
                }
            } else {
                view.setImageViewResource(R.id.four_by_one_control_play, R.drawable.ic_action_playback_play_dark);
                if (PlayerApplication.hasICS_MR1()) {
                    view.setContentDescription(R.id.four_by_one_control_play, context.getString(R.string.imageview_content_description_play));
                }
            }

            // push update
            notifyUpdate(context, null, view);
        }
        else {
            final RemoteViews view = new RemoteViews(context.getPackageName(), R.layout.app_widget_no_playlist);
            view.setOnClickPendingIntent(R.id.unavailable, pendingIntent);

            notifyUpdate(context, null, view);
        }
    }
}


