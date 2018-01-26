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

package com.nextgis.wtc_collector.activity;

import android.Manifest;
import android.accounts.Account;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.nextgis.maplib.api.GpsEventListener;
import com.nextgis.maplib.api.IGISApplication;
import com.nextgis.maplib.api.MapEventListener;
import com.nextgis.maplib.datasource.Feature;
import com.nextgis.maplib.datasource.GeoPoint;
import com.nextgis.maplib.location.GpsEventSource;
import com.nextgis.maplib.map.MapBase;
import com.nextgis.maplib.map.MapEventSource;
import com.nextgis.maplib.map.NGWLookupTable;
import com.nextgis.maplib.map.NGWVectorLayer;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplibui.activity.NGActivity;
import com.nextgis.maplibui.fragment.NGWLoginFragment;
import com.nextgis.maplibui.util.SettingsConstantsUI;
import com.nextgis.wtc_collector.MainApplication;
import com.nextgis.wtc_collector.R;
import com.nextgis.wtc_collector.fragment.LoginFragment;
import com.nextgis.wtc_collector.map.WtcNGWVectorLayer;
import com.nextgis.wtc_collector.service.InitService;
import com.nextgis.wtc_collector.service.WtcTrackerService;
import com.nextgis.wtc_collector.util.AppConstants;
import com.nextgis.wtc_collector.util.AppSettingsConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;


public class MainActivity
        extends NGActivity
        implements MainApplication.OnAccountAddedListener,
                   MainApplication.OnAccountDeletedListener,
                   MainApplication.OnReloadMapListener,
                   GpsEventListener,
                   MapEventListener
{
    protected final static int PERMISSIONS_REQUEST = 1;

    protected Toolbar mToolbar;
    protected long    mBackPressed;

    protected boolean mFirstRun;
    protected BroadcastReceiver mSyncStatusReceiver;

    protected GpsEventSource mGpsEventSource;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // initialize the default settings
        PreferenceManager.setDefaultValues(this, R.xml.preferences_general, false);
//        PreferenceManager.setDefaultValues(this, R.xml.preferences_map, false);
        PreferenceManager.setDefaultValues(this, R.xml.preferences_location, false);
//        PreferenceManager.setDefaultValues(this, R.xml.preferences_tracks, false);

        if (!hasPermissions()) {
            String[] permissions = new String[] {
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.GET_ACCOUNTS,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE};
            requestPermissions(R.string.permissions, R.string.requested_permissions,
                    PERMISSIONS_REQUEST, permissions);
        }

        final MainApplication app = (MainApplication) getApplication();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(app);
        mGpsEventSource = app.getGpsEventSource();

        // Check if first run.
        final Account account = app.getAccount();
        if (account == null) {
            Log.d(AppConstants.APP_TAG,
                    "MainActivity. No account " + getString(R.string.account_name)
                            + " created. Run first step.");
            mFirstRun = true;
            createFirstStartView();
        } else {
            MapBase map = app.getMap();
            if (map.getLayerCount() <= 0 || app.isInitServiceRunning()) {
                Log.d(AppConstants.APP_TAG,
                        "MainActivity. Account " + getString(R.string.account_name)
                                + " created. Run second step.");
                mFirstRun = true;
                createSecondStartView();
            } else if (TextUtils.isEmpty(
                    prefs.getString(AppSettingsConstants.KEY_PREF_USER_NAME, ""))) {
                Log.d(AppConstants.APP_TAG, "MainActivity. User is not selected. Run third step.");
                createThirdStartView(prefs, map);
            } else {
                Log.d(AppConstants.APP_TAG,
                        "MainActivity. Account " + getString(R.string.account_name) + " created.");
                Log.d(AppConstants.APP_TAG, "MainActivity. User is selected.");
//                Log.d(AppConstants.APP_TAG, "MainActivity. Map data updating.");
//                updateMap(map);
                Log.d(AppConstants.APP_TAG, "MainActivity. Layers created. Run normal view.");
                mFirstRun = false;
                createNormalView(app, prefs, map);
            }
        }
    }

    protected boolean hasPermissions()
    {
        return isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION) && isPermissionGranted(
                Manifest.permission.ACCESS_COARSE_LOCATION) && isPermissionGranted(
                Manifest.permission.GET_ACCOUNTS) && isPermissionGranted(
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull
                    String[] permissions,
            @NonNull
                    int[] grantResults)
    {
        switch (requestCode) {
            case PERMISSIONS_REQUEST:
                restartGpsListener();
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    protected void restartGpsListener()
    {
        mGpsEventSource.removeListener(this);
        mGpsEventSource.addListener(this);
    }

    protected void createFirstStartView()
    {
        setContentView(R.layout.activity_main_first);

        mToolbar = (Toolbar) findViewById(R.id.main_toolbar);
        setSupportActionBar(mToolbar);
        if (null != getSupportActionBar()) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        }
        setTitle(getText(R.string.first_run));

        MainApplication app = (MainApplication) getApplication();
        FragmentManager fm = getSupportFragmentManager();
        NGWLoginFragment ngwLoginFragment = (NGWLoginFragment) fm.findFragmentByTag("NGWLogin");

        if (ngwLoginFragment == null) {
            ngwLoginFragment = new LoginFragment();
            ngwLoginFragment.setOnAddAccountListener(app);

            FragmentTransaction ft = fm.beginTransaction();
            ft.add(com.nextgis.maplibui.R.id.login_frame, ngwLoginFragment, "NGWLogin");
            ft.commit();
        }
    }

    protected void createSecondStartView()
    {
        setContentView(R.layout.activity_main_second);

        mToolbar = (Toolbar) findViewById(R.id.main_toolbar);
        setSupportActionBar(mToolbar);
        if (null != getSupportActionBar()) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        }
        setTitle(getText(R.string.initialization));

        final TextView stepView = (TextView) findViewById(R.id.step);
        final TextView messageView = (TextView) findViewById(R.id.message);

        final AlertDialog.Builder noneRootDialog = new AlertDialog.Builder(MainActivity.this);
        noneRootDialog.setMessage(R.string.root_resource_group_not_found_msg)
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(
                            DialogInterface dialog,
                            int which)
                    {
                        Intent syncIntent = new Intent(MainActivity.this, InitService.class);
                        syncIntent.setAction(InitService.ACTION_STOP);
                        startService(syncIntent);

                        MainApplication app = (MainApplication) getApplication();
                        app.cancelAccountCreation();
                    }
                })
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(
                            DialogInterface dialog,
                            int which)
                    {
                        Intent syncIntent = new Intent(MainActivity.this, InitService.class);
                        syncIntent.setAction(InitService.ACTION_CREATE_STRUCT);
                        startService(syncIntent);
                    }
                });

        final AlertDialog.Builder errorRootDialog = new AlertDialog.Builder(MainActivity.this);
        errorRootDialog.setMessage(R.string.root_resource_group_found_with_error)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(
                            DialogInterface dialog,
                            int which)
                    {
                        MainApplication app = (MainApplication) getApplication();
                        app.cancelAccountCreation();
                    }
                });

        mSyncStatusReceiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(
                    Context context,
                    Intent intent)
            {
                int step = intent.getIntExtra(AppConstants.KEY_STEP, 0);
                int stepCount = intent.getIntExtra(AppConstants.KEY_STEP_COUNT, 0);
                String message = intent.getStringExtra(AppConstants.KEY_MESSAGE);
                int state = intent.getIntExtra(AppConstants.KEY_STATE, 0);

                switch (state) {
                    case AppConstants.STEP_STATE_FINISH:
                    case AppConstants.STEP_STATE_CANCEL:
                        // refreshActivityView(); // performed by reloadMap from MainApplication
                        break;

                    case AppConstants.STEP_STATE_WAIT:
                    case AppConstants.STEP_STATE_WORK:
                    case AppConstants.STEP_STATE_DONE:
                        stepView.setText(
                                String.format(getString(R.string.step), step + 1, stepCount));
                        messageView.setText(String.format(getString(R.string.message), message));
                        break;
                    case AppConstants.STEP_STATE_NONE_ROOT:
                        noneRootDialog.show();
                        break;
                    case AppConstants.STEP_STATE_ERROR_ROOT:
                        errorRootDialog.show();
                        break;
                }
            }
        };

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(AppConstants.BROADCAST_MESSAGE);
        registerReceiver(mSyncStatusReceiver, intentFilter);

        Button cancelButton = (Button) findViewById(R.id.cancel);
        cancelButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                Intent syncIntent = new Intent(MainActivity.this, InitService.class);
                syncIntent.setAction(InitService.ACTION_STOP);
                startService(syncIntent);
            }
        });

        MainApplication app = (MainApplication) getApplication();
        String action;
        if (app.isInitServiceRunning()) {
            action = InitService.ACTION_REPORT;
        } else {
            action = InitService.ACTION_START;
        }

        Intent syncIntent = new Intent(MainActivity.this, InitService.class);
        syncIntent.setAction(action);
        startService(syncIntent);
    }

    protected void createThirdStartView(
            final SharedPreferences prefs,
            MapBase map)
    {
        setContentView(R.layout.activity_main_third);

        mToolbar = (Toolbar) findViewById(R.id.main_toolbar);
        setSupportActionBar(mToolbar);
        if (null != getSupportActionBar()) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        }
        setTitle(getText(R.string.name_selection));

        final NGWLookupTable peopleTable =
                (NGWLookupTable) map.getLayerByName(AppConstants.KEY_LAYER_PEOPLE);
        if (null != peopleTable) {
            List<String> peopleArray = new ArrayList<>();
            peopleArray.addAll(peopleTable.getData().values());
            Collections.sort(peopleArray);

            ArrayAdapter<String> adapter =
                    new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, peopleArray);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

            final Spinner nameListView = (Spinner) findViewById(R.id.name_list);
            nameListView.setAdapter(adapter);

            Button okButton = (Button) findViewById(R.id.ok);
            okButton.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    String name = nameListView.getSelectedItem().toString();
                    for (Map.Entry<String, String> entry : peopleTable.getData().entrySet()) {
                        if (entry.getValue().equals(name)) {
                            String key = entry.getKey();
                            SharedPreferences.Editor edit = prefs.edit();
                            edit.putString(AppSettingsConstants.KEY_PREF_USER_NAME, key);
                            edit.commit();
                            refreshActivityView();
                            break;
                        }
                    }
                }
            });
        }
    }

    protected void createNormalView(
            final MainApplication app,
            final SharedPreferences prefs,
            final MapBase map)
    {
        setContentView(R.layout.activity_main);

        mToolbar = (Toolbar) findViewById(R.id.main_toolbar);
        setSupportActionBar(mToolbar);
        if (null != getSupportActionBar()) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        }
        setTitle(getText(R.string.app_name));

        final NGWLookupTable peopleTable =
                (NGWLookupTable) map.getLayerByName(AppConstants.KEY_LAYER_PEOPLE);
        if (null != peopleTable) {
            TextView nameView = (TextView) findViewById(R.id.name);
            String nameKey = prefs.getString(AppSettingsConstants.KEY_PREF_USER_NAME, "");
            String nameValue = peopleTable.getData().get(nameKey);
            if (!TextUtils.isEmpty(nameValue)) {
                nameView.setText(nameValue);
            }
        }

        NGWLookupTable speciesTable =
                (NGWLookupTable) map.getLayerByName(AppConstants.KEY_LAYER_SPECIES);
        if (null != speciesTable) {
            List<String> speciesArray = new ArrayList<>();
            Map<String, String> data = speciesTable.getData();

            speciesArray.addAll(data.keySet());
            Collections.sort(speciesArray);

            LinearLayout speciesLayout = (LinearLayout) findViewById(R.id.species_layout);

            View.OnClickListener onClickListener = new View.OnClickListener()
            {
                @Override
                public void onClick(View view)
                {
                    boolean isWtcTrackerRunning = WtcTrackerService.isTrackerServiceRunning(app);

                    if (isWtcTrackerRunning) {
                        Button button = (Button) view;
                        final String speciesKey = (String) button.getTag();
                        if (prefs.getBoolean(AppSettingsConstants.KEY_PREF_RIGHT_LEFT, false)) {
                            AlertDialog.Builder side = new AlertDialog.Builder(MainActivity.this);
                            side.setCancelable(false)
                                    .setMessage(R.string.left_or_right)
                                    .setNeutralButton(android.R.string.cancel, null)
                                    .setNegativeButton(R.string.left,
                                            new DialogInterface.OnClickListener()
                                            {
                                                @Override
                                                public void onClick(
                                                        DialogInterface dialog,
                                                        int which)
                                                {
                                                    writeZmuData(app, prefs, speciesKey,
                                                            getString(R.string.left));
                                                }
                                            })
                                    .setPositiveButton(R.string.right,
                                            new DialogInterface.OnClickListener()
                                            {
                                                @Override
                                                public void onClick(
                                                        DialogInterface dialog,
                                                        int which)
                                                {
                                                    writeZmuData(app, prefs, speciesKey,
                                                            getString(R.string.right));
                                                }
                                            })
                                    .show();
                        } else {
                            writeZmuData(app, prefs, speciesKey, "");
                        }

                    } else {
                        AlertDialog.Builder warning = new AlertDialog.Builder(MainActivity.this);
                        warning.setMessage(R.string.track_rec_not_start)
                                .setPositiveButton(android.R.string.ok, null)
                                .show();
                    }
                }
            };

            for (int i = 0; i < speciesArray.size(); ++i) {
                String speciesKey = speciesArray.get(i);
                String speciesValue = data.get(speciesKey);

                View buttonLayout = LayoutInflater.from(this)
                        .inflate(R.layout.item_button_species, null, false);
                Button speciesButton = (Button) buttonLayout.findViewById(R.id.species_button);
                speciesButton.setText(speciesValue);
                speciesButton.setTag(speciesKey);
                speciesButton.setOnClickListener(onClickListener);
                speciesLayout.addView(buttonLayout);
            }
        }

        int trackPoints = -1;
        WtcNGWVectorLayer tracksLayer =
                (WtcNGWVectorLayer) map.getLayerByName(getString(R.string.tracks_layer));
        if (tracksLayer != null) {
            trackPoints = tracksLayer.getCount();
        }

        int trails = -1;
        WtcNGWVectorLayer zmudataLayer =
                (WtcNGWVectorLayer) map.getLayerByName(getString(R.string.zmudata_layer));
        if (zmudataLayer != null) {
            trails = zmudataLayer.getCount();
        }

        TextView countersView = (TextView) findViewById(R.id.point_counters);
        countersView.setText(
                String.format(getString(R.string.point_counters), trackPoints, trails));

        Button startButton = (Button) findViewById(R.id.start);
        startButton.setText(WtcTrackerService.isTrackerServiceRunning(app)
                            ? getString(R.string.stop)
                            : getString(R.string.start));
        startButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                toggleWtcTrackerService();
            }
        });
    }

    protected boolean writeZmuData(
            MainApplication app,
            SharedPreferences prefs,
            String speciesKey,
            String leftOrRight)
    {
        String GUID = UUID.randomUUID().toString();
        long timeMillis = System.currentTimeMillis();
        String collector = prefs.getString(AppSettingsConstants.KEY_PREF_USER_NAME, "");

        Location location = mGpsEventSource.getLastKnownLocation();
        double x = location.getLongitude();
        double y = location.getLatitude();

        Feature feature = app.getTempFeature();
        feature.setFieldValue(AppConstants.FIELD_ZMUDATA_GUID, GUID);
        feature.setFieldValue(AppConstants.FIELD_ZMUDATA_LAT, y);
        feature.setFieldValue(AppConstants.FIELD_ZMUDATA_LON, x);
        feature.setFieldValue(AppConstants.FIELD_ZMUDATA_DATE, timeMillis);
        feature.setFieldValue(AppConstants.FIELD_ZMUDATA_TIME, timeMillis);
        feature.setFieldValue(AppConstants.FIELD_ZMUDATA_SPECIES, speciesKey);
        feature.setFieldValue(AppConstants.FIELD_ZMUDATA_COLLECTOR, collector);
        feature.setFieldValue(AppConstants.FIELD_ZMUDATA_SIDE, leftOrRight);

        GeoPoint pt = new GeoPoint(x, y);
        pt.setCRS(GeoConstants.CRS_WGS84);
        pt.project(GeoConstants.CRS_WEB_MERCATOR);
        feature.setGeometry(new GeoPoint(pt.getX(), pt.getY()));

        NGWVectorLayer layer = app.getZmuDataLayer();

        if (layer.updateFeatureWithAttachesWithFlags(feature) > 0) {
            layer.setFeatureWithAttachesTempFlag(feature, false);
            layer.setFeatureWithAttachesNotSyncFlag(feature, false);
            layer.addChange(feature.getId(), Constants.CHANGE_OPERATION_NEW);
        } else {
            return false;
        }

        return true;
    }

    protected void toggleWtcTrackerService()
    {
        boolean isWtcTrackerRunning = WtcTrackerService.isTrackerServiceRunning(getApplication());

        Button startButton = (Button) findViewById(R.id.start);
        startButton.setText(
                isWtcTrackerRunning ? getString(R.string.start) : getString(R.string.stop));

        Intent intent = new Intent(MainActivity.this, WtcTrackerService.class);
        if (isWtcTrackerRunning) {
            intent.setAction(WtcTrackerService.TRACKER_ACTION_STOP);
        }
        startService(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu)
    {
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        final IGISApplication app = (IGISApplication) getApplication();

        switch (item.getItemId()) {
            case android.R.id.home:
                if (hasFragments()) {
                    return finishFragment();
                }
            case R.id.menu_settings:
                app.showSettings(SettingsConstantsUI.ACTION_PREFS_GENERAL);
                return true;
            case R.id.menu_about:
                Intent intentAbout = new Intent(this, AboutActivity.class);
                startActivity(intentAbout);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public boolean hasFragments()
    {
        return getSupportFragmentManager().getBackStackEntryCount() > 0;
    }

    public boolean finishFragment()
    {
        if (hasFragments()) {
            getSupportFragmentManager().popBackStack();
            setActionBarState(true);
            return true;
        }

        return false;
    }

    public void setActionBarState(boolean state)
    {
        if (state) {
            mToolbar.getBackground().setAlpha(128);
        } else {
            mToolbar.getBackground().setAlpha(255);
        }
    }

    @Override
    protected boolean isHomeEnabled()
    {
        return false;
    }

    @Override
    public void onBackPressed()
    {
        if (finishFragment()) {
            return;
        }

        if (mBackPressed + 2000 > System.currentTimeMillis()) {
            super.onBackPressed();
        } else {
            Toast.makeText(this, R.string.press_aback_again, Toast.LENGTH_SHORT).show();
        }

        mBackPressed = System.currentTimeMillis();
    }

    @Override
    protected void onPause()
    {
        super.onPause();

        MainApplication app = (MainApplication) getApplication();
        app.setOnAccountAddedListener(null);
        app.setOnAccountDeletedListener(null);
        app.setOnReloadMapListener(null);

        if (null != mSyncStatusReceiver) {
            unregisterReceiver(mSyncStatusReceiver);
        }
        if (null != mGpsEventSource) {
            mGpsEventSource.removeListener(this);
        }

        MapEventSource map = (MapEventSource) app.getMap();
        map.removeListener(this);
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        MainApplication app = (MainApplication) getApplication();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(app);

        if (prefs.getBoolean(AppSettingsConstants.KEY_PREF_USER_NAME_CLEARED, false)) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.remove(AppSettingsConstants.KEY_PREF_USER_NAME_CLEARED);
            editor.apply();
            refreshActivityView();
        }

        app.setOnAccountAddedListener(this);
        app.setOnAccountDeletedListener(this);
        app.setOnReloadMapListener(this);

        if (app.isAccountAdded() || app.isAccountDeleted() || app.isMapReloaded()) {
            refreshActivityView();
        }

        if (null != mSyncStatusReceiver) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(AppConstants.BROADCAST_MESSAGE);
            registerReceiver(mSyncStatusReceiver, intentFilter);
        }
        if (null != mGpsEventSource) {
            mGpsEventSource.addListener(this);
        }

        MapEventSource map = (MapEventSource) app.getMap();
        map.addListener(this);
    }

    @Override
    public void onAccountAdded()
    {
        refreshActivityView();
    }

    @Override
    public void onAccountDeleted()
    {
        refreshActivityView();
    }

    @Override
    public void onReloadMap()
    {
        refreshActivityView();
    }

    @Override
    public void onLocationChanged(Location location)
    {

    }

    @Override
    public void onBestLocationChanged(Location location)
    {

    }

    @Override
    public void onGpsStatusChanged(int event)
    {

    }

    @Override
    public void onLayerAdded(int id)
    {

    }

    @Override
    public void onLayerDeleted(int id)
    {

    }

    @Override
    public void onLayerChanged(int id)
    {
        MainApplication app = (MainApplication) getApplication();
        MapBase map = app.getMap();

        int trackPoints = -1;
        WtcNGWVectorLayer tracksLayer =
                (WtcNGWVectorLayer) map.getLayerByName(getString(R.string.tracks_layer));
        if (tracksLayer != null) {
            trackPoints = tracksLayer.getCount();
        }

        int trails = -1;
        WtcNGWVectorLayer zmudataLayer =
                (WtcNGWVectorLayer) map.getLayerByName(getString(R.string.zmudata_layer));
        if (zmudataLayer != null) {
            trails = zmudataLayer.getCount();
        }

        TextView countersView = (TextView) findViewById(R.id.point_counters);
        countersView.setText(
                String.format(getString(R.string.point_counters), trackPoints, trails));
    }

    @Override
    public void onExtentChanged(
            float zoom,
            GeoPoint center)
    {

    }

    @Override
    public void onLayersReordered()
    {

    }

    @Override
    public void onLayerDrawFinished(
            int id,
            float percent)
    {

    }

    @Override
    public void onLayerDrawStarted()
    {

    }
}
