/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.monitor

import android.util.Log
import com.datadog.android.Datadog
import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.log.internal.user.UserInfo
import com.datadog.android.log.internal.user.UserInfoProvider
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.assertj.RumEventAssert.Companion.assertThat
import com.datadog.android.rum.internal.domain.event.RumEvent
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.forge.exhaustiveAttributes
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.forge.aThrowable
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
internal class DatadogRumMonitorInternalMethodsTest {

    lateinit var testedMonitor: DatadogRumMonitor

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
        mockedUserInfo = forge.getForgery()
        whenever(mockUserInfoProvider.getUserInfo()).thenReturn(mockedUserInfo)
        whenever(mockTimeProvider.getDeviceTimestamp()) doReturn fakeTimestamp
        testedMonitor = DatadogRumMonitor(mockWriter, mockTimeProvider, mockUserInfoProvider)
    }

    // region User action

    @Test
    fun `startUserAction doesn't send anything`(
        forge: Forge
    ) {
        val viewKey = forge.anAlphabeticalString()
        val viewName = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")

        testedMonitor.startView(viewKey, viewName, emptyMap())
        testedMonitor.startUserAction()

        verifyZeroInteractions(mockWriter)
    }

    @Test
    fun `resource started within started userAction scope have action id`(
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
        testedMonitor.startUserAction()
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
    fun `resource started within started and closed userAction scope have action id`(
        forge: Forge
    ) {
        val viewKey = forge.anAlphabeticalString()
        val actionName = forge.anAlphabeticalString()
        val viewName = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        val resourceKey = forge.anAlphabeticalString()
        val resourceMethod = forge.anElementFrom("GET", "PUT", "POST", "DELETE")
        val resourceUrl = forge.aStringMatching("http(s?)://[a-z]+.com/[a-z]+")
        val resourceKind = forge.aValueFrom(RumResourceKind::class.java)
        val attributes = forge.exhaustiveAttributes()

        testedMonitor.startView(viewKey, viewName, emptyMap())
        testedMonitor.startUserAction()
        val viewId = GlobalRum.getRumContext().viewId
        testedMonitor.startResource(resourceKey, resourceMethod, resourceUrl, attributes)
        testedMonitor.stopUserAction(actionName, attributes)
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
    fun `resource started outside closed userAction scope has no action and sends action`(
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
        val userActionDuration = measureNanoTime {
            testedMonitor.startUserAction()
            Thread.sleep(1)
            testedMonitor.stopUserAction(actionName, attributes)
        }
        Thread.sleep(DatadogRumMonitor.ACTION_INACTIVITY_MS)
        val viewId = GlobalRum.getRumContext().viewId
        val resourceDuration = measureNanoTime {
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
                    hasDurationLowerThan(userActionDuration)
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
                    hasDurationLowerThan(resourceDuration)
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
    fun `resource started outside opened userAction scope extends the scope`(
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
        testedMonitor.startUserAction()
        Thread.sleep(DatadogRumMonitor.ACTION_INACTIVITY_MS)
        val viewId = GlobalRum.getRumContext().viewId
        val resourceDuration = measureNanoTime {
            testedMonitor.startResource(resourceKey, resourceMethod, resourceUrl, attributes)
            testedMonitor.stopResource(resourceKey, resourceKind, emptyMap())
        }
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
                    hasDurationLowerThan(resourceDuration)
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
    fun `resource started within started userAction scope and stopped later extends scope`(
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
        testedMonitor.startUserAction()
        val viewId = GlobalRum.getRumContext().viewId
        testedMonitor.startResource(resourceKey, resourceMethod, resourceUrl, attributes)
        testedMonitor.stopUserAction(actionName, attributes)
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
        testedMonitor.startUserAction()
        val viewId = GlobalRum.getRumContext().viewId
        testedMonitor.startResource(resourceKey, resourceMethod, resourceUrl, attributes)
        testedMonitor.stopUserAction(actionNAme, attributes)
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
        testedMonitor.startUserAction()
        val viewId = GlobalRum.getRumContext().viewId
        testedMonitor.startResource(resourceKey, resourceMethod, resourceUrl, attributes)
        Thread.sleep(DatadogRumMonitor.ACTION_INACTIVITY_MS * 5)
        testedMonitor.stopUserAction(actionNAme, attributes)
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
        testedMonitor.startUserAction()
        val viewId = GlobalRum.getRumContext().viewId
        testedMonitor.startResource(resourceKey, resourceMethod, resourceUrl, attributes)
        Thread.sleep(DatadogRumMonitor.ACTION_INACTIVITY_MS * 5)
        testedMonitor.stopResourceWithError(
            resourceKey,
            resErrorMessage,
            resErrorOrigin,
            resErrorThrowable
        )
        testedMonitor.stopUserAction(actionNAme, attributes)
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
        testedMonitor.startUserAction()
        val viewId = GlobalRum.getRumContext().viewId
        testedMonitor.stopUserAction(actionNAme, attributes)
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
    fun `addError within recently closed userAction scope has action id`(
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
        testedMonitor.startUserAction()
        Thread.sleep(DatadogRumMonitor.ACTION_INACTIVITY_MS)
        testedMonitor.stopUserAction(actionNAme, attributes)
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
    fun `addError outside closed userAction scope has no action and sends action`(
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
        val userActionDuration = measureNanoTime {
            testedMonitor.startUserAction()
            testedMonitor.stopUserAction(actionNAme, attributes)
        }
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
                    hasDurationLowerThan(userActionDuration)
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
        val eventDuration = measureNanoTime {
            testedMonitor.startUserAction()
            testedMonitor.stopUserAction(actionName, attributes)
            testedMonitor.stopView(viewKey, emptyMap())
        }

        checkNotNull(viewId)
        argumentCaptor<RumEvent> {
            verify(mockWriter, times(3)).write(capture())

            assertThat(firstValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasAttributes(attributes)
                .hasUserActionData {
                    hasName(actionName)
                    hasDurationLowerThan(eventDuration)
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
    fun `stopView sends last unclosed Action`(
        forge: Forge
    ) {
        val viewKey = forge.anAlphabeticalString()
        val viewName = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        val attributes = forge.exhaustiveAttributes()

        testedMonitor.startView(viewKey, viewName, attributes)
        val viewId = GlobalRum.getRumContext().viewId
        testedMonitor.startUserAction()
        testedMonitor.stopView(viewKey, emptyMap())

        checkNotNull(viewId)
        argumentCaptor<RumEvent> {
            verify(mockWriter, times(3)).write(capture())

            assertThat(firstValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasAttributes(emptyMap())
                .hasUserActionData {
                    hasName("")
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
    fun `startUserAction sends previous unclosed action`(
        forge: Forge
    ) {

        val viewKey = forge.anAlphabeticalString()
        val viewName = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        val attributes = forge.exhaustiveAttributes()

        testedMonitor.startView(viewKey, viewName, attributes)
        val viewId = GlobalRum.getRumContext().viewId
        val actionDuration = measureNanoTime {
            testedMonitor.startUserAction()
            Thread.sleep(DatadogRumMonitor.ACTION_INACTIVITY_MS)
            testedMonitor.startUserAction()
        }

        checkNotNull(viewId)
        argumentCaptor<RumEvent> {
            verify(mockWriter, times(2)).write(capture())

            assertThat(firstValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasAttributes(emptyMap())
                .hasUserActionData {
                    hasName("")
                    hasDurationLowerThan(actionDuration)
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
        }
        assertThat(GlobalRum.getRumContext().viewId).isEqualTo(viewId)
    }

    @Test
    fun `startUserAction ignored if previous action recent`(
        forge: Forge
    ) {
        val viewKey = forge.anAlphabeticalString()
        val viewName = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        val attributes = forge.exhaustiveAttributes()

        testedMonitor.startView(viewKey, viewName, attributes)
        val viewId = GlobalRum.getRumContext().viewId
        testedMonitor.startUserAction()
        testedMonitor.startUserAction()
        testedMonitor.stopView(viewKey, emptyMap())

        checkNotNull(viewId)
        argumentCaptor<RumEvent> {
            verify(mockWriter, times(3)).write(capture())

            assertThat(firstValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasAttributes(emptyMap())
                .hasUserActionData {
                    hasName("")
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
    fun `startUserAction does not invalidate previous started action if too recent`(
        forge: Forge
    ) {
        val viewKey = forge.anAlphabeticalString()
        val viewName = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        val resourceKey = forge.anAlphabeticalString()
        val resourceMethod = forge.anElementFrom("GET", "PUT", "POST", "DELETE")
        val resourceUrl = forge.aStringMatching("http(s?)://[a-z]+.com/[a-z]+")
        val errorOrigin = forge.anAlphabeticalString()
        val errorMessage = forge.anAlphabeticalString()
        val errorThrowable = forge.aThrowable()
        val attributes = forge.exhaustiveAttributes()

        testedMonitor.startView(viewKey, viewName, emptyMap())
        testedMonitor.startUserAction()
        testedMonitor.startUserAction()
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
        assertThat(GlobalRum.getRumContext().viewId).isEqualTo(viewId)
    }

    @Test
    fun `startUserAction ignored if previous action active (unclosed resources)`(
        forge: Forge
    ) {
        val viewKey = forge.anAlphabeticalString()
        val viewName = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        val resourceKey = forge.anAlphabeticalString()
        val resourceMethod = forge.anElementFrom("GET", "PUT", "POST", "DELETE")
        val resourceUrl = forge.aStringMatching("http(s?)://[a-z]+.com/[a-z]+")
        val resourceKind = forge.aValueFrom(RumResourceKind::class.java)
        val attributes = forge.exhaustiveAttributes()

        testedMonitor.startView(viewKey, viewName, attributes)
        val viewId = GlobalRum.getRumContext().viewId
        testedMonitor.startUserAction()
        testedMonitor.startResource(resourceKey, resourceMethod, resourceUrl, emptyMap())
        testedMonitor.startUserAction()
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
                    hasName("")
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

    @Test
    fun `calling stopUserAction without starting an action will do nothing`(forge: Forge) {
        val actionName = forge.aString()
        val actionAttributes = forge.exhaustiveAttributes()
        val viewKey = forge.anAlphabeticalString()
        val viewName = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        val resourceKey = forge.anAlphabeticalString()
        val resourceMethod = forge.anElementFrom("GET", "PUT", "POST", "DELETE")
        val resourceUrl = forge.aStringMatching("http(s?)://[a-z]+.com/[a-z]+")
        val resourceKind = forge.aValueFrom(RumResourceKind::class.java)
        val attributes = forge.exhaustiveAttributes()

        testedMonitor.startView(viewKey, viewName, attributes)
        val viewId = GlobalRum.getRumContext().viewId
        testedMonitor.stopUserAction(actionName, actionAttributes)
        testedMonitor.startResource(resourceKey, resourceMethod, resourceUrl, emptyMap())
        testedMonitor.stopResource(resourceKey, resourceKind, emptyMap())
        testedMonitor.stopUserAction(actionName, actionAttributes)
        testedMonitor.stopView(viewKey, emptyMap())

        checkNotNull(viewId)
        argumentCaptor<RumEvent> {
            verify(mockWriter, times(3)).write(capture())

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
            assertThat(lastValue)
                .hasTimestamp(fakeTimestamp)
                .hasUserInfo(mockedUserInfo)
                .hasAttributes(attributes)
                .hasViewData {
                    hasName(viewName.replace('.', '/'))
                    hasVersion(3)
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
        }
        assertThat(GlobalRum.getRumContext().viewId)
            .isNull()
    }

    // endregion
}
