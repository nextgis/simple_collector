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

package com.nextgis.simple_collector.activity;

import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;
import com.nextgis.maplibui.activity.NGActivity;
import com.nextgis.simple_collector.BuildConfig;
import com.nextgis.simple_collector.R;


public class AboutActivity
        extends NGActivity
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_about);
        setToolbar(R.id.main_toolbar);

        TextView txtVersion = (TextView) findViewById(R.id.app_version);
        txtVersion.setText(String.format(getString(R.string.version), BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE));

        TextView txtCopyrightText = (TextView) findViewById(R.id.copyright);
        txtCopyrightText.setText(Html.fromHtml(getString(R.string.copyright)));
        txtCopyrightText.setMovementMethod(LinkMovementMethod.getInstance());
    }
}
