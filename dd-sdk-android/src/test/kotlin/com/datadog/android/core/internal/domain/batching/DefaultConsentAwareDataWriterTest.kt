/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.domain.batching

import com.datadog.android.core.internal.domain.batching.migrators.BatchedDataMigrator
import com.datadog.android.core.internal.domain.batching.processors.DataProcessor
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class DefaultConsentAwareDataWriterTest {

    @Mock
    lateinit var mockedConsentProvider: ConsentProvider

    @Mock
    lateinit var mockProcessorFactory: DataProcessorFactory<String>

    @Mock
    lateinit var mockedMigratorFactory: MigratorFactory

    @StringForgery
    lateinit var fakeEvent: String

    @Mock
    lateinit var mockedNoOpMigrator: BatchedDataMigrator

    @Mock
    lateinit var mockedMigrator: BatchedDataMigrator

    @Mock
    lateinit var mockedProcessor: DataProcessor<String>

    lateinit var testedHandler: DefaultConsentAwareDataWriter<String>

    lateinit var fakeInitialConsent: TrackingConsent

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeInitialConsent = forge.aValueFrom(TrackingConsent::class.java)
        whenever(mockedConsentProvider.getConsent())
            .thenReturn(fakeInitialConsent)
        whenever(mockedMigratorFactory.resolveMigrator(null, fakeInitialConsent))
            .thenReturn(
                mockedNoOpMigrator
            )
        whenever(
            mockProcessorFactory.resolveProcessor(
                fakeInitialConsent
            )
        ).thenReturn(
            mockedProcessor
        )
        testedHandler = DefaultConsentAwareDataWriter(
            mockedConsentProvider,
            mockProcessorFactory,
            mockedMigratorFactory
        )
    }

    @Test
    fun `M register as callback for ConsentProvider W initialising`() {
        // THEN
        verify(mockedConsentProvider).registerCallback(testedHandler)
    }

    @Test
    fun `M migrate data W consent changed`(forge: Forge) {
        // GIVEN
        // should be a value different than current one as this is the contract with the
        // TrackingConsentProvider
        val fakeNewConsent = forge.aValueFrom(
            TrackingConsent::class.java,
            listOf(fakeInitialConsent)
        )
        whenever(
            mockedMigratorFactory.resolveMigrator(
                fakeInitialConsent,
                fakeNewConsent
            )
        ).thenReturn(mockedMigrator)

        // WHEN
        testedHandler.onConsentUpdated(fakeInitialConsent, fakeNewConsent)

        // THEN
        verify(mockedMigrator).migrateData()
    }

    @Test
    fun `M process event W requested`() {
        // WHEN
        testedHandler.write(fakeEvent)

        // THEN
        verify(mockedProcessor).consume(fakeEvent)
    }

    @Test
    fun `M process collection of events W requested`(forge: Forge) {
        // GIVEN
        val fakeEvents = forge.aList { forge.aString() }

        // WHEN
        testedHandler.write(fakeEvents)

        // THEN
        verify(mockedProcessor).consume(fakeEvents)
    }

    @Test
    fun `M process data W requested after updating the consent`(forge: Forge) {
        // GIVEN
        val mockedNewProcessor: DataProcessor<String> = mock()
        val fakeNewConsent =
            forge.aValueFrom(TrackingConsent::class.java, listOf(fakeInitialConsent))
        whenever(
            mockedMigratorFactory.resolveMigrator(
                fakeInitialConsent,
                fakeNewConsent
            )
        ).thenReturn(mockedMigrator)
        whenever(
            mockProcessorFactory.resolveProcessor(
                fakeNewConsent
            )
        ).thenReturn(
            mockedNewProcessor
        )
        testedHandler.onConsentUpdated(fakeInitialConsent, fakeNewConsent)

        // WHEN
        testedHandler.write(fakeEvent)

        // THEN
        verify(mockedNewProcessor).consume(fakeEvent)
    }

    @Test
    fun `M be synchronous W write { event } in concurrent usage`(forge: Forge) {
        // GIVEN
        val fakeNewConsent =
            forge.aValueFrom(TrackingConsent::class.java, listOf(fakeInitialConsent))
        val mockedNewProcessor: DataProcessor<String> = mock()
        val countDownLatch = CountDownLatch(2)
        whenever(
            mockedMigratorFactory.resolveMigrator(
                fakeInitialConsent,
                fakeNewConsent
            )
        ).thenReturn(mockedMigrator)
        whenever(
            mockProcessorFactory.resolveProcessor(
                fakeNewConsent
            )
        ).thenReturn(
            mockedNewProcessor
        )

        // WHEN
        Thread {
            testedHandler.onConsentUpdated(fakeInitialConsent, fakeNewConsent)
            countDownLatch.countDown()
        }.start()
        Thread {
            // Give time to first thread to acquire the lock
            Thread.sleep(1)
            testedHandler.write(fakeEvent)
            countDownLatch.countDown()
        }.start()

        // THEN
        countDownLatch.await(1, TimeUnit.SECONDS)
        inOrder(
            mockedNoOpMigrator,
            mockedMigrator,
            mockedNewProcessor
        ) {
            verify(mockedNoOpMigrator).migrateData()
            verify(mockedMigrator).migrateData()
            verify(mockedNewProcessor).consume(fakeEvent)
        }
    }

    @Test
    fun `M be synchronous W write { events } in concurrent usage`(forge: Forge) {
        // GIVEN
        val fakeEvents = forge.aList { forge.aString() }
        val fakeNewConsent = forge.aValueFrom(
            TrackingConsent::class.java,
            listOf(fakeInitialConsent)
        )
        val mockedNewProcessor: DataProcessor<String> = mock()
        val countDownLatch = CountDownLatch(2)
        whenever(
            mockedMigratorFactory.resolveMigrator(
                fakeInitialConsent,
                fakeNewConsent
            )
        ).thenReturn(mockedMigrator)
        whenever(
            mockProcessorFactory.resolveProcessor(
                fakeNewConsent
            )
        ).thenReturn(
            mockedNewProcessor
        )

        // WHEN
        Thread {
            testedHandler.onConsentUpdated(fakeInitialConsent, fakeNewConsent)
            countDownLatch.countDown()
        }.start()
        Thread {
            // Give time to first thread to acquire the lock
            Thread.sleep(1)
            testedHandler.write(fakeEvents)
            countDownLatch.countDown()
        }.start()

        // THEN
        countDownLatch.await(1, TimeUnit.SECONDS)
        inOrder(
            mockedNoOpMigrator,
            mockedMigrator,
            mockedNewProcessor
        ) {
            verify(mockedNoOpMigrator).migrateData()
            verify(mockedMigrator).migrateData()
            verify(mockedNewProcessor).consume(fakeEvents)
        }
    }
}
