/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.rum.internal.monitor

import android.util.Log
import com.datadog.android.Datadog
import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.log.internal.user.UserInfo
import com.datadog.android.log.internal.user.UserInfoProvider
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.assertj.RumEventAssert.Companion.assertThat
import com.datadog.android.rum.internal.domain.RumEvent
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.forge.exhaustiveAttributes
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.forge.aThrowable
import com.datadog.tools.unit.setFieldValue
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
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
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogRumMonitorTest {

    lateinit var testedMonitor: RumMonitor

    @Mock
    lateinit var mockWriter: Writer<RumEvent>

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @Mock
    lateinit var mockUserInfoProvider: UserInfoProvider

    lateinit var mockedUserInfo: UserInfo

    @Forgery
    lateinit var fakeApplicationId: UUID

    @LongForgery(min = 0L)
    var fakeTimestamp: Long = 0L

    @BeforeEach
    fun `set up`(forge: Forge) {
        Datadog.setVerbosity(Log.VERBOSE)
        GlobalRum.updateApplicationId(fakeApplicationId)
        mockedUserInfo = UserInfo(
            forge.aString(),
            forge.aString(),
            forge.aStringMatching("[a-z0-9]+@[a-z0-9]+\\.com")
        )
        whenever(mockUserInfoProvider.getUserInfo()).thenReturn(mockedUserInfo)
        whenever(mockTimeProvider.getDeviceTimestamp()) doReturn fakeTimestamp
        testedMonitor = DatadogRumMonitor(mockWriter, mockTimeProvider, mockUserInfoProvider)
    }

    // region View

    @Test
    fun `startView doesn't send anything without stopView and updates global context`(
        forge: Forge
    ) {
        val key = forge.anAlphabeticalString()
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
        val key = forge.anAlphabeticalString()
        val name = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        val attributes = forge.exhaustiveAttributes()
        var viewId: UUID? = null

        val duration = measureNanoTime {
            testedMonitor.startView(key, name, attributes)
            viewId = GlobalRum.getRumContext().viewId
            testedMonitor.startView(
                forge.anAlphabeticalString(),
                forge.aStringMatching("[a-z]+(\\.[a-z]+)+"),
                emptyMap()
            )
        }

        checkNotNull(viewId)
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())

            assertThat(lastValue)
                .hasTimestamp(fakeTimestamp)
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
        val key = forge.anAlphabeticalString()

        testedMonitor.stopView(key, emptyMap())

        verifyZeroInteractions(mockWriter)
        assertThat(GlobalRum.getRumContext().viewId)
            .isNull()
    }

    @Test
    fun `stopView doesn't send anything without matching startView`(
        forge: Forge
    ) {
        val startKey = forge.anAlphabeticalString()
        val name = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        val stopKey = forge.anAlphabeticalString()

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
        val key = forge.anAlphabeticalString()
        val name = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        val attributes = forge.exhaustiveAttributes()
        var viewId: UUID? = null

        val duration = measureNanoTime {
            testedMonitor.startView(key, name, attributes)
            viewId = GlobalRum.getRumContext().viewId
            testedMonitor.stopView(key, emptyMap())
        }

        checkNotNull(viewId)
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())

            assertThat(lastValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
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
    fun `stopView sends view Rum Event only once`(
        forge: Forge
    ) {
        val key = forge.anAlphabeticalString()
        val name = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        val attributes = forge.exhaustiveAttributes()
        var viewId: UUID? = null

        val duration = measureNanoTime {
            testedMonitor.startView(key, name, attributes)
            viewId = GlobalRum.getRumContext().viewId
            testedMonitor.stopView(key, emptyMap())
            testedMonitor.stopView(key, emptyMap())
        }

        checkNotNull(viewId)
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())

            assertThat(lastValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
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
        var key = forge.anAlphabeticalString().toByteArray()
        val name = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        val attributes = forge.exhaustiveAttributes()
        var viewId: UUID? = null

        val duration = measureNanoTime {
            testedMonitor.startView(key, name, attributes)
            viewId = GlobalRum.getRumContext().viewId
            key = forge.anAlphabeticalString().toByteArray()
            testedMonitor.setFieldValue("activeViewKey", WeakReference(null))
            testedMonitor.stopView(key, emptyMap())
        }

        checkNotNull(viewId)
        argumentCaptor<RumEvent> {
            verify(mockWriter).write(capture())

            assertThat(lastValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasAttributes(attributes)
                .hasAttributes(mapOf(DatadogRumMonitor.TAG_EVENT_UNSTOPPED to true))
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
    fun `stopView sends unclosed resource Rum Event with missing key`(
        forge: Forge
    ) {
        val viewKey = forge.anAlphabeticalString()
        val viewName = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        var resourceKey = forge.anAlphabeticalString(size = 32).toByteArray()
        val resourceMethod = forge.anElementFrom("GET", "PUT", "POST", "DELETE")
        val resourceUrl = forge.aStringMatching("http(s?)://[a-z]+.com/[a-z]+")
        val attributes = forge.exhaustiveAttributes()
        var viewId: UUID? = null

        testedMonitor.startView(viewKey, viewName, attributes)
        testedMonitor.startResource(resourceKey, resourceMethod, resourceUrl, attributes)
        resourceKey = forge.anAlphabeticalString().toByteArray()
        System.gc()
        viewId = GlobalRum.getRumContext().viewId
        testedMonitor.stopView(viewKey, emptyMap())

        checkNotNull(viewId)
        argumentCaptor<RumEvent> {
            verify(mockWriter, times(3)).write(capture())
            assertThat(firstValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasAttributes(attributes)
                .hasAttributes(mapOf(DatadogRumMonitor.TAG_EVENT_UNSTOPPED to true))
                .hasResourceData {
                    hasUrl(resourceUrl)
                    hasMethod(resourceMethod)
                    hasKind(RumResourceKind.UNKNOWN)
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }

            assertThat(secondValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasAttributes(attributes)
                .hasViewData {
                    hasName(viewName.replace('.', '/'))
                    hasVersion(2)
                    hasMeasures {
                        hasErrorCount(0)
                        hasResourceCount(1)
                        hasUserActionCount(0)
                    }
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }

            assertThat(lastValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasAttributes(attributes)
                .hasViewData {
                    hasName(viewName.replace('.', '/'))
                    hasVersion(3)
                    hasMeasures {
                        hasErrorCount(0)
                        hasResourceCount(1)
                        hasUserActionCount(0)
                    }
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }
        }
        assertThat(GlobalRum.getRumContext().viewId)
            .isNull()
    }

    // endregion

    // region Resource

    @Test
    fun `startResource doesn't send anything without stopResource`(
        forge: Forge
    ) {
        val viewKey = forge.anAlphabeticalString()
        val viewName = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        val resourceKey = forge.anAlphabeticalString()
        val resourceMethod = forge.anElementFrom("GET", "PUT", "POST", "DELETE")
        val resourceUrl = forge.aStringMatching("http(s?)://[a-z]+.com/[a-z]+")

        testedMonitor.startView(viewKey, viewName, emptyMap())
        testedMonitor.startResource(resourceKey, resourceMethod, resourceUrl, emptyMap())

        verifyZeroInteractions(mockWriter)
    }

    @Test
    fun `stopResource doesn't send anything without startResource`(
        forge: Forge
    ) {
        val resourceKey = forge.anAlphabeticalString()
        val resourceKind = forge.aValueFrom(RumResourceKind::class.java)

        testedMonitor.stopResource(resourceKey, resourceKind, emptyMap())

        verifyZeroInteractions(mockWriter)
    }

    @Test
    fun `stopResource sends resource Rum Event and updates view event`(
        forge: Forge
    ) {
        val viewKey = forge.anAlphabeticalString()
        val viewName = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        val resourceKey = forge.anAlphabeticalString()
        val resourceMethod = forge.anElementFrom("GET", "PUT", "POST", "DELETE")
        val resourceUrl = forge.aStringMatching("http(s?)://[a-z]+.com/[a-z]+")
        val resourceKind = forge.aValueFrom(RumResourceKind::class.java)
        val attributes = forge.exhaustiveAttributes()

        testedMonitor.startView(viewKey, viewName, emptyMap())
        val viewId = GlobalRum.getRumContext().viewId
        val duration = measureNanoTime {
            testedMonitor.startResource(resourceKey, resourceMethod, resourceUrl, attributes)
            testedMonitor.stopResource(resourceKey, resourceKind, emptyMap())
        }

        checkNotNull(viewId)
        argumentCaptor<RumEvent> {
            verify(mockWriter, times(2)).write(capture())

            assertThat(firstValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasAttributes(attributes)
                .hasResourceData {
                    hasUrl(resourceUrl)
                    hasDurationLowerThan(duration)
                    hasKind(resourceKind)
                    hasMethod(resourceMethod)
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }
            assertThat(lastValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasViewData {
                    hasName(viewName.replace('.', '/'))
                    hasVersion(2)
                    hasMeasures {
                        hasErrorCount(0)
                        hasResourceCount(1)
                        hasUserActionCount(0)
                    }
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
    fun `stopResource sends unclosed resource Rum Event automatically when key reference is null`(
        forge: Forge
    ) {
        val viewKey = forge.anAlphabeticalString()
        val viewName = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        var resourceKey: Any = forge.anAlphabeticalString().toByteArray()
        val resourceMethod = forge.anElementFrom("GET", "PUT", "POST", "DELETE")
        val resourceUrl = forge.aStringMatching("http(s?)://[a-z]+.com/[a-z]+")
        val attributes = forge.exhaustiveAttributes()
        val kind = forge.aValueFrom(RumResourceKind::class.java, listOf(RumResourceKind.UNKNOWN))

        testedMonitor.startView(viewKey, viewName, emptyMap())
        val viewId = GlobalRum.getRumContext().viewId
        testedMonitor.startResource(resourceKey, resourceMethod, resourceUrl, attributes)
        resourceKey = forge.anAlphabeticalString().toByteArray()
        System.gc()
        testedMonitor.stopResource(resourceKey, kind, attributes)

        checkNotNull(viewId)
        argumentCaptor<RumEvent> {
            verify(mockWriter, times(2)).write(capture())

            assertThat(firstValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasAttributes(attributes)
                .hasAttributes(mapOf(DatadogRumMonitor.TAG_EVENT_UNSTOPPED to true))
                .hasResourceData {
                    hasUrl(resourceUrl)
                    hasMethod(resourceMethod)
                    hasKind(RumResourceKind.UNKNOWN)
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }

            assertThat(lastValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasViewData {
                    hasName(viewName.replace('.', '/'))
                    hasVersion(2)
                    hasMeasures {
                        hasErrorCount(0)
                        hasResourceCount(1)
                        hasUserActionCount(0)
                    }
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
        val resourceKey = forge.anAlphabeticalString()
        val message = forge.anAlphabeticalString()
        val origin = forge.anAlphabeticalString()
        val error = forge.aThrowable()

        testedMonitor.stopResourceWithError(resourceKey, message, origin, error)

        verifyZeroInteractions(mockWriter)
    }

    @Test
    fun `stopResourceWithError sends error Rum Event and updates view`(
        forge: Forge
    ) {
        val viewKey = forge.anAlphabeticalString()
        val viewName = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        val resourceKey = forge.anAlphabeticalString()
        val resourceMethod = forge.anElementFrom("GET", "PUT", "POST", "DELETE")
        val resourceUrl = forge.aStringMatching("http(s?)://[a-z]+.com/[a-z]+")
        val errorOrigin = forge.anAlphabeticalString()
        val errorMessage = forge.anAlphabeticalString()
        val throwable = forge.aThrowable()
        val attributes = forge.exhaustiveAttributes()

        testedMonitor.startView(viewKey, viewName, emptyMap())
        val viewId = GlobalRum.getRumContext().viewId
        testedMonitor.startResource(resourceKey, resourceMethod, resourceUrl, attributes)
        testedMonitor.stopResourceWithError(resourceKey, errorMessage, errorOrigin, throwable)

        checkNotNull(viewId)
        argumentCaptor<RumEvent> {
            verify(mockWriter, times(2)).write(capture())

            assertThat(firstValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
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

            assertThat(lastValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasViewData {
                    hasName(viewName.replace('.', '/'))
                    hasVersion(2)
                    hasMeasures {
                        hasErrorCount(1)
                        hasResourceCount(0)
                        hasUserActionCount(0)
                    }
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
        val viewKey = forge.anAlphabeticalString()
        val viewName = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        var resourceKey: Any = forge.anAlphabeticalString().toByteArray()
        val resourceMethod = forge.anElementFrom("GET", "PUT", "POST", "DELETE")
        val resourceUrl = forge.aStringMatching("http(s?)://[a-z]+.com/[a-z]+")
        val attributes = forge.exhaustiveAttributes()
        val errorOrigin = forge.anAlphabeticalString()
        val errorMessage = forge.anAlphabeticalString()
        val throwable = forge.aThrowable()

        testedMonitor.startView(viewKey, viewName, emptyMap())
        val viewId = GlobalRum.getRumContext().viewId
        testedMonitor.startResource(resourceKey, resourceMethod, resourceUrl, attributes)
        resourceKey = forge.anAlphabeticalString().toByteArray()
        System.gc()
        testedMonitor.stopResourceWithError(resourceKey, errorMessage, errorOrigin, throwable)

        checkNotNull(viewId)
        argumentCaptor<RumEvent> {
            verify(mockWriter, times(2)).write(capture())

            assertThat(firstValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasAttributes(attributes)
                .hasAttributes(mapOf(DatadogRumMonitor.TAG_EVENT_UNSTOPPED to true))
                .hasResourceData {
                    hasUrl(resourceUrl)
                    hasKind(RumResourceKind.UNKNOWN)
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }

            assertThat(lastValue)
                .hasTimestamp(fakeTimestamp)
                .hasViewData {
                    hasName(viewName.replace('.', '/'))
                    hasVersion(2)
                    hasMeasures {
                        hasErrorCount(0)
                        hasResourceCount(1)
                        hasUserActionCount(0)
                    }
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }
        }
        assertThat(GlobalRum.getRumContext().viewId)
            .isEqualTo(viewId)
    }

    // endregion

    // region Error

    @Test
    fun `addError sends error Rum Event and updates view`(
        forge: Forge
    ) {
        val viewKey = forge.anAlphabeticalString()
        val viewName = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        val attributes = forge.exhaustiveAttributes()
        val errorOrigin = forge.anAlphabeticalString()
        val errorMessage = forge.anAlphabeticalString()
        val throwable = forge.aThrowable()

        testedMonitor.startView(viewKey, viewName, emptyMap())
        val viewId = GlobalRum.getRumContext().viewId
        testedMonitor.addError(errorMessage, errorOrigin, throwable, attributes)

        checkNotNull(viewId)
        argumentCaptor<RumEvent> {
            verify(mockWriter, times(2)).write(capture())

            assertThat(firstValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasAttributes(attributes)
                .hasErrorData {
                    hasMessage(errorMessage)
                    hasOrigin(errorOrigin)
                    hasThrowable(throwable)
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }

            assertThat(lastValue)
                .hasTimestamp(fakeTimestamp)
                .hasViewData {
                    hasMeasures {
                        hasErrorCount(1)
                        hasResourceCount(0)
                        hasUserActionCount(0)
                    }
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }
        }
        assertThat(GlobalRum.getRumContext().viewId)
            .isEqualTo(viewId)
    }

    // endregion

    // region User action

    @Test
    fun `addUserAction doesn't send anything`(
        forge: Forge
    ) {
        val viewKey = forge.anAlphabeticalString()
        val viewName = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        val actionNAme = forge.anAlphabeticalString()
        val attributes = forge.exhaustiveAttributes()

        testedMonitor.startView(viewKey, viewName, emptyMap())
        testedMonitor.addUserAction(actionNAme, attributes)

        verifyZeroInteractions(mockWriter)
    }

    @Test
    fun `resource started within userAction scope have action id`(
        forge: Forge
    ) {
        val viewKey = forge.anAlphabeticalString()
        val viewName = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        val actionNAme = forge.anAlphabeticalString()
        val resourceKey = forge.anAlphabeticalString()
        val resourceMethod = forge.anElementFrom("GET", "PUT", "POST", "DELETE")
        val resourceUrl = forge.aStringMatching("http(s?)://[a-z]+.com/[a-z]+")
        val resourceKind = forge.aValueFrom(RumResourceKind::class.java)
        val attributes = forge.exhaustiveAttributes()

        testedMonitor.startView(viewKey, viewName, emptyMap())
        testedMonitor.addUserAction(actionNAme, attributes)
        val viewId = GlobalRum.getRumContext().viewId
        testedMonitor.startResource(resourceKey, resourceMethod, resourceUrl, attributes)
        testedMonitor.stopResource(resourceKey, resourceKind, emptyMap())

        checkNotNull(viewId)
        argumentCaptor<RumEvent> {
            verify(mockWriter, times(2)).write(capture())

            assertThat(firstValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasAttributes(attributes)
                .hasUserActionAttribute()
                .hasResourceData {
                    hasUrl(resourceUrl)
                    hasMethod(resourceMethod)
                    hasKind(resourceKind)
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }
            assertThat(lastValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasViewData {
                    hasName(viewName.replace('.', '/'))
                    hasVersion(2)
                    hasMeasures {
                        hasErrorCount(0)
                        hasResourceCount(1)
                        hasUserActionCount(0)
                    }
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
    fun `resource started outside userAction scope has no action and sends action`(
        forge: Forge
    ) {
        val viewKey = forge.anAlphabeticalString()
        val viewName = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        val actionName = forge.anAlphabeticalString()
        val resourceKey = forge.anAlphabeticalString()
        val resourceMethod = forge.anElementFrom("GET", "PUT", "POST", "DELETE")
        val resourceUrl = forge.aStringMatching("http(s?)://[a-z]+.com/[a-z]+")
        val resourceKind = forge.aValueFrom(RumResourceKind::class.java)
        val attributes = forge.exhaustiveAttributes()

        testedMonitor.startView(viewKey, viewName, emptyMap())
        testedMonitor.addUserAction(actionName, attributes)
        Thread.sleep(DatadogRumMonitor.ACTION_INACTIVITY_MS)
        val viewId = GlobalRum.getRumContext().viewId
        val duration = measureNanoTime {
            testedMonitor.startResource(resourceKey, resourceMethod, resourceUrl, attributes)
            testedMonitor.stopResource(resourceKey, resourceKind, emptyMap())
        }

        checkNotNull(viewId)
        argumentCaptor<RumEvent> {
            verify(mockWriter, times(4)).write(capture())
            assertThat(firstValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasAttributes(attributes)
                .hasUserActionData {
                    hasName(actionName)
                    hasNonDefaultId()
                    hasDuration(1L)
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }

            assertThat(secondValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasViewData {
                    hasMeasures {
                        hasErrorCount(0)
                        hasResourceCount(0)
                        hasUserActionCount(1)
                    }
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }

            assertThat(thirdValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasAttributes(attributes)
                .hasNoUserActionAttribute()
                .hasResourceData {
                    hasUrl(resourceUrl)
                    hasMethod(resourceMethod)
                    hasDurationLowerThan(duration)
                    hasKind(resourceKind)
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }

            assertThat(lastValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasViewData {
                    hasName(viewName.replace('.', '/'))
                    hasVersion(3)
                    hasMeasures {
                        hasErrorCount(0)
                        hasResourceCount(1)
                        hasUserActionCount(1)
                    }
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
    fun `resource started within userAction scope and stopped later extends scope`(
        forge: Forge
    ) {
        val viewKey = forge.anAlphabeticalString()
        val viewName = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        val actionNAme = forge.anAlphabeticalString()
        val resourceKey = forge.anAlphabeticalString()
        val resourceMethod = forge.anElementFrom("GET", "PUT", "POST", "DELETE")
        val resourceUrl = forge.aStringMatching("http(s?)://[a-z]+.com/[a-z]+")
        val resourceKind = forge.aValueFrom(RumResourceKind::class.java)
        val attributes = forge.exhaustiveAttributes()

        testedMonitor.startView(viewKey, viewName, emptyMap())
        testedMonitor.addUserAction(actionNAme, attributes)
        val viewId = GlobalRum.getRumContext().viewId
        testedMonitor.startResource(resourceKey, resourceMethod, resourceUrl, attributes)
        Thread.sleep(DatadogRumMonitor.ACTION_INACTIVITY_MS * 5)
        testedMonitor.stopResource(resourceKey, resourceKind, emptyMap())
        val resourceKey2 = forge.anAlphabeticalString()
        testedMonitor.startResource(resourceKey2, resourceMethod, resourceUrl, attributes)

        checkNotNull(viewId)
        argumentCaptor<RumEvent> {
            verify(mockWriter, times(2)).write(capture())

            assertThat(firstValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasAttributes(attributes)
                .hasUserActionAttribute()
                .hasResourceData {
                    hasUrl(resourceUrl)
                    hasMethod(resourceMethod)
                    hasKind(resourceKind)
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }
            assertThat(lastValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasViewData {
                    hasName(viewName.replace('.', '/'))
                    hasVersion(2)
                    hasMeasures {
                        hasErrorCount(0)
                        hasResourceCount(1)
                        hasUserActionCount(0)
                    }
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
    fun `resource started within userAction scope and stopped later extends scope only by 100ms`(
        forge: Forge
    ) {
        val viewKey = forge.anAlphabeticalString()
        val viewName = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        val actionNAme = forge.anAlphabeticalString()
        val resourceKey = forge.anAlphabeticalString()
        val resourceMethod = forge.anElementFrom("GET", "PUT", "POST", "DELETE")
        val resourceUrl = forge.aStringMatching("http(s?)://[a-z]+.com/[a-z]+")
        val resourceKind = forge.aValueFrom(RumResourceKind::class.java)
        val errorOrigin = forge.anAlphabeticalString()
        val errorMessage = forge.anAlphabeticalString()
        val throwable = forge.aThrowable()
        val attributes = forge.exhaustiveAttributes()

        testedMonitor.startView(viewKey, viewName, emptyMap())
        testedMonitor.addUserAction(actionNAme, attributes)
        val viewId = GlobalRum.getRumContext().viewId
        testedMonitor.startResource(resourceKey, resourceMethod, resourceUrl, attributes)
        Thread.sleep(DatadogRumMonitor.ACTION_INACTIVITY_MS * 5)
        testedMonitor.stopResource(resourceKey, resourceKind, emptyMap())
        Thread.sleep(DatadogRumMonitor.ACTION_INACTIVITY_MS)
        testedMonitor.addError(errorMessage, errorOrigin, throwable, attributes)

        checkNotNull(viewId)
        argumentCaptor<RumEvent> {
            verify(mockWriter, times(6)).write(capture())

            assertThat(firstValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasAttributes(attributes)
                .hasUserActionAttribute()
                .hasResourceData {
                    hasUrl(resourceUrl)
                    hasKind(resourceKind)
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }
            assertThat(secondValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasViewData {
                    hasName(viewName.replace('.', '/'))
                    hasVersion(2)
                    hasMeasures {
                        hasErrorCount(0)
                        hasResourceCount(1)
                        hasUserActionCount(0)
                    }
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }
            assertThat(thirdValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasAttributes(attributes)
                .hasUserActionData {
                    hasName(actionNAme)
                    hasNonDefaultId()
                    hasDurationLowerThan(DatadogRumMonitor.ACTION_INACTIVITY_NS * 6)
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }

            assertThat(allValues[3])
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasViewData {
                    hasVersion(3)
                    hasMeasures {
                        hasErrorCount(0)
                        hasResourceCount(1)
                        hasUserActionCount(1)
                    }
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }

            assertThat(allValues[4])
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasAttributes(attributes)
                .hasNoUserActionAttribute()
                .hasErrorData {
                    hasMessage(errorMessage)
                    hasOrigin(errorOrigin)
                    hasThrowable(throwable)
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }

            assertThat(lastValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasViewData {
                    hasVersion(4)
                    hasMeasures {
                        hasErrorCount(1)
                        hasResourceCount(1)
                        hasUserActionCount(1)
                    }
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
    fun `resource started within userAction scope and stopped with error later extends scope`(
        forge: Forge
    ) {
        val viewKey = forge.anAlphabeticalString()
        val viewName = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        val actionNAme = forge.anAlphabeticalString()
        val resourceKey = forge.anAlphabeticalString()
        val resourceMethod = forge.anElementFrom("GET", "PUT", "POST", "DELETE")
        val resourceUrl = forge.aStringMatching("http(s?)://[a-z]+.com/[a-z]+")
        val errorOrigin = forge.anAlphabeticalString()
        val errorMessage = forge.anAlphabeticalString()
        val errorThrowable = forge.aThrowable()
        val attributes = forge.exhaustiveAttributes()

        testedMonitor.startView(viewKey, viewName, emptyMap())
        testedMonitor.addUserAction(actionNAme, attributes)
        val viewId = GlobalRum.getRumContext().viewId
        testedMonitor.startResource(resourceKey, resourceMethod, resourceUrl, attributes)
        Thread.sleep(DatadogRumMonitor.ACTION_INACTIVITY_MS * 5)
        testedMonitor.stopResourceWithError(resourceKey, errorMessage, errorOrigin, errorThrowable)
        val resourceKey2 = forge.anAlphabeticalString()
        testedMonitor.startResource(resourceKey2, resourceMethod, resourceUrl, attributes)

        checkNotNull(viewId)
        argumentCaptor<RumEvent> {
            verify(mockWriter, times(2)).write(capture())

            assertThat(firstValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasAttributes(attributes)
                .hasUserActionAttribute()
                .hasErrorData {
                    hasMessage(errorMessage)
                    hasOrigin(errorOrigin)
                    hasThrowable(errorThrowable)
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }
            assertThat(lastValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasViewData {
                    hasName(viewName.replace('.', '/'))
                    hasVersion(2)
                    hasMeasures {
                        hasErrorCount(1)
                        hasResourceCount(0)
                        hasUserActionCount(0)
                    }
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
    fun `resource started and stopped with error extends scope only by 100ms`(
        forge: Forge
    ) {
        val viewKey = forge.anAlphabeticalString()
        val viewName = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        val actionNAme = forge.anAlphabeticalString()
        val resourceKey = forge.anAlphabeticalString()
        val resourceMethod = forge.anElementFrom("GET", "PUT", "POST", "DELETE")
        val resourceUrl = forge.aStringMatching("http(s?)://[a-z]+.com/[a-z]+")
        val resErrorOrigin = forge.anAlphabeticalString()
        val resErrorMessage = forge.anAlphabeticalString()
        val resErrorThrowable = forge.aThrowable()
        val errorOrigin = forge.anAlphabeticalString()
        val errorMessage = forge.anAlphabeticalString()
        val errorThrowable = forge.aThrowable()
        val attributes = forge.exhaustiveAttributes()

        testedMonitor.startView(viewKey, viewName, emptyMap())
        testedMonitor.addUserAction(actionNAme, attributes)
        val viewId = GlobalRum.getRumContext().viewId
        testedMonitor.startResource(resourceKey, resourceMethod, resourceUrl, attributes)
        Thread.sleep(DatadogRumMonitor.ACTION_INACTIVITY_MS * 5)
        testedMonitor.stopResourceWithError(
            resourceKey,
            resErrorMessage,
            resErrorOrigin,
            resErrorThrowable
        )
        Thread.sleep(DatadogRumMonitor.ACTION_INACTIVITY_MS)
        testedMonitor.addError(errorMessage, errorOrigin, errorThrowable, attributes)

        checkNotNull(viewId)
        argumentCaptor<RumEvent> {
            verify(mockWriter, times(6)).write(capture())

            assertThat(firstValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasAttributes(attributes)
                .hasUserActionAttribute()
                .hasErrorData {
                    hasMessage(resErrorMessage)
                    hasOrigin(resErrorOrigin)
                    hasThrowable(resErrorThrowable)
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }
            assertThat(secondValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasViewData {
                    hasName(viewName.replace('.', '/'))
                    hasVersion(2)
                    hasMeasures {
                        hasErrorCount(1)
                        hasResourceCount(0)
                        hasUserActionCount(0)
                    }
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }
            assertThat(thirdValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasAttributes(attributes)
                .hasUserActionData {
                    hasName(actionNAme)
                    hasNonDefaultId()
                    hasDurationLowerThan(DatadogRumMonitor.ACTION_INACTIVITY_NS * 6)
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }

            assertThat(allValues[3])
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasViewData {
                    hasVersion(3)
                    hasMeasures {
                        hasErrorCount(1)
                        hasResourceCount(0)
                        hasUserActionCount(1)
                    }
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }

            assertThat(allValues[4])
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasAttributes(attributes)
                .hasNoUserActionAttribute()
                .hasErrorData {
                    hasMessage(errorMessage)
                    hasOrigin(errorOrigin)
                    hasThrowable(errorThrowable)
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }

            assertThat(lastValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasViewData {
                    hasVersion(4)
                    hasMeasures {
                        hasErrorCount(2)
                        hasResourceCount(0)
                        hasUserActionCount(1)
                    }
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
    fun `resource started within userAction scope and unstopped yet extends scope`(
        forge: Forge
    ) {
        val viewKey = forge.anAlphabeticalString()
        val viewName = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        val actionNAme = forge.anAlphabeticalString()
        val resourceKey = forge.anAlphabeticalString()
        val resourceMethod = forge.anElementFrom("GET", "PUT", "POST", "DELETE")
        val resourceUrl = forge.aStringMatching("http(s?)://[a-z]+.com/[a-z]+")
        val errorOrigin = forge.anAlphabeticalString()
        val errorMessage = forge.anAlphabeticalString()
        val throwable = forge.aThrowable()
        val attributes = forge.exhaustiveAttributes()

        testedMonitor.startView(viewKey, viewName, emptyMap())
        testedMonitor.addUserAction(actionNAme, attributes)
        val viewId = GlobalRum.getRumContext().viewId
        testedMonitor.startResource(resourceKey, resourceMethod, resourceUrl, attributes)
        Thread.sleep(DatadogRumMonitor.ACTION_INACTIVITY_MS * 5)
        testedMonitor.addError(errorMessage, errorOrigin, throwable, attributes)

        checkNotNull(viewId)
        argumentCaptor<RumEvent> {
            verify(mockWriter, times(2)).write(capture())

            assertThat(firstValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasAttributes(attributes)
                .hasUserActionAttribute()
                .hasErrorData {
                    hasMessage(errorMessage)
                    hasOrigin(errorOrigin)
                    hasThrowable(throwable)
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }
            assertThat(lastValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasViewData {
                    hasName(viewName.replace('.', '/'))
                    hasVersion(2)
                    hasMeasures {
                        hasErrorCount(1)
                        hasResourceCount(0)
                        hasUserActionCount(0)
                    }
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
    fun `addError within userAction scope has action id`(
        forge: Forge
    ) {
        val viewKey = forge.anAlphabeticalString()
        val viewName = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        val actionNAme = forge.anAlphabeticalString()
        val attributes = forge.exhaustiveAttributes()
        val errorOrigin = forge.anAlphabeticalString()
        val errorMessage = forge.anAlphabeticalString()
        val throwable = forge.aThrowable()

        testedMonitor.startView(viewKey, viewName, emptyMap())
        testedMonitor.addUserAction(actionNAme, emptyMap())
        val viewId = GlobalRum.getRumContext().viewId
        testedMonitor.addError(errorMessage, errorOrigin, throwable, attributes)

        checkNotNull(viewId)
        argumentCaptor<RumEvent> {
            verify(mockWriter, times(2)).write(capture())

            assertThat(firstValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasAttributes(attributes)
                .hasUserActionAttribute()
                .hasErrorData {
                    hasMessage(errorMessage)
                    hasOrigin(errorOrigin)
                    hasThrowable(throwable)
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }

            assertThat(lastValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasViewData {
                    hasVersion(2)
                    hasMeasures {
                        hasErrorCount(1)
                        hasResourceCount(0)
                        hasUserActionCount(0)
                    }
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
    fun `addError outside userAction scope has no action and sends action`(
        forge: Forge
    ) {
        val viewKey = forge.anAlphabeticalString()
        val viewName = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        val actionNAme = forge.anAlphabeticalString()
        val attributes = forge.exhaustiveAttributes()
        val errorOrigin = forge.anAlphabeticalString()
        val errorMessage = forge.anAlphabeticalString()
        val throwable = forge.aThrowable()

        testedMonitor.startView(viewKey, viewName, emptyMap())
        testedMonitor.addUserAction(actionNAme, attributes)
        val viewId = GlobalRum.getRumContext().viewId
        Thread.sleep(DatadogRumMonitor.ACTION_INACTIVITY_MS)
        testedMonitor.addError(errorMessage, errorOrigin, throwable, attributes)

        checkNotNull(viewId)
        argumentCaptor<RumEvent> {
            verify(mockWriter, times(4)).write(capture())
            assertThat(firstValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasAttributes(attributes)
                .hasUserActionData {
                    hasName(actionNAme)
                    hasNonDefaultId()
                    hasDuration(1L)
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }

            assertThat(secondValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasViewData {
                    hasMeasures {
                        hasErrorCount(0)
                        hasResourceCount(0)
                        hasUserActionCount(1)
                    }
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }

            assertThat(thirdValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasAttributes(attributes)
                .hasNoUserActionAttribute()
                .hasErrorData {
                    hasMessage(errorMessage)
                    hasOrigin(errorOrigin)
                    hasThrowable(throwable)
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }

            assertThat(lastValue)
                .hasTimestamp(fakeTimestamp)
                .hasViewData {
                    hasVersion(3)
                    hasMeasures {
                        hasErrorCount(1)
                        hasResourceCount(0)
                        hasUserActionCount(1)
                    }
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
    fun `stopView sends last user Action`(
        forge: Forge
    ) {
        val viewKey = forge.anAlphabeticalString()
        val viewName = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        val actionName = forge.anAlphabeticalString()
        val attributes = forge.exhaustiveAttributes()

        testedMonitor.startView(viewKey, viewName, attributes)
        val viewId = GlobalRum.getRumContext().viewId
        testedMonitor.addUserAction(actionName, attributes)
        testedMonitor.stopView(viewKey, emptyMap())

        checkNotNull(viewId)
        argumentCaptor<RumEvent> {
            verify(mockWriter, times(3)).write(capture())

            assertThat(firstValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasAttributes(attributes)
                .hasUserActionData {
                    hasName(actionName)
                    hasDuration(1)
                    hasNonDefaultId()
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }
            assertThat(secondValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasAttributes(attributes)
                .hasViewData {
                    hasName(viewName.replace('.', '/'))
                    hasVersion(2)
                    hasMeasures {
                        hasResourceCount(0)
                        hasErrorCount(0)
                        hasUserActionCount(1)
                    }
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }
            assertThat(lastValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasAttributes(attributes)
                .hasViewData {
                    hasName(viewName.replace('.', '/'))
                    hasVersion(3)
                    hasMeasures {
                        hasResourceCount(0)
                        hasErrorCount(0)
                        hasUserActionCount(1)
                    }
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
    fun `addUserAction sends previous unclosed action`(
        forge: Forge
    ) {

        val viewKey = forge.anAlphabeticalString()
        val viewName = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        val actionName1 = forge.anAlphabeticalString()
        val actionName2 = forge.anAlphabeticalString()
        val attributes = forge.exhaustiveAttributes()

        testedMonitor.startView(viewKey, viewName, attributes)
        val viewId = GlobalRum.getRumContext().viewId
        testedMonitor.addUserAction(actionName1, attributes)
        Thread.sleep(DatadogRumMonitor.ACTION_INACTIVITY_MS)
        testedMonitor.addUserAction(actionName2, attributes)
        testedMonitor.stopView(viewKey, emptyMap())

        checkNotNull(viewId)
        argumentCaptor<RumEvent> {
            verify(mockWriter, times(5)).write(capture())

            assertThat(firstValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasAttributes(attributes)
                .hasUserActionData {
                    hasName(actionName1)
                    hasDuration(1)
                    hasNonDefaultId()
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }
            assertThat(secondValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasAttributes(attributes)
                .hasViewData {
                    hasName(viewName.replace('.', '/'))
                    hasVersion(2)
                    hasMeasures {
                        hasResourceCount(0)
                        hasErrorCount(0)
                        hasUserActionCount(1)
                    }
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }
            assertThat(thirdValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasAttributes(attributes)
                .hasUserActionData {
                    hasName(actionName2)
                    hasDuration(1)
                    hasNonDefaultId()
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }
            assertThat(allValues[3])
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasAttributes(attributes)
                .hasViewData {
                    hasName(viewName.replace('.', '/'))
                    hasVersion(3)
                    hasMeasures {
                        hasResourceCount(0)
                        hasErrorCount(0)
                        hasUserActionCount(2)
                    }
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }
            assertThat(lastValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasAttributes(attributes)
                .hasViewData {
                    hasName(viewName.replace('.', '/'))
                    hasVersion(4)
                    hasMeasures {
                        hasResourceCount(0)
                        hasErrorCount(0)
                        hasUserActionCount(2)
                    }
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
    fun `addUserAction ignored if previous action recent`(
        forge: Forge
    ) {
        val viewKey = forge.anAlphabeticalString()
        val viewName = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        val actionName1 = forge.anAlphabeticalString()
        val actionName2 = forge.anAlphabeticalString()
        val attributes = forge.exhaustiveAttributes()

        testedMonitor.startView(viewKey, viewName, attributes)
        val viewId = GlobalRum.getRumContext().viewId
        testedMonitor.addUserAction(actionName1, attributes)
        testedMonitor.addUserAction(actionName2, attributes)
        testedMonitor.stopView(viewKey, emptyMap())

        checkNotNull(viewId)
        argumentCaptor<RumEvent> {
            verify(mockWriter, times(3)).write(capture())

            assertThat(firstValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasAttributes(attributes)
                .hasUserActionData {
                    hasName(actionName1)
                    hasDuration(1)
                    hasNonDefaultId()
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }
            assertThat(secondValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasAttributes(attributes)
                .hasViewData {
                    hasName(viewName.replace('.', '/'))
                    hasVersion(2)
                    hasMeasures {
                        hasResourceCount(0)
                        hasErrorCount(0)
                        hasUserActionCount(1)
                    }
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }
            assertThat(lastValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasAttributes(attributes)
                .hasViewData {
                    hasName(viewName.replace('.', '/'))
                    hasVersion(3)
                    hasMeasures {
                        hasResourceCount(0)
                        hasErrorCount(0)
                        hasUserActionCount(1)
                    }
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
    fun `addUserAction ignored if previous action active (unclosed resources)`(
        forge: Forge
    ) {
        val viewKey = forge.anAlphabeticalString()
        val viewName = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        val resourceKey = forge.anAlphabeticalString()
        val resourceMethod = forge.anElementFrom("GET", "PUT", "POST", "DELETE")
        val resourceUrl = forge.aStringMatching("http(s?)://[a-z]+.com/[a-z]+")
        val resourceKind = forge.aValueFrom(RumResourceKind::class.java)
        val actionName1 = forge.anAlphabeticalString()
        val actionName2 = forge.anAlphabeticalString()
        val attributes = forge.exhaustiveAttributes()

        testedMonitor.startView(viewKey, viewName, attributes)
        val viewId = GlobalRum.getRumContext().viewId
        testedMonitor.addUserAction(actionName1, attributes)
        testedMonitor.startResource(resourceKey, resourceMethod, resourceUrl, emptyMap())
        testedMonitor.addUserAction(actionName2, attributes)
        testedMonitor.stopResource(resourceKey, resourceKind, emptyMap())
        testedMonitor.stopView(viewKey, emptyMap())

        checkNotNull(viewId)
        argumentCaptor<RumEvent> {
            verify(mockWriter, times(5)).write(capture())

            assertThat(firstValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasResourceData {
                    hasUrl(resourceUrl)
                    hasMethod(resourceMethod)
                    hasKind(resourceKind)
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }
            assertThat(secondValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasAttributes(attributes)
                .hasViewData {
                    hasName(viewName.replace('.', '/'))
                    hasVersion(2)
                    hasMeasures {
                        hasResourceCount(1)
                        hasErrorCount(0)
                        hasUserActionCount(0)
                    }
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }

            assertThat(thirdValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasUserActionData {
                    hasName(actionName1)
                    hasNonDefaultId()
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }

            assertThat(allValues[3])
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasAttributes(attributes)
                .hasViewData {
                    hasName(viewName.replace('.', '/'))
                    hasVersion(3)
                    hasMeasures {
                        hasResourceCount(1)
                        hasErrorCount(0)
                        hasUserActionCount(1)
                    }
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }
            assertThat(lastValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasAttributes(attributes)
                .hasViewData {
                    hasName(viewName.replace('.', '/'))
                    hasVersion(4)
                    hasMeasures {
                        hasResourceCount(1)
                        hasErrorCount(0)
                        hasUserActionCount(1)
                    }
                }
                .hasContext {
                    hasApplicationId(fakeApplicationId)
                    hasViewId(viewId)
                }
        }
        assertThat(GlobalRum.getRumContext().viewId)
            .isNull()
    }

    // endregion
}
