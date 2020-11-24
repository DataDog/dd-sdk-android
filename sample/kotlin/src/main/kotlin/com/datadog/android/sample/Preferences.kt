/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample

import android.content.Context
import androidx.preference.PreferenceManager
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.sample.user.UserFragment

object Preferences {

    fun defaultPreferences(context: Context): DefaultPreferences = DefaultPreferences(context)

    class DefaultPreferences(context: Context) {

        private val applicationContext = context.applicationContext

        fun getUserId(): String? {
            return PreferenceManager
                .getDefaultSharedPreferences(applicationContext)
                .getString(PREF_ID, null)
        }

        fun getUserName(): String? {
            return PreferenceManager
                .getDefaultSharedPreferences(applicationContext)
                .getString(PREF_NAME, null)
        }

        fun getUserEmail(): String? {
            return PreferenceManager
                .getDefaultSharedPreferences(applicationContext)
                .getString(PREF_EMAIL, null)
        }

        fun getTrackingConsent(): TrackingConsent {
            val consentId =
                PreferenceManager.getDefaultSharedPreferences(applicationContext)
                    .getInt(PREF_TRACKING_CONSENT, CONSENT_PENDING)
            return resolveConsentFromId(consentId)
        }

        fun setUserCredentials(id: String, name: String, email: String) {
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
                .edit()
                .putString(UserFragment.PREF_ID, id)
                .putString(UserFragment.PREF_NAME, name)
                .putString(UserFragment.PREF_EMAIL, email)
                .apply()
        }

        fun setTrackingConsent(consent: TrackingConsent) {
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
                .edit()
                .putInt(PREF_TRACKING_CONSENT, resolveIdFromConsent(consent))
                .apply()
        }

        private fun resolveConsentFromId(consentId: Int): TrackingConsent {
            return when (consentId) {
                CONSENT_PENDING -> TrackingConsent.PENDING
                CONSENT_GRANTED -> TrackingConsent.GRANTED
                else -> TrackingConsent.NOT_GRANTED
            }
        }

        private fun resolveIdFromConsent(trackingConsent: TrackingConsent): Int {
            return when (trackingConsent) {
                TrackingConsent.PENDING -> CONSENT_PENDING
                TrackingConsent.GRANTED -> CONSENT_GRANTED
                else -> CONSENT_NOT_GRANTED
            }
        }

        companion object {
            private const val PREF_ID = "user-id"
            private const val PREF_NAME = "user-name"
            private const val PREF_EMAIL = "user-email"
            private const val PREF_TRACKING_CONSENT = "tracking-consent"

            private const val CONSENT_PENDING = 0
            private const val CONSENT_GRANTED = 1
            private const val CONSENT_NOT_GRANTED = 2
        }
    }
}
