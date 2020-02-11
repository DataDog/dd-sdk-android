/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.sample.user;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.datadog.android.Datadog;
import com.datadog.android.log.Logger;
import com.datadog.android.sample.R;
import com.datadog.android.sample.traces.TracesFragment;
import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;

public class UserFragment extends Fragment implements View.OnClickListener {

    public static final String TAG_DIALOG = "user_dialog";

    public static final String PREF_ID = "user-id";
    public static final String PREF_NAME = "user-name";
    public static final String PREF_EMAIL = "user-email";


    private Logger mLogger = new Logger.Builder()
            .setLoggerName("user_fragment")
            .setLogcatLogsEnabled(true)
            .build();

    public static UserFragment newInstance() {
        return new UserFragment();
    }

    private EditText mId;
    private EditText mName;
    private EditText mEmail;
    private View mSave;

    // region Fragment

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_user, container, false);

        mId = rootView.findViewById(R.id.user_id);
        mName = rootView.findViewById(R.id.user_name);
        mEmail = rootView.findViewById(R.id.user_email);
        mSave = rootView.findViewById(R.id.save_user);

        mSave.setOnClickListener(this);
        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        mId.setText(prefs.getString(PREF_ID, null));
        mName.setText(prefs.getString(PREF_NAME, null));
        mEmail.setText(prefs.getString(PREF_EMAIL, null));
    }

    // endregion

    // region View.OnClickListener

    @Override
    public void onClick(View v) {
        final Span span = GlobalTracer.get()
                .buildSpan("Saving User Info")
                .start();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        String id = mId.getText().toString();
        String name = mName.getText().toString();
        String email = mEmail.getText().toString();

        prefs.edit()
                .putString(PREF_ID, id)
                .putString(PREF_NAME, name)
                .putString(PREF_EMAIL, email)
                .apply();

        Datadog.setUserInfo(id, name, email);
        span.log("Updated user info");
        span.finish();
    }

    // endregion

}
