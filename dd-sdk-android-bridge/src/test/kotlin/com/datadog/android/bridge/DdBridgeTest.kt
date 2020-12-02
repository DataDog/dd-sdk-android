/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.bridge

import android.content.Context
import com.datadog.android.bridge.internal.BridgeLogs
import com.datadog.android.bridge.internal.BridgeRum
import com.datadog.android.bridge.internal.BridgeSdk
import com.datadog.android.bridge.internal.BridgeTrace
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
class DdBridgeTest {

    @Mock
    lateinit var mockContext: Context
    @Mock
    lateinit var mockAppContext: Context

    @Test
    fun `M return Datadog implementation W getDatadog()`() {
        // Given
        whenever(mockContext.applicationContext) doReturn mockAppContext

        // When
        val result = DdBridge.getDdSdk(mockContext)

        // Then
        check(result is BridgeSdk)
        assertThat(result.appContext).isSameAs(mockAppContext)
    }

    @Test
    fun `M return DdLogs implementation W getDdLogs()`() {
        // When
        val result = DdBridge.getDdLogs(mockContext)

        // Then
        check(result is BridgeLogs)
    }

    @Test
    fun `M return DdRum implementation W getDdRum()`() {
        // When
        val result = DdBridge.getDdRum(mockContext)

        // Then
        check(result is BridgeRum)
    }

    @Test
    fun `M return DdTrace implementation W getDdTrace()`() {
        // When
        val result = DdBridge.getDdTrace(mockContext)

        // Then
        check(result is BridgeTrace)
    }
}
