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
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
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
    @Ignore
    fun test_dummy() = runTest {
        val logcatData =
            MutableSharedFlow<String>(onBufferOverflow = BufferOverflow.DROP_OLDEST, extraBufferCapacity = 1024)

        val logcatJob = LogcatCollector(InstrumentationRegistry.getInstrumentation())
            .subscribe("logcat -s AppStartupTypeManager2")
            .flowOn(Dispatchers.IO)
            .onEach { logcatData.tryEmit(it) }
            .launchIn(this)

        val deferred = async {
            delay(5000)
            1
        }

        delay(1000)
        deferred.await()

        logcatJob.cancel()
    }

    @Test
    fun test_warm() = runTest {
        Log.w("WAGAGA", "1")

        Log.w("WAGAGA", "2")

        // Initialize UiDevice instance
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        Log.w("WAGAGA", "3")

        // Start from the home screen
        device.pressHome()
        Log.w("WAGAGA", "4")

        // Wait for launcher
        val launcherPackage: String = device.launcherPackageName
        assertThat(launcherPackage, notNullValue())
        device.wait(
            Until.hasObject(By.pkg(launcherPackage).depth(0)),
            LAUNCH_TIMEOUT
        )

        Log.w("WAGAGA", "5")

        val logcatData = MutableSharedFlow<String>(onBufferOverflow = BufferOverflow.DROP_OLDEST, extraBufferCapacity = 1024)

        val logcatJob = LogcatCollector(InstrumentationRegistry.getInstrumentation())
            .subscribe("logcat -s AppStartupTypeManager2")
            .flowOn(Dispatchers.IO)
            .onEach { logcatData.tryEmit(it) }
            .launchIn(this)
//
        suspend fun executeAndWaitForLogcat(predicate: (String) -> Boolean, block: () -> Unit): Boolean = coroutineScope {
            withContext(Dispatchers.IO) {
                Log.w("WAGAGA", "8")
                val logcatResultDeferred = async {
                    logcatData
                        .filter(predicate)
                        .map {
                            Result.success(it)
                        }
//                        .timeout(10.seconds)
//                        .catch { e ->
//                            if (e is TimeoutCancellationException) {
//                                emit(Result.failure(e))
//                            } else {
//                                throw e
//                            }
//                        }
                        .first()
                }

                delay(2.seconds)
                block()

                Log.w("WAGAGA", "9")

                val logcatResult = logcatResultDeferred.await()

                Log.w("WAGAGA", "10")

                logcatResult.isSuccess
            }
        }

        // Launch the app
        val context = InstrumentationRegistry.getInstrumentation().context

        val broadcastIntent = Intent().apply {
            component = ComponentName(BASIC_SAMPLE_PACKAGE, "$BASIC_SAMPLE_PACKAGE.DummyBroadcastReceiver")
        }

        context.sendBroadcast(broadcastIntent)

        Log.w("WAGAGA", "6")

        delay(10.seconds)

        Log.w("WAGAGA", "7")

        val logcatResultDeferred = async {
            delay(5000)
            1
        }

        delay(2000)

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

//        val result = executeAndWaitForLogcat({it.contains("scenario 3")}) {
//            val intent = Intent().apply {
//                component = ComponentName(BASIC_SAMPLE_PACKAGE, "$BASIC_SAMPLE_PACKAGE.MainActivity")
//                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//            }
//
//            context.startActivity(intent)
//
//            // Wait for the app to appear
//            device.wait(
//                Until.hasObject(By.pkg(BASIC_SAMPLE_PACKAGE).depth(0)),
//                LAUNCH_TIMEOUT
//            )
//        }
//
//        assertEquals(result, true)



        logcatResultDeferred.await()

        Log.w("WAGAGA", "12_1")
        logcatJob.cancel()
        Log.w("WAGAGA", "12_2")
        logcatResultDeferred.cancel()
        coroutineContext.cancelChildren()
        Log.w("WAGAGA", "12_3")
//        delay(1000)
        Log.w("WAGAGA", "12_4")
        val job = coroutineContext[Job]!!
        Log.w("WAGAGA", "12_5")
        val allChildren = job.allChildren().toList()
        Log.w("WAGAGA", "children count: ${allChildren.size}")
//        coroutineContext.cancelChildren()
        Log.w("WAGAGA", "11")
    }
}

private fun Job.allChildren(): Sequence<Job> {
    return sequence {
        job.children.forEach { chld ->
            yield(chld)
            yieldAll(chld.allChildren())
        }
    }
}
