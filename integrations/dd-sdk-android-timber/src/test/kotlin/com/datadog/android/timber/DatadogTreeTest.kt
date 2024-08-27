/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.timber

import android.util.Log
import com.datadog.android.log.Logger
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness
import timber.log.Timber

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
class DatadogTreeTest {

    lateinit var testedTree: Timber.Tree

    @Mock
    lateinit var mockLogger: Logger

    @StringForgery
    lateinit var fakeMessage: String

    @BeforeEach
    fun `set up`() {
        testedTree = DatadogTree(mockLogger)
        Timber.plant(testedTree)
    }

    @AfterEach
    fun `tear down`() {
        Timber.uprootAll()
    }

    @Test
    fun `tree logs message with verbose level`() {
        Timber.v(fakeMessage)

        verify(mockLogger)
            .log(Log.VERBOSE, fakeMessage, null, emptyMap())
    }

    @Test
    fun `tree logs message with debug level`() {
        Timber.d(fakeMessage)

        verify(mockLogger)
            .log(Log.DEBUG, fakeMessage, null, emptyMap())
    }

    @Test
    fun `tree logs message with info level`() {
        Timber.i(fakeMessage)

        verify(mockLogger)
            .log(Log.INFO, fakeMessage, null, emptyMap())
    }

    @Test
    fun `tree logs message with warning level`() {
        Timber.w(fakeMessage)

        verify(mockLogger)
            .log(Log.WARN, fakeMessage, null, emptyMap())
    }

    @Test
    fun `tree logs message with error level`() {
        Timber.e(fakeMessage)

        verify(mockLogger)
            .log(Log.ERROR, fakeMessage, null, emptyMap())
    }

    @Test
    fun `tree logs message with assert level`() {
        Timber.wtf(fakeMessage)

        verify(mockLogger)
            .log(Log.ASSERT, fakeMessage, null, emptyMap())
    }

    @Test
    fun `tree logs message with tag`(
        @StringForgery fakeTag: String
    ) {
        // When
        Timber.tag(fakeTag)
        Timber.d(fakeMessage)

        // Then
        verify(mockLogger)
            .log(Log.DEBUG, fakeMessage, null, mapOf("timber.tag" to fakeTag))
    }
}
