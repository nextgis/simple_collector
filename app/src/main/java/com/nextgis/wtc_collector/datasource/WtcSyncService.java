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

package com.nextgis.wtc_collector.datasource;

import android.content.Context;
import com.nextgis.maplib.service.NGWSyncService;


public class WtcSyncService
        extends NGWSyncService
{
    @Override
    protected WtcSyncAdapter createSyncAdapter(
            Context context,
            boolean autoInitialize)
    {
        return new WtcSyncAdapter(context, autoInitialize);
    }
}
