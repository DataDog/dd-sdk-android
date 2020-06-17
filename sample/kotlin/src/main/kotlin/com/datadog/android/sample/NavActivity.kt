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
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.findNavController
import com.google.android.material.snackbar.Snackbar

class NavActivity : AppCompatActivity() {

    lateinit var navController: NavController

    // region Activity

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_nav)

        navController = findNavController(R.id.nav_host_fragment)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.navigation, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        crashAndGetExceptionMessage()
        val arguments = Bundle(2)
        arguments.putInt("item.itemId", item.itemId)
        arguments.putString("item.title", item.title.toString())
        var result = true
        when (item.itemId) {
            R.id.navigation_logs -> navController.navigate(R.id.fragment_logs, arguments)
            R.id.navigation_webview -> navController.navigate(R.id.fragment_webview, arguments)
            R.id.navigation_traces -> navController.navigate(R.id.fragment_trace, arguments)
            R.id.navigation_data_list -> navController.navigate(R.id.fragment_data_list, arguments)
            R.id.navigation_view_pager -> {
                startActivity(Intent(this, ViewPagerActivity::class.java))
            }
            R.id.show_snack_bar -> {
                Snackbar.make(
                    this.window.decorView.rootView,
                    "Demo message",
                    Snackbar.LENGTH_LONG
                ).show()
            }
            else -> result = super.onOptionsItemSelected(item)
        }
        return result
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application. It will throw a C++ exception
     * and catch it in the signal handler which will be visible in the logs.
     */
    external fun crashAndGetExceptionMessage()

    // endregion

    companion object {
        init {
            System.loadLibrary("native-lib");
        }
    }
}
