package net.opusapp.player.ui.widgets;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;

import net.opusapp.player.R;
import net.opusapp.player.ui.activities.LibraryMainActivity;
import net.opusapp.player.ui.utils.PlayerApplication;

public class AppWidget4x2 extends AbstractAppWidget {


    private static AppWidget4x2 instance;

    public static synchronized AppWidget4x2 getInstance() {
        if (instance == null) {
            instance = new AppWidget4x2();
        }

        return instance;
    }

    @Override
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    public void applyUpdate(Context context) {

        final PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, new Intent(context, LibraryMainActivity.class), 0);

        if (hasPlaylist) {
            final RemoteViews view = new RemoteViews(context.getPackageName(), R.layout.app_widget4x2);

            // set widget touch events
            view.setOnClickPendingIntent(R.id.four_by_two_info, pendingIntent);
            view.setOnClickPendingIntent(R.id.four_by_two_albumart, pendingIntent);

            view.setOnClickPendingIntent(R.id.four_by_two_control_play, APPWIDGET_PAUSE_INTENT);
            view.setOnClickPendingIntent(R.id.four_by_two_control_next, APPWIDGET_NEXT_INTENT);
            view.setOnClickPendingIntent(R.id.four_by_two_control_prev, APPWIDGET_PREV_INTENT);

            // set widget content
            view.setTextViewText(R.id.four_by_two_trackname, track);
            view.setTextViewText(R.id.four_by_two_artistname, artist);
            view.setTextViewText(R.id.four_by_two_albumname, album);

            if (albumImage != null) {
                view.setImageViewBitmap(R.id.four_by_two_albumart, albumImage);
            }
            else {
                view.setImageViewResource(R.id.four_by_two_albumart, R.drawable.no_art_small);
            }

            if (isPlaying) {
                view.setImageViewResource(R.id.four_by_two_control_play, R.drawable.ic_pause_white_48dp);
                if (PlayerApplication.hasICS_MR1()) {
                    view.setContentDescription(R.id.four_by_two_control_play, context.getString(R.string.imageview_content_description_play));
                }
            } else {
                view.setImageViewResource(R.id.four_by_two_control_play, R.drawable.ic_play_arrow_white_48dp);
                if (PlayerApplication.hasICS_MR1()) {
                    view.setContentDescription(R.id.four_by_two_control_play, context.getString(R.string.imageview_content_description_play));
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


