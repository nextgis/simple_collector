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

import android.accounts.Account;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;
import com.nextgis.maplib.api.IProgressor;
import com.nextgis.maplib.datasource.Field;
import com.nextgis.maplib.datasource.ngw.Connection;
import com.nextgis.maplib.datasource.ngw.INGWResource;
import com.nextgis.maplib.datasource.ngw.Resource;
import com.nextgis.maplib.datasource.ngw.ResourceGroup;
import com.nextgis.maplib.map.MapBase;
import com.nextgis.maplib.map.NGWLookupTable;
import com.nextgis.maplib.util.HttpResponse;
import com.nextgis.maplib.util.NGException;
import com.nextgis.maplib.util.NGWUtil;
import com.nextgis.maplib.util.NetworkUtil;
import com.nextgis.wtc_collector.MainApplication;
import com.nextgis.wtc_collector.R;
import com.nextgis.wtc_collector.map.WtcNGWVectorLayer;
import com.nextgis.wtc_collector.util.AppConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


/**
 * Application initialisation service
 */
public class InitService
        extends Service
{
    public static final String ACTION_START = "START_INITIAL_SYNC";
    public static final String ACTION_STOP = "STOP_INITIAL_SYNC";
    public static final String ACTION_REPORT = "REPORT_INITIAL_SYNC";

    public static final int MAX_SYNC_STEP = 5;

    private InitialSyncThread mThread;

    private volatile boolean mIsRunning;

    @Override
    public void onCreate()
    {
        // For service debug
//        android.os.Debug.waitForDebugger();

        super.onCreate();
        mIsRunning = false;
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId)
    {
        if (intent == null || intent.getAction() == null) {
            return START_NOT_STICKY;
        }

        switch (intent.getAction()) {
            case ACTION_START:
                if (mIsRunning) {
                    Log.d(AppConstants.APP_TAG, "reportSync()");
                    reportSync();
                } else {
                    Log.d(AppConstants.APP_TAG, "startSync()");
                    startSync();
                }
                break;
            case ACTION_STOP:
                Log.d(AppConstants.APP_TAG, "stopSync()");
                reportState(getString(R.string.operaton_canceled), AppConstants.STEP_STATE_CANCEL);
                stopSync();
                break;
            case ACTION_REPORT:
                if (mIsRunning) {
                    Log.d(AppConstants.APP_TAG, "reportSync()");
                    reportSync();
                } else {
                    Log.d(AppConstants.APP_TAG, "reportState() for finish");
                    reportState(getString(R.string.done), AppConstants.STEP_STATE_FINISH);
                    stopSync();
                }
                break;
        }

        return START_STICKY;
    }

    private void reportSync()
    {
        if (null != mThread) {
            mThread.publishProgress(getString(R.string.working), AppConstants.STEP_STATE_WORK);
        }
    }

    private void reportState(
            String message,
            int state)
    {
        Intent intent = new Intent(AppConstants.BROADCAST_MESSAGE);

        intent.putExtra(AppConstants.KEY_STEP, MAX_SYNC_STEP);
        intent.putExtra(AppConstants.KEY_MESSAGE, message);
        intent.putExtra(AppConstants.KEY_STATE, state);

        sendBroadcast(intent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }

    private void startSync()
    {
        final MainApplication app = (MainApplication) getApplication();
        if (app == null) {
            String error = "InitService. Failed to get main application";
            Log.d(AppConstants.APP_TAG, error);
            reportState(error, AppConstants.STEP_STATE_ERROR);
            stopSync();
            return;
        }

        final Account account = app.getAccount(getString(R.string.account_name));
        if (account == null) {
            String error = "InitService. No account " + getString(R.string.account_name)
                    + " created. Run first step.";
            Log.d(AppConstants.APP_TAG, error);
            reportState(error, AppConstants.STEP_STATE_ERROR);
            stopSync();
            return;
        }

        mThread = new InitialSyncThread(account);
        mIsRunning = true;
        mThread.start();
    }

    private void stopSync()
    {
        mIsRunning = false;

        if (mThread != null && mThread.isAlive()) {
            mThread.interrupt();
        }

        stopSelf();
    }

    @Override
    public void onDestroy()
    {
        mIsRunning = false;
        super.onDestroy();
    }

    private class InitialSyncThread
            extends Thread
            implements IProgressor
    {
        protected Account mAccount;
        protected int     mMaxProgress;
        protected String  mProgressMessage;
        protected int     mStep;
        protected Intent  mMessageIntent;

        public InitialSyncThread(Account account)
        {
            mAccount = account;
            mMaxProgress = 0;
        }

        @Override
        public void setMax(int maxValue)
        {
            mMaxProgress = maxValue;
        }

        @Override
        public boolean isCanceled()
        {
            return !mIsRunning;
        }

        @Override
        public void setValue(int value)
        {
            String message = mProgressMessage + " (" + value + " " + getString(R.string.of) + " "
                    + mMaxProgress + ")";
            publishProgress(message, AppConstants.STEP_STATE_WORK);
        }

        @Override
        public void setIndeterminate(boolean indeterminate)
        {

        }

        @Override
        public void setMessage(String message)
        {
            mProgressMessage = message;
        }

        @Override
        public void run()
        {
            doWork();
            InitService.this.stopSync();
        }

        public final void publishProgress(
                String message,
                int state)
        {
            if (null == mMessageIntent) {
                return;
            }

            mMessageIntent.putExtra(AppConstants.KEY_STEP, mStep);
            mMessageIntent.putExtra(AppConstants.KEY_MESSAGE, message);
            mMessageIntent.putExtra(AppConstants.KEY_STATE, state);
            sendBroadcast(mMessageIntent);
        }

        protected Boolean doWork()
        {
            mMessageIntent = new Intent(AppConstants.BROADCAST_MESSAGE);
            int nTimeout = 4000;

            // step 1: connect to server
            mStep = 0;

            final MainApplication app = (MainApplication) getApplication();
            final String sLogin = app.getAccountLogin(mAccount);
            final String sPassword = app.getAccountPassword(mAccount);
            final String sURL = app.getAccountUrl(mAccount);

            if (null == sURL || null == sPassword || null == sLogin) {
                publishProgress(
                        getString(R.string.error_unexpected), AppConstants.STEP_STATE_ERROR);
                return false;
            }

            Connection connection = new Connection("tmp", sLogin, sPassword, sURL);
            publishProgress(getString(R.string.connecting), AppConstants.STEP_STATE_WORK);

            if (!connection.connect()) {
                publishProgress(
                        getString(R.string.error_connect_failed), AppConstants.STEP_STATE_ERROR);
                return false;
            } else {
                publishProgress(getString(R.string.connected), AppConstants.STEP_STATE_WORK);
            }

            if (isCanceled()) {
                return false;
            }

            // step 1: find keys

            publishProgress(getString(R.string.check_tables_exist), AppConstants.STEP_STATE_WORK);

            Map<String, Long> keys = new HashMap<>();
            keys.put(AppConstants.KEY_ZMUDATA, -1L);
            keys.put(AppConstants.KEY_PEOPLE, -1L);
            keys.put(AppConstants.KEY_SPECIES, -1L);
            keys.put(AppConstants.KEY_TRACKS, -1L);

            Map<String, List<String>> keysFields = new HashMap<>(keys.size());

            List<String> zmudataFields = new LinkedList<>();
            zmudataFields.add(AppConstants.FIELD_ZMUDATA_GUID);
            zmudataFields.add(AppConstants.FIELD_ZMUDATA_LAT);
            zmudataFields.add(AppConstants.FIELD_ZMUDATA_LON);
            zmudataFields.add(AppConstants.FIELD_ZMUDATA_DATE);
            zmudataFields.add(AppConstants.FIELD_ZMUDATA_TIME);
            zmudataFields.add(AppConstants.FIELD_ZMUDATA_SPECIES);
            zmudataFields.add(AppConstants.FIELD_ZMUDATA_COLLECTOR);
            keysFields.put(AppConstants.KEY_ZMUDATA, zmudataFields);

            List<String> tracksFields = new LinkedList<>();
            tracksFields.add(AppConstants.FIELD_TRACKS_LAT);
            tracksFields.add(AppConstants.FIELD_TRACKS_LON);
            tracksFields.add(AppConstants.FIELD_TRACKS_TIMESTAMP);
            tracksFields.add(AppConstants.FIELD_TRACKS_COLLECTOR);
            keysFields.put(AppConstants.KEY_TRACKS, tracksFields);

            if (!checkServerLayers(connection, keys, keysFields)) {
                publishProgress(
                        getString(R.string.error_wrong_server), AppConstants.STEP_STATE_ERROR);

                try {
                    Thread.sleep(nTimeout);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                return false;
            } else {
                publishProgress(getString(R.string.done), AppConstants.STEP_STATE_DONE);
            }

            if (isCanceled()) {
                return false;
            }

            MapBase map = app.getMap();

            // step 2: create data layer
            mStep = 1;

            publishProgress(getString(R.string.working), AppConstants.STEP_STATE_WORK);

            if (!createZmuDataLayer(keys.get(AppConstants.KEY_ZMUDATA), mAccount.name, map, this)) {
                publishProgress(
                        getString(R.string.error_unexpected), AppConstants.STEP_STATE_ERROR);
                return false;
            } else {
                publishProgress(getString(R.string.done), AppConstants.STEP_STATE_DONE);
            }

            if (isCanceled()) {
                return false;
            }

            // step 3: load people lookup table
            mStep = 2;

            publishProgress(getString(R.string.working), AppConstants.STEP_STATE_WORK);

            if (!loadLookupTables(keys.get(AppConstants.KEY_PEOPLE), mAccount.name,
                    AppConstants.KEY_LAYER_PEOPLE, map, this)) {
                publishProgress(
                        getString(R.string.error_unexpected), AppConstants.STEP_STATE_ERROR);
                return false;
            } else {
                publishProgress(getString(R.string.done), AppConstants.STEP_STATE_DONE);
            }

            if (isCanceled()) {
                return false;
            }

            // step 4: load species lookup table
            mStep = 3;

            publishProgress(getString(R.string.working), AppConstants.STEP_STATE_WORK);

            if (!loadLookupTables(keys.get(AppConstants.KEY_SPECIES), mAccount.name,
                    AppConstants.KEY_LAYER_SPECIES, map, this)) {
                publishProgress(
                        getString(R.string.error_unexpected), AppConstants.STEP_STATE_ERROR);
                return false;
            } else {
                publishProgress(getString(R.string.done), AppConstants.STEP_STATE_DONE);
            }

            if (isCanceled()) {
                return false;
            }

            // step 5: create data layer
            mStep = 4;

            publishProgress(getString(R.string.working), AppConstants.STEP_STATE_WORK);

            if (!createTracksLayer(keys.get(AppConstants.KEY_TRACKS), mAccount.name, map, this)) {
                publishProgress(
                        getString(R.string.error_unexpected), AppConstants.STEP_STATE_ERROR);
                return false;
            } else {
                publishProgress(getString(R.string.done), AppConstants.STEP_STATE_DONE);
            }

            if (isCanceled()) {
                return false;
            }

            map.save();

            mStep = MAX_SYNC_STEP; // add extra step to finish view
            publishProgress(getString(R.string.done), AppConstants.STEP_STATE_FINISH);
            Log.d(AppConstants.APP_TAG, "init work is finished");

            return true;
        }

        protected boolean checkFields(
                Connection connection,
                long remoteId,
                List<String> fieldsNames)
                throws JSONException, NGException, IOException
        {
            if (null == fieldsNames) {
                Log.d(AppConstants.APP_TAG, "checkFields() is not required");
                return true;
            }

            HttpResponse response =
                    NetworkUtil.get(NGWUtil.getResourceMetaUrl(connection.getURL(), remoteId),
                            connection.getLogin(), connection.getPassword(), false);

            String data = response.getResponseBody();
            if (null == data) {
                throw new NGException(getString(R.string.error_download_data));
            }

            JSONObject geoJSONObject = new JSONObject(data);

            JSONObject featureLayerJSONObject = geoJSONObject.getJSONObject("feature_layer");
            JSONArray fieldsJSONArray = featureLayerJSONObject.getJSONArray(NGWUtil.NGWKEY_FIELDS);
            List<Field> remoteFields = NGWUtil.getFieldsFromJson(fieldsJSONArray);

            for (String name : fieldsNames) {
                boolean isNameContained = false;
                for (Field field : remoteFields) {
                    if (field.getName().equals(name)) {
                        isNameContained = true;
                        break;
                    }
                }
                if (!isNameContained) {
                    return false;
                }
            }

            Log.d(AppConstants.APP_TAG, "checkFields() is OK");
            return true;
        }

        protected boolean checkServerLayers(
                INGWResource resource,
                Map<String, Long> keys,
                Map<String, List<String>> keysFields)
        {
            if (resource instanceof Connection) {
                Connection connection = (Connection) resource;
                connection.loadChildren();
            } else if (resource instanceof ResourceGroup) {
                ResourceGroup resourceGroup = (ResourceGroup) resource;
                resourceGroup.loadChildren();
            }

            for (int i = 0; i < resource.getChildrenCount(); ++i) {
                INGWResource childResource = resource.getChild(i);

                if (!(childResource instanceof Resource)) {
                    continue;
                }

                // TODO: remove it
                Resource res = (Resource) childResource;
                long remoteId = res.getRemoteId();
                if (!(remoteId == 1356 || remoteId == 1503 || remoteId == 1502 || remoteId == 1454
                        || remoteId == 1450)) {
                    continue;
                }

                if (keys.containsKey(childResource.getKey()) && childResource instanceof Resource) {
                    Resource ngwResource = (Resource) childResource;
                    Log.d(AppConstants.APP_TAG, "checkServerLayers() for: " + ngwResource.getKey());
                    Connection connection = ngwResource.getConnection();

                    try {
                        if (!checkFields(connection, ngwResource.getRemoteId(),
                                keysFields.get(ngwResource.getKey()))) {
                            Log.d(
                                    AppConstants.APP_TAG,
                                    "checkFields() ERROR: fields are not exist");
                            return false;
                        }
                    } catch (JSONException | NGException | IOException e) {
                        Log.d(AppConstants.APP_TAG,
                                "checkFields() ERROR: " + e.getLocalizedMessage());
                        return false;
                    }

                    keys.put(ngwResource.getKey(), ngwResource.getRemoteId());
                }

                boolean bIsFill = true;
                for (Map.Entry<String, Long> entry : keys.entrySet()) {
                    if (entry.getValue() <= 0) {
                        bIsFill = false;
                        break;
                    }
                }

                if (bIsFill) {
                    return true;
                }

                if (checkServerLayers(childResource, keys, keysFields)) {
                    return true;
                }
            }

            boolean bIsFill = true;

            for (Map.Entry<String, Long> entry : keys.entrySet()) {
                if (entry.getValue() <= 0) {
                    bIsFill = false;
                    break;
                }
            }

            return bIsFill;
        }

        protected boolean createZmuDataLayer(
                long resourceId,
                String accountName,
                MapBase map,
                IProgressor progressor)
        {
//            final SharedPreferences prefs =
//                    PreferenceManager.getDefaultSharedPreferences(InitService.this);
//            float minX = prefs.getFloat(AppSettingsConstants.KEY_PREF_USERMINX, -2000.0f);
//            float minY = prefs.getFloat(AppSettingsConstants.KEY_PREF_USERMINY, -2000.0f);
//            float maxX = prefs.getFloat(AppSettingsConstants.KEY_PREF_USERMAXX, 2000.0f);
//            float maxY = prefs.getFloat(AppSettingsConstants.KEY_PREF_USERMAXY, 2000.0f);

            WtcNGWVectorLayer ngwVectorLayer = new WtcNGWVectorLayer(getApplicationContext(),
                    map.createLayerStorage(AppConstants.KEY_LAYER_ZMUDATA));
            ngwVectorLayer.setName(getString(R.string.zmudata_layer));
            ngwVectorLayer.setRemoteId(resourceId);
//            ngwVectorLayer.setServerWhere(
//                    String.format(Locale.US, "bbox=%f,%f,%f,%f", minX, minY, maxX, maxY));
            ngwVectorLayer.setVisible(true);
            ngwVectorLayer.setAccountName(accountName);
            ngwVectorLayer.setSyncType(com.nextgis.maplib.util.Constants.SYNC_ALL);
            ngwVectorLayer.setSyncDirection(1); // NGWVectorLayer.DIRECTION_TO
            ngwVectorLayer.setMinZoom(0);
            ngwVectorLayer.setMaxZoom(25);

            map.addLayer(ngwVectorLayer);

            try {
                ngwVectorLayer.createFromNGW(progressor);
            } catch (NGException | IOException | JSONException e) {
                e.printStackTrace();
                return false;
            }

            return true;
        }

        protected boolean createTracksLayer(
                long resourceId,
                String accountName,
                MapBase map,
                IProgressor progressor)
        {
//            final SharedPreferences prefs =
//                    PreferenceManager.getDefaultSharedPreferences(InitService.this);
//            float minX = prefs.getFloat(AppSettingsConstants.KEY_PREF_USERMINX, -2000.0f);
//            float minY = prefs.getFloat(AppSettingsConstants.KEY_PREF_USERMINY, -2000.0f);
//            float maxX = prefs.getFloat(AppSettingsConstants.KEY_PREF_USERMAXX, 2000.0f);
//            float maxY = prefs.getFloat(AppSettingsConstants.KEY_PREF_USERMAXY, 2000.0f);

            WtcNGWVectorLayer ngwVectorLayer = new WtcNGWVectorLayer(getApplicationContext(),
                    map.createLayerStorage(AppConstants.KEY_LAYER_TRACKS));
            ngwVectorLayer.setName(getString(R.string.tracks_layer));
            ngwVectorLayer.setRemoteId(resourceId);
//            ngwVectorLayer.setServerWhere(
//                    String.format(Locale.US, "bbox=%f,%f,%f,%f", minX, minY, maxX, maxY));
            ngwVectorLayer.setVisible(true);
            ngwVectorLayer.setAccountName(accountName);
            ngwVectorLayer.setSyncType(com.nextgis.maplib.util.Constants.SYNC_ALL);
            ngwVectorLayer.setSyncDirection(1); // NGWVectorLayer.DIRECTION_TO
            ngwVectorLayer.setMinZoom(0);
            ngwVectorLayer.setMaxZoom(25);

            map.addLayer(ngwVectorLayer);

            try {
                ngwVectorLayer.createFromNGW(progressor);
            } catch (NGException | IOException | JSONException e) {
                e.printStackTrace();
                return false;
            }

            return true;
        }

        protected boolean loadLookupTables(
                long resourceId,
                String accountName,
                String layerName,
                MapBase map,
                IProgressor progressor)
        {

            NGWLookupTable ngwTable =
                    new NGWLookupTable(getApplicationContext(), map.createLayerStorage(layerName));

            ngwTable.setName(layerName);
            ngwTable.setRemoteId(resourceId);
            ngwTable.setAccountName(accountName);
            ngwTable.setSyncType(com.nextgis.maplib.util.Constants.SYNC_DATA);

            try {
                ngwTable.fillFromNGW(progressor);
            } catch (NGException | IOException | JSONException e) {
                e.printStackTrace();
                return false;
            }

            map.addLayer(ngwTable);

            return true;
        }
    }
}
