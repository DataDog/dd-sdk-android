/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.integration

import com.datadog.android.core.stub.StubSDKCore
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum.resource.RumResourceInputStream
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito.mock
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.whenever
import java.io.ByteArrayOutputStream
import java.io.InputStream

abstract class BaseRumResourceInputStreamTest {

    protected lateinit var stubSdkCore: StubSDKCore

    @StringForgery
    protected lateinit var fakeApplicationId: String

    protected open fun configureRumBuilder(builder: RumConfiguration.Builder) {}

    @BeforeEach
    fun `set up`(forge: Forge) {
        stubSdkCore = StubSDKCore(forge)
        val builder = RumConfiguration.Builder(fakeApplicationId)
            .trackNonFatalAnrs(false)
        configureRumBuilder(builder)
        Rum.enable(builder.build(), stubSdkCore)
    }

    // region Shared Scenarios

    protected fun runResourceTransfer(
        viewKey: String,
        viewName: String,
        resourceUrl: String,
        data: String
    ): ByteArray {
        GlobalRumMonitor.get(stubSdkCore).startView(viewKey, viewName)
        val input = data.toByteArray()
        val inputStream = input.inputStream()
        val rumResourceInputStream = RumResourceInputStream(inputStream, resourceUrl, stubSdkCore)
        val outputStream = ByteArrayOutputStream(input.size)
        rumResourceInputStream.use {
            it.transferTo(outputStream)
        }
        return outputStream.toByteArray()
    }

    protected fun runResourceReadWithError(
        viewKey: String,
        viewName: String,
        resourceUrl: String,
        error: Throwable
    ): Throwable? {
        GlobalRumMonitor.get(stubSdkCore).startView(viewKey, viewName)
        val inputStream: InputStream = mock()
        val rumResourceInputStream = RumResourceInputStream(inputStream, resourceUrl, stubSdkCore)
        whenever(inputStream.read()) doThrow error

        var forwardedError: Throwable? = null
        try {
            rumResourceInputStream.read()
        } catch (e: Throwable) {
            forwardedError = e
        }
        return forwardedError
    }

    // endregion
}
