/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rx

import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumMonitor
import com.datadog.tools.unit.forge.BaseConfigurator
import com.datadog.tools.unit.getStaticValue
import com.nhaarman.mockitokotlin2.verify
import fr.xgouchet.elmyr.Forge
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

    @Forgery
    lateinit var fakeException: Throwable

    @BeforeEach
    fun `set up`() {
        GlobalRum.registerIfAbsent(mockRumMonitor)
    }

    @AfterEach
    fun `tear down`() {
        val isRegistered: AtomicBoolean = GlobalRum::class.java.getStaticValue("isRegistered")
        isRegistered.set(false)
    }

    @Test
    fun `M send an error event W exception in the stream {Observable}`(forge: Forge) {
        // GIVEN
        val testObservable = Observable
            .just(0, 1, 2)
            .concatWith(ObservableError.error(fakeException))

        // WHEN
        val testSubscriber = testObservable.sendErrorToDatadog().test()

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
    fun `M send an error event W exception in the stream {Single}`(forge: Forge) {
        // GIVEN
        val testSingle = SingleError.error<Int>(fakeException)

        // WHEN
        val testSubscriber = testSingle.sendErrorToDatadog().test()

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
    fun `M send an error event W exception in the stream {Maybe}`(forge: Forge) {
        // GIVEN
        val testMaybe = MaybeError.error<Int>(fakeException)

        // WHEN
        val testSubscriber = testMaybe.sendErrorToDatadog().test()

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
    fun `M send an error event W exception in the stream {Completable}`(forge: Forge) {
        // WHEN
        CompletableError.error(fakeException).sendErrorToDatadog().test()

        // THEN
        verify(mockRumMonitor).addError(
            DatadogRumErrorConsumer.REQUEST_ERROR_MESSAGE,
            RumErrorSource.SOURCE,
            fakeException,
            emptyMap()
        )
    }

    @Test
    fun `M send an error event W exception in the stream {Flowable}`(forge: Forge) {
        // GIVEN
        val testFlowable = Flowable
            .just(0, 1, 2)
            .concatWith(FlowableError.error(fakeException))

        // WHEN
        val testSubscriber = testFlowable.sendErrorToDatadog().test()

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
