/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.rum.internal.monitor

import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.assertj.RumEventAssert.Companion.assertThat
import com.datadog.android.rum.internal.domain.RumEvent
import com.datadog.android.rum.internal.domain.RumEventSerializer
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.forge.exhaustiveAttributes
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.extensions.SystemOutputExtension
import com.datadog.tools.unit.forge.aThrowable
import com.datadog.tools.unit.setFieldValue
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import fr.xgouchet.elmyr.jvm.ext.aTimestamp
import java.lang.ref.WeakReference
import java.util.UUID
import kotlin.system.measureNanoTime
import org.assertj.core.api.Assertions.assertThat
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
    ExtendWith(ApiLevelExtension::class),
    ExtendWith(SystemOutputExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogRumMonitorTest {

    lateinit var testedMonitor: RumMonitor

    @Mock
    lateinit var mockWriter: Writer<RumEvent>
    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @Forgery
    lateinit var fakeApplicationId: UUID

    @BeforeEach
    fun `set up`() {
        GlobalRum.updateApplicationId(fakeApplicationId)
        testedMonitor = DatadogRumMonitor(mockTimeProvider, mockWriter)
    }

    @Test
    fun `startView doesn't send anything without stopView and updates global context`(
        forge: Forge
    ) {
        val key = forge.anAsciiString()
        val name = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")

        testedMonitor.startView(key, name, emptyMap())

        verifyZeroInteractions(mockWriter)
        assertThat(GlobalRum.getRumContext().viewId)
            .isNotNull()
            .isNotEqualTo(UUID(0, 0))
    }

    @Test
    fun `startView sends previous unstopped view Rum Event`(
        forge: Forge
    ) {
        val timestamp = forge.aTimestamp()
        val key = forge.anAsciiString()
        val name = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        val attributes = forge.exhaustiveAttributes()
        var viewId: UUID? = null
        whenever(mockTimeProvider.getServerTimestamp()) doReturn timestamp

        val duration = measureNanoTime {
            testedMonitor.startView(key, name, attributes)
            viewId = GlobalRum.getRumContext().viewId
            testedMonitor.startView(
                forge.anAsciiString(),
                forge.aStringMatching("[a-z]+(\\.[a-z]+)+"),
                emptyMap()
            )
        }

        checkNotNull(viewId)
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())

            assertThat(lastValue)
                .hasTimestamp(timestamp)
                .hasAttributes(attributes)
                .hasViewData {
                    hasName(name.replace('.', '/'))
                    hasDurationLowerThan(duration)
                    hasVersion(2)
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }
        }
        assertThat(GlobalRum.getRumContext().viewId)
            .isNotNull()
            .isNotEqualTo(viewId)
    }

    @Test
    fun `stopView doesn't send anything without startView`(
        forge: Forge
    ) {
        val key = forge.anAsciiString()

        testedMonitor.stopView(key, emptyMap())

        verifyZeroInteractions(mockWriter)
        assertThat(GlobalRum.getRumContext().viewId)
            .isNull()
    }

    @Test
    fun `stopView doesn't send anything without matching startView`(
        forge: Forge
    ) {
        val startKey = forge.anAsciiString()
        val name = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        val stopKey = forge.anAsciiString()

        testedMonitor.startView(startKey, name, emptyMap())
        testedMonitor.stopView(stopKey, emptyMap())

        verifyZeroInteractions(mockWriter)
        assertThat(GlobalRum.getRumContext().viewId)
            .isNull()
    }

    @Test
    fun `stopView sends view Rum Event`(
        forge: Forge
    ) {
        val timestamp = forge.aTimestamp()
        val key = forge.anAsciiString()
        val name = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        val attributes = forge.exhaustiveAttributes()
        var viewId: UUID? = null
        whenever(mockTimeProvider.getServerTimestamp()) doReturn timestamp

        val duration = measureNanoTime {
            testedMonitor.startView(key, name, attributes)
            viewId = GlobalRum.getRumContext().viewId
            testedMonitor.stopView(key, emptyMap())
        }

        checkNotNull(viewId)
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())

            assertThat(lastValue)
                .hasTimestamp(timestamp)
                .hasAttributes(attributes)
                .hasViewData {
                    hasName(name.replace('.', '/'))
                    hasDurationLowerThan(duration)
                    hasVersion(2)
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }
        }
        assertThat(GlobalRum.getRumContext().viewId)
            .isNull()
    }

    @Test
    fun `stopView sends unclosed view Rum Event with missing key`(
        forge: Forge
    ) {
        val timestamp = forge.aTimestamp()
        var key = forge.anAsciiString().toByteArray()
        val name = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        val attributes = forge.exhaustiveAttributes()
        var viewId: UUID? = null
        whenever(mockTimeProvider.getServerTimestamp()) doReturn timestamp

        val duration = measureNanoTime {
            testedMonitor.startView(key, name, attributes)
            viewId = GlobalRum.getRumContext().viewId
            key = forge.anAsciiString().toByteArray()
            testedMonitor.setFieldValue("activeViewKey", WeakReference(null))
            testedMonitor.stopView(key, emptyMap())
        }

        checkNotNull(viewId)
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())

            assertThat(lastValue)
                .hasTimestamp(timestamp)
                .hasAttributes(attributes)
                .hasAttributes(mapOf(RumEventSerializer.TAG_EVENT_UNSTOPPED to true))
                .hasViewData {
                    hasName(name.replace('.', '/'))
                    hasDurationLowerThan(duration)
                    hasVersion(2)
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }
        }
        assertThat(GlobalRum.getRumContext().viewId)
            .isNull()
    }

    @Test
    fun `startResource doesn't send anything without stopResource`(
        forge: Forge
    ) {
        val viewKey = forge.anAsciiString()
        val viewName = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        val resourceKey = forge.anAsciiString()
        val resourceUrl = forge.aStringMatching("http(s?)://[a-z]+.com/[a-z]+")

        testedMonitor.startView(viewKey, viewName, emptyMap())
        testedMonitor.startResource(resourceKey, resourceUrl, emptyMap())

        verifyZeroInteractions(mockWriter)
    }

    @Test
    fun `stopResource doesn't send anything without startResource`(
        forge: Forge
    ) {
        val resourceKey = forge.anAsciiString()
        val resourceKind = forge.aValueFrom(RumResourceKind::class.java)

        testedMonitor.stopResource(resourceKey, resourceKind, emptyMap())

        verifyZeroInteractions(mockWriter)
    }

    @Test
    fun `stopResource sends resource Rum Event`(
        forge: Forge
    ) {
        val timestamp = forge.aTimestamp()
        val viewKey = forge.anAsciiString()
        val viewName = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        val resourceKey = forge.anAsciiString()
        val resourceUrl = forge.aStringMatching("http(s?)://[a-z]+.com/[a-z]+")
        val resourceKind = forge.aValueFrom(RumResourceKind::class.java)
        val attributes = forge.exhaustiveAttributes()
        whenever(mockTimeProvider.getServerTimestamp()) doReturn timestamp

        testedMonitor.startView(viewKey, viewName, emptyMap())
        val viewId = GlobalRum.getRumContext().viewId
        val duration = measureNanoTime {
            testedMonitor.startResource(resourceKey, resourceUrl, attributes)
            testedMonitor.stopResource(resourceKey, resourceKind, emptyMap())
        }

        checkNotNull(viewId)
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())

            assertThat(lastValue)
                .hasTimestamp(timestamp)
                .hasAttributes(attributes)
                .hasResourceData {
                    hasUrl(resourceUrl)
                    hasDurationLowerThan(duration)
                    hasKind(resourceKind)
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }
        }
        assertThat(GlobalRum.getRumContext().viewId)
            .isEqualTo(viewId)
    }

    @Test
    fun `stopResource sends resource Rum Event automatically when key reference is null`(
        forge: Forge
    ) {
        val timestamp = forge.aTimestamp()
        val viewKey = forge.anAsciiString()
        val viewName = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        var resourceKey: Any = forge.anAsciiString().toByteArray()
        val resourceUrl = forge.aStringMatching("http(s?)://[a-z]+.com/[a-z]+")
        val attributes = forge.exhaustiveAttributes()
        val kind = forge.aValueFrom(RumResourceKind::class.java, listOf(RumResourceKind.UNKNOWN))
        whenever(mockTimeProvider.getServerTimestamp()) doReturn timestamp

        testedMonitor.startView(viewKey, viewName, emptyMap())
        val viewId = GlobalRum.getRumContext().viewId
        testedMonitor.startResource(resourceKey, resourceUrl, attributes)
        resourceKey = forge.anAsciiString().toByteArray()
        System.gc()
        testedMonitor.stopResource(resourceKey, kind, attributes)

        checkNotNull(viewId)
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())

            assertThat(lastValue)
                .hasTimestamp(timestamp)
                .hasAttributes(attributes)
                .hasAttributes(mapOf(RumEventSerializer.TAG_EVENT_UNSTOPPED to true))
                .hasResourceData {
                    hasUrl(resourceUrl)
                    hasKind(RumResourceKind.UNKNOWN)
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }
        }
        assertThat(GlobalRum.getRumContext().viewId)
            .isEqualTo(viewId)
    }

    @Test
    fun `stopResourceWithError doesn't send anything without startResource`(
        forge: Forge
    ) {
        val resourceKey = forge.anAsciiString()
        val message = forge.anAlphabeticalString()
        val origin = forge.anAlphabeticalString()
        val error = forge.aThrowable()

        testedMonitor.stopResourceWithError(resourceKey, message, origin, error)

        verifyZeroInteractions(mockWriter)
    }

    @Test
    fun `stopResourceWithError sends error Rum Event`(
        forge: Forge
    ) {
        val timestamp = forge.aTimestamp()
        val viewKey = forge.anAsciiString()
        val viewName = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        val resourceKey = forge.anAsciiString()
        val resourceUrl = forge.aStringMatching("http(s?)://[a-z]+.com/[a-z]+")
        val errorOrigin = forge.anAlphabeticalString()
        val errorMessage = forge.anAlphabeticalString()
        val throwable = forge.aThrowable()
        val attributes = forge.exhaustiveAttributes()
        whenever(mockTimeProvider.getServerTimestamp()) doReturn timestamp

        testedMonitor.startView(viewKey, viewName, emptyMap())
        val viewId = GlobalRum.getRumContext().viewId
        testedMonitor.startResource(resourceKey, resourceUrl, attributes)
        testedMonitor.stopResourceWithError(resourceKey, errorMessage, errorOrigin, throwable)

        checkNotNull(viewId)
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())

            assertThat(lastValue)
                .hasTimestamp(timestamp)
                .hasAttributes(attributes)
                .hasErrorData {
                    hasOrigin(errorOrigin)
                    hasMessage(errorMessage)
                    hasThrowable(throwable)
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }
        }
        assertThat(GlobalRum.getRumContext().viewId)
            .isEqualTo(viewId)
    }

    @Test
    fun `stopResourceWithError sends resource Rum Event automatically when key reference is null`(
        forge: Forge
    ) {
        val timestamp = forge.aTimestamp()
        val viewKey = forge.anAsciiString()
        val viewName = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        var resourceKey: Any = forge.anAsciiString().toByteArray()
        val resourceUrl = forge.aStringMatching("http(s?)://[a-z]+.com/[a-z]+")
        val attributes = forge.exhaustiveAttributes()
        val errorOrigin = forge.anAlphabeticalString()
        val errorMessage = forge.anAlphabeticalString()
        val throwable = forge.aThrowable()
        whenever(mockTimeProvider.getServerTimestamp()) doReturn timestamp

        testedMonitor.startView(viewKey, viewName, emptyMap())
        val viewId = GlobalRum.getRumContext().viewId
        testedMonitor.startResource(resourceKey, resourceUrl, attributes)
        resourceKey = forge.anAsciiString().toByteArray()
        System.gc()
        testedMonitor.stopResourceWithError(resourceKey, errorMessage, errorOrigin, throwable)

        checkNotNull(viewId)
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())

            assertThat(lastValue)
                .hasTimestamp(timestamp)
                .hasAttributes(attributes)
                .hasAttributes(mapOf(RumEventSerializer.TAG_EVENT_UNSTOPPED to true))
                .hasResourceData {
                    hasUrl(resourceUrl)
                    hasKind(RumResourceKind.UNKNOWN)
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }
        }
        assertThat(GlobalRum.getRumContext().viewId)
            .isEqualTo(viewId)
    }

    @Test
    fun `addUserAction sends action Rum Event`(
        forge: Forge
    ) {
        val timestamp = forge.aTimestamp()
        val viewKey = forge.anAsciiString()
        val viewName = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        val actionNAme = forge.anAsciiString()
        val attributes = forge.exhaustiveAttributes()
        whenever(mockTimeProvider.getServerTimestamp()) doReturn timestamp

        testedMonitor.startView(viewKey, viewName, emptyMap())
        val viewId = GlobalRum.getRumContext().viewId
        testedMonitor.addUserAction(actionNAme, attributes)

        checkNotNull(viewId)
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())

            assertThat(lastValue)
                .hasTimestamp(timestamp)
                .hasAttributes(attributes)
                .hasUserActionData {
                    hasName(actionNAme)
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }
        }
        assertThat(GlobalRum.getRumContext().viewId)
            .isEqualTo(viewId)
    }
}
