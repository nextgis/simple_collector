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

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.Loader;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplibui.activity.NGWLoginActivity;
import com.nextgis.maplibui.fragment.NGWLoginFragment;
import com.nextgis.simple_collector.MainApplication;
import com.nextgis.simple_collector.R;
import com.nextgis.simple_collector.service.HTTPUserListLoader;
import com.nextgis.simple_collector.util.AppConstants;
import io.sentry.Sentry;


public class ServerCheckerFragment
        extends NGWLoginFragment
{
    protected static int MSG_FOUND_USER = 0;
    protected static int MSG_NOT_FOUND_USER = 1;

    protected Handler mHandler;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        mHandler = new Handler(Looper.getMainLooper())
        {
            @Override
            public void handleMessage(Message msg)
            {
                getLoaderManager().destroyLoader(R.id.user_list_loader);
                mLoader = null;

                if (msg.what == MSG_FOUND_USER) {
                    showNext();
                } else if (msg.what == MSG_NOT_FOUND_USER) {
                    showError();
                }
            }
        };
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            @Nullable
                    ViewGroup container,
            @Nullable
                    Bundle savedInstanceState)
    {
        final View view = inflater.inflate(R.layout.fragment_server_checker, container, false);
        mURL = (EditText) view.findViewById(R.id.url);
        mSignInButton = (Button) view.findViewById(R.id.next);

        TextWatcher watcher = new WtcTextWatcher();
        mURL.addTextChangedListener(watcher);

        TextView loginDescription = (TextView) view.findViewById(R.id.server_description);
        Drawable addition = getResources().getDrawable(R.drawable.nextgis_addition);
        mURL.setCompoundDrawablesWithIntrinsicBounds(null, null, addition, null);

        mURL.setFocusableInTouchMode(true);
        mURL.requestFocus();

        loginDescription.setText(R.string.server_description);

        // For debug
//        mURL.setText("test");

        return view;
    }

    @Override
    public void onClick(View v)
    {
        if (v != mSignInButton) {
            return;
        }

        if (!mUrlText.contains(ENDING)) {
            mUrlText += ENDING;
        }

        mSignInButton.setEnabled(false);

        if (null != mLoader && mLoader.isStarted()) {
            mLoader = getLoaderManager().restartLoader(R.id.user_list_loader, null, this);
        } else {
            mLoader = getLoaderManager().initLoader(R.id.user_list_loader, null, this);
        }
    }

    protected void checkServer(String userList)
    {
        String splitter = "\n";
        if (userList.contains("\r\n")) {
            splitter = "\r\n";
        } else if (userList.contains("\n")) {
            splitter = "\n";
        } else if (userList.contains("\r")) {
            splitter = "\r";
        }
        String[] list = userList.split(splitter);

        boolean found = false;
        for (String validName : list) {
            if (mUrlText.startsWith(validName + ".")) {
                found = true;
                break;
            }
        }

        if (Constants.DEBUG_MODE && !found) {
            String msg = "ServerCheckerFragment.checkServer() das not found user in list.";
            Log.d(AppConstants.APP_TAG, msg);
            Sentry.capture(msg);
        }

        mHandler.sendEmptyMessage(found ? MSG_FOUND_USER : MSG_NOT_FOUND_USER);
    }

    protected void showNext()
    {
        MainApplication app = (MainApplication) getActivity().getApplication();
        FragmentManager fm = getFragmentManager();
        LoginFragment loginFragment = (LoginFragment) fm.findFragmentByTag("LoginFragment");
        if (loginFragment == null) {
            loginFragment = new LoginFragment();
            loginFragment.setOnAddAccountListener(app);

            Bundle args = new Bundle();
            args.putString(NGWLoginActivity.ACCOUNT_URL_TEXT, mUrlText);
            loginFragment.setArguments(args);

            FragmentTransaction ft = fm.beginTransaction();
            ft.replace(com.nextgis.maplibui.R.id.login_frame, loginFragment, "LoginFragment");
            ft.addToBackStack(null);
            ft.commit();
        }
    }

    protected void showError()
    {
        Context context = getContext();
        View messageView = View.inflate(context, R.layout.message_invalid_ngw_name, null);
        AlertDialog.Builder confirm = new AlertDialog.Builder(context);
        confirm.setView(messageView).setPositiveButton(android.R.string.ok, null).show();
    }

    @Override
    public Loader<String> onCreateLoader(
            int id,
            Bundle args)
    {
        return new HTTPUserListLoader(getActivity().getApplicationContext(),
                AppConstants.VALID_NGW_NAMES_URL, "", "");
    }

    @Override
    public void onLoadFinished(
            Loader<String> loader,
            String userList)
    {
        mSignInButton.setEnabled(true);

        if (loader.getId() == R.id.user_list_loader) {
            if (!TextUtils.isEmpty(userList)) {
                checkServer(userList);
            } else {
                Toast.makeText(getActivity(), R.string.error_connect_failed, Toast.LENGTH_SHORT)
                        .show();
            }
        }
    }

    @Override
    public void onLoaderReset(Loader<String> loader)
    {
        // Do nothing.
    }

    protected void updateButtonState()
    {
        if (checkEditText(mURL)) {
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
