/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.user

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import com.datadog.android.Datadog
import com.datadog.android.sample.Preferences
import com.datadog.android.sample.R
import com.datadog.android.trace.logAttributes
import com.datadog.android.trace.withinSpan
import com.google.android.material.snackbar.Snackbar

internal class UserFragment : Fragment(), View.OnClickListener {

    lateinit var idField: EditText
    lateinit var nameField: EditText
    lateinit var emailField: EditText
    lateinit var userGenderField: EditText
    lateinit var userAgeField: EditText

    // region Fragment

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val rootView: View = inflater.inflate(R.layout.fragment_user, container, false)
        idField = rootView.findViewById(R.id.user_id)
        nameField = rootView.findViewById(R.id.user_name)
        emailField = rootView.findViewById(R.id.user_email)
        userGenderField = rootView.findViewById(R.id.user_gender)
        userAgeField = rootView.findViewById(R.id.user_age)
        rootView.findViewById<View>(R.id.save_user).setOnClickListener(this)
        return rootView
    }

    override fun onResume() {
        super.onResume()
        val preferences = Preferences.defaultPreferences(requireContext())
        idField.setText(preferences.getUserId())
        nameField.setText(preferences.getUserName())
        emailField.setText(preferences.getUserEmail())
        userGenderField.setText(preferences.getUserGender())
        userAgeField.setText(preferences.getUserAge().toString())
    }

    // endregion

    // region View.OnClickListener

    override fun onClick(v: View) {
        withinSpan("updateUserInfo") {
            val id: String = idField.text.toString()
            val name: String = nameField.text.toString()
            val email: String = emailField.text.toString()
            val gender: String = userGenderField.text.toString()
            val age: Int = Integer.valueOf(userAgeField.text.toString())
            Preferences.defaultPreferences(requireContext())
                .setUserCredentials(id, name, email, gender, age)
            Datadog.setUserInfo(id, name, email, emptyMap())
            Datadog.addUserProperties(
                mapOf<String, Any>(
                    GENDER_KEY to gender,
                    AGE_KEY to age
                )
            )
            logAttributes("Updated user info")
        }
        Snackbar.make(view ?: v.rootView, "User info updated", Snackbar.LENGTH_SHORT).show()
    }

    // endregion

    companion object {
        internal const val GENDER_KEY = "gender"
        internal const val AGE_KEY = "age"
    }
}
