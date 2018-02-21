/*
 * Project:  WTC Collector
 * Purpose:  Mobile application for WTC data collection.
 * Author:   NikitaFeodonit, nfeodonit@yandex.com
 * ****************************************************************************
 * Copyright (c) 2017 NextGIS, info@nextgis.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextgis.wtc_collector.service;

import android.app.ActivityManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.location.Location;
import android.net.Uri;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;
import android.widget.Toast;
import com.nextgis.maplib.api.GpsEventListener;
import com.nextgis.maplib.api.IGISApplication;
import com.nextgis.maplib.datasource.GeoPoint;
import com.nextgis.maplib.location.GpsEventSource;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplib.util.LocationUtil;
import com.nextgis.maplibui.R;
import com.nextgis.maplibui.util.NotificationHelper;
import com.nextgis.wtc_collector.activity.MainActivity;
import com.nextgis.wtc_collector.util.AppConstants;
import com.nextgis.wtc_collector.util.AppSettingsConstants;
import io.sentry.Sentry;

import java.io.IOException;


public class WtcTrackerService
        extends Service
        implements GpsEventListener

{
    public static final String TRACKER_ACTION_STOP = "wtc.collector.TRACK_STOP";

    private static final int WTC_TRACK_NOTIFICATION_ID = 101;

    private boolean mIsRunning;

    private GpsEventSource mGpsEventSource;

    private Uri mContentUriTracks;
    private ContentValues mValues;
    private GeoPoint mPoint;

    private NotificationManager mNotificationManager;

    private String mTicker;
    private int    mSmallIcon;

    private String mUserName;
    private String mRouteName;

    @Override
    public void onCreate()
    {
        super.onCreate();

        IGISApplication app = (IGISApplication) getApplication();
        String authority = app.getAuthority();
        mContentUriTracks =
                Uri.parse("content://" + authority + "/" + AppConstants.KEY_LAYER_TRACKS);

        mPoint = new GeoPoint();
        mValues = new ContentValues();

        mGpsEventSource = app.getGpsEventSource();

        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mTicker = getString(R.string.tracks_running);
        mSmallIcon = R.drawable.ic_action_maps_directions_walk;

//        NotificationHelper.showLocationInfo(this);
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId)
    {
        if (intent != null) {
            String action = intent.getAction();

            mUserName = intent.getStringExtra(AppSettingsConstants.KEY_PREF_USER_NAME);
            mRouteName = intent.getStringExtra(AppSettingsConstants.KEY_PREF_ROUTE_NAME);

            if (!TextUtils.isEmpty(action)) {
                switch (action) {
                    case TRACKER_ACTION_STOP:
                        stopTrack();
                        stopSelf();
                        return START_NOT_STICKY;
                }
            }
        }

        if (!mIsRunning) {
            if (mUserName == null) {
                mUserName = "";
            }
            if (mRouteName == null) {
                mRouteName = "";
            }

            startTrack();
            addNotification();
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }

    @Override
    public void onDestroy()
    {
        removeNotification();
        stopSelf();

        super.onDestroy();
    }

    private void addNotification()
    {
        String title = String.format(getString(R.string.tracks_title), "WTC");
        Bitmap largeIcon =
                NotificationHelper.getLargeIcon(R.drawable.ic_action_maps_directions_walk,
                        getResources());

        Intent intentStop = new Intent(this, WtcTrackerService.class);
        intentStop.setAction(TRACKER_ACTION_STOP);
        PendingIntent stopService =
                PendingIntent.getService(this, 0, intentStop, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent intentActivity = new Intent(this, MainActivity.class);
        intentActivity.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent activityIntent = PendingIntent.getActivity(this, 0, intentActivity,
                PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setContentIntent(activityIntent)
                .setSmallIcon(mSmallIcon)
                .setLargeIcon(largeIcon)
                .setTicker(mTicker)
                .setWhen(System.currentTimeMillis())
                .setAutoCancel(false)
                .setContentTitle(title)
                .setContentText(mTicker)
                .setOngoing(true);

        builder.addAction(R.drawable.ic_location, getString(R.string.tracks_open), activityIntent);
        builder.addAction(R.drawable.ic_action_cancel_dark, getString(R.string.tracks_stop),
                stopService);

        mNotificationManager.notify(WTC_TRACK_NOTIFICATION_ID, builder.build());
        startForeground(WTC_TRACK_NOTIFICATION_ID, builder.build());

        Toast.makeText(this, title, Toast.LENGTH_SHORT).show();
    }

    private void removeNotification()
    {
        mNotificationManager.cancel(WTC_TRACK_NOTIFICATION_ID);
    }

    private void writeLocation(
            Location location,
            String status)
    {
        if (location == null) {
            return;
        }

        double x = location.getLongitude();
        double y = location.getLatitude();

        mPoint.setCoordinates(x, y);
        mPoint.setCRS(GeoConstants.CRS_WGS84);
        mPoint.project(GeoConstants.CRS_WEB_MERCATOR);

        mValues.clear();
        mValues.put(AppConstants.FIELD_TRACKS_LAT, y);
        mValues.put(AppConstants.FIELD_TRACKS_LON, x);
        mValues.put(AppConstants.FIELD_TRACKS_TIMESTAMP, System.currentTimeMillis());
        mValues.put(AppConstants.FIELD_TRACKS_COLLECTOR, mUserName);
        mValues.put(AppConstants.FIELD_TRACKS_STATUS, status);
        mValues.put(AppConstants.FIELD_TRACKS_ROUTE, mRouteName);
        try {
            mValues.put(Constants.FIELD_GEOM, mPoint.toBlob());
        } catch (IOException e) {
            if (Constants.DEBUG_MODE) {
                e.printStackTrace();
                Sentry.capture(e);
            }
        }
        getContentResolver().insert(mContentUriTracks, mValues);
    }

    private void startTrack()
    {
        mIsRunning = true;

        if (null != mGpsEventSource) {
            mGpsEventSource.addListener(this);
            writeLocation(mGpsEventSource.getLastKnownLocation(), AppConstants.TRACK_STATUS_START);
        }
    }

    private void stopTrack()
    {
        if (null != mGpsEventSource) {
            writeLocation(mGpsEventSource.getLastKnownLocation(), AppConstants.TRACK_STATUS_FINISH);
            mGpsEventSource.removeListener(this);
        }

        mIsRunning = false;
    }

    @Override
    public void onLocationChanged(Location location)
    {
        boolean update = LocationUtil.isProviderEnabled(this, location.getProvider(), true);
        if (!mIsRunning || !update) {
            return;
        }

        writeLocation(location, "");
    }

    @Override
    public void onBestLocationChanged(Location location)
    {

    }

    @Override
    public void onGpsStatusChanged(int event)
    {

    }

    public static boolean isTrackerServiceRunning(Context context)
    {
        ActivityManager manager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        if (manager == null) {
            return false;
        }

        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(
                Integer.MAX_VALUE)) {
            if (WtcTrackerService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }

        return false;
    }
}
