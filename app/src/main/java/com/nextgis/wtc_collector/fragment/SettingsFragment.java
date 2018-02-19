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

package com.nextgis.wtc_collector.fragment;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteException;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.EditTextPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.preference.PreferenceScreen;
import android.widget.Toast;
import com.nextgis.maplib.api.ILayer;
import com.nextgis.maplib.map.MapBase;
import com.nextgis.maplib.map.MapContentProviderHelper;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.SettingsConstants;
import com.nextgis.maplibui.fragment.NGPreferenceSettingsFragment;
import com.nextgis.maplibui.service.TrackerService;
import com.nextgis.maplibui.util.SettingsConstantsUI;
import com.nextgis.wtc_collector.MainApplication;
import com.nextgis.wtc_collector.R;
import com.nextgis.wtc_collector.service.WtcTrackerService;
import com.nextgis.wtc_collector.util.AppSettingsConstants;
import io.sentry.Sentry;

import java.io.File;


public class SettingsFragment
        extends NGPreferenceSettingsFragment
{
    @Override
    protected void createPreferences(PreferenceScreen screen)
    {
        switch (mAction) {
            case SettingsConstantsUI.ACTION_PREFS_GENERAL:
                addPreferencesFromResource(R.xml.preferences_general);

                final Preference change_user_name =
                        findPreference(AppSettingsConstants.KEY_PREF_CHANGE_NAME);
                initializeChangeUserName(getActivity(), change_user_name);

                final Preference reset =
                        findPreference(SettingsConstantsUI.KEY_PREF_RESET_SETTINGS);
                initializeReset(getActivity(), reset);
                break;
            case SettingsConstantsUI.ACTION_PREFS_LOCATION:
                addPreferencesFromResource(R.xml.preferences_location);

                final ListPreference lpLocationAccuracy =
                        (ListPreference) findPreference(SettingsConstants.KEY_PREF_LOCATION_SOURCE);
                initializeLocationAccuracy(lpLocationAccuracy, false);

                final ListPreference minTimeLoc = (ListPreference) findPreference(
                        SettingsConstants.KEY_PREF_LOCATION_MIN_TIME);
                final ListPreference minDistanceLoc = (ListPreference) findPreference(
                        SettingsConstants.KEY_PREF_LOCATION_MIN_DISTANCE);
                initializeLocationMins(minTimeLoc, minDistanceLoc, false);

                final EditTextPreference accurateMaxCount = (EditTextPreference) findPreference(
                        SettingsConstants.KEY_PREF_LOCATION_ACCURATE_COUNT);
                initializeAccurateTaking(accurateMaxCount);
                break;
//            case SettingsConstantsUI.ACTION_PREFS_TRACKING:
//                addPreferencesFromResource(R.xml.preferences_tracks);
//
//                final ListPreference lpTracksAccuracy =
//                        (ListPreference) findPreference(SettingsConstants.KEY_PREF_TRACKS_SOURCE);
//                initializeLocationAccuracy(lpTracksAccuracy, true);
//
//                final ListPreference minTime =
//                        (ListPreference) findPreference(SettingsConstants.KEY_PREF_TRACKS_MIN_TIME);
//                final ListPreference minDistance = (ListPreference) findPreference(
//                        SettingsConstants.KEY_PREF_TRACKS_MIN_DISTANCE);
//                initializeLocationMins(minTime, minDistance, true);
//                break;
        }
    }

    public static void initializeChangeUserName(
            final Activity activity,
            final Preference preference)
    {
        if (null != preference) {
            preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
            {
                @Override
                public boolean onPreferenceClick(Preference preference)
                {
                    MainApplication app = (MainApplication) activity.getApplication();
                    boolean isChanges = app.getMap().isChanges();
                    int messageId = isChanges
                                    ? R.string.change_name_message_not_sync
                                    : R.string.change_name_message;

                    AlertDialog.Builder confirm = new AlertDialog.Builder(activity);
                    confirm.setTitle(R.string.change_name_title)
                            .setMessage(messageId)
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(android.R.string.ok,
                                    new DialogInterface.OnClickListener()
                                    {
                                        @Override
                                        public void onClick(
                                                DialogInterface dialog,
                                                int which)
                                        {
                                            stopWtcTrackerService(activity);
                                            resetUserName(activity);
                                            deleteMap(activity);
                                            activity.finish();
                                        }
                                    })
                            .show();
                    return false;
                }
            });
        }
    }

    protected static void stopWtcTrackerService(final Activity activity)
    {
        boolean isWtcTrackerRunning =
                WtcTrackerService.isTrackerServiceRunning(activity.getApplication());
        if (isWtcTrackerRunning) {
            Intent intent = new Intent(activity, WtcTrackerService.class);
            intent.setAction(WtcTrackerService.TRACKER_ACTION_STOP);
            activity.startService(intent);

            while (WtcTrackerService.isTrackerServiceRunning(activity.getApplication())) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignore) {
                }
            }
        }
    }

    protected static void deleteMap(final Activity activity)
    {
        MainApplication app = (MainApplication) activity.getApplication();
        MapBase map = app.getMap();
        map.delete();
    }

    protected static void resetUserName(Activity activity)
    {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove(AppSettingsConstants.KEY_PREF_USER_NAME);
        editor.putBoolean(AppSettingsConstants.KEY_PREF_USER_NAME_CLEARED, true);
        editor.apply();
    }

    public static void initializeReset(
            final Activity activity,
            final Preference preference)
    {
        if (null != preference) {
            preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
            {
                @Override
                public boolean onPreferenceClick(Preference preference)
                {
                    AlertDialog.Builder confirm = new AlertDialog.Builder(activity);
                    confirm.setTitle(R.string.reset_settings_title)
                            .setMessage(R.string.reset_settings_message)
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(android.R.string.ok,
                                    new DialogInterface.OnClickListener()
                                    {
                                        @Override
                                        public void onClick(
                                                DialogInterface dialog,
                                                int which)
                                        {
                                            resetSettings(activity);
                                            deleteLayers(activity);
                                        }
                                    })
                            .show();
                    return false;
                }
            });
        }
    }

    public static void initializeLocationAccuracy(
            final ListPreference listPreference,
            final boolean isTracks)
    {
        if (listPreference != null) {
            Context ctx = listPreference.getContext();
            CharSequence[] entries = new CharSequence[3];
            entries[0] = ctx.getString(R.string.pref_location_accuracy_gps);
            entries[1] = ctx.getString(R.string.pref_location_accuracy_cell);
            entries[2] = ctx.getString(R.string.pref_location_accuracy_gps) + " & " + ctx.getString(
                    R.string.pref_location_accuracy_cell);
            listPreference.setEntries(entries);
            listPreference.setSummary(listPreference.getEntry());

            listPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
            {
                @Override
                public boolean onPreferenceChange(
                        Preference preference,
                        Object newValue)
                {
                    int value = Integer.parseInt(newValue.toString());
                    CharSequence summary = ((ListPreference) preference).getEntries()[value - 1];
                    preference.setSummary(summary);

                    sectionWork(preference.getContext(), isTracks);

                    return true;
                }
            });
        }
    }

    public static void initializeLocationMins(
            ListPreference minTime,
            final ListPreference minDistance,
            final boolean isTracks)
    {
        final Context context = minDistance.getContext();
        minTime.setSummary(getMinSummary(context, minTime.getEntry(), minTime.getValue()));
        minDistance.setSummary(
                getMinSummary(context, minDistance.getEntry(), minDistance.getValue()));

        minTime.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
        {
            @Override
            public boolean onPreferenceChange(
                    Preference preference,
                    Object newValue)
            {
                int id = ((ListPreference) preference).findIndexOfValue((String) newValue);
                preference.setSummary(
                        getMinSummary(context, ((ListPreference) preference).getEntries()[id],
                                (String) newValue));

                String preferenceKey = isTracks
                                       ? SettingsConstants.KEY_PREF_TRACKS_MIN_TIME
                                       : SettingsConstants.KEY_PREF_LOCATION_MIN_TIME;
                preference.getSharedPreferences()
                        .edit()
                        .putString(preferenceKey, (String) newValue)
                        .apply();

                sectionWork(preference.getContext(), isTracks);

                return true;
            }
        });

        minDistance.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
        {
            @Override
            public boolean onPreferenceChange(
                    Preference preference,
                    Object newValue)
            {
                int id = ((ListPreference) preference).findIndexOfValue((String) newValue);
                preference.setSummary(
                        getMinSummary(context, ((ListPreference) preference).getEntries()[id],
                                (String) newValue));

                String preferenceKey = isTracks
                                       ? SettingsConstants.KEY_PREF_TRACKS_MIN_DISTANCE
                                       : SettingsConstants.KEY_PREF_LOCATION_MIN_DISTANCE;
                preference.getSharedPreferences()
                        .edit()
                        .putString(preferenceKey, (String) newValue)
                        .apply();

                sectionWork(preference.getContext(), isTracks);

                return true;
            }
        });
    }

    public static void initializeAccurateTaking(EditTextPreference accurateMaxCount)
    {
        accurateMaxCount.setSummary(accurateMaxCount.getText());

        accurateMaxCount.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
        {
            @Override
            public boolean onPreferenceChange(
                    Preference preference,
                    Object newValue)
            {
                preference.setSummary((CharSequence) newValue);
                return true;
            }
        });
    }

    private static String getMinSummary(
            Context context,
            CharSequence newEntry,
            String newValue)
    {
        int value = 0;

        try {
            value = Integer.parseInt(newValue);
        } catch (NumberFormatException e) {
            if (Constants.DEBUG_MODE) {
                e.printStackTrace();
                Sentry.capture(e);
            }
        }

        String addition = newEntry + "";
        addition += value == 0 ? context.getString(R.string.frequentest) : "";

        return addition;
    }

    protected static void sectionWork(
            Context context,
            boolean isTracks)
    {
        if (!isTracks) {
            MainApplication application = (MainApplication) context.getApplicationContext();
            application.getGpsEventSource().updateActiveListeners();
        } else {
            if (TrackerService.isTrackerServiceRunning(context)) {
                Toast.makeText(
                        context, context.getString(R.string.tracks_reload), Toast.LENGTH_SHORT)
                        .show();
            }
        }
    }

    protected static void resetSettings(Activity activity)
    {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove(SettingsConstantsUI.KEY_PREF_THEME);
        editor.remove(SettingsConstantsUI.KEY_PREF_COMPASS_TRUE_NORTH);
        editor.remove(SettingsConstantsUI.KEY_PREF_COMPASS_MAGNETIC);
        editor.remove(SettingsConstantsUI.KEY_PREF_COMPASS_KEEP_SCREEN);
        editor.remove(SettingsConstantsUI.KEY_PREF_COMPASS_VIBRATE);
        editor.remove(SettingsConstantsUI.KEY_PREF_SHOW_STATUS_PANEL);
        editor.remove(SettingsConstantsUI.KEY_PREF_SHOW_CURRENT_LOC);
        editor.remove(AppSettingsConstants.KEY_PREF_SHOW_COMPASS);
        editor.remove(SettingsConstantsUI.KEY_PREF_KEEPSCREENON);
        editor.remove(SettingsConstantsUI.KEY_PREF_COORD_FORMAT);
        editor.remove(AppSettingsConstants.KEY_PREF_SHOW_ZOOM_CONTROLS);
        editor.remove(SettingsConstants.KEY_PREF_LOCATION_SOURCE);
        editor.remove(SettingsConstants.KEY_PREF_LOCATION_MIN_TIME);
        editor.remove(SettingsConstants.KEY_PREF_LOCATION_MIN_DISTANCE);
        editor.remove(SettingsConstants.KEY_PREF_LOCATION_ACCURATE_COUNT);
        editor.remove(SettingsConstants.KEY_PREF_TRACKS_SOURCE);
        editor.remove(SettingsConstants.KEY_PREF_TRACKS_MIN_TIME);
        editor.remove(SettingsConstants.KEY_PREF_TRACKS_MIN_DISTANCE);
        editor.remove(SettingsConstants.KEY_PREF_TRACK_RESTORE);
        editor.remove(AppSettingsConstants.KEY_PREF_SHOW_MEASURING);
        editor.remove(AppSettingsConstants.KEY_PREF_SHOW_SCALE_RULER);
        editor.remove(SettingsConstantsUI.KEY_PREF_SHOW_GEO_DIALOG);
        editor.remove(AppSettingsConstants.KEY_PREF_GA);

        editor.remove(AppSettingsConstants.KEY_PREF_RIGHT_LEFT);
        editor.remove(AppSettingsConstants.KEY_PREF_USER_NAME);
        editor.remove(AppSettingsConstants.KEY_PREF_USER_NAME_CLEARED);
        editor.remove(AppSettingsConstants.KEY_PREF_REFRESH_VIEW);
        editor.remove(AppSettingsConstants.KEY_PREF_LAST_LOCATION_TIME);
        editor.remove(AppSettingsConstants.KEY_PREF_POINT_CREATE_CONFIRM);
        editor.remove(AppSettingsConstants.KEY_PREF_ENTER_POINT_COUNT);

        File defaultPath = activity.getExternalFilesDir(SettingsConstants.KEY_PREF_MAP);
        if (defaultPath == null) {
            defaultPath = new File(activity.getFilesDir(), SettingsConstants.KEY_PREF_MAP);
        }

        editor.putString(SettingsConstants.KEY_PREF_MAP_PATH, defaultPath.getPath());
        editor.apply();

        PreferenceManager.setDefaultValues(activity, R.xml.preferences_general, true);
//        PreferenceManager.setDefaultValues(activity, R.xml.preferences_map, true);
        PreferenceManager.setDefaultValues(activity, R.xml.preferences_location, true);
//        PreferenceManager.setDefaultValues(activity, R.xml.preferences_tracks, true);
    }

    protected static void deleteLayers(Activity activity)
    {
        MainApplication app = (MainApplication) activity.getApplication();
        for (int i = app.getMap().getLayerCount() - 1; i >= 0; i--) {
            ILayer layer = app.getMap().getLayer(i);
            if (!layer.getPath().getName().equals(MainApplication.LAYER_TRACKS)) {
                layer.delete();
            }
        }

        try {
            ((MapContentProviderHelper) MapBase.getInstance()).getDatabase(false).execSQL("VACUUM");
        } catch (SQLiteException e) {
            if (Constants.DEBUG_MODE) {
                e.printStackTrace();
                Sentry.capture(e);
            }
        }
    }
}
