/*
 * Project:  Simple Reports
 * Purpose:  Mobile application for WTC data collection.
 * Author:   NikitaFeodonit, nfeodonit@yandex.com
 * ****************************************************************************
 * Copyright (c) 2017-2018 NextGIS, info@nextgis.com
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

package com.nextgis.simple_reports.map;

import android.content.Context;
import android.util.Log;
import com.nextgis.maplib.api.ILayer;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.FileUtil;
import com.nextgis.maplibui.mapui.LayerFactoryUI;
import com.nextgis.simple_reports.R;
import com.nextgis.simple_reports.util.AppConstants;
import io.sentry.Sentry;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

import static com.nextgis.maplib.util.Constants.*;


public class WtcLayerFactory
        extends LayerFactoryUI
{
    @Override
    public ILayer createLayer(
            Context context,
            File path)
    {
        ILayer layer = super.createLayer(context, path);
        if (null != layer) {
            return layer;
        }
        File config_file = new File(path, CONFIG);
        try {
            String sData = FileUtil.readFromFile(config_file);
            JSONObject rootObject = new JSONObject(sData);
            int nType = rootObject.getInt(JSON_TYPE_KEY);

            switch (nType) {
                case AppConstants.LAYERTYPE_WTC_NGW_VECTOR:
                    layer = new WtcNGWVectorLayer(context, path);
                    break;
            }
        } catch (IOException | JSONException e) {
            if (Constants.DEBUG_MODE) {
                Log.d(AppConstants.APP_TAG, e.getLocalizedMessage());
                Sentry.capture(e);
            }
        }

        return layer;
    }

    @Override
    public String getLayerTypeString(
            Context context,
            int type)
    {
        switch (type) {
            case AppConstants.LAYERTYPE_WTC_NGW_VECTOR:
                return context.getString(R.string.zmudata_layer);
            default:
                return super.getLayerTypeString(context, type);
        }
    }
}
