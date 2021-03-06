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

package com.nextgis.simple_collector.fragment;

import android.support.v7.preference.PreferenceScreen;
import com.nextgis.maplibui.fragment.NGPreferenceHeaderFragment;
import com.nextgis.simple_collector.R;


public class SettingsHeaderFragment
        extends NGPreferenceHeaderFragment
{
    @Override
    protected void createPreferences(PreferenceScreen screen)
    {
        addPreferencesFromResource(R.xml.preference_headers);
    }
}
