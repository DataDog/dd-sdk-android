/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.ndk

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.datadog.tools.unit.assertj.JsonObjectAssert.Companion.assertThat
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.junit4.ForgeRule
import java.lang.RuntimeException
import java.nio.charset.Charset
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class NdkTests {

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
    fun ndkUnitTests() {
        if (runNdkUnitTests() != 0) {
            throw RuntimeException("NDK Unit tests failed")
        }
    }

    @Test
    fun signalHandlerIntegrationTest() {

        val signal = forge.anInt(min = 1, max = 32)
        // we need to keep this this size because we are using a buffer of [30] size in c++ for
        // the error.signal attribute
        val signalName = forge.anAlphabeticalString(size = 20)
        val signalErrorMessage = forge.anAlphabeticalString()
        runNdkSignalHandlerIntegrationTest(
            temporaryFolder.root.absolutePath,
            signal,
            signalName,
            signalErrorMessage
        )

        // we need to give time to native part to write the file
        // otherwise we will get into race condition issues
        Thread.sleep(5000)

        // assert the log file
        val inputStream = temporaryFolder.root.listFiles()?.first()?.inputStream()
        inputStream?.use {
            val jsonString = String(it.readBytes(), Charset.forName("utf-8"))
            val jsonObject = JsonParser.parseString(jsonString).asJsonObject
            assertThat(jsonObject).hasField("service", "")
            assertThat(jsonObject).hasField("env", "")
            assertThat(jsonObject).hasField("status", "emergency")
            assertThat(jsonObject).hasField("message", "Native crash detected")
            assertThat(jsonObject).hasField("error.message", signalErrorMessage)
            assertThat(jsonObject).hasField("error.signal", "$signalName: $signal")
            assertThat(jsonObject).hasField("error.kind", "Native")
            assertThat(jsonObject).hasField("logger.name", "crash")
        }
    }

    /**
     * Will run the actual test suites on the NDK side.
     * @return 0 if all the tests passed.
     */
    private external fun runNdkUnitTests(): Int

    /**
     * Will the integration test scenario for handling a crash signal on the NDK side.
     * @param storageDir the storage directory for the reported crash logs
     * @param signal the signal id (between 1 and 32)
     * @param signalName the signal name (e.g. SIGHUP, SIGINT, SIGILL, etc.)
     * @param signalMessage the signal error message
     */
    private external fun runNdkSignalHandlerIntegrationTest(
        storageDir: String,
        signal: Int,
        signalName: String,
        signalMessage: String
    )
}
