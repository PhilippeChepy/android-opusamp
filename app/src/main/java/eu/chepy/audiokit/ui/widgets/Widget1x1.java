package eu.chepy.audiokit.ui.widgets;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

public class Widget1x1 extends AppWidgetProvider {

	private static Widget1x1 instance;
	
	public static synchronized Widget1x1 getInstance() {
		if (instance == null) {
			instance = new Widget1x1();
		}
		
		return instance;
	}
	
	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
		super.onUpdate(context, appWidgetManager, appWidgetIds);
	}
}
