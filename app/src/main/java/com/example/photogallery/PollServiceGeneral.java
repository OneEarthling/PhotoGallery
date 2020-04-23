package com.example.photogallery;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.Log;

import java.util.List;

public class PollServiceGeneral {
    private static final String TAG = "PollServiceGeneral";
    public static final String ACTION_SHOW_NOTIFICATION = "com.example.android.photogallery.SHOW_NOTIFICATION";
    public static final String PERM_PRIVATE = "com.example.android.photogallery.PRIVATE";
    public static final String REQUEST_CODE = "REQUEST_CODE";
    public static final String NOTIFICATION = "NOTIFICATION";

    public static Intent newIntent(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return new Intent(context, PollJobService.class);
        } else {
            return new Intent(context, PollService.class);
        }
    }

    public static void setServiceAlarm(Context context, boolean isOn){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            PollJobService.setServiceAlarm(context, isOn);
        } else {
            PollService.setServiceAlarm(context, isOn);
        }
    }

    public static void pollImages(Context context) {

        if (!isNetworkAvailableAndConnected(context)) {
            return;
        }
        String query = QueryPreferences.getStoredQuery(context);
        String lastResultId = QueryPreferences.getLastResultId(context);
        List<GalleryItem> items;

        if (query == null){
            items = new FlickrFetchr().fetchRecentPhotos(1);
        } else {
            items = new FlickrFetchr().searchPhotos(query);
        }

        if ( items.size() == 0){
            return;
        }
        String resultId = items.get(0).getId();
        if (resultId.equals(lastResultId)){
            Log.i(TAG, "Got an old result: " + resultId);
        } else {
            Log.i(TAG, "Got a new result: " + resultId);

            Resources resources = context.getResources();
            Intent i = PhotoGalleryActivity.newIntent(context);
            PendingIntent pi = PendingIntent.getActivity(context, 0, i, 0);

            NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            String CHANNEL_ID = "channel_1";
            Notification.Builder builder = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder = new Notification.Builder(context, CHANNEL_ID)
                        .setTicker(resources.getString(R.string.new_pictures_title))
                        .setSmallIcon(android.R.drawable.ic_menu_report_image)
                        .setContentTitle(resources.getString(R.string.new_pictures_title))
                        .setContentText(resources.getString(R.string.new_pictures_text))
                        .setContentIntent(pi)
                        .setAutoCancel(true);

                NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                        context.getString(R.string.new_pictures_title), NotificationManager.IMPORTANCE_DEFAULT);
                assert notificationManager != null;
                notificationManager.createNotificationChannel(channel);
            } else {
                builder = new Notification
                        .Builder(context)
                        .setSmallIcon(android.R.drawable.ic_menu_report_image)
                        .setContentTitle(resources.getString(R.string.new_pictures_title))
                        .setContentText(resources.getString(R.string.new_pictures_text))
                        .setContentIntent(pi)
                        .setAutoCancel(true);
            }

            //notificationManager.notify(0, builder.build());

            context.sendBroadcast(new Intent(ACTION_SHOW_NOTIFICATION), PERM_PRIVATE);
        }

        QueryPreferences.setLastResultId(context, resultId);

    }

    private static void showBackgroundNotification(Context context, int requestCode, Notification notificationCompat) {
        Intent i = new Intent(ACTION_SHOW_NOTIFICATION);
        i.putExtra(REQUEST_CODE, requestCode);
        i.putExtra(NOTIFICATION, notificationCompat);
        context.sendOrderedBroadcast(i, PERM_PRIVATE, null, null,
                Activity.RESULT_OK, null, null);
    }

    public static boolean isNetworkAvailableAndConnected(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(context.CONNECTIVITY_SERVICE);
        boolean isNetworkAvailable = cm.getActiveNetworkInfo() != null;
        boolean isNetworkConnected = isNetworkAvailable && cm.getActiveNetworkInfo().isConnected();

        return isNetworkConnected;
    }

}
