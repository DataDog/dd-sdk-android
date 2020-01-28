/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */
package com.datadog.android.sample

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.datadog.android.log.Logger
import com.datadog.android.sample.logs.LogsFragment
import com.datadog.android.sample.traces.TracesFragment
import com.datadog.android.sample.webview.WebFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.opentracing.Scope
import io.opentracing.Span
import io.opentracing.util.GlobalTracer
import java.lang.Exception

class MainActivity : AppCompatActivity() {

    lateinit var mainScope: Scope
    lateinit var mainSpan: Span
    private lateinit var resumePauseSpan: Span

    private val logger: Logger by lazy {
        Logger.Builder()
            .setServiceName("android-sample-kotlin")
            .setLoggerName("main_activity")
            .setNetworkInfoEnabled(true)
            .build()
            .apply {
                addTag("flavor", BuildConfig.FLAVOR)
                addTag("build_type", BuildConfig.BUILD_TYPE)

                val device = JsonObject()
                val abis = JsonArray()
                try {
                    device.addProperty("api", Build.VERSION.SDK_INT)
                    device.addProperty("brand", Build.BRAND)
                    device.addProperty("manufacturer", Build.MANUFACTURER)
                    device.addProperty("model", Build.MODEL)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        for (abi in Build.SUPPORTED_ABIS) {
                            abis.add(abi)
                        }
                    }
                } catch (t: Throwable) {
                    // ignore
                }
                addAttribute("device", device)
                addAttribute("supported_abis", abis)
            }
    }

    private val navigationItemSelectedListener =
        BottomNavigationView.OnNavigationItemSelectedListener { menuItem ->
            switchToFragment(
                menuItem.itemId
            )
        }

    // region Activity Lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        logger.d("MainActivity/onCreate")

        setContentView(R.layout.activity_main)
        switchToFragment(R.id.navigation_logs)
        (findViewById<View>(R.id.navigation) as BottomNavigationView)
            .setOnNavigationItemSelectedListener(navigationItemSelectedListener)
    }

    override fun onStart() {
        val tracer = GlobalTracer.get()
        mainSpan = tracer
            .buildSpan("MainActivity").start()
        mainScope = tracer.activateSpan(mainSpan)
        super.onStart()
    }

    override fun onRestart() {
        super.onRestart()
        logger.d("MainActivity/onRestart")
    }

    override fun onResume() {
        resumePauseSpan = GlobalTracer.get()
            .buildSpan("onResumeOnPause")
            .asChildOf(mainSpan)
            .start()
        super.onResume()
        logger.d("MainActivity/onResume")
    }

    override fun onPause() {
        super.onPause()
        logger.d("MainActivity/onPause")
        resumePauseSpan.finish()
    }

    override fun onStop() {
        super.onStop()
        logger.d("MainActivity/onStop")
        mainScope.close()
        mainSpan.finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        logger.d("MainActivity/onDestroy")
    }

    // endregion

    // region Internal

    private fun switchToFragment(@IdRes id: Int): Boolean {
        val fragmentToUse: Fragment
        val spanName: String
        when (id) {
            R.id.navigation_logs -> {
                logger.i("Switching to fragment: Logs")
                spanName = "SwitchingToLogsFragment"
                fragmentToUse = LogsFragment.newInstance()
            }
            R.id.navigation_webview -> {
                logger.i("Switching to fragment: Web")
                spanName = "SwitchingToWebViewFragment"
                fragmentToUse = WebFragment.newInstance()
            }
            else -> {
                logger.i("Switching to fragment: Traces")
                spanName = "SwitchingToTracesFragment"
                fragmentToUse = TracesFragment.newInstance()
            }
        }

        addSpanInScope(spanName) {
            val ft = supportFragmentManager.beginTransaction()
            ft.replace(R.id.fragment_host, fragmentToUse)
            ft.commit()
        }
        return true
    }

    private fun addSpanInScope(opName: String, execute: () -> Unit) {
        val tracer = GlobalTracer.get()
        val span = tracer.buildSpan(opName).start()
        try {
            val scope = tracer.activateSpan(span)
            execute()
            scope.close()
        } catch (e: Exception) {
            span.log(e.message)
        } finally {
            span.finish()
        }
    }

    // endregion
}
