/*
 * Project:  Simple Collector
 * Purpose:  Mobile application for simple data collection.
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

package com.nextgis.simple_collector.service;

import android.content.Context;
import android.support.v4.content.AsyncTaskLoader;
import android.util.Log;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.HttpResponse;
import com.nextgis.maplib.util.NetworkUtil;
import com.nextgis.simple_collector.util.AppConstants;
import io.sentry.Sentry;

import java.io.IOException;


// See example in
// http://developer.android.com/reference/android/content/AsyncTaskLoader.html
public class HTTPUserListLoader
        extends AsyncTaskLoader<String>
{
    protected final String mUrl;
    protected final String mLogin;
    protected final String mPassword;

    protected String mUserList;

    public HTTPUserListLoader(
            Context context,
            String url,
            String login,
            String password)
    {
        super(context);

        mUrl = url;
        mLogin = login;
        mPassword = password;
    }

    /**
     * This is where the bulk of our work is done.  This function is called in a background thread
     * and should generate a new set of data to be published by the loader.
     */
    @Override
    public String loadInBackground()
    {
        return getUserList();
    }

    protected String getUserList()
    {
        try {
            HttpResponse response = NetworkUtil.get(mUrl, mLogin, mPassword, false);
            if (response.isOk()) {
                return response.getResponseBody();
            }
        } catch (IOException e) {
            if (Constants.DEBUG_MODE) {
                String msg = e.getMessage();
                Log.e(AppConstants.APP_TAG, msg);
                Sentry.capture(msg);
            }
        }
        if (Constants.DEBUG_MODE) {
            String msg = "HTTPUserListLoader.getUserList() returns empty user list.";
            Log.d(AppConstants.APP_TAG, msg);
            Sentry.capture(msg);
        }
        return "";
    }

    /**
     * Called when there is new data to deliver to the client.  The super class will take care of
     * delivering it; the implementation here just adds a little more logic.
     */
    @Override
    public void deliverResult(String userList)
    {
        if (isReset()) {
            // An async query came in while the loader is stopped.  We don't need the result.
            onReleaseResources(userList);
        }

        String oldUserList = null;
        if (!userList.equals(mUserList)) {
            oldUserList = mUserList;
            mUserList = userList;
        }

        if (isStarted()) {
            // If the Loader is currently started, we can immediately deliver its results.
            super.deliverResult(userList);
        }

        // At this point we can release the resources associated with 'oldUserList' if needed;
        // now that the new result is delivered we know that it is no longer in use.
        onReleaseResources(oldUserList);
    }

    /**
     * Handles a request to start the Loader.
     */
    @Override
    protected void onStartLoading()
    {
//        if (mUserList != null) {
//            // If we currently have a result available, deliver it immediately.
//            deliverResult(mUserList);
//        }

        // Start watching for changes in the userList.
//        if (null != mMap) {
//            mMap.addListener(this);
//        }

        if (takeContentChanged() || mUserList == null) {
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
    public void onCanceled(String userList)
    {
        super.onCanceled(userList);

        // At this point we can release the resources associated with 'userList' if needed.
        onReleaseResources(userList);
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

        // At this point we can release the resources associated with 'mUserList'  if needed.
        if (mUserList != null) {
            onReleaseResources(mUserList);
            mUserList = null;
        }

        // Stop monitoring for changes.
//        if (null != mMap) {
//            mMap.removeListener(this);
//        }
    }

    /**
     * Helper function to take care of releasing resources associated with an actively loaded data
     * set.
     */
    protected void onReleaseResources(String userList)
    {
//        if (userList != null) {
//            userList.clear();
//        }
    }
}
