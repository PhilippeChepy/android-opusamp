package eu.chepy.audiokit.ui.adapter;

import android.app.Activity;
import android.database.Cursor;
import android.support.v7.widget.PopupMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import eu.chepy.audiokit.R;
import eu.chepy.audiokit.ui.utils.PlayerApplication;
import eu.chepy.audiokit.ui.utils.ProviderStreamImageDownloader;

public class LibraryAdapter extends SimpleCursorAdapter {

    public static final String TAG = LibraryAdapter.class.getSimpleName();


    protected Activity parentActivity;



    protected int itemView;

    protected int managerType;


    /*
        Text resources
     */
    protected int[] textColumns;

    protected int[] textViews;



    /*
        Image resources
     */
    protected int idColumn;

    protected int imagePlaceHolder;

    protected int imageView;

    protected int visibilityColumn;

    protected boolean transparentBg;

    protected long positionIndicator;

    protected int positionIndicatorView;



    protected int source;



    public static final int PLAYER_MANAGER = 0;

    public static final int LIBRARY_MANAGER = 1;



    public LibraryAdapter(Activity activity, int source, int managerType, int itemView, int[] textColumns, int[] textViews, int idColumn, int imagePlaceHolder, int imageView, int visibilityColumn) {
        this(activity, source, managerType, itemView, textColumns, textViews, idColumn, imagePlaceHolder, imageView, visibilityColumn, -1);
    }

    @SuppressWarnings("deprecation")
    public LibraryAdapter(Activity activity, int source, int managerType, int itemView, int[] textColumns, int[] textViews, int idColumn, int imagePlaceHolder, int imageView, int visibilityColumn, int indicator) {
        super(PlayerApplication.context, itemView, null, new String[] {}, new int[] {});

        this.parentActivity = activity;

        this.source = source;
        this.managerType = managerType;

        this.itemView = itemView;

        this.textColumns = textColumns.clone();
        this.idColumn = idColumn;
        this.imagePlaceHolder = imagePlaceHolder;

        this.textViews = textViews.clone();
        this.imageView = imageView;

        this.visibilityColumn = visibilityColumn;

        this.transparentBg = false;
        positionIndicator = -1;
        positionIndicatorView = indicator;
    }

    public void setTransparentBackground(boolean transparentBackground) {
        transparentBg = transparentBackground;
    }

    @Override
    public View getView(int position, View convertView, final ViewGroup parent) {
        //final View view = super.getView(position, convertView, parent);
        final Cursor cursor = (Cursor) getItem(position);

        LibraryHolder viewHolder;

        if (cursor == null) {
            return convertView;
        }


        if (convertView == null) {
            final LayoutInflater layoutInflater = LayoutInflater.from (PlayerApplication.context);
            convertView = layoutInflater.inflate(itemView, parent, false);

            final View contextMenuHandle = convertView.findViewById(R.id.context_menu_handle);
            if (contextMenuHandle != null) {
                contextMenuHandle.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        //parentActivity.openContextMenu(parent);
                        PopupMenu popupMenu = new PopupMenu(parentActivity, contextMenuHandle);
                        popupMenu.getMenu().add(Menu.NONE, 1, Menu.NONE, "Test1");
                        popupMenu.getMenu().add(Menu.NONE, 1, Menu.NONE, "Test2");
                        popupMenu.getMenu().add(Menu.NONE, 1, Menu.NONE, "Test3");
                        popupMenu.show();
                    }
                });
            }

            viewHolder = new LibraryHolder();
            if (convertView != null) {
                viewHolder.textViews = new TextView[textViews.length];
                for (int textIndex = 0; textIndex < textColumns.length; textIndex++) {
                    viewHolder.textViews[textIndex] = (TextView) convertView.findViewById(textViews[textIndex]);
                }

                viewHolder.imageView = (ImageView) convertView.findViewById(imageView);

                if (positionIndicatorView >= 0) {
                    viewHolder.positionIndicatorView = (ImageView) convertView.findViewById(positionIndicatorView);
                }
                else {
                    viewHolder.positionIndicatorView = null;
                }

                convertView.setTag(viewHolder);
            }
        } else {
            viewHolder = (LibraryHolder) convertView.getTag();
        }

        if (viewHolder != null) {
            for (int textIndex = 0; textIndex < textColumns.length; textIndex++) {
                viewHolder.textViews[textIndex].setText(cursor.getString(textColumns[textIndex]));
            }

            if (idColumn >= 0) {
                PlayerApplication.thumbnailImageLoader.cancelDisplayTask(viewHolder.imageView);
                viewHolder.imageView.setImageResource(imagePlaceHolder);

                String imageUri = null;

                int managerIndex = (managerType == LIBRARY_MANAGER) ? PlayerApplication.libraryManagerIndex : PlayerApplication.playerManagerIndex;

                switch (source) {
                    case LibraryAdapterFactory.ADAPTER_SONG:
                    case LibraryAdapterFactory.ADAPTER_PLAYLIST_DETAILS:
                        imageUri =
                                ProviderStreamImageDownloader.SCHEME_URI_PREFIX +
                                ProviderStreamImageDownloader.SUBTYPE_MEDIA + "/" +
                                        managerIndex + "/" + cursor.getString(idColumn);
                        break;
                    case LibraryAdapterFactory.ADAPTER_ALBUM:
                    case LibraryAdapterFactory.ADAPTER_ALBUM_SIMPLE:
                        imageUri =
                                ProviderStreamImageDownloader.SCHEME_URI_PREFIX +
                                ProviderStreamImageDownloader.SUBTYPE_ALBUM + "/" +
                                        managerIndex + "/" + cursor.getString(idColumn);
                        break;
                    case LibraryAdapterFactory.ADAPTER_STORAGE:
                        imageUri = cursor.getString(idColumn);
                        break;
                }

                if (imageUri != null) {
                    PlayerApplication.thumbnailImageLoader.displayImage(imageUri, viewHolder.imageView);
                }

            }

            if (visibilityColumn >= 0 && convertView != null && cursor != null) {
                if (cursor.getInt(visibilityColumn) == 0) {
                    convertView.setBackgroundColor(PlayerApplication.context.getResources().getColor(R.color.holo_orange));
                }
                else {
                    if (transparentBg) {
                        convertView.setBackgroundResource(R.drawable.song_list_no_background);
                    }
                    else {
                        convertView.setBackgroundResource(R.drawable.song_list);
                    }
                }
            }

            if (viewHolder.positionIndicatorView != null) {
                if (cursor.getPosition() == positionIndicator) {
                    viewHolder.positionIndicatorView.setVisibility(View.VISIBLE);
                }
                else {
                    viewHolder.positionIndicatorView.setVisibility(View.GONE);
                }
            }
        }

        return convertView;
    }

    public void setPosition(long position) {
        positionIndicator = position;
    }



    private final class LibraryHolder {

        public TextView textViews[];

        public ImageView imageView;

        public ImageView positionIndicatorView;
    }
}
