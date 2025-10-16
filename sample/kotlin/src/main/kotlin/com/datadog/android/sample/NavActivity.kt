/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.findNavController
import com.datadog.android.Datadog
import com.datadog.android.insights.LocalInsightOverlay
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.sample.service.LogsForegroundService
import com.google.android.material.snackbar.Snackbar
import timber.log.Timber

@OptIn(ExperimentalRumApi::class)
@Suppress("UndocumentedPublicProperty", "UndocumentedPublicClass")
class NavActivity : AppCompatActivity(), TrackingConsentChangeListener {

    lateinit var navController: NavController
    lateinit var rootView: View
    lateinit var appInfoView: TextView

    private val localInsights = LocalInsightOverlay()

    // region Activity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("onStart")

        setTheme(R.style.Sample_Theme_Custom)

        setContentView(R.layout.activity_nav)
        rootView = findViewById(R.id.frame_container)
        appInfoView = findViewById(R.id.app_info)
        localInsights.attach(this)
    }

    override fun onStart() {
        super.onStart()
        Timber.d("onStart")
    }

    override fun onRestart() {
        super.onRestart()
        Timber.d("onRestart")
    }

    override fun onResume() {
        super.onResume()
        Timber.d("onResume")

        navController = findNavController(R.id.nav_host_fragment)

        val tracking = Preferences.defaultPreferences(this).getTrackingConsent()
        updateTrackingConsentLabel(tracking)
    }

    override fun onPause() {
        super.onPause()
        Timber.d("onPause")
    }

    override fun onStop() {
        super.onStop()
        Timber.d("onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        localInsights.detach()
        Timber.d("onDestroy")
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.navigation, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        var result = true
        when (item.itemId) {
            R.id.set_user_info -> {
                navController.navigate(R.id.fragment_user)
            }
            R.id.set_account_info -> {
                navController.navigate(R.id.fragment_account)
            }
            R.id.show_snack_bar -> {
                Snackbar.make(rootView, LIPSUM, Snackbar.LENGTH_LONG).show()
            }
            R.id.start_foreground_service -> {
                val serviceIntent = Intent(this, LogsForegroundService::class.java)
                startService(serviceIntent)
            }
            R.id.gdpr -> {
                navController.navigate(R.id.fragment_gdpr)
            }
            R.id.clear_all_data -> {
                promptClearAllData()
            }
            else -> result = super.onOptionsItemSelected(item)
        }
        return result
    }

    private fun promptClearAllData() {
        AlertDialog.Builder(this)
            .setMessage(R.string.msg_clear_all_data)
            .setNeutralButton(android.R.string.cancel) { _, _ ->
                // No Op
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                Datadog.getInstance().clearAllData()
                Toast.makeText(this, R.string.msg_all_data_cleared, Toast.LENGTH_SHORT).show()
            }
            .create()
            .show()
    }

    override fun onTrackingConsentChanged(trackingConsent: TrackingConsent) =
        updateTrackingConsentLabel(trackingConsent)

    private fun updateTrackingConsentLabel(trackingConsent: TrackingConsent) {
        appInfoView.text = "${BuildConfig.FLAVOR} / Tracking: $trackingConsent"
    }

    // endregion

    companion object {
        internal const val LIPSUM = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, …"
    }
}

internal interface TrackingConsentChangeListener {
    fun onTrackingConsentChanged(trackingConsent: TrackingConsent)
}
