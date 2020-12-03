/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.bridge.internal

import com.datadog.android.bridge.DdRum
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.RumResourceKind
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.forge.BaseConfigurator
import com.datadog.tools.unit.getStaticValue
import com.datadog.tools.unit.setStaticValue
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
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
@ForgeConfiguration(BaseConfigurator::class)
internal class BridgeRumTest {

    lateinit var testedDdRum: DdRum

    @Mock
    lateinit var mockRumMonitor: RumMonitor

    lateinit var fakeContext: Map<String, Any?>

    @LongForgery(1000000000000, 2000000000000)
    var fakeTimestamp: Long = 0L

    @BeforeEach
    fun `set up`(forge: Forge) {
        GlobalRum.registerIfAbsent(mockRumMonitor)

        fakeContext = forge.aMap {
            anAlphabeticalString() to aNullable {
                anElementFrom(
                    anHexadecimalString(),
                    anInt(),
                    aLong(),
                    getForgery<Date>(),
                    getForgery<Throwable>()
                )
            }
        }

        testedDdRum = BridgeRum()
    }

    @AfterEach
    fun `tear down`() {
        GlobalRum.javaClass.setStaticValue("monitor", mock<RumMonitor>())
        GlobalRum.javaClass.getStaticValue<GlobalRum, AtomicBoolean>("isRegistered").set(false)
    }

    @Test
    fun `M call startView W startView()`(
        @StringForgery key: String,
        @StringForgery name: String
    ) {
        // Given
        val updatedContext = fakeContext + (RumAttributes.INTERNAL_TIMESTAMP to fakeTimestamp)

        // When
        testedDdRum.startView(key, name, fakeTimestamp, fakeContext)

        // Then
        verify(mockRumMonitor).startView(key, name, updatedContext)
    }

    @Test
    fun `M call stopView W stopView()`(
        @StringForgery key: String,
        @StringForgery name: String
    ) {
        // Given
        val updatedContext = fakeContext + (RumAttributes.INTERNAL_TIMESTAMP to fakeTimestamp)

        // When
        testedDdRum.stopView(key, fakeTimestamp, fakeContext)

        // Then
        verify(mockRumMonitor).stopView(key, updatedContext)
    }

    @Test
    fun `M call addAction W addAction()`(
        @StringForgery name: String,
        @Forgery type: RumActionType
    ) {
        // Given
        val updatedContext = fakeContext + (RumAttributes.INTERNAL_TIMESTAMP to fakeTimestamp)

        // When
        testedDdRum.addAction(type.name, name, fakeTimestamp, fakeContext)

        // Then
        verify(mockRumMonitor).addUserAction(type, name, updatedContext)
    }

    @Test
    fun `M call addAction W addAction() with invalid type`(
        @StringForgery name: String,
        @StringForgery(StringForgeryType.HEXADECIMAL) type: String
    ) {
        // Given
        val updatedContext = fakeContext + (RumAttributes.INTERNAL_TIMESTAMP to fakeTimestamp)

        // When
        testedDdRum.addAction(type, name, fakeTimestamp, fakeContext)

        // Then
        verify(mockRumMonitor).addUserAction(RumActionType.CUSTOM, name, updatedContext)
    }

    @Test
    fun `M call startAction W startAction()`(
        @Forgery type: RumActionType,
        @StringForgery name: String
    ) {
        // Given
        val updatedContext = fakeContext + (RumAttributes.INTERNAL_TIMESTAMP to fakeTimestamp)

        // When
        testedDdRum.startAction(type.name, name, fakeTimestamp, fakeContext)

        // Then
        verify(mockRumMonitor).startUserAction(type, name, updatedContext)
    }

    @Test
    fun `M call startAction W startAction() with invalid typ`(
        @StringForgery name: String,
        @StringForgery(StringForgeryType.HEXADECIMAL) type: String
    ) {
        // Given
        val updatedContext = fakeContext + (RumAttributes.INTERNAL_TIMESTAMP to fakeTimestamp)

        // When
        testedDdRum.startAction(type, name, fakeTimestamp, fakeContext)

        // Then
        verify(mockRumMonitor).startUserAction(RumActionType.CUSTOM, name, updatedContext)
    }

    @Test
    fun `M call stopAction W stopAction()`(
        @StringForgery key: String,
        @StringForgery name: String
    ) {
        // Given
        val updatedContext = fakeContext + (RumAttributes.INTERNAL_TIMESTAMP to fakeTimestamp)

        // When
        testedDdRum.stopAction(fakeTimestamp, fakeContext)

        // Then
        verify(mockRumMonitor).stopUserAction(updatedContext)
    }

    @Test
    fun `M call startResource W startResource()`(
        @StringForgery key: String,
        @StringForgery(regex = "GET|POST|DELETE") method: String,
        @StringForgery(regex = "http(s?)://[a-z]+\\.com/\\w+") url: String
    ) {
        // Given
        val updatedContext = fakeContext + (RumAttributes.INTERNAL_TIMESTAMP to fakeTimestamp)

        // When
        testedDdRum.startResource(key, method, url, fakeTimestamp, fakeContext)

        // Then
        verify(mockRumMonitor).startResource(key, method, url, updatedContext)
    }

    @Test
    fun `M call stopResource W stopResource()`(
        @StringForgery key: String,
        @IntForgery(200, 600) statusCode: Int,
        @Forgery kind: RumResourceKind
    ) {
        // Given
        val updatedContext = fakeContext + (RumAttributes.INTERNAL_TIMESTAMP to fakeTimestamp)

        // When
        testedDdRum.stopResource(
            key,
            statusCode.toLong(),
            kind.toString(),
            fakeTimestamp,
            fakeContext
        )

        // Then
        verify(mockRumMonitor).stopResource(key, statusCode, null, kind, updatedContext)
    }

    @Test
    fun `M call stopResource W stopResource() with invalid kind`(
        @StringForgery key: String,
        @IntForgery(200, 600) statusCode: Int,
        @StringForgery(StringForgeryType.HEXADECIMAL) kind: String
    ) {
        // Given
        val updatedContext = fakeContext + (RumAttributes.INTERNAL_TIMESTAMP to fakeTimestamp)

        // When
        testedDdRum.stopResource(
            key,
            statusCode.toLong(),
            kind,
            fakeTimestamp,
            fakeContext
        )

        // Then
        verify(mockRumMonitor).stopResource(
            key,
            statusCode,
            null,
            RumResourceKind.UNKNOWN,
            updatedContext
        )
    }

    @Test
    fun `M call addError W addError()`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @StringForgery stackTrace: String
    ) {
        // Given
        val updatedContext = fakeContext + (RumAttributes.INTERNAL_TIMESTAMP to fakeTimestamp)

        // When
        testedDdRum.addError(message, source.name, stackTrace, fakeTimestamp, fakeContext)

        // Then
        verify(mockRumMonitor).addErrorWithStacktrace(message, source, stackTrace, updatedContext)
    }

    @Test
    fun `M call addError W addError() with invalid source`(
        @StringForgery message: String,
        @StringForgery(StringForgeryType.HEXADECIMAL) source: String,
        @StringForgery stackTrace: String
    ) {
        // Given
        val updatedContext = fakeContext + (RumAttributes.INTERNAL_TIMESTAMP to fakeTimestamp)

        // When
        testedDdRum.addError(message, source, stackTrace, fakeTimestamp, fakeContext)

        // Then
        verify(mockRumMonitor).addErrorWithStacktrace(
            message,
            RumErrorSource.SOURCE,
            stackTrace,
            updatedContext
        )
    }
}
