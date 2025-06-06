/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample

import android.content.Context
import androidx.preference.PreferenceManager
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.sample.datalist.DataSourceType
import com.datadog.android.sample.picture.ImageLoaderType
import timber.log.Timber

internal object Preferences {

    fun defaultPreferences(context: Context): DefaultPreferences = DefaultPreferences(context)

    @Suppress("TooManyFunctions")
    class DefaultPreferences(context: Context) {

        private val applicationContext = context.applicationContext

        fun getUserId(): String? {
            return PreferenceManager
                .getDefaultSharedPreferences(applicationContext)
                .getString(PREF_USR_ID, null)
        }

        fun getUserName(): String? {
            return PreferenceManager
                .getDefaultSharedPreferences(applicationContext)
                .getString(PREF_USR_NAME, null)
        }

        fun getUserEmail(): String? {
            return PreferenceManager
                .getDefaultSharedPreferences(applicationContext)
                .getString(PREF_USR_EMAIL, null)
        }

        fun getUserGender(): String? {
            return PreferenceManager
                .getDefaultSharedPreferences(applicationContext)
                .getString(
                    PREF_USR_GENDER,
                    null
                )
        }

        fun getUserAge(): Int {
            return PreferenceManager
                .getDefaultSharedPreferences(applicationContext)
                .getInt(
                    PREF_USR_AGE,
                    0
                )
        }

        fun getAccountId(): String? {
            return PreferenceManager
                .getDefaultSharedPreferences(applicationContext)
                .getString(PREF_ACCOUNT_ID, null)
        }

        fun getAccountName(): String? {
            return PreferenceManager
                .getDefaultSharedPreferences(applicationContext)
                .getString(PREF_ACCOUNT_NAME, null)
        }

        fun getAccountRole(): String? {
            return PreferenceManager
                .getDefaultSharedPreferences(applicationContext)
                .getString(
                    PREF_ACCOUNT_ROLE,
                    null
                )
        }

        fun getAccountAge(): Int {
            return PreferenceManager
                .getDefaultSharedPreferences(applicationContext)
                .getInt(
                    PREF_ACCOUNT_AGE,
                    0
                )
        }

        fun getTrackingConsent(): TrackingConsent {
            val consentId = PreferenceManager
                .getDefaultSharedPreferences(applicationContext)
                .getInt(PREF_TRACKING_CONSENT, CONSENT_GRANTED)
            return resolveConsentFromId(consentId)
        }

        fun getLocalDataSource(): DataSourceType {
            val source = PreferenceManager
                .getDefaultSharedPreferences(applicationContext)
                .getString(PREF_LOCAL_DATA_SOURCE, null)
            return try {
                DataSourceType.valueOf(source.orEmpty())
            } catch (e: IllegalArgumentException) {
                Timber.e("Error getting pref $PREF_LOCAL_DATA_SOURCE")
                DataSourceType.ROOM
            }
        }

        fun getImageLoader(): ImageLoaderType {
            val source = PreferenceManager
                .getDefaultSharedPreferences(applicationContext)
                .getString(PREF_IMAGE_LOADER, null)
            return try {
                ImageLoaderType.valueOf(source.orEmpty())
            } catch (e: IllegalArgumentException) {
                Timber.e("Error getting pref $PREF_IMAGE_LOADER")
                ImageLoaderType.GLIDE
            }
        }

        fun setAccountInfo(
            id: String,
            name: String,
            role: String,
            age: Int
        ) {
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
                .edit()
                .putString(PREF_ACCOUNT_ID, id)
                .putString(PREF_ACCOUNT_NAME, name)
                .putString(PREF_ACCOUNT_ROLE, role)
                .putInt(PREF_USR_AGE, age)
                .apply()
        }

        fun setUserCredentials(
            id: String,
            name: String,
            email: String,
            gender: String,
            age: Int
        ) {
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
                .edit()
                .putString(PREF_USR_ID, id)
                .putString(PREF_USR_NAME, name)
                .putString(PREF_USR_EMAIL, email)
                .putString(PREF_USR_GENDER, gender)
                .putInt(PREF_USR_AGE, age)
                .apply()
        }

        fun setTrackingConsent(consent: TrackingConsent) {
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
                .edit()
                .putInt(PREF_TRACKING_CONSENT, resolveIdFromConsent(consent))
                .apply()
        }

        fun setLocalDataSource(type: DataSourceType) {
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
                .edit()
                .putString(PREF_LOCAL_DATA_SOURCE, type.name)
                .apply()
        }

        fun setImageLoader(type: ImageLoaderType) {
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
                .edit()
                .putString(PREF_IMAGE_LOADER, type.name)
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
            private const val PREF_USR_ID = "user-id"
            private const val PREF_USR_NAME = "user-name"
            private const val PREF_USR_EMAIL = "user-email"
            private const val PREF_USR_GENDER = "user-gender"
            private const val PREF_USR_AGE = "user-age"
            private const val PREF_ACCOUNT_ID = "account-id"
            private const val PREF_ACCOUNT_NAME = "account-name"
            private const val PREF_ACCOUNT_AGE = "account-age"
            private const val PREF_ACCOUNT_ROLE = "account-role"
            private const val PREF_TRACKING_CONSENT = "tracking-consent"
            private const val PREF_LOCAL_DATA_SOURCE = "local-data-source"
            private const val PREF_IMAGE_LOADER = "image-loader"

            private const val CONSENT_PENDING = 0
            private const val CONSENT_GRANTED = 1
            private const val CONSENT_NOT_GRANTED = 2
        }
    }
}
