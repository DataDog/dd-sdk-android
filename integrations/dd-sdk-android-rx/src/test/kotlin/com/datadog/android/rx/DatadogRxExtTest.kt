/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rx

import android.annotation.SuppressLint
import com.datadog.android.api.SdkCore
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumMonitor
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.internal.operators.completable.CompletableError
import io.reactivex.rxjava3.internal.operators.flowable.FlowableError
import io.reactivex.rxjava3.internal.operators.maybe.MaybeError
import io.reactivex.rxjava3.internal.operators.observable.ObservableError
import io.reactivex.rxjava3.internal.operators.single.SingleError
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
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.isAccessible

@Extensions(
    ExtendWith(
        MockitoExtension::class,
        ForgeExtension::class
    )
)
@ForgeConfiguration(value = BaseConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DatadogRxExtTest {

    @Mock
    lateinit var mockRumMonitor: RumMonitor

    @Mock
    lateinit var mockSdkCore: SdkCore

    @Forgery
    lateinit var fakeException: Throwable

    @BeforeEach
    fun `set up`() {
        GlobalRumMonitor::class.declaredFunctions.first { it.name == "registerIfAbsent" }.apply {
            isAccessible = true
            call(GlobalRumMonitor::class.objectInstance, mockRumMonitor, mockSdkCore)
        }
    }

    @AfterEach
    fun `tear down`() {
        GlobalRumMonitor::class.java.getDeclaredMethod("reset").apply {
            isAccessible = true
            invoke(null)
        }
    }

    @Test
    fun `M send an error event W exception in the stream {Observable}`() {
        // GIVEN
        val testObservable = Observable
            .just(0, 1, 2)
            .concatWith(ObservableError.error(fakeException))

        // WHEN
        val testSubscriber = testObservable.sendErrorToDatadog(mockSdkCore).test()

        // THEN
        testSubscriber.assertValues(0, 1, 2)
        testSubscriber.assertError(fakeException)
        verify(mockRumMonitor).addError(
            DatadogRumErrorConsumer.REQUEST_ERROR_MESSAGE,
            RumErrorSource.SOURCE,
            fakeException,
            emptyMap()
        )
    }

    @Test
    fun `M send an error event W exception in the stream {Single}`() {
        // GIVEN
        val testSingle = SingleError.error<Int>(fakeException)

        // WHEN
        val testSubscriber = testSingle.sendErrorToDatadog(mockSdkCore).test()

        // THEN
        testSubscriber.assertError(fakeException)
        verify(mockRumMonitor).addError(
            DatadogRumErrorConsumer.REQUEST_ERROR_MESSAGE,
            RumErrorSource.SOURCE,
            fakeException,
            emptyMap()
        )
    }

    @Test
    fun `M send an error event W exception in the stream {Maybe}`() {
        // GIVEN
        val testMaybe = MaybeError.error<Int>(fakeException)

        // WHEN
        val testSubscriber = testMaybe.sendErrorToDatadog(mockSdkCore).test()

        // THEN
        testSubscriber.assertError(fakeException)
        verify(mockRumMonitor).addError(
            DatadogRumErrorConsumer.REQUEST_ERROR_MESSAGE,
            RumErrorSource.SOURCE,
            fakeException,
            emptyMap()
        )
    }

    @SuppressLint("CheckResult")
    @Test
    fun `M send an error event W exception in the stream {Completable}`() {
        // WHEN
        CompletableError.error(fakeException).sendErrorToDatadog(mockSdkCore).test()

        // THEN
        verify(mockRumMonitor).addError(
            DatadogRumErrorConsumer.REQUEST_ERROR_MESSAGE,
            RumErrorSource.SOURCE,
            fakeException,
            emptyMap()
        )
    }

    @Test
    fun `M send an error event W exception in the stream {Flowable}`() {
        // GIVEN
        val testFlowable = Flowable
            .just(0, 1, 2)
            .concatWith(FlowableError.error(fakeException))

        // WHEN
        val testSubscriber = testFlowable.sendErrorToDatadog(mockSdkCore).test()

        // THEN
        testSubscriber.assertValues(0, 1, 2)
        testSubscriber.assertError(fakeException)
        verify(mockRumMonitor).addError(
            DatadogRumErrorConsumer.REQUEST_ERROR_MESSAGE,
            RumErrorSource.SOURCE,
            fakeException,
            emptyMap()
        )
    }
}
