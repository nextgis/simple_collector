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

package com.nextgis.wtc_collector;

import android.content.Intent;
import android.graphics.Bitmap;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import com.google.android.gms.analytics.ExceptionReporter;
import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.StandardExceptionParser;
import com.google.android.gms.analytics.Tracker;
import com.joshdholtz.sentry.Sentry;
import com.nextgis.maplib.api.ILayer;
import com.nextgis.maplib.map.LayerGroup;
import com.nextgis.maplib.map.MapBase;
import com.nextgis.maplib.map.MapDrawable;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.SettingsConstants;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.maplibui.mapui.LayerFactoryUI;
import com.nextgis.maplibui.mapui.TrackLayerUI;
import com.nextgis.maplibui.util.SettingsConstantsUI;
import com.nextgis.wtc_collector.activity.SettingsActivity;
import com.nextgis.wtc_collector.util.AppSettingsConstants;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


/**
 * Main application class
 * The initial layers create here.
 */
public class MainApplication
        extends GISApplication
{
    public static final String LAYER_TRACKS = "tracks";

    private Tracker mTracker;

    @Override
    public void onCreate()
    {
        Sentry.init(this, BuildConfig.SENTRY_DSN);
        Sentry.captureMessage("WTC Collector Sentry is init.", Sentry.SentryEventLevel.DEBUG);

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        GoogleAnalytics.getInstance(this)
                .setAppOptOut(
                        !mSharedPreferences.getBoolean(AppSettingsConstants.KEY_PREF_GA, true));
        GoogleAnalytics.getInstance(this).setDryRun(Constants.DEBUG_MODE);
        getTracker();
        setExceptionHandler();

        super.onCreate();
    }

    private void setExceptionHandler()
    {
        ExceptionReporter handler =
                new ExceptionReporter(getTracker(), Thread.getDefaultUncaughtExceptionHandler(),
                        this);
        StandardExceptionParser exceptionParser =
                new StandardExceptionParser(getApplicationContext(), null)
                {
                    @Override
                    public String getDescription(
                            String threadName,
                            Throwable t)
                    {
                        return "{" + threadName + "} " + Log.getStackTraceString(t);
                    }
                };

        handler.setExceptionParser(exceptionParser);
        Thread.setDefaultUncaughtExceptionHandler(handler);
    }

    public synchronized Tracker getTracker()
    {
        if (mTracker == null) {
            mTracker = GoogleAnalytics.getInstance(this).newTracker(R.xml.app_tracker);
        }

        return mTracker;
    }

    @Override
    public void sendScreen(String name)
    {
        mTracker.setScreenName(name);
        mTracker.send(new HitBuilders.ScreenViewBuilder().build());
    }

    @Override
    public void sendEvent(
            String category,
            String action,
            String label)
    {
        HitBuilders.EventBuilder event = new HitBuilders.EventBuilder().setCategory(category)
                .setAction(action)
                .setLabel(label);

        getTracker().send(event.build());
    }

    @Override
    public String getAccountsType()
    {
        return Constants.NGW_ACCOUNT_TYPE;
    }

    @Override
    public String getAuthority()
    {
        return AppSettingsConstants.AUTHORITY;
    }

    @Override
    protected void onFirstRun()
    {
    }

    @Override
    public void showSettings(String settings)
    {
        if (TextUtils.isEmpty(settings)) {
            settings = SettingsConstantsUI.ACTION_PREFS_GENERAL;
        }

        switch (settings) {
            case SettingsConstantsUI.ACTION_PREFS_GENERAL:
            case SettingsConstantsUI.ACTION_PREFS_LOCATION:
            case SettingsConstantsUI.ACTION_PREFS_TRACKING:
                break;
            default:
                return;
        }

        Intent intent = new Intent(this, SettingsActivity.class);
        intent.setAction(settings);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    public MapBase getMap()
    {
        if (null != mMap) {
            return mMap;
        }

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        File defaultPath = getExternalFilesDir(SettingsConstants.KEY_PREF_MAP);
        if (defaultPath == null) {
            defaultPath = new File(getFilesDir(), SettingsConstants.KEY_PREF_MAP);
        }

        String mapPath = mSharedPreferences.getString(SettingsConstants.KEY_PREF_MAP_PATH,
                defaultPath.getPath());
        String mapName =
                mSharedPreferences.getString(SettingsConstantsUI.KEY_PREF_MAP_NAME, "default");

        File mapFullPath = new File(mapPath, mapName + Constants.MAP_EXT);

        final Bitmap bkBitmap = getMapBackground();
        mMap = new MapDrawable(bkBitmap, this, mapFullPath, new LayerFactoryUI());
        mMap.setName(mapName);
        mMap.load();

        checkTracksLayerExist();

        return mMap;
    }

    protected void checkTracksLayerExist()
    {
        List<ILayer> tracks = new ArrayList<>();
        LayerGroup.getLayersByType(mMap, Constants.LAYERTYPE_TRACKS, tracks);
        if (tracks.isEmpty()) {
            String trackLayerName = getString(R.string.tracks);
            TrackLayerUI trackLayer = new TrackLayerUI(getApplicationContext(),
                    mMap.createLayerStorage(LAYER_TRACKS));
            trackLayer.setName(trackLayerName);
            trackLayer.setVisible(true);
            mMap.addLayer(trackLayer);
            mMap.save();
        }
    }
}
