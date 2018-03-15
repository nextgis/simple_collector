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

package com.nextgis.simple_reports.service;

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
import com.nextgis.maplib.map.NGWVectorLayer;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplib.util.HttpResponse;
import com.nextgis.maplib.util.NGException;
import com.nextgis.maplib.util.NGWUtil;
import com.nextgis.maplib.util.NetworkUtil;
import com.nextgis.simple_reports.MainApplication;
import com.nextgis.simple_reports.R;
import com.nextgis.simple_reports.map.WtcNGWVectorLayer;
import com.nextgis.simple_reports.util.AppConstants;
import io.sentry.Sentry;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
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
    public static final String ACTION_START         = "START_INITIAL_SYNC";
    public static final String ACTION_STOP          = "STOP_INITIAL_SYNC";
    public static final String ACTION_REPORT        = "REPORT_INITIAL_SYNC";
    public static final String ACTION_CREATE_STRUCT = "CREATE_REMOTE_STRUCT_INITIAL_SYNC";

    public static final int MAX_SYNC_STEP                           = 8;
    public static final int MAX_SYNC_STEP_WITH_CREATE_REMOTE_STRUCT = 14;

    private boolean mCreateRemote = false;

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
                    if (Constants.DEBUG_MODE) {
                        String msg = "reportSync()";
                        Log.d(AppConstants.APP_TAG, msg);
                        Sentry.capture(msg);
                    }
                    reportSync();
                } else {
                    if (Constants.DEBUG_MODE) {
                        String msg = "startSync()";
                        Log.d(AppConstants.APP_TAG, msg);
                        Sentry.capture(msg);
                    }
                    mCreateRemote = false;
                    startSync(mCreateRemote);
                }
                break;
            case ACTION_STOP:
                if (Constants.DEBUG_MODE) {
                    String msg = "stopSync()";
                    Log.d(AppConstants.APP_TAG, msg);
                    Sentry.capture(msg);
                }
                reportState(getString(R.string.operaton_canceled), AppConstants.STEP_STATE_CANCEL);
                stopSync();
                break;
            case ACTION_REPORT:
                if (mIsRunning) {
                    if (Constants.DEBUG_MODE) {
                        String msg = "reportSync()";
                        Log.d(AppConstants.APP_TAG, msg);
                        Sentry.capture(msg);
                    }
                    reportSync();
                } else {
                    if (Constants.DEBUG_MODE) {
                        String msg = "reportState() for finish";
                        Log.d(AppConstants.APP_TAG, msg);
                        Sentry.capture(msg);
                    }
                    reportState(getString(R.string.done), AppConstants.STEP_STATE_FINISH);
                    stopSync();
                }
                break;
            case ACTION_CREATE_STRUCT:
                if (Constants.DEBUG_MODE) {
                    String msg = "startSync() with remote struct creation";
                    Log.d(AppConstants.APP_TAG, msg);
                    Sentry.capture(msg);
                }
                mCreateRemote = true;
                startSync(mCreateRemote);
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
        Intent intent = new Intent(AppConstants.INIT_SYNC_BROADCAST_MESSAGE);

        int maxStepCount =
                (mCreateRemote ? MAX_SYNC_STEP_WITH_CREATE_REMOTE_STRUCT : MAX_SYNC_STEP);
        intent.putExtra(AppConstants.KEY_STEP, maxStepCount);
        intent.putExtra(AppConstants.KEY_STEP_COUNT, maxStepCount);
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

    private void startSync(boolean createRemoteStruct)
    {
        final MainApplication app = (MainApplication) getApplication();
        if (app == null) {
            String error = "InitService. Failed to get main application";
            if (Constants.DEBUG_MODE) {
                Log.d(AppConstants.APP_TAG, error);
                Sentry.capture(error);
            }
            reportState(error, AppConstants.STEP_STATE_ERROR);
            stopSync();
            return;
        }

        final Account account = app.getAccount(getString(R.string.account_name));
        if (account == null) {
            String error = "InitService. No account " + getString(R.string.account_name)
                    + " created. Run first step.";
            if (Constants.DEBUG_MODE) {
                Log.d(AppConstants.APP_TAG, error);
                Sentry.capture(error);
            }
            reportState(error, AppConstants.STEP_STATE_ERROR);
            stopSync();
            return;
        }

        mThread = new InitialSyncThread(account, createRemoteStruct);
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
        protected boolean mCreateRemoteStruct;

        public InitialSyncThread(
                Account account,
                boolean createRemoteStruct)
        {
            mAccount = account;
            mCreateRemoteStruct = createRemoteStruct;
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
            Boolean res = doWork();
            if (res == null) {
                return;
            }
            InitService.this.stopSync();
        }

        public final void publishProgress(
                String message,
                int state)
        {
            if (null == mMessageIntent) {
                return;
            }

            int maxStepCount =
                    (mCreateRemoteStruct ? MAX_SYNC_STEP_WITH_CREATE_REMOTE_STRUCT : MAX_SYNC_STEP);
            mMessageIntent.putExtra(AppConstants.KEY_STEP, mStep);
            mMessageIntent.putExtra(AppConstants.KEY_STEP_COUNT, maxStepCount);
            mMessageIntent.putExtra(AppConstants.KEY_MESSAGE, message);
            mMessageIntent.putExtra(AppConstants.KEY_STATE, state);
            sendBroadcast(mMessageIntent);
        }

        protected Boolean doWork()
        {
            mMessageIntent = new Intent(AppConstants.INIT_SYNC_BROADCAST_MESSAGE);

            // step: connect to server
            mStep = 0;

            final MainApplication app = (MainApplication) getApplication();
            final String accountName = app.getAccount().name;
            final String sLogin = app.getAccountLogin(mAccount);
            final String sPassword = app.getAccountPassword(mAccount);
            final String sURL = app.getAccountUrl(mAccount);

            if (null == sURL || null == sPassword || null == sLogin) {
                publishProgress(
                        getString(R.string.error_unexpected), AppConstants.STEP_STATE_ERROR);
                return false;
            }

            Connection connection = new Connection(accountName, sLogin, sPassword, sURL);
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

            if (mCreateRemoteStruct) {
                // step: create remote root resource group
                ++mStep;

                publishProgress(getString(R.string.create_root_resource_group),
                        AppConstants.STEP_STATE_WORK);

                HttpResponse response = NGWUtil.createNewGroup(InitService.this, connection, 0,
                        AppConstants.NAME_ROOT_RESOURCE_GROUP,
                        AppConstants.KEY_ROOT_RESOURCE_GROUP);

                if (!response.isOk()) {
                    publishProgress(getString(R.string.error_create_root_resource),
                            AppConstants.STEP_STATE_ERROR);
                    return false;
                }

                if (isCanceled()) {
                    return false;
                }
            }

            // step: check remote root resource group
            ++mStep;

            publishProgress(getString(R.string.check_root_resource_group),
                    AppConstants.STEP_STATE_WORK);

            ResourceGroup rootResGroup =
                    getRootResourceGroup(connection, AppConstants.KEY_ROOT_RESOURCE_GROUP);

            if (rootResGroup == null) {
                int state = mCreateRemoteStruct
                            ? AppConstants.STEP_STATE_ERROR
                            : AppConstants.STEP_STATE_NONE_ROOT;
                publishProgress(getString(R.string.root_resource_group_not_found), state);

                if (!mCreateRemoteStruct) {
                    return null;
                }

                return false;
            } else {
                publishProgress(getString(R.string.done), AppConstants.STEP_STATE_DONE);
            }

            if (isCanceled()) {
                return false;
            }

            MapBase map = app.getMap();

            if (mCreateRemoteStruct) {
                // create remote layers
                if (!createRemoteLayers(accountName, connection, rootResGroup.getRemoteId(), map)) {
                    return false;
                }
            }

            // step: check remote layers
            ++mStep;

            publishProgress(getString(R.string.check_tables_exist), AppConstants.STEP_STATE_WORK);

            Map<String, Long> keys = new HashMap<>();
            keys.put(AppConstants.KEY_ZMUDATA, -1L);
            keys.put(AppConstants.KEY_PEOPLE, -1L);
            keys.put(AppConstants.KEY_SPECIES, -1L);
            keys.put(AppConstants.KEY_TRACKS, -1L);
            keys.put(AppConstants.KEY_ROUTES, -1L);

            Map<String, List<String>> keysFields = new HashMap<>(keys.size());

            List<String> zmudataFields = new LinkedList<>();
            zmudataFields.add(AppConstants.FIELD_ZMUDATA_GUID);
            zmudataFields.add(AppConstants.FIELD_ZMUDATA_LAT);
            zmudataFields.add(AppConstants.FIELD_ZMUDATA_LON);
            zmudataFields.add(AppConstants.FIELD_ZMUDATA_SIDE);
            zmudataFields.add(AppConstants.FIELD_ZMUDATA_ROUTE);
            zmudataFields.add(AppConstants.FIELD_ZMUDATA_DATE);
            zmudataFields.add(AppConstants.FIELD_ZMUDATA_TIME);
            zmudataFields.add(AppConstants.FIELD_ZMUDATA_SPECIES);
            zmudataFields.add(AppConstants.FIELD_ZMUDATA_CNT);
            zmudataFields.add(AppConstants.FIELD_ZMUDATA_COLLECTOR);
            keysFields.put(AppConstants.KEY_ZMUDATA, zmudataFields);

            List<String> tracksFields = new LinkedList<>();
            tracksFields.add(AppConstants.FIELD_TRACKS_LAT);
            tracksFields.add(AppConstants.FIELD_TRACKS_LON);
            tracksFields.add(AppConstants.FIELD_TRACKS_TIMESTAMP);
            tracksFields.add(AppConstants.FIELD_TRACKS_STATUS);
            tracksFields.add(AppConstants.FIELD_TRACKS_ROUTE);
            tracksFields.add(AppConstants.FIELD_TRACKS_COLLECTOR);
            keysFields.put(AppConstants.KEY_TRACKS, tracksFields);

            List<String> routesFields = new LinkedList<>();
            routesFields.add(AppConstants.FIELD_ROUTES_NAME);
            keysFields.put(AppConstants.KEY_ROUTES, routesFields);

            if (!checkRemoteLayers(rootResGroup, keys, keysFields)) {
                publishProgress(getString(R.string.error_resource_struct),
                        AppConstants.STEP_STATE_ERROR_ROOT);
                return false;
            } else {
                publishProgress(getString(R.string.done), AppConstants.STEP_STATE_DONE);
            }

            if (isCanceled()) {
                return false;
            }

            if (!loadLayersFromNGW(map, keys)) {
                return false;
            }

            map.save();

            // add extra step to finish view
            mStep = mCreateRemoteStruct ? MAX_SYNC_STEP_WITH_CREATE_REMOTE_STRUCT : MAX_SYNC_STEP;
            publishProgress(getString(R.string.done), AppConstants.STEP_STATE_FINISH);
            if (Constants.DEBUG_MODE) {
                String msg = "init work is finished";
                Log.d(AppConstants.APP_TAG, msg);
                Sentry.capture(msg);
            }

            return true;
        }

        protected ResourceGroup getRootResourceGroup(
                Connection connection,
                String keyName)
        {
            connection.loadChildren();
            for (int i = 0; i < connection.getChildrenCount(); ++i) {
                INGWResource childResource = connection.getChild(i);
                if (!(childResource instanceof ResourceGroup)) {
                    continue;
                }
                if (childResource.getKey().equals(keyName)) {
                    return (ResourceGroup) childResource;
                }
            }
            return null;
        }

        protected boolean checkRemoteLayers(
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

                if (keys.containsKey(childResource.getKey())) {
                    Resource ngwResource = (Resource) childResource;
                    if (Constants.DEBUG_MODE) {
                        String msg = "checkServerLayers() for: " + ngwResource.getKey();
                        Log.d(AppConstants.APP_TAG, msg);
                        Sentry.capture(msg);
                    }
                    Connection connection = ngwResource.getConnection();

                    try {
                        if (!checkFields(connection, ngwResource.getRemoteId(),
                                keysFields.get(ngwResource.getKey()))) {
                            if (Constants.DEBUG_MODE) {
                                String msg = "checkFields() ERROR: fields are not exist";
                                Log.d(AppConstants.APP_TAG, msg);
                                Sentry.capture(msg);
                            }
                            return false;
                        }
                    } catch (JSONException | NGException | IOException e) {
                        if (Constants.DEBUG_MODE) {
                            String msg = "checkFields() ERROR: " + e.getLocalizedMessage();
                            Log.d(AppConstants.APP_TAG, msg);
                            Sentry.capture(msg);
                        }
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

                if (checkRemoteLayers(childResource, keys, keysFields)) {
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

        protected boolean checkFields(
                Connection connection,
                long remoteId,
                List<String> fieldsNames)
                throws JSONException, NGException, IOException
        {
            if (null == fieldsNames) {
                if (Constants.DEBUG_MODE) {
                    String msg = "checkFields() is not required";
                    Log.d(AppConstants.APP_TAG, msg);
                    Sentry.capture(msg);
                }
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

            if (Constants.DEBUG_MODE) {
                String msg = "checkFields() is OK";
                Log.d(AppConstants.APP_TAG, msg);
                Sentry.capture(msg);
            }
            return true;
        }

        protected boolean createRemoteLayers(
                String accountName,
                Connection connection,
                long parentId,
                MapBase map)
        {
            // step: create remote zmu data layer
            ++mStep;

            publishProgress(getString(R.string.working), AppConstants.STEP_STATE_WORK);

            if (!createRemoteZmuDataLayer(accountName, connection, parentId, map)) {
                publishProgress(
                        getString(R.string.error_unexpected), AppConstants.STEP_STATE_ERROR);
                return false;
            } else {
                publishProgress(getString(R.string.done), AppConstants.STEP_STATE_DONE);
            }

            if (isCanceled()) {
                return false;
            }

            // step: create remote tracks layer
            ++mStep;

            publishProgress(getString(R.string.working), AppConstants.STEP_STATE_WORK);

            if (!createRemoteTracksLayer(accountName, connection, parentId, map)) {
                publishProgress(
                        getString(R.string.error_unexpected), AppConstants.STEP_STATE_ERROR);
                return false;
            } else {
                publishProgress(getString(R.string.done), AppConstants.STEP_STATE_DONE);
            }

            if (isCanceled()) {
                return false;
            }

            // step: create remote routes layer
            ++mStep;

            publishProgress(getString(R.string.working), AppConstants.STEP_STATE_WORK);

            if (!createRemoteRoutesLayer(accountName, connection, parentId, map)) {
                publishProgress(
                        getString(R.string.error_unexpected), AppConstants.STEP_STATE_ERROR);
                return false;
            } else {
                publishProgress(getString(R.string.done), AppConstants.STEP_STATE_DONE);
            }

            if (isCanceled()) {
                return false;
            }

            // step: create remote people lookup table
            ++mStep;

            publishProgress(getString(R.string.working), AppConstants.STEP_STATE_WORK);

            if (!createRemoteLookupTable(accountName, connection, parentId, map,
                    AppConstants.KEY_LAYER_PEOPLE, AppConstants.KEY_PEOPLE, null)) {
                publishProgress(
                        getString(R.string.error_unexpected), AppConstants.STEP_STATE_ERROR);
                return false;
            } else {
                publishProgress(getString(R.string.done), AppConstants.STEP_STATE_DONE);
            }

            if (isCanceled()) {
                return false;
            }

            // step: create remote species lookup table
            ++mStep;

            publishProgress(getString(R.string.working), AppConstants.STEP_STATE_WORK);

            if (!createRemoteLookupTable(accountName, connection, parentId, map,
                    AppConstants.KEY_LAYER_SPECIES, AppConstants.KEY_SPECIES, null)) {
                publishProgress(
                        getString(R.string.error_unexpected), AppConstants.STEP_STATE_ERROR);
                return false;
            } else {
                publishProgress(getString(R.string.done), AppConstants.STEP_STATE_DONE);
            }

            if (isCanceled()) {
                return false;
            }

            return true;
        }

        protected boolean loadLayersFromNGW(
                MapBase map,
                Map<String, Long> keys)
        {
            // step: create data layer from NGW
            ++mStep;

            publishProgress(getString(R.string.working), AppConstants.STEP_STATE_WORK);

            if (!loadZmuDataLayerFromNGW(
                    keys.get(AppConstants.KEY_ZMUDATA), mAccount.name, map, this)) {
                publishProgress(
                        getString(R.string.error_unexpected), AppConstants.STEP_STATE_ERROR);
                return false;
            } else {
                publishProgress(getString(R.string.done), AppConstants.STEP_STATE_DONE);
            }

            if (isCanceled()) {
                return false;
            }

            // step: create tracks layer from NGW
            ++mStep;

            publishProgress(getString(R.string.working), AppConstants.STEP_STATE_WORK);

            if (!loadTracksLayerFromNGW(
                    keys.get(AppConstants.KEY_TRACKS), mAccount.name, map, this)) {
                publishProgress(
                        getString(R.string.error_unexpected), AppConstants.STEP_STATE_ERROR);
                return false;
            } else {
                publishProgress(getString(R.string.done), AppConstants.STEP_STATE_DONE);
            }

            if (isCanceled()) {
                return false;
            }

            // step: create routes layer from NGW
            ++mStep;

            publishProgress(getString(R.string.working), AppConstants.STEP_STATE_WORK);

            if (!loadRoutesLayerFromNGW(
                    keys.get(AppConstants.KEY_ROUTES), mAccount.name, map, this)) {
                publishProgress(
                        getString(R.string.error_unexpected), AppConstants.STEP_STATE_ERROR);
                return false;
            } else {
                publishProgress(getString(R.string.done), AppConstants.STEP_STATE_DONE);
            }

            if (isCanceled()) {
                return false;
            }

            // step: load people lookup table from NGW
            ++mStep;

            publishProgress(getString(R.string.working), AppConstants.STEP_STATE_WORK);

            if (!loadLookupTableFromNGW(keys.get(AppConstants.KEY_PEOPLE), mAccount.name, map,
                    AppConstants.KEY_LAYER_PEOPLE, this)) {
                publishProgress(
                        getString(R.string.error_unexpected), AppConstants.STEP_STATE_ERROR);
                return false;
            } else {
                publishProgress(getString(R.string.done), AppConstants.STEP_STATE_DONE);
            }

            if (isCanceled()) {
                return false;
            }

            // step: load species lookup table from NGW
            ++mStep;

            publishProgress(getString(R.string.working), AppConstants.STEP_STATE_WORK);

            if (!loadLookupTableFromNGW(keys.get(AppConstants.KEY_SPECIES), mAccount.name, map,
                    AppConstants.KEY_LAYER_SPECIES, this)) {
                publishProgress(
                        getString(R.string.error_unexpected), AppConstants.STEP_STATE_ERROR);
                return false;
            } else {
                publishProgress(getString(R.string.done), AppConstants.STEP_STATE_DONE);
            }

            if (isCanceled()) {
                return false;
            }

            return true;
        }

        protected boolean loadZmuDataLayerFromNGW(
                long resourceId,
                String accountName,
                MapBase map,
                IProgressor progressor)
        {
            WtcNGWVectorLayer layer = createZmuDataLayer(accountName, map);
            layer.setRemoteId(resourceId);

            map.addLayer(layer);

            try {
                layer.createFromNGW(progressor);
            } catch (NGException | IOException | JSONException e) {
                if (Constants.DEBUG_MODE) {
                    e.printStackTrace();
                    Sentry.capture(e);
                }
                return false;
            }

            return true;
        }

        protected boolean loadTracksLayerFromNGW(
                long resourceId,
                String accountName,
                MapBase map,
                IProgressor progressor)
        {
            WtcNGWVectorLayer layer = createTracksLayer(accountName, map);
            layer.setRemoteId(resourceId);

            map.addLayer(layer);

            try {
                layer.createFromNGW(progressor);
            } catch (NGException | IOException | JSONException e) {
                if (Constants.DEBUG_MODE) {
                    e.printStackTrace();
                    Sentry.capture(e);
                }
                return false;
            }

            return true;
        }

        protected boolean loadRoutesLayerFromNGW(
                long resourceId,
                String accountName,
                MapBase map,
                IProgressor progressor)
        {
            NGWVectorLayer layer = createRoutesLayer(accountName, map); // NGWVectorLayer !!!
            layer.setRemoteId(resourceId);

            map.addLayer(layer);

            try {
                layer.createFromNGW(progressor);
            } catch (NGException | IOException | JSONException e) {
                if (Constants.DEBUG_MODE) {
                    e.printStackTrace();
                    Sentry.capture(e);
                }
                return false;
            }

            return true;
        }

        protected boolean loadLookupTableFromNGW(
                long resourceId,
                String accountName,
                MapBase map,
                String tableName,
                IProgressor progressor)
        {
            NGWLookupTable table = createLookupTable(accountName, map, tableName);
            table.setRemoteId(resourceId);

            try {
                table.fillFromNGW(progressor);
            } catch (NGException | IOException | JSONException e) {
                if (Constants.DEBUG_MODE) {
                    e.printStackTrace();
                    Sentry.capture(e);
                }
                return false;
            }

            map.addLayer(table);

            return true;
        }

        protected boolean createRemoteZmuDataLayer(
                String accountName,
                Connection connection,
                long parentId,
                MapBase map)
        {
            WtcNGWVectorLayer layer = createLocalZmuDataLayer(accountName, map);
            HttpResponse response =
                    NGWUtil.createNewLayer(connection, layer, parentId, AppConstants.KEY_ZMUDATA);
            layer.delete();
            return response.isOk();
        }

        protected boolean createRemoteTracksLayer(
                String accountName,
                Connection connection,
                long parentId,
                MapBase map)
        {
            WtcNGWVectorLayer layer = createLocalTracksLayer(accountName, map);
            HttpResponse response =
                    NGWUtil.createNewLayer(connection, layer, parentId, AppConstants.KEY_TRACKS);
            layer.delete();
            return response.isOk();
        }

        protected boolean createRemoteRoutesLayer(
                String accountName,
                Connection connection,
                long parentId,
                MapBase map)
        {
            NGWVectorLayer layer = createLocalRoutesLayer(accountName, map); // NGWVectorLayer !!!
            HttpResponse response =
                    NGWUtil.createNewLayer(connection, layer, parentId, AppConstants.KEY_ROUTES);
            layer.delete();
            return response.isOk();
        }

        protected boolean createRemoteLookupTable(
                String accountName,
                Connection connection,
                long parentId,
                MapBase map,
                String tableName,
                String keyName,
                Map<String, String> data)
        {
            NGWLookupTable table = createLocalLookupTable(accountName, map, tableName, data);
            HttpResponse response =
                    NGWUtil.createNewLookupTable(connection, table, parentId, keyName);
            table.delete();
            return response.isOk();
        }

        protected WtcNGWVectorLayer createLocalZmuDataLayer(
                String accountName,
                MapBase map)
        {
            WtcNGWVectorLayer layer = createZmuDataLayer(accountName, map);

            List<Field> fields = new ArrayList<>(9);

            fields.add(new Field(GeoConstants.FTString, AppConstants.FIELD_ZMUDATA_SPECIES,
                    AppConstants.FIELD_ZMUDATA_SPECIES));
            fields.add(new Field(GeoConstants.FTInteger, AppConstants.FIELD_ZMUDATA_CNT,
                    AppConstants.FIELD_ZMUDATA_CNT));
            fields.add(new Field(GeoConstants.FTReal, AppConstants.FIELD_ZMUDATA_LAT,
                    AppConstants.FIELD_ZMUDATA_LAT));
            fields.add(new Field(GeoConstants.FTReal, AppConstants.FIELD_ZMUDATA_LON,
                    AppConstants.FIELD_ZMUDATA_LON));
            fields.add(new Field(GeoConstants.FTString, AppConstants.FIELD_ZMUDATA_SIDE,
                    AppConstants.FIELD_ZMUDATA_SIDE));
            fields.add(new Field(GeoConstants.FTString, AppConstants.FIELD_ZMUDATA_ROUTE,
                    AppConstants.FIELD_ZMUDATA_ROUTE));
            fields.add(new Field(GeoConstants.FTDate, AppConstants.FIELD_ZMUDATA_DATE,
                    AppConstants.FIELD_ZMUDATA_DATE));
            fields.add(new Field(GeoConstants.FTTime, AppConstants.FIELD_ZMUDATA_TIME,
                    AppConstants.FIELD_ZMUDATA_TIME));
            fields.add(new Field(GeoConstants.FTString, AppConstants.FIELD_ZMUDATA_COLLECTOR,
                    AppConstants.FIELD_ZMUDATA_COLLECTOR));
            fields.add(new Field(GeoConstants.FTString, AppConstants.FIELD_ZMUDATA_GUID,
                    AppConstants.FIELD_ZMUDATA_GUID));

            layer.create(GeoConstants.GTPoint, fields);

            return layer;
        }

        protected WtcNGWVectorLayer createLocalTracksLayer(
                String accountName,
                MapBase map)
        {
            WtcNGWVectorLayer layer = createTracksLayer(accountName, map);

            List<Field> fields = new ArrayList<>(4);

            fields.add(new Field(GeoConstants.FTReal, AppConstants.FIELD_TRACKS_LAT,
                    AppConstants.FIELD_TRACKS_LAT));
            fields.add(new Field(GeoConstants.FTReal, AppConstants.FIELD_TRACKS_LON,
                    AppConstants.FIELD_TRACKS_LON));
            fields.add(new Field(GeoConstants.FTDateTime, AppConstants.FIELD_TRACKS_TIMESTAMP,
                    AppConstants.FIELD_TRACKS_TIMESTAMP));
            fields.add(new Field(GeoConstants.FTString, AppConstants.FIELD_TRACKS_STATUS,
                    AppConstants.FIELD_TRACKS_STATUS));
            fields.add(new Field(GeoConstants.FTString, AppConstants.FIELD_TRACKS_ROUTE,
                    AppConstants.FIELD_TRACKS_ROUTE));
            fields.add(new Field(GeoConstants.FTString, AppConstants.FIELD_TRACKS_COLLECTOR,
                    AppConstants.FIELD_TRACKS_COLLECTOR));

            layer.create(GeoConstants.GTPoint, fields);

            return layer;
        }

        protected NGWVectorLayer createLocalRoutesLayer(
                String accountName,
                MapBase map)
        {
            NGWVectorLayer layer = createRoutesLayer(accountName, map); // NGWVectorLayer !!!

            List<Field> fields = new ArrayList<>(1);

            fields.add(new Field(GeoConstants.FTString, AppConstants.FIELD_ROUTES_NAME,
                    AppConstants.FIELD_ROUTES_NAME));

            layer.create(GeoConstants.GTLineString, fields);

            return layer;
        }

        protected NGWLookupTable createLocalLookupTable(
                String accountName,
                MapBase map,
                String tableName,
                Map<String, String> data)
        {
            NGWLookupTable table = createLookupTable(accountName, map, tableName);
            table.setData(data);
            return table;
        }

        protected WtcNGWVectorLayer createZmuDataLayer(
                String accountName,
                MapBase map)
        {
            WtcNGWVectorLayer layer = new WtcNGWVectorLayer(getApplicationContext(),
                    map.createLayerStorage(AppConstants.KEY_LAYER_ZMUDATA));
            layer.setName(getString(R.string.zmudata_layer));
            layer.setVisible(true);
            layer.setAccountName(accountName);
            layer.setSyncType(com.nextgis.maplib.util.Constants.SYNC_ALL);
            layer.setSyncDirection(1); // NGWVectorLayer.DIRECTION_TO
            layer.setMinZoom(GeoConstants.DEFAULT_MIN_ZOOM);
            layer.setMaxZoom(GeoConstants.DEFAULT_MAX_ZOOM);

            return layer;
        }

        protected WtcNGWVectorLayer createTracksLayer(
                String accountName,
                MapBase map)
        {
            WtcNGWVectorLayer layer = new WtcNGWVectorLayer(getApplicationContext(),
                    map.createLayerStorage(AppConstants.KEY_LAYER_TRACKS));
            layer.setName(getString(R.string.tracks_layer));
            layer.setVisible(true);
            layer.setAccountName(accountName);
            layer.setSyncType(com.nextgis.maplib.util.Constants.SYNC_ALL);
            layer.setSyncDirection(1); // NGWVectorLayer.DIRECTION_TO
            layer.setMinZoom(GeoConstants.DEFAULT_MIN_ZOOM);
            layer.setMaxZoom(GeoConstants.DEFAULT_MAX_ZOOM);

            return layer;
        }

        protected NGWVectorLayer createRoutesLayer(
                String accountName,
                MapBase map)
        {
            // NGWVectorLayer !!!
            NGWVectorLayer layer = new NGWVectorLayer(getApplicationContext(),
                    map.createLayerStorage(AppConstants.KEY_LAYER_ROUTES));
            layer.setName(getString(R.string.routes_layer));
            layer.setVisible(true);
            layer.setAccountName(accountName);
            layer.setSyncType(com.nextgis.maplib.util.Constants.SYNC_ALL);
            layer.setSyncDirection(2); // NGWVectorLayer.DIRECTION_FROM
            layer.setMinZoom(GeoConstants.DEFAULT_MIN_ZOOM);
            layer.setMaxZoom(GeoConstants.DEFAULT_MAX_ZOOM);

            return layer;
        }

        protected NGWLookupTable createLookupTable(
                String accountName,
                MapBase map,
                String tableName)
        {
            NGWLookupTable table =
                    new NGWLookupTable(getApplicationContext(), map.createLayerStorage(tableName));
            table.setName(tableName);
            table.setAccountName(accountName);
            table.setSyncType(com.nextgis.maplib.util.Constants.SYNC_DATA);

            return table;
        }
    }
}
