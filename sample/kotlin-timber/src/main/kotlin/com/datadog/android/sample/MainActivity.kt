/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.sample

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.datadog.android.sample.logs.LogsFragment
import com.datadog.android.sample.webview.WebFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    private val navigationItemSelectedListener =
        BottomNavigationView.OnNavigationItemSelectedListener { menuItem ->
            switchToFragment(
                menuItem.itemId
            )
        }

    // region Activity Lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("MainActivity/onCreate")
        setContentView(R.layout.activity_main)
        switchToFragment(R.id.navigation_logs)
        (findViewById<View>(R.id.navigation) as BottomNavigationView)
            .setOnNavigationItemSelectedListener(navigationItemSelectedListener)
    }

    override fun onStart() {
        super.onStart()
        Timber.d("MainActivity/onStart")
    }

    override fun onRestart() {
        super.onRestart()
        Timber.d("MainActivity/onRestart")
    }

    override fun onResume() {
        super.onResume()
        Timber.d("MainActivity/onResume")
    }

    override fun onPause() {
        super.onPause()
        Timber.d("MainActivity/onPause")
    }

    override fun onStop() {
        super.onStop()
        Timber.d("MainActivity/onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("MainActivity/onDestroy")
    }

    // endregion
// region Internal
    private fun switchToFragment(@IdRes id: Int): Boolean {
        var fragmentToUse: Fragment? = null
        when (id) {
            R.id.navigation_logs -> {
                Timber.i("Switching to fragment: Logs")
                fragmentToUse = LogsFragment.newInstance()
            }
            R.id.navigation_webview -> {
                Timber.i("Switching to fragment: Web")
                fragmentToUse = WebFragment.newInstance()
            }
        }
        return if (fragmentToUse == null) {
            Timber.w("Switching to fragment: unknown @$id")
            Toast.makeText(this, "We're unable to create this fragment.", Toast.LENGTH_LONG).show()
            false
        } else {
            val ft =
                supportFragmentManager.beginTransaction()
            ft.replace(R.id.fragment_host, fragmentToUse)
            ft.commit()
            true
        }
    } // endregion
}
