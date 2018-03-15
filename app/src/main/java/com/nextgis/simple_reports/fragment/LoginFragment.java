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

package com.nextgis.simple_reports.fragment;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import com.nextgis.maplibui.fragment.NGWLoginFragment;
import com.nextgis.simple_reports.R;
import com.nextgis.simple_reports.util.AppConstants;


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
        mURL.addTextChangedListener(watcher);
        mLogin.addTextChangedListener(watcher);
        mPassword.addTextChangedListener(watcher);

        TextView loginDescription = (TextView) view.findViewById(R.id.login_description);
        Drawable addition = getResources().getDrawable(R.drawable.nextgis_addition);
        mURL.setCompoundDrawablesWithIntrinsicBounds(null, null, addition, null);

        if (mForNewAccount) {
            loginDescription.setText(R.string.login_description);

            // For debug
//            mURL.setText("test");
//            mLogin.setText("test");
//            mPassword.setText("test");

        } else {
            loginDescription.setText(R.string.edit_pass_description);

            if (mUrlText.endsWith(ENDING)) {
                mURL.setText(mUrlText.replace(ENDING, ""));
            } else {
                mURL.setText(mUrlText);
            }

            mLogin.setText(mLoginText);

            mURL.setEnabled(mChangeAccountUrl);
            mLogin.setEnabled(mChangeAccountLogin);
        }

        return view;
    }

    @Override
    public void onClick(View v)
    {
        if (mForNewAccount) {
            mLogin.setText(mLogin.getText().toString().trim()); // change mUrlText in WtcTextWatcher
        }

        if (!mUrlText.contains(ENDING)) {
            mUrlText += ENDING;
        }

        boolean found = false;
        for (String validName : AppConstants.VALID_NGW_NAMES) {
            if (mUrlText.startsWith(validName + ".")) {
                found = true;
                break;
            }
        }
        if (!found) {
            Context context = getContext();
            View messageView = View.inflate(context, R.layout.message_invalid_ngw_name, null);
            AlertDialog.Builder confirm = new AlertDialog.Builder(context);
            confirm.setView(messageView).setPositiveButton(android.R.string.ok, null).show();
            return;
        }

        if (v == mSignInButton) {
            if (null != mLoader && mLoader.isStarted()) {
                mLoader = getLoaderManager().restartLoader(R.id.auth_token_loader, null, this);
            } else {
                mLoader = getLoaderManager().initLoader(R.id.auth_token_loader, null, this);
            }
        }

        mSignInButton.setEnabled(false);
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
