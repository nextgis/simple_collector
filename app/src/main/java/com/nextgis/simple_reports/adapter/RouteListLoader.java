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

package com.nextgis.simple_reports.adapter;

import android.content.Context;
import android.database.Cursor;
import android.support.v4.content.AsyncTaskLoader;
import com.nextgis.maplib.api.ILayer;
import com.nextgis.maplib.api.MapEventListener;
import com.nextgis.maplib.datasource.GeoPoint;
import com.nextgis.maplib.map.MapBase;
import com.nextgis.maplib.map.MapEventSource;
import com.nextgis.maplib.map.VectorLayer;
import com.nextgis.simple_reports.R;
import com.nextgis.simple_reports.util.AppConstants;

import java.util.ArrayList;
import java.util.List;


// see example in
// http://developer.android.com/reference/android/content/AsyncTaskLoader.html
public class RouteListLoader
        extends AsyncTaskLoader<List<String>>
        implements MapEventListener
{
    protected Context mContext;

    protected List<String>   mRoutes;
    protected MapEventSource mMap;

    protected int mRoutesLayerId;

    public RouteListLoader(Context context)
    {
        super(context);

        mContext = context;

        mMap = (MapEventSource) MapBase.getInstance();
    }

    /**
     * This is where the bulk of our work is done.  This function is called in a background thread
     * and should generate a new set of data to be published by the loader.
     */
    @Override
    public List<String> loadInBackground()
    {
        List<String> routeList = new ArrayList<>();

        if (null == mMap) {
            return routeList;
        }

        mRoutesLayerId = -10;

        ILayer layer = mMap.getLayerByName(mContext.getString(R.string.routes_layer));
        if (null != layer) {
            mRoutesLayerId = layer.getId();
            VectorLayer routesLayer = (VectorLayer) layer;
            String[] columns = new String[] {AppConstants.FIELD_ROUTES_NAME};
            String sortOrder = AppConstants.FIELD_ROUTES_NAME + " ASC";
            Cursor cursor = routesLayer.query(columns, null, null, sortOrder, null);

            if (null != cursor) {
                int namePos = cursor.getColumnIndex(AppConstants.FIELD_ROUTES_NAME);
                if (cursor.moveToFirst()) {
                    do {
                        routeList.add(cursor.getString(namePos));
                    } while (cursor.moveToNext());
                }
                cursor.close();
            }
        }

        return routeList;
    }

    /**
     * Called when there is new data to deliver to the client.  The super class will take care of
     * delivering it; the implementation here just adds a little more logic.
     */
    @Override
    public void deliverResult(List<String> routes)
    {
        if (isReset()) {
            // An async query came in while the loader is stopped.  We don't need the result.
            if (routes != null) {
                onReleaseResources(routes);
            }
        }

        List<String> oldRoutes = null;
        if (routes != mRoutes) {
            oldRoutes = mRoutes;
            mRoutes = routes;
        }

        if (isStarted()) {
            // If the Loader is currently started, we can immediately deliver its results.
            super.deliverResult(routes);
        }

        // At this point we can release the resources associated with 'oldRoutes' if needed;
        // now that the new result is delivered we know that it is no longer in use.
        if (oldRoutes != null) {
            onReleaseResources(oldRoutes);
        }
    }

    /**
     * Handles a request to start the Loader.
     */
    @Override
    protected void onStartLoading()
    {
        if (mRoutes != null) {
            // If we currently have a result available, deliver it
            // immediately.
            deliverResult(mRoutes);
        }

        // Start watching for changes in the routes.
        if (null != mMap) {
            mMap.addListener(this);
        }

        if (takeContentChanged() || mRoutes == null) {
            // If the data has changed since the last time it was loaded
            // or is not currently available, start a load.
            forceLoad();
        }
    }

    /**
     * Handles a request to stop the Loader.
     */
    @Override
    protected void onStopLoading()
    {
        // Attempt to cancel the current load task if possible.
        cancelLoad();
    }

    /**
     * Handles a request to cancel a load.
     */
    @Override
    public void onCanceled(List<String> routes)
    {
        super.onCanceled(routes);

        // At this point we can release the resources associated with 'routes' if needed.
        onReleaseResources(routes);
    }

    /**
     * Handles a request to completely reset the Loader.
     */
    @Override
    protected void onReset()
    {
        super.onReset();

        // Ensure the loader is stopped
        onStopLoading();

        // At this point we can release the resources associated with 'mRoutes'  if needed.
        if (mRoutes != null) {
            onReleaseResources(mRoutes);
            mRoutes = null;
        }

        // Stop monitoring for changes.
        if (null != mMap) {
            mMap.removeListener(this);
        }
    }

    /**
     * Helper function to take care of releasing resources associated with an actively loaded data
     * set.
     */
    protected void onReleaseResources(List<String> routes)
    {
        if (null != routes) {
            routes.clear();
        }
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
        //work only on routes layer
        if (id == mRoutesLayerId) {
            // Tell the loader about the change.
            onContentChanged();
        }
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
