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

package com.nextgis.simple_collector.util;

public interface AppConstants
{
    String APP_TAG = "Simple Collector";

    String VALID_NGW_NAMES_URL = "http://nextgis.ru/programs/wtc.ini";

    int LAYERTYPE_WTC_NGW_VECTOR = 1 << 16;

    /**
     * init sync status
     */
    String INIT_SYNC_BROADCAST_MESSAGE = "simple.collector.init_sync_message";
    String SYNC_BROADCAST_MESSAGE      = "simple.collector.sync_message";

    String KEY_STEP       = "sync_step";
    String KEY_STEP_COUNT = "sync_step_count";
    String KEY_STATE      = "sync_state";
    String KEY_MESSAGE    = "sync_message";

    int STEP_STATE_WAIT       = 0;
    int STEP_STATE_WORK       = 1;
    int STEP_STATE_DONE       = 2;
    int STEP_STATE_FINISH     = 3;
    int STEP_STATE_ERROR      = 4;
    int STEP_STATE_CANCEL     = 5;
    int STEP_STATE_NONE_ROOT  = 6;
    int STEP_STATE_ERROR_ROOT = 7;

    /**
     * Fragments tags
     */
    String FRAGMENT_SETTINGS_HEADER_FRAGMENT = "settings_header_fragment";
    String FRAGMENT_SETTINGS_FRAGMENT        = "settings_fragment";

    int DEFAULT_COORDINATES_FRACTION_DIGITS = 6;

    String KEY_ROOT_RESOURCE_GROUP = "sc_root";

    String KEY_DATA    = "sc_data";
    String KEY_TRACKS  = "sc_tracks";
    String KEY_ROUTES  = "sc_routes";
    String KEY_PEOPLE  = "sc_people";
    String KEY_OBJECTS = "sc_objects";

    String KEY_LAYER_DATA    = "data";
    String KEY_LAYER_TRACKS  = "tracks";
    String KEY_LAYER_ROUTES  = "routes";
    String KEY_LAYER_PEOPLE  = "people";
    String KEY_LAYER_OBJECTS = "objects";

    String FIELD_DATA_GUID      = "GUID"; // STRING
    String FIELD_DATA_LAT       = "LAT"; // REAL
    String FIELD_DATA_LON       = "LON"; // REAL
    String FIELD_DATA_SIDE      = "SIDE"; // STRING
    String FIELD_DATA_ROUTE     = "ROUTE"; // STRING
    String FIELD_DATA_DATE      = "DATE"; // DATE
    String FIELD_DATA_TIME      = "TIME"; // TIME
    String FIELD_DATA_SPECIES   = "SPECIES"; // STRING
    String FIELD_DATA_CNT       = "CNT"; // INTEGER
    String FIELD_DATA_COLLECTOR = "COLLECTOR"; // STRING

    String FIELD_TRACKS_LAT       = "LAT"; // REAL
    String FIELD_TRACKS_LON       = "LON"; // REAL
    String FIELD_TRACKS_TIMESTAMP = "TIMESTAMP"; // DATETIME
    String FIELD_TRACKS_STATUS    = "STATUS"; // STRING
    String FIELD_TRACKS_ROUTE     = "ROUTE"; // STRING
    String FIELD_TRACKS_COLLECTOR = "COLLECTOR"; // STRING

    String FIELD_ROUTES_NAME = "Name"; // STRING

    String TRACK_STATUS_START  = "START";
    String TRACK_STATUS_FINISH = "FINISH";

    int ROUTES_LOADER = 0;
}
