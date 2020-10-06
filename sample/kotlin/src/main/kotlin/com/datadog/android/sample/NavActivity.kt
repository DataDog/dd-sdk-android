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
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.findNavController
import com.datadog.android.sample.service.LogsForegroundService
import com.google.android.material.snackbar.Snackbar
import timber.log.Timber

class NavActivity : AppCompatActivity() {

    lateinit var navController: NavController
    lateinit var rootView: View

    // region Activity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("onStart")

        setTheme(R.style.Sample_Theme_Custom)

        setContentView(R.layout.activity_nav)
        rootView = findViewById(R.id.frame_container)

        navController = findNavController(R.id.nav_host_fragment)
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
            R.id.show_snack_bar -> {
                Snackbar.make(rootView, LIPSUM, Snackbar.LENGTH_LONG).show()
            }
            R.id.start_foreground_service -> {
                val serviceIntent = Intent(this, LogsForegroundService::class.java)
                startService(serviceIntent)
            }
            else -> result = super.onOptionsItemSelected(item)
        }
        return result
    }

    // endregion

    companion object {
        const val LIPSUM = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, …"
    }
}
