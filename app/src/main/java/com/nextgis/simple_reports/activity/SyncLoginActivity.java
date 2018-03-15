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

package com.nextgis.simple_reports.activity;

import android.accounts.Account;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import com.nextgis.maplibui.activity.NGWLoginActivity;
import com.nextgis.maplibui.fragment.NGWLoginFragment;
import com.nextgis.simple_reports.MainApplication;
import com.nextgis.simple_reports.R;
import com.nextgis.simple_reports.fragment.LoginFragment;


public class SyncLoginActivity
        extends NGWLoginActivity
{
    @Override
    protected NGWLoginFragment getNewLoginFragment()
    {
        return new LoginFragment();
    }


    @Override
    public void onAddAccount(
            Account account,
            String token,
            boolean accountAdded)
    {
        super.onAddAccount(account, token, accountAdded);
        MainApplication app = (MainApplication) getApplication();
        app.onAddAccount(account, token, accountAdded);
    }


    @Override
    protected void createView()
    {
        setContentView(R.layout.activity_main_first);

        setToolbar(R.id.main_toolbar);
        setTitle(getText(R.string.action_settings));

        FragmentManager fm = getSupportFragmentManager();
        NGWLoginFragment ngwLoginFragment = (NGWLoginFragment) fm.findFragmentByTag("SyncLogin");

        if (ngwLoginFragment == null) {
            ngwLoginFragment = getNewLoginFragment();
            ngwLoginFragment.setForNewAccount(mForNewAccount);
            ngwLoginFragment.setUrlText(mUrlText);
            ngwLoginFragment.setLoginText(mLoginText);
            ngwLoginFragment.setChangeAccountUrl(mChangeAccountUrl);
            ngwLoginFragment.setChangeAccountLogin(mChangeAccountLogin);

            ngwLoginFragment.setOnAddAccountListener(this);

            FragmentTransaction ft = fm.beginTransaction();
            ft.add(R.id.login_frame, ngwLoginFragment, "SyncLogin");
            ft.commit();
        }
    }
}
