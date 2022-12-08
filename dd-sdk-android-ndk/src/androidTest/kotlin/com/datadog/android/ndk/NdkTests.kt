/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.ndk

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.datadog.tools.unit.ConditionWatcher
import com.datadog.tools.unit.assertj.JsonObjectAssert.Companion.assertThat
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.junit4.ForgeRule
import org.assertj.core.api.Assertions
import org.assertj.core.data.Offset
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.lang.RuntimeException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class NdkTests {

    @get:Rule
    val forge = ForgeRule()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    companion object {
        init {
            System.loadLibrary("datadog-native-lib")
            System.loadLibrary("datadog-native-lib-test")
        }
    }

    @Test
    fun ndkSuitTests() {
        if (runNdkSuitTests() != 0) {
            throw RuntimeException("NDK Suit tests failed")
        }
    }

    @Test
    fun ndkStandaloneTests() {
        if (runNdkStandaloneTests() != 0) {
            throw RuntimeException("NDK Standalone tests failed")
        }
    }

    @Test
    fun mustWriteAnErrorLog_whenHandlingSignal_whenConsentUpdatedToGranted() {
        val fakeSignal = forge.anInt(min = 1, max = 32)
        // we need to keep this this size because we are using a buffer of [30] size in c++ for
        // the error.signal attribute
        val fakeSignalName = forge.anAlphabeticalString(size = 20)
        val fakeErrorMessage = forge.anAlphabeticalString()
        val fakeErrorStack = forge.anAlphabeticalString()
        initNdkErrorHandler(temporaryFolder.root.absolutePath)
        updateTrackingConsent(1)
        simulateSignalInterception(
            fakeSignal,
            fakeSignalName,
            fakeErrorMessage,
            fakeErrorStack
        )

        val expectedTimestamp = System.currentTimeMillis()

        // we need to give time to native part to write the file
        // otherwise we will get into race condition issues
        ConditionWatcher {
            // assert the log file
            val listFiles = temporaryFolder.root.listFiles()
            val inputStream = listFiles?.first()?.inputStream()
            inputStream?.use {
                val jsonString = String(it.readBytes(), Charset.forName("utf-8"))
                val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                assertThat(jsonObject).hasField("signal", fakeSignal)
                assertThat(jsonObject).hasField("signal_name", fakeSignalName)
                assertThat(jsonObject).hasField("message", fakeErrorMessage)
                assertThat(jsonObject).hasField("stacktrace", fakeErrorStack)
                assertThat(jsonObject).hasField(
                    "timestamp",
                    expectedTimestamp,
                    Offset.offset(TimeUnit.SECONDS.toMillis(10))
                )
            }
            true
        }.doWait(timeoutMs = 5000)
    }

    @Test
    fun mustNotWriteAnyLog_whenHandlingSignal_whenConsentUpdatedToPending() {
        val signal = forge.anInt(min = 1, max = 32)
        // we need to keep this this size because we are using a buffer of [30] size in c++ for
        // the error.signal attribute
        val signalName = forge.anAlphabeticalString(size = 20)
        val errorMessage = forge.anAlphabeticalString()
        val errorStack = forge.anAlphabeticalString()
        initNdkErrorHandler(temporaryFolder.root.absolutePath)
        updateTrackingConsent(0)
        simulateSignalInterception(
            signal,
            signalName,
            errorMessage,
            errorStack
        )

        // we need to give time to native part to write the file
        // otherwise we will get into race condition issues
        ConditionWatcher {
            // assert the log file
            Assertions.assertThat(temporaryFolder.root.listFiles()).isEmpty()
            true
        }.doWait(timeoutMs = 5000)
    }

    @Test
    fun mustNotWriteAnyLog_whenHandlingSignal_whenConsentUpdatedToNotGranted() {
        val signal = forge.anInt(min = 1, max = 32)
        // we need to keep this this size because we are using a buffer of [30] size in c++ for
        // the error.signal attribute
        val signalName = forge.anAlphabeticalString(size = 20)
        val errorMessage = forge.anAlphabeticalString()
        val errorStack = forge.anAlphabeticalString()
        initNdkErrorHandler(temporaryFolder.root.absolutePath)
        updateTrackingConsent(2)
        simulateSignalInterception(
            signal,
            signalName,
            errorMessage,
            errorStack
        )

        // we need to give time to native part to write the file
        // otherwise we will get into race condition issues
        ConditionWatcher {
            // assert the log file
            Assertions.assertThat(temporaryFolder.root.listFiles()).isEmpty()
            true
        }.doWait(timeoutMs = 5000)
    }

    // region NDK

    /**
     * Will run the actual test suites on the NDK side.
     * @return 0 if all the tests passed.
     */
    private external fun runNdkSuitTests(): Int

    /**
     * Will run the singular tests on the NDK side.
     * @return 0 if all the tests passed.
     */
    private external fun runNdkStandaloneTests(): Int

    /**
     * Will initialize the NDK crash reporter.
     * @param storageDir the storage directory for the reported crash logs
     */
    private external fun initNdkErrorHandler(
        storageDir: String
    )

    /**
     * Simulate a signal interception into the NDK crash reporter.
     * @param signal the signal id (between 1 and 32)
     * @param signalName the signal name (e.g. SIGHUP, SIGINT, SIGILL, etc.)
     * @param errorMessage the error message
     * @param errorStack the error stack
     */
    private external fun simulateSignalInterception(
        signal: Int,
        signalName: String,
        errorMessage: String,
        errorStack: String
    )

    /**
     * Updates the tracking consent into the NDK crash reporter.
     * @param consent as the tracking consent value (0 - PENDING, 1 - GRANTED, 2 - NOT-GRANTED)
     */
    private external fun updateTrackingConsent(
        consent: Int
    )

    // endregion
}
