/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.startuptest

import android.content.ComponentName
import android.content.Intent
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.datadog.android.startuptest.utils.LogcatCollector
import junit.framework.Assert.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

private const val BASIC_SAMPLE_PACKAGE = "com.datadog.android.uitestappxml"
private const val LAUNCH_TIMEOUT = 5000L
private const val STRING_TO_BE_TYPED = "UiAutomator"

@RunWith(AndroidJUnit4::class)
class FirstTest {

    private lateinit var device: UiDevice

//    @Before
//    fun startMainActivityFromHomeScreen() {
//        // Initialize UiDevice instance
//        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
//
//        // Start from the home screen
//        device.pressHome()
//
//        // Wait for launcher
//        val launcherPackage: String = device.launcherPackageName
//        assertThat(launcherPackage, notNullValue())
//        device.wait(
//            Until.hasObject(By.pkg(launcherPackage).depth(0)),
//            LAUNCH_TIMEOUT
//        )
//
//        // Launch the app
//        val context = InstrumentationRegistry.getInstrumentation().context
//        val intent = Intent().apply {
//            component = ComponentName(BASIC_SAMPLE_PACKAGE, "$BASIC_SAMPLE_PACKAGE.MainActivity")
//            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//        }
//        context.startActivity(intent)
//
//        // Wait for the app to appear
//        device.wait(
//            Until.hasObject(By.pkg(BASIC_SAMPLE_PACKAGE).depth(0)),
//            LAUNCH_TIMEOUT
//        )
//    }
//
//    @Test
//    fun test1() {
//        runBlocking {
//            val joba = LogcatCollector(InstrumentationRegistry.getInstrumentation()).subscribe("logcat -s AppStartupTypeManager2")
//                .onEach {
//                    Log.w("WAHAHA_COPY", it)
//                }
//                .launchIn(this)
//
//            delay(10000)
//            joba.cancel()
//        }
//    }

    @Test
    fun test_warm() = runBlocking(Dispatchers.IO) {
        val logcatData = LogcatCollector(InstrumentationRegistry.getInstrumentation())
            .subscribe("logcat -s AppStartupTypeManager2")
            .shareIn(this, SharingStarted.Eagerly)

        suspend fun executeAndWaitForLogcat(predicate: (String) -> Boolean, block: () -> Unit): Boolean = coroutineScope {
            val logcatResultDeferred = async {
                logcatData
                    .onEach {
                        Log.w("WAGAGA", it)
                    }
                    .filter(predicate)
                    .map { Result.success(it) }
                    .timeout(10.seconds)
                    .catch { e ->
                        if (e is TimeoutCancellationException) {
                            emit(Result.failure(e))
                        } else {
                            throw e
                        }
                    }
                    .first()
            }

            block()

            val logcatResult = logcatResultDeferred.await()

            return@coroutineScope logcatResult.isSuccess
        }

        // Initialize UiDevice instance
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        // Start from the home screen
        device.pressHome()

        // Wait for launcher
        val launcherPackage: String = device.launcherPackageName
        assertThat(launcherPackage, notNullValue())
        device.wait(
            Until.hasObject(By.pkg(launcherPackage).depth(0)),
            LAUNCH_TIMEOUT
        )

        // Launch the app
        val context = InstrumentationRegistry.getInstrumentation().context

        val broadcastIntent = Intent().apply {
            component = ComponentName(BASIC_SAMPLE_PACKAGE, "$BASIC_SAMPLE_PACKAGE.DummyBroadcastReceiver")
        }

        context.sendBroadcast(broadcastIntent)

        delay(10.seconds)

        val result = executeAndWaitForLogcat({it.contains("warm_3")}) {
            val intent = Intent().apply {
                component = ComponentName(BASIC_SAMPLE_PACKAGE, "$BASIC_SAMPLE_PACKAGE.MainActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)

            // Wait for the app to appear
            device.wait(
                Until.hasObject(By.pkg(BASIC_SAMPLE_PACKAGE).depth(0)),
                LAUNCH_TIMEOUT
            )
        }

        assertEquals(result, true)
    }
}
