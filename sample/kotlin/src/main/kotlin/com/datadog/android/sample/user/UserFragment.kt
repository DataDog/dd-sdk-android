/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.user

import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import com.datadog.android.Datadog.setUserInfo
import com.datadog.android.ktx.tracing.withinSpan
import com.datadog.android.sample.R
import com.google.android.material.snackbar.Snackbar

class UserFragment : Fragment(), View.OnClickListener {

    lateinit var idField: EditText
    lateinit var nameField: EditText
    lateinit var emailField: EditText

    // region Fragment

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView: View = inflater.inflate(R.layout.fragment_user, container, false)
        idField = rootView.findViewById(R.id.user_id)
        nameField = rootView.findViewById(R.id.user_name)
        emailField = rootView.findViewById(R.id.user_email)
        rootView.findViewById<View>(R.id.save_user).setOnClickListener(this)
        return rootView
    }

    override fun onResume() {
        super.onResume()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        idField.setText(prefs.getString(PREF_ID, null))
        nameField.setText(prefs.getString(PREF_NAME, null))
        emailField.setText(prefs.getString(PREF_EMAIL, null))
    }

    // endregion

    // region View.OnClickListener

    override fun onClick(v: View) {
        withinSpan("updateUserInfo") {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val id: String = idField.text.toString()
            val name: String = nameField.text.toString()
            val email: String = emailField.text.toString()
            prefs.edit()
                .putString(PREF_ID, id)
                .putString(PREF_NAME, name)
                .putString(PREF_EMAIL, email)
                .apply()
            setUserInfo(id, name, email)
            log("Updated user info")
        }
        Snackbar.make(view ?: v.rootView, "User info updated", Snackbar.LENGTH_SHORT).show()
    }

    // endregion

    companion object {
        const val PREF_ID = "user-id"
        const val PREF_NAME = "user-name"
        const val PREF_EMAIL = "user-email"
    }
}
