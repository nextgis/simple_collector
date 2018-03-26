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

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import com.nextgis.maplibui.activity.NGWLoginActivity;
import com.nextgis.maplibui.fragment.NGWLoginFragment;
import com.nextgis.simple_collector.R;


public class LoginFragment
        extends NGWLoginFragment
{
    @Override
    public View onCreateView(
            LayoutInflater inflater,
            @Nullable
                    ViewGroup container,
            @Nullable
                    Bundle savedInstanceState)
    {
        final View view = inflater.inflate(R.layout.fragment_login, container, false);
        mURL = (EditText) view.findViewById(R.id.url);
        mLogin = (EditText) view.findViewById(R.id.login);
        mPassword = (EditText) view.findViewById(R.id.password);
        mSignInButton = (Button) view.findViewById(R.id.signin);

        TextWatcher watcher = new WtcTextWatcher();
        mLogin.addTextChangedListener(watcher);
        mPassword.addTextChangedListener(watcher);

        TextView loginDescription = (TextView) view.findViewById(R.id.login_description);

        if (mForNewAccount) {
            loginDescription.setText(R.string.login_description);
            mURL.setEnabled(false);

            Bundle args = getArguments();
            if (args != null) {
                mUrlText = args.getString(NGWLoginActivity.ACCOUNT_URL_TEXT);
                if (TextUtils.isEmpty(mUrlText)) {
                    mUrlText = "";
                }
            }

            mLogin.setFocusableInTouchMode(true);
            mLogin.requestFocus();

        } else {
            loginDescription.setText(R.string.edit_pass_description);
            mURL.setEnabled(mChangeAccountUrl);
            mLogin.setEnabled(mChangeAccountLogin);
        }

        mURL.setText(mUrlText);

        if (!mForNewAccount) {
            mLogin.setText(mLoginText); // change mUrlText in WtcTextWatcher
        }

        // For debug
//        if (mForNewAccount) {
//            mLogin.setText("test"); // change mUrlText in WtcTextWatcher
//            mPassword.setText("test");
//        }

        return view;
    }

    @Override
    public void onClick(View v)
    {
        if (v != mSignInButton) {
            return;
        }

        if (mForNewAccount) {
            mLogin.setText(mLogin.getText().toString().trim()); // change mUrlText in WtcTextWatcher
        }

        if (!mUrlText.contains(ENDING)) {
            mUrlText += ENDING;
        }

        mSignInButton.setEnabled(false);

        if (null != mLoader && mLoader.isStarted()) {
            mLoader = getLoaderManager().restartLoader(R.id.auth_token_loader, null, this);
        } else {
            mLoader = getLoaderManager().initLoader(R.id.auth_token_loader, null, this);
        }
    }

    @Override
    public void onTokenReceived(
            String accountName,
            String token)
    {
        accountName = getString(R.string.account_name);
        super.onTokenReceived(accountName, token);
    }

    protected void updateButtonState()
    {
        if (checkEditText(mURL) && checkEditText(mLogin) && checkEditText(mPassword)) {
            mSignInButton.setEnabled(true);
        } else {
            mSignInButton.setEnabled(false);
        }
    }

    public class WtcTextWatcher
            implements TextWatcher
    {
        public void afterTextChanged(Editable s)
        {
            updateButtonState();
            mUrlText = mURL.getText().toString().trim();
        }

        public void beforeTextChanged(
                CharSequence s,
                int start,
                int count,
                int after)
        {
        }

        public void onTextChanged(
                CharSequence s,
                int start,
                int before,
                int count)
        {
        }
    }
}
