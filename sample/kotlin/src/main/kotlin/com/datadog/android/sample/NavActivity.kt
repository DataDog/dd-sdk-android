/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.findNavController

class NavActivity : AppCompatActivity() {

    lateinit var navController: NavController

    // region Activity

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
        val arguments = Bundle(2)
        arguments.putInt("item.itemId", item.itemId)
        arguments.putString("item.title", item.title.toString())
        var result = true
        when (item.itemId) {
            R.id.navigation_logs -> navController.navigate(R.id.fragment_logs, arguments)
            R.id.navigation_webview -> navController.navigate(R.id.fragment_webview, arguments)
            R.id.navigation_traces -> navController.navigate(R.id.fragment_trace, arguments)
            else -> result = super.onOptionsItemSelected(item)
        }
        return result
    }

    // endregion
}
