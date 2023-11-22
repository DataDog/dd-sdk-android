/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.telemetry.assertj

import com.datadog.android.telemetry.model.TelemetryConfigurationEvent
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat
import com.datadog.android.telemetry.model.TelemetryConfigurationEvent.ViewTrackingStrategy as VTS

internal class TelemetryConfigurationEventAssert(actual: TelemetryConfigurationEvent) :
    AbstractObjectAssert<TelemetryConfigurationEventAssert, TelemetryConfigurationEvent>(
        actual,
        TelemetryConfigurationEventAssert::class.java
    ) {

    // region Common Telemetry

    fun hasDate(expected: Long): TelemetryConfigurationEventAssert {
        assertThat(actual.date)
            .overridingErrorMessage(
                "Expected event data to have date $expected but was ${actual.date}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasSource(expected: TelemetryConfigurationEvent.Source): TelemetryConfigurationEventAssert {
        assertThat(actual.source)
            .overridingErrorMessage(
                "Expected event data to have source $expected but was ${actual.source}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasService(expected: String): TelemetryConfigurationEventAssert {
        assertThat(actual.service)
            .overridingErrorMessage(
                "Expected event data to have service $expected but was ${actual.service}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasVersion(expected: String): TelemetryConfigurationEventAssert {
        assertThat(actual.version)
            .overridingErrorMessage(
                "Expected event data to have version $expected but was ${actual.version}"
            )
            .isEqualTo(expected)
        return this
    }

    // endregion

    // region RUM Context

    fun hasApplicationId(expected: String?): TelemetryConfigurationEventAssert {
        assertThat(actual.application?.id)
            .overridingErrorMessage(
                "Expected event data to have" +
                    " application.id $expected but was ${actual.application?.id}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasSessionId(expected: String?): TelemetryConfigurationEventAssert {
        assertThat(actual.session?.id)
            .overridingErrorMessage(
                "Expected event data to have session.id $expected but was ${actual.session?.id}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasViewId(expected: String?): TelemetryConfigurationEventAssert {
        assertThat(actual.view?.id)
            .overridingErrorMessage(
                "Expected event data to have view.id $expected but was ${actual.view?.id}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasActionId(expected: String?): TelemetryConfigurationEventAssert {
        assertThat(actual.action?.id)
            .overridingErrorMessage(
                "Expected event data to have action ID $expected but was ${actual.action?.id}"
            )
            .isEqualTo(expected)
        return this
    }

    // endregion

    // region Native Configuration

    fun hasSessionSampleRate(expected: Long?): TelemetryConfigurationEventAssert {
        assertThat(actual.telemetry.configuration.sessionSampleRate)
            .overridingErrorMessage(
                "Expected event data to have telemetry.configuration.sessionSampleRate $expected " +
                    "but was ${actual.telemetry.configuration.sessionSampleRate}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasTelemetrySampleRate(expected: Long?): TelemetryConfigurationEventAssert {
        assertThat(actual.telemetry.configuration.telemetrySampleRate)
            .overridingErrorMessage(
                "Expected event data to have telemetry.configuration.telemetrySampleRate $expected " +
                    "but was ${actual.telemetry.configuration.telemetrySampleRate}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasUseProxy(expected: Boolean?): TelemetryConfigurationEventAssert {
        assertThat(actual.telemetry.configuration.useProxy)
            .overridingErrorMessage(
                "Expected event data to have telemetry.configuration.useProxy $expected " +
                    "but was ${actual.telemetry.configuration.useProxy}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasTrackFrustrations(expected: Boolean?): TelemetryConfigurationEventAssert {
        assertThat(actual.telemetry.configuration.trackFrustrations)
            .overridingErrorMessage(
                "Expected event data to have telemetry.configuration.trackFrustrations $expected " +
                    "but was ${actual.telemetry.configuration.trackFrustrations}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasUseLocalEncryption(expected: Boolean?): TelemetryConfigurationEventAssert {
        assertThat(actual.telemetry.configuration.useLocalEncryption)
            .overridingErrorMessage(
                "Expected event data to have telemetry.configuration.useLocalEncryption $expected " +
                    "but was ${actual.telemetry.configuration.useLocalEncryption}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasViewTrackingStrategy(expected: VTS?): TelemetryConfigurationEventAssert {
        assertThat(actual.telemetry.configuration.viewTrackingStrategy)
            .overridingErrorMessage(
                "Expected event data to have telemetry.configuration.viewTrackingStrategy $expected " +
                    "but was ${actual.telemetry.configuration.viewTrackingStrategy}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasTrackBackgroundEvents(expected: Boolean?): TelemetryConfigurationEventAssert {
        assertThat(actual.telemetry.configuration.trackBackgroundEvents)
            .overridingErrorMessage(
                "Expected event data to have telemetry.configuration.trackBackgroundEvents $expected " +
                    "but was ${actual.telemetry.configuration.trackBackgroundEvents}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasMobileVitalsUpdatePeriod(expected: Long?): TelemetryConfigurationEventAssert {
        assertThat(actual.telemetry.configuration.mobileVitalsUpdatePeriod)
            .overridingErrorMessage(
                "Expected event data to have telemetry.configuration.mobileVitalsUpdatePeriod $expected " +
                    "but was ${actual.telemetry.configuration.mobileVitalsUpdatePeriod}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasTrackInteractions(expected: Boolean?): TelemetryConfigurationEventAssert {
        assertThat(actual.telemetry.configuration.trackInteractions)
            .overridingErrorMessage(
                "Expected event data to have telemetry.configuration.trackActions $expected " +
                    "but was ${actual.telemetry.configuration.trackInteractions}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasTrackErrors(expected: Boolean?): TelemetryConfigurationEventAssert {
        assertThat(actual.telemetry.configuration.trackErrors)
            .overridingErrorMessage(
                "Expected event data to have telemetry.configuration.trackErrors $expected " +
                    "but was ${actual.telemetry.configuration.trackErrors}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasTrackNetworkRequests(expected: Boolean?): TelemetryConfigurationEventAssert {
        assertThat(actual.telemetry.configuration.trackNetworkRequests)
            .overridingErrorMessage(
                "Expected event data to have telemetry.configuration.trackNetworkRequests $expected " +
                    "but was ${actual.telemetry.configuration.trackNetworkRequests}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasTrackLongTasks(expected: Boolean?): TelemetryConfigurationEventAssert {
        assertThat(actual.telemetry.configuration.trackNativeLongTasks)
            .overridingErrorMessage(
                "Expected event data to have telemetry.configuration.trackNativeLongTasks $expected " +
                    "but was ${actual.telemetry.configuration.trackNativeLongTasks}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasUseTracing(expected: Boolean?): TelemetryConfigurationEventAssert {
        assertThat(actual.telemetry.configuration.useTracing)
            .overridingErrorMessage(
                "Expected event data to have telemetry.configuration.useTracing $expected " +
                    "but was ${actual.telemetry.configuration.useTracing}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasBatchSize(expected: Long?): TelemetryConfigurationEventAssert {
        assertThat(actual.telemetry.configuration.batchSize)
            .overridingErrorMessage(
                "Expected event data to have telemetry.configuration.batchSize $expected " +
                    "but was ${actual.telemetry.configuration.batchSize}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasBatchUploadFrequency(expected: Long?): TelemetryConfigurationEventAssert {
        assertThat(actual.telemetry.configuration.batchUploadFrequency)
            .overridingErrorMessage(
                "Expected event data to have telemetry.configuration.batchUploadFrequency $expected " +
                    "but was ${actual.telemetry.configuration.batchUploadFrequency}"
            )
            .isEqualTo(expected)
        return this
    }
    fun hasBatchProcessingLevel(expected: Int?): TelemetryConfigurationEventAssert {
        assertThat(actual.telemetry.configuration.batchProcessingLevel)
            .overridingErrorMessage(
                "Expected event data to have telemetry.configuration.batchProcessingLevel $expected " +
                    "but was ${actual.telemetry.configuration.batchProcessingLevel}"
            )
            .isEqualTo(expected?.toLong())
        return this
    }

    // endregion

    // region CrossPlatform Configuration

    fun hasTrackNativeViews(expected: Boolean?): TelemetryConfigurationEventAssert {
        assertThat(actual.telemetry.configuration.trackNativeViews)
            .overridingErrorMessage(
                "Expected event data to have telemetry.configuration.trackNativeViews $expected " +
                    "but was ${actual.telemetry.configuration.trackNativeViews}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasTrackNativeErrors(expected: Boolean?): TelemetryConfigurationEventAssert {
        assertThat(actual.telemetry.configuration.trackNativeErrors)
            .overridingErrorMessage(
                "Expected event data to have telemetry.configuration.trackNativeErrors $expected " +
                    "but was ${actual.telemetry.configuration.trackNativeErrors}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasUseFirstPartyHosts(expected: Boolean?): TelemetryConfigurationEventAssert {
        assertThat(actual.telemetry.configuration.useFirstPartyHosts)
            .overridingErrorMessage(
                "Expected event data to have telemetry.configuration.useFirstPartyHosts $expected " +
                    "but was ${actual.telemetry.configuration.useFirstPartyHosts}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasTrackFlutterPerformance(expected: Boolean?): TelemetryConfigurationEventAssert {
        assertThat(actual.telemetry.configuration.trackFlutterPerformance)
            .overridingErrorMessage(
                "Expected event data to have telemetry.configuration.trackFlutterPerformance $expected " +
                    "but was ${actual.telemetry.configuration.trackFlutterPerformance}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasInitializationType(expected: String?): TelemetryConfigurationEventAssert {
        assertThat(actual.telemetry.configuration.initializationType)
            .overridingErrorMessage(
                "Expected event data to have telemetry.configuration.initializationType $expected " +
                    "but was ${actual.telemetry.configuration.initializationType}"
            )
            .isEqualTo(expected)
        return this
    }

    // endregion

    // region Session Replay configuration

    fun hasSessionReplaySampleRate(expected: Long?): TelemetryConfigurationEventAssert {
        assertThat(actual.telemetry.configuration.sessionReplaySampleRate)
            .overridingErrorMessage(
                "Expected event data to have telemetry.configuration.sessionReplaySampleRate" +
                    " $expected " +
                    "but was ${actual.telemetry.configuration.sessionReplaySampleRate}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasSessionReplayPrivacy(expected: String?): TelemetryConfigurationEventAssert {
        assertThat(actual.telemetry.configuration.defaultPrivacyLevel)
            .overridingErrorMessage(
                "Expected event data to have telemetry.configuration.defaultPrivacyLevel" +
                    " $expected " +
                    "but was ${actual.telemetry.configuration.defaultPrivacyLevel}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasSessionReplayStartManually(expected: Boolean?): TelemetryConfigurationEventAssert {
        val assertErrorMessage = "Expected event data to have" +
            " telemetry.configuration.startSessionReplayRecordingManually" +
            " $expected " +
            "but was ${actual.telemetry.configuration.startSessionReplayRecordingManually}"
        assertThat(actual.telemetry.configuration.startSessionReplayRecordingManually)
            .overridingErrorMessage(assertErrorMessage)
            .isEqualTo(expected)
        return this
    }

    // endregion

    companion object {
        fun assertThat(actual: TelemetryConfigurationEvent) =
            TelemetryConfigurationEventAssert(actual)
    }
}
