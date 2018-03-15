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

package com.nextgis.simple_collector.datasource;

import android.accounts.Account;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import com.nextgis.maplib.datasource.ngw.SyncAdapter;
import com.nextgis.maplibui.util.NotificationHelper;
import com.nextgis.simple_collector.R;
import com.nextgis.simple_collector.activity.MainActivity;
import com.nextgis.simple_collector.util.AppConstants;
import com.nextgis.simple_collector.util.AppSettingsConstants;


public class WtcSyncAdapter
        extends SyncAdapter
{
    private static final int NOTIFICATION_ID = 517;

    public WtcSyncAdapter(
            Context context,
            boolean autoInitialize)
    {
        super(context, autoInitialize);
    }

    public WtcSyncAdapter(
            Context context,
            boolean autoInitialize,
            boolean allowParallelSyncs)
    {
        super(context, autoInitialize, allowParallelSyncs);
    }

    @Override
    public void onPerformSync(
            Account account,
            Bundle bundle,
            String authority,
            ContentProviderClient contentProviderClient,
            SyncResult syncResult)
    {
        sendNotification(getContext(), SYNC_START, null);

        super.onPerformSync(account, bundle, authority, contentProviderClient, syncResult);

        if (isCanceled()) {
            sendNotification(getContext(), SYNC_CANCELED, null);
        } else if (syncResult.hasError()) {
            sendNotification(getContext(), SYNC_CHANGES, mError);
        } else {
            sendNotification(getContext(), SYNC_FINISH, null);

            boolean manual = bundle.getBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, false);
            boolean expedited = bundle.getBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, false);
            if (manual && expedited) {
                Intent intent = new Intent(AppConstants.SYNC_BROADCAST_MESSAGE);
                intent.putExtra(AppConstants.KEY_STATE, SYNC_FINISH);
                getContext().sendBroadcast(intent);
            }
        }
    }

    public void sendNotification(
            Context context,
            String notificationType,
            String message)
    {
        if (!PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(AppSettingsConstants.KEY_PREF_SHOW_SYNC, false)) {
            return;
        }

        Intent notificationIntent = new Intent(context, MainActivity.class);
        notificationIntent.setFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent,
                PendingIntent.FLAG_CANCEL_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        builder.setSmallIcon(R.drawable.ic_action_sync)
                .setWhen(System.currentTimeMillis())
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setOngoing(false);

        Bitmap largeIcon =
                NotificationHelper.getLargeIcon(R.drawable.ic_action_sync, context.getResources());
        switch (notificationType) {
            case SYNC_START:
                largeIcon = NotificationHelper.getLargeIcon(R.drawable.ic_next_dark,
                        context.getResources());
                builder.setProgress(0, 0, true)
                        .setTicker(context.getString(R.string.sync_started))
                        .setContentTitle(context.getString(R.string.synchronization))
                        .setContentText(context.getString(R.string.sync_progress));
                break;

            case SYNC_FINISH:
                largeIcon = NotificationHelper.getLargeIcon(R.drawable.ic_action_apply_dark,
                        context.getResources());
                builder.setProgress(0, 0, false)
                        .setTicker(context.getString(R.string.sync_finished))
                        .setContentTitle(context.getString(R.string.synchronization))
                        .setContentText(context.getString(R.string.sync_finished));
                break;

            case SYNC_CANCELED:
                largeIcon = NotificationHelper.getLargeIcon(R.drawable.ic_action_cancel_dark,
                        context.getResources());
                builder.setProgress(0, 0, false)
                        .setTicker(context.getString(R.string.sync_canceled))
                        .setContentTitle(context.getString(R.string.synchronization))
                        .setContentText(context.getString(R.string.sync_canceled));
                break;

            case SYNC_CHANGES:
                largeIcon = NotificationHelper.getLargeIcon(R.drawable.ic_action_warning_dark,
                        context.getResources());
                builder.setProgress(0, 0, false)
                        .setTicker(context.getString(R.string.sync_error))
                        .setContentTitle(context.getString(R.string.synchronization))
                        .setContentText(message);
                break;
        }

        builder.setLargeIcon(largeIcon);
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }
}
