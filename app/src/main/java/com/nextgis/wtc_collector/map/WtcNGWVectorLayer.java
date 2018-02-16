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

package com.nextgis.wtc_collector.map;

import android.content.Context;
import android.database.sqlite.SQLiteException;
import android.util.Log;
import android.util.Pair;
import com.nextgis.maplib.api.IProgressor;
import com.nextgis.maplib.datasource.Field;
import com.nextgis.maplib.datasource.GeoGeometryFactory;
import com.nextgis.maplib.datasource.ngw.Connection;
import com.nextgis.maplib.map.NGWVectorLayer;
import com.nextgis.maplib.util.AccountUtil;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplib.util.HttpResponse;
import com.nextgis.maplib.util.NGException;
import com.nextgis.maplib.util.NGWUtil;
import com.nextgis.maplib.util.NetworkUtil;
import com.nextgis.wtc_collector.util.AppConstants;
import io.sentry.Sentry;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.List;


public class WtcNGWVectorLayer
        extends NGWVectorLayer
{
    public WtcNGWVectorLayer(
            Context context,
            File path)
    {
        super(context, path);
        mLayerType = AppConstants.LAYERTYPE_WTC_NGW_VECTOR;
    }

    @Override
    public void createFromNGW(IProgressor progressor)
            throws NGException, IOException, JSONException, SQLiteException
    {
        // A part from NGWVectorLayer.createFromNGW().

        if (!mNet.isNetworkAvailable()) { //return tile from cache
            throw new NGException(getContext().getString(com.nextgis.maplib.R.string.error_network_unavailable));
        }

        if (Constants.DEBUG_MODE) {
            String msg = "download layer " + getName();
            Log.d(AppConstants.APP_TAG, msg);
            Sentry.capture(msg);
        }

        // get account
        AccountUtil.AccountData accountData;
        try {
            accountData = AccountUtil.getAccountData(mContext, mAccountName);
        } catch (IllegalStateException e) {
            String msg = getContext().getString(com.nextgis.maplib.R.string.error_auth);
            if (Constants.DEBUG_MODE) {
                Sentry.capture(e);
                Sentry.capture(msg);
            }
            throw new NGException(msg);
        }

        if (null == accountData.url) {
            String msg = getContext().getString(com.nextgis.maplib.R.string.error_404);
            if (Constants.DEBUG_MODE) {
                Sentry.capture("WtcNGWVectorLayer.createFromNGW(), null == accountData.url");
                Sentry.capture(msg);
            }
            throw new NGException(msg);
        }

        // get NGW version
        Pair<Integer, Integer> ver = null;
        try {
            ver = NGWUtil.getNgwVersion(accountData.url, accountData.login, accountData.password);
        } catch (IOException | JSONException | NumberFormatException ignored) { }

        if (null != ver) {
            mNgwVersionMajor = ver.first;
            mNgwVersionMinor = ver.second;
        }

        // get layer description
        JSONObject geoJSONObject;
        HttpResponse response = NetworkUtil.get(getResourceMetaUrl(accountData), accountData.login,
                accountData.password, false);
        if (!response.isOk()) {
            throw new NGException(NetworkUtil.getError(mContext, response.getResponseCode()));
        }
        geoJSONObject = new JSONObject(response.getResponseBody());

        //fill field list
        JSONObject featureLayerJSONObject = geoJSONObject.getJSONObject("feature_layer");
        JSONArray fieldsJSONArray = featureLayerJSONObject.getJSONArray(NGWUtil.NGWKEY_FIELDS);
        List<Field> fields = NGWUtil.getFieldsFromJson(fieldsJSONArray);

        //fill SRS
        JSONObject vectorLayerJSONObject = null;
        if (geoJSONObject.has(getRequiredCls())) {
            vectorLayerJSONObject = geoJSONObject.getJSONObject(getRequiredCls());
            mNGWLayerType = Connection.NGWResourceTypeVectorLayer;
        } else if (mNgwVersionMajor >= Constants.NGW_v3 && geoJSONObject.has("postgis_layer")) {
            vectorLayerJSONObject = geoJSONObject.getJSONObject("postgis_layer");
            mNGWLayerType = Connection.NGWResourceTypePostgisLayer;
        }
        if (null == vectorLayerJSONObject) {
            throw new NGException(getContext().getString(com.nextgis.maplib.R.string.error_download_data));
        }

        String geomTypeString = vectorLayerJSONObject.getString(JSON_GEOMETRY_TYPE_KEY);
        int geomType = GeoGeometryFactory.typeFromString(geomTypeString);
        JSONObject srs = vectorLayerJSONObject.getJSONObject(NGWUtil.NGWKEY_SRS);
        mCRS = srs.getInt("id");
        if (mCRS != GeoConstants.CRS_WEB_MERCATOR && mCRS != GeoConstants.CRS_WGS84) {
            throw new NGException(getContext().getString(com.nextgis.maplib.R.string.error_crs_unsupported));
        }

        create(geomType, fields);
    }
}
