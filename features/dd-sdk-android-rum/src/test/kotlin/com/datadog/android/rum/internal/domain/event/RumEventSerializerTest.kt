/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.UserInfo
import com.datadog.android.core.constraints.DataConstraints
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.telemetry.model.TelemetryConfigurationEvent
import com.datadog.android.telemetry.model.TelemetryDebugEvent
import com.datadog.android.telemetry.model.TelemetryErrorEvent
import com.datadog.tools.unit.assertj.JsonObjectAssert.Companion.assertThat
import com.datadog.tools.unit.forge.anException
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.quality.Strictness
import java.util.Locale

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumEventSerializerTest {

    private lateinit var testedSerializer: RumEventSerializer

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun `set up`() {
        testedSerializer = RumEventSerializer(internalLogger = mockInternalLogger)
    }

    @RepeatedTest(8)
    fun `𝕄 serialize RUM event 𝕎 serialize() with ResourceEvent`(@Forgery event: ResourceEvent) {
        val serialized = testedSerializer.serialize(event)

        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        assertThat(jsonObject)
            .hasField("type", "resource")
            .hasField("date", event.date)
            .hasField("resource") {
                hasField("type", event.resource.type.name.lowercase(Locale.US))
                hasField("url", event.resource.url)
                hasNullableField("duration", event.resource.duration)
                hasNullableField("method", event.resource.method?.name)
                hasNullableField("status_code", event.resource.statusCode)
                hasNullableField("size", event.resource.size)
                // TODO timing ?
            }
            .hasField("application") {
                hasField("id", event.application.id)
            }
            .hasField("session") {
                hasField("id", event.session.id)
                hasField("type", event.session.type.name.lowercase(Locale.US))
            }
            .hasField("view") {
                hasField("id", event.view.id)
                hasField("url", event.view.url)
            }
            .hasField("_dd") {
                hasField("format_version", 2L)
                hasNullableField("span_id", event.dd.spanId)
                hasNullableField("trace_id", event.dd.traceId)
            }

        event.usr?.let { usr ->
            assertThat(jsonObject).hasField("usr") {
                hasNullableField("id", usr.id)
                hasNullableField("name", usr.name)
                hasNullableField("email", usr.email)
                containsAttributes(usr.additionalProperties)
            }
        }
        event.connectivity?.let { connectivity ->
            assertThat(jsonObject).hasField("connectivity") {
                hasNullableField("status", connectivity.status.name.lowercase(Locale.US))
                hasNullableField(
                    "interfaces",
                    connectivity.interfaces.map { it.name.lowercase(Locale.US) }
                )
                connectivity.cellular?.let { cellular ->
                    hasField("cellular") {
                        hasNullableField("technology", cellular.technology)
                        hasNullableField("carrier_name", cellular.carrierName)
                    }
                }
            }
        }
        event.context?.additionalProperties?.let {
            assertThat(jsonObject).hasField("context") {
                containsAttributes(it)
            }
        }
    }

    @RepeatedTest(8)
    fun `𝕄 serialize RUM event 𝕎 serialize() with ActionEvent`(
        @Forgery event: ActionEvent
    ) {
        val serialized = testedSerializer.serialize(event)

        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        assertThat(jsonObject)
            .hasField("type", "action")
            .hasField("date", event.date)
            .hasField("action") {
                hasField("type", event.action.type.name.lowercase(Locale.US))
                hasNullableField("id", event.action.id)
                event.action.target?.let {
                    hasField("target") {
                        hasField("name", it.name)
                    }
                }
                event.action.resource?.let {
                    hasField("resource") {
                        hasField("count", it.count)
                    }
                }
                event.action.error?.let {
                    hasField("error") {
                        hasField("count", it.count)
                    }
                }
                event.action.longTask?.let {
                    hasField("long_task") {
                        hasField("count", it.count)
                    }
                }
                event.action.crash?.let {
                    hasField("crash") {
                        hasField("count", it.count)
                    }
                }
                hasNullableField("loading_time", event.action.loadingTime)
            }
            .hasField("application") {
                hasField("id", event.application.id)
            }
            .hasField("session") {
                hasField("id", event.session.id)
                hasField("type", event.session.type.name.lowercase(Locale.US))
            }
            .hasField("view") {
                hasField("id", event.view.id)
                hasField("url", event.view.url)
            }
            .hasField("_dd") {
                hasField("format_version", 2L)
            }

        event.usr?.let { usr ->
            assertThat(jsonObject).hasField("usr") {
                hasNullableField("id", usr.id)
                hasNullableField("name", usr.name)
                hasNullableField("email", usr.email)
                containsAttributes(usr.additionalProperties)
            }
        }
        event.connectivity?.let { connectivity ->
            assertThat(jsonObject).hasField("connectivity") {
                hasNullableField("status", connectivity.status.name.lowercase(Locale.US))
                hasNullableField(
                    "interfaces",
                    connectivity.interfaces.map { it.name.lowercase(Locale.US) }
                )
                connectivity.cellular?.let { cellular ->
                    hasField("cellular") {
                        hasNullableField("technology", cellular.technology)
                        hasNullableField("carrier_name", cellular.carrierName)
                    }
                }
            }
        }
        event.context?.additionalProperties?.let {
            assertThat(jsonObject).hasField("context") {
                containsAttributes(it)
            }
        }
    }

    @RepeatedTest(8)
    fun `𝕄 serialize RUM event 𝕎 serialize() with ViewEvent`(@Forgery event: ViewEvent) {
        val serialized = testedSerializer.serialize(event)

        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        assertThat(jsonObject)
            .hasField("type", "view")
            .hasField("date", event.date)
            .hasField("application") {
                hasField("id", event.application.id)
            }
            .hasField("session") {
                hasField("id", event.session.id)
                hasField("type", event.session.type.name.lowercase(Locale.US))
            }
            .hasField("view") {
                hasField("id", event.view.id)
                hasField("url", event.view.url)
                hasField("time_spent", event.view.timeSpent)
                hasField("action") {
                    hasField("count", event.view.action.count)
                }
                hasField("resource") {
                    hasField("count", event.view.resource.count)
                }
                hasField("error") {
                    hasField("count", event.view.error.count)
                }
                event.view.longTask?.let {
                    hasField("long_task") {
                        hasField("count", it.count)
                    }
                }
            }
            .hasField("_dd") {
                hasField("format_version", 2L)
            }

        event.usr?.let { usr ->
            assertThat(jsonObject).hasField("usr") {
                hasNullableField("id", usr.id)
                hasNullableField("name", usr.name)
                hasNullableField("email", usr.email)
                containsAttributes(usr.additionalProperties)
            }
        }
        event.connectivity?.let { connectivity ->
            assertThat(jsonObject).hasField("connectivity") {
                hasNullableField("status", connectivity.status.name.lowercase(Locale.US))
                hasNullableField(
                    "interfaces",
                    connectivity.interfaces.map { it.name.lowercase(Locale.US) }
                )
                connectivity.cellular?.let { cellular ->
                    hasField("cellular") {
                        hasNullableField("technology", cellular.technology)
                        hasNullableField("carrier_name", cellular.carrierName)
                    }
                }
            }
        }
        event.context?.additionalProperties?.let {
            assertThat(jsonObject).hasField("context") {
                containsAttributes(it)
            }
        }
    }

    @RepeatedTest(8)
    fun `𝕄 serialize RUM event 𝕎 serialize() with ErrorEvent`(@Forgery event: ErrorEvent) {
        val serialized = testedSerializer.serialize(event)
        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        assertThat(jsonObject)
            .hasField("type", "error")
            .hasField("date", event.date)
            .hasField("error") {
                hasField("message", event.error.message)
                hasField("source", event.error.source.name.lowercase(Locale.US))
                hasNullableField("stack", event.error.stack)
                event.error.resource?.let {
                    hasField("resource") {
                        hasField("method", it.method.name.uppercase(Locale.US))
                        hasField("status_code", it.statusCode)
                        hasField("url", it.url)
                    }
                }
            }
            .hasField("application") {
                hasField("id", event.application.id)
            }
            .hasField("session") {
                hasField("id", event.session.id)
                hasField("type", event.session.type.name.lowercase(Locale.US))
            }
            .hasField("view") {
                hasField("id", event.view.id)
                hasField("url", event.view.url)
            }
            .hasField("_dd") {
                hasField("format_version", 2L)
            }

        event.usr?.let { usr ->
            assertThat(jsonObject).hasField("usr") {
                hasNullableField("id", usr.id)
                hasNullableField("name", usr.name)
                hasNullableField("email", usr.email)
                containsAttributes(usr.additionalProperties)
            }
        }
        event.connectivity?.let { connectivity ->
            assertThat(jsonObject).hasField("connectivity") {
                hasNullableField("status", connectivity.status.name.lowercase(Locale.US))
                hasNullableField(
                    "interfaces",
                    connectivity.interfaces.map { it.name.lowercase(Locale.US) }
                )
                connectivity.cellular?.let { cellular ->
                    hasField("cellular") {
                        hasNullableField("technology", cellular.technology)
                        hasNullableField("carrier_name", cellular.carrierName)
                    }
                }
            }
        }
        event.context?.additionalProperties?.let {
            assertThat(jsonObject).hasField("context") {
                containsAttributes(it)
            }
        }
    }

    @RepeatedTest(8)
    fun `𝕄 serialize RUM event 𝕎 serialize() with LongTaskEvent`(
        @Forgery event: LongTaskEvent
    ) {
        val serialized = testedSerializer.serialize(event)
        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        assertThat(jsonObject)
            .hasField("type", "long_task")
            .hasField("date", event.date)
            .hasField("long_task") {
                hasField("duration", event.longTask.duration)
            }
            .hasField("application") {
                hasField("id", event.application.id)
            }
            .hasField("session") {
                hasField("id", event.session.id)
                hasField("type", event.session.type.name.lowercase(Locale.US))
            }
            .hasField("view") {
                hasField("id", event.view.id)
                hasField("url", event.view.url)
            }
            .hasField("_dd") {
                hasField("format_version", 2L)
            }
            .hasNullableField("service", event.service)

        event.usr?.let { usr ->
            assertThat(jsonObject).hasField("usr") {
                hasNullableField("id", usr.id)
                hasNullableField("name", usr.name)
                hasNullableField("email", usr.email)
                containsAttributes(usr.additionalProperties)
            }
        }
        event.connectivity?.let { connectivity ->
            assertThat(jsonObject).hasField("connectivity") {
                hasNullableField("status", connectivity.status.name.lowercase(Locale.US))
                hasNullableField(
                    "interfaces",
                    connectivity.interfaces.map { it.name.lowercase(Locale.US) }
                )
                connectivity.cellular?.let { cellular ->
                    hasField("cellular") {
                        hasNullableField("technology", cellular.technology)
                        hasNullableField("carrier_name", cellular.carrierName)
                    }
                }
            }
        }
        event.context?.additionalProperties?.let {
            assertThat(jsonObject).hasField("context") {
                containsAttributes(it)
            }
        }
    }

    @RepeatedTest(8)
    fun `𝕄 serialize RUM event 𝕎 serialize() with TelemetryDebugEvent`(
        @Forgery event: TelemetryDebugEvent
    ) {
        val serialized = testedSerializer.serialize(event)
        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        assertThat(jsonObject)
            .hasField("type", "telemetry")
            .hasField("_dd") {
                hasField("format_version", 2L)
            }
            .hasField("date", event.date)
            .hasField("source", event.source.name.lowercase(Locale.US).replace('_', '-'))
            .hasField("service", event.service)
            .hasField("version", event.version)
            .hasField("telemetry") {
                hasField("message", event.telemetry.message)
                hasField("status", "debug")
            }

        val application = event.application
        if (application != null) {
            assertThat(jsonObject)
                .hasField("application") {
                    hasField("id", application.id)
                }
        } else {
            assertThat(jsonObject).doesNotHaveField("application")
        }

        val session = event.session
        if (session != null) {
            assertThat(jsonObject)
                .hasField("session") {
                    hasField("id", session.id)
                }
        } else {
            assertThat(jsonObject).doesNotHaveField("session")
        }

        val view = event.view
        if (view != null) {
            assertThat(jsonObject)
                .hasField("view") {
                    hasField("id", view.id)
                }
        } else {
            assertThat(jsonObject).doesNotHaveField("view")
        }

        val action = event.action
        if (action != null) {
            assertThat(jsonObject)
                .hasField("action") {
                    hasField("id", action.id)
                }
        } else {
            assertThat(jsonObject).doesNotHaveField("action")
        }
    }

    @RepeatedTest(8)
    fun `𝕄 serialize RUM event 𝕎 serialize() with TelemetryErrorEvent`(
        @Forgery event: TelemetryErrorEvent
    ) {
        val serialized = testedSerializer.serialize(event)
        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        assertThat(jsonObject)
            .hasField("type", "telemetry")
            .hasField("_dd") {
                hasField("format_version", 2L)
            }
            .hasField("date", event.date)
            .hasField("source", event.source.name.lowercase(Locale.US).replace('_', '-'))
            .hasField("service", event.service)
            .hasField("version", event.version)
            .hasField("telemetry") {
                hasField("status", "error")
                hasField("message", event.telemetry.message)
                val error = event.telemetry.error
                if (error != null) {
                    hasField("error") {
                        hasNullableField("stack", error.stack)
                        hasNullableField("kind", error.kind)
                    }
                } else {
                    doesNotHaveField("error")
                }
            }

        val application = event.application
        if (application != null) {
            assertThat(jsonObject)
                .hasField("application") {
                    hasField("id", application.id)
                }
        } else {
            assertThat(jsonObject).doesNotHaveField("application")
        }

        val session = event.session
        if (session != null) {
            assertThat(jsonObject)
                .hasField("session") {
                    hasField("id", session.id)
                }
        } else {
            assertThat(jsonObject).doesNotHaveField("session")
        }

        val view = event.view
        if (view != null) {
            assertThat(jsonObject)
                .hasField("view") {
                    hasField("id", view.id)
                }
        } else {
            assertThat(jsonObject).doesNotHaveField("view")
        }

        val action = event.action
        if (action != null) {
            assertThat(jsonObject)
                .hasField("action") {
                    hasField("id", action.id)
                }
        } else {
            assertThat(jsonObject).doesNotHaveField("action")
        }
    }

    @RepeatedTest(8)
    fun `𝕄 serialize RUM event 𝕎 serialize() with TelemetryConfigurationEvent`(
        @Forgery event: TelemetryConfigurationEvent
    ) {
        val serialized = testedSerializer.serialize(event)
        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        assertThat(jsonObject)
            .hasField("type", "telemetry")
            .hasField("_dd") {
                hasField("format_version", 2L)
            }
            .hasField("date", event.date)
            .hasField("source", event.source.name.lowercase(Locale.US).replace('_', '-'))
            .hasField("service", event.service)
            .hasField("version", event.version)
            .hasField("telemetry") {
                hasField("configuration") {
                    val configuration = event.telemetry.configuration
                    if (configuration.sessionSampleRate != null) {
                        hasField("session_sample_rate", configuration.sessionSampleRate!!)
                    }
                    if (configuration.telemetrySampleRate != null) {
                        hasField("telemetry_sample_rate", configuration.telemetrySampleRate!!)
                    }
                    if (configuration.telemetryConfigurationSampleRate != null) {
                        hasField(
                            "telemetry_configuration_sample_rate",
                            configuration.telemetryConfigurationSampleRate!!
                        )
                    }
                    if (configuration.traceSampleRate != null) {
                        hasField("trace_sample_rate", configuration.traceSampleRate!!)
                    }
                    if (configuration.premiumSampleRate != null) {
                        hasField("premium_sample_rate", configuration.premiumSampleRate!!)
                    }
                    if (configuration.replaySampleRate != null) {
                        hasField("replay_sample_rate", configuration.replaySampleRate!!)
                    }
                    if (configuration.sessionReplaySampleRate != null) {
                        hasField(
                            "session_replay_sample_rate",
                            configuration.sessionReplaySampleRate!!
                        )
                    }
                    if (configuration.useProxy != null) {
                        hasField("use_proxy", configuration.useProxy!!)
                    }
                    if (configuration.useBeforeSend != null) {
                        hasField("use_before_send", configuration.useBeforeSend!!)
                    }
                    if (configuration.silentMultipleInit != null) {
                        hasField("silent_multiple_init", configuration.silentMultipleInit!!)
                    }
                    if (configuration.trackSessionAcrossSubdomains != null) {
                        hasField(
                            "track_session_across_subdomains",
                            configuration.trackSessionAcrossSubdomains!!
                        )
                    }
                    if (configuration.useCrossSiteSessionCookie != null) {
                        hasField(
                            "use_cross_site_session_cookie",
                            configuration.useCrossSiteSessionCookie!!
                        )
                    }
                    if (configuration.useSecureSessionCookie != null) {
                        hasField(
                            "use_secure_session_cookie",
                            configuration.useSecureSessionCookie!!
                        )
                    }
                    if (configuration.actionNameAttribute != null) {
                        hasField("action_name_attribute", configuration.actionNameAttribute!!)
                    }
                    if (configuration.useAllowedTracingOrigins != null) {
                        hasField(
                            "use_allowed_tracing_origins",
                            configuration.useAllowedTracingOrigins!!
                        )
                    }
                    if (configuration.defaultPrivacyLevel != null) {
                        hasField("default_privacy_level", configuration.defaultPrivacyLevel!!)
                    }
                    if (configuration.useExcludedActivityUrls != null) {
                        hasField(
                            "use_excluded_activity_urls",
                            configuration.useExcludedActivityUrls!!
                        )
                    }
                    if (configuration.trackFrustrations != null) {
                        hasField("track_frustrations", configuration.trackFrustrations!!)
                    }
                    if (configuration.trackViewsManually != null) {
                        hasField("track_views_manually", configuration.trackViewsManually!!)
                    }
                    if (configuration.trackInteractions != null) {
                        hasField("track_interactions", configuration.trackInteractions!!)
                    }
                    if (configuration.forwardErrorsToLogs != null) {
                        hasField("forward_errors_to_logs", configuration.forwardErrorsToLogs!!)
                    }
                    if (configuration.forwardConsoleLogs != null) {
                        hasField("forward_console_logs", configuration.forwardConsoleLogs!!)
                    }
                    if (configuration.forwardReports != null) {
                        hasField("forward_reports", configuration.forwardReports!!)
                    }
                    if (configuration.useLocalEncryption != null) {
                        hasField("use_local_encryption", configuration.useLocalEncryption!!)
                    }
                    if (configuration.viewTrackingStrategy != null) {
                        hasField(
                            "view_tracking_strategy",
                            configuration.viewTrackingStrategy!!.toJson()
                        )
                    }
                    if (configuration.trackBackgroundEvents != null) {
                        hasField("track_background_events", configuration.trackBackgroundEvents!!)
                    }
                    if (configuration.mobileVitalsUpdatePeriod != null) {
                        hasField(
                            "mobile_vitals_update_period",
                            configuration.mobileVitalsUpdatePeriod!!
                        )
                    }
                    if (configuration.trackInteractions != null) {
                        hasField("track_interactions", configuration.trackInteractions!!)
                    }
                    if (configuration.trackErrors != null) {
                        hasField("track_errors", configuration.trackErrors!!)
                    }
                    if (configuration.trackNetworkRequests != null) {
                        hasField("track_network_requests", configuration.trackNetworkRequests!!)
                    }
                    if (configuration.useTracing != null) {
                        hasField("use_tracing", configuration.useTracing!!)
                    }
                    if (configuration.trackNativeViews != null) {
                        hasField("track_native_views", configuration.trackNativeViews!!)
                    }
                    if (configuration.trackNativeErrors != null) {
                        hasField("track_native_errors", configuration.trackNativeErrors!!)
                    }
                    if (configuration.trackNativeLongTasks != null) {
                        hasField("track_native_long_tasks", configuration.trackNativeLongTasks!!)
                    }
                    if (configuration.trackCrossPlatformLongTasks != null) {
                        hasField(
                            "track_cross_platform_long_tasks",
                            configuration.trackCrossPlatformLongTasks!!
                        )
                    }
                    if (configuration.useFirstPartyHosts != null) {
                        hasField("use_first_party_hosts", configuration.useFirstPartyHosts!!)
                    }
                    if (configuration.initializationType != null) {
                        hasField("initialization_type", configuration.initializationType!!)
                    }
                    if (configuration.trackFlutterPerformance != null) {
                        hasField(
                            "track_flutter_performance",
                            configuration.trackFlutterPerformance!!
                        )
                    }
                    if (configuration.batchSize != null) {
                        hasField("batch_size", configuration.batchSize!!)
                    }
                    if (configuration.batchUploadFrequency != null) {
                        hasField("batch_upload_frequency", configuration.batchUploadFrequency!!)
                    }
                }
            }

        val application = event.application
        if (application != null) {
            assertThat(jsonObject)
                .hasField("application") {
                    hasField("id", application.id)
                }
        } else {
            assertThat(jsonObject).doesNotHaveField("application")
        }

        val session = event.session
        if (session != null) {
            assertThat(jsonObject)
                .hasField("session") {
                    hasField("id", session.id)
                }
        } else {
            assertThat(jsonObject).doesNotHaveField("session")
        }

        val view = event.view
        if (view != null) {
            assertThat(jsonObject)
                .hasField("view") {
                    hasField("id", view.id)
                }
        } else {
            assertThat(jsonObject).doesNotHaveField("view")
        }

        val action = event.action
        if (action != null) {
            assertThat(jsonObject)
                .hasField("action") {
                    hasField("id", action.id)
                }
        } else {
            assertThat(jsonObject).doesNotHaveField("action")
        }
    }

    @Test
    fun `𝕄 serialize RUM event 𝕎 serialize() with unknown event`(
        @Forgery unknownEvent: UserInfo
    ) {
        val serialized = testedSerializer.serialize(unknownEvent)
        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        assertThat(jsonObject)
            .doesNotHaveField("type")
            .doesNotHaveField("date")
            .doesNotHaveField("error")
            .doesNotHaveField("action")
            .doesNotHaveField("resource")
            .doesNotHaveField("application")
            .doesNotHaveField("session")
            .doesNotHaveField("view")
            .doesNotHaveField("usr")
            .doesNotHaveField("_dd")
    }

    @Test
    fun `𝕄 do nothing and return the same object 𝕎 serialize() { already serialized event }`(
        @Forgery fakeJsonObject: JsonObject
    ) {
        val serialized = testedSerializer.serialize(fakeJsonObject)
        assertThat(serialized).isEqualTo(fakeJsonObject.toString())
    }

    @Test
    fun `𝕄 keep known custom attributes as is 𝕎 serialize()`(forge: Forge) {
        val key = forge.anElementFrom(RumEventSerializer.knownAttributes)
        val value = forge.anAlphabeticalString()
        val event = forge.forgeRumEvent(mutableMapOf(key to value))

        val serialized = testedSerializer.serialize(event)

        val jsonObject = JsonParser.parseString(serialized).asJsonObject

        assertThat(jsonObject)
            .hasField(key, value)
    }

    @Test
    fun `M sanitise the custom attributes keys W level deeper than 9`(forge: Forge) {
        // GIVEN
        val fakeBadKey =
            forge.aList(size = 10) { forge.anAlphabeticalString() }.joinToString(".")
        val lastIndexOf = fakeBadKey.lastIndexOf('.')
        val expectedSanitisedKey =
            fakeBadKey.replaceRange(lastIndexOf..lastIndexOf, "_")
        val fakeAttributeValue = forge.anAlphabeticalString()
        val fakeEvent = forge.forgeRumEvent(
            mutableMapOf(
                fakeBadKey to fakeAttributeValue
            )
        )

        // WHEN
        val serializedEvent = testedSerializer.serialize(fakeEvent)
        val jsonObject = JsonParser.parseString(serializedEvent).asJsonObject

        // THEN
        val contextObject = jsonObject.getAsJsonObject(RumEventSerializer.GLOBAL_ATTRIBUTE_PREFIX)
        assertThat(contextObject)
            .hasField(
                expectedSanitisedKey,
                fakeAttributeValue
            )
        assertThat(contextObject)
            .doesNotHaveField(fakeBadKey)
    }

    @Test
    fun `M sanitise the user extra info keys W total level deeper than 10`(forge: Forge) {
        // GIVEN
        val fakeBadKey = forge.aList(size = 10) { forge.anAlphabeticalString() }.joinToString(".")
        val lastIndexOf = fakeBadKey.lastIndexOf('.')
        val expectedSanitisedKey =
            fakeBadKey.replaceRange(lastIndexOf..lastIndexOf, "_")
        val fakeAttributeValue = forge.anAlphabeticalString()
        val fakeEvent = forge.forgeRumEvent(
            userAttributes = mutableMapOf(
                fakeBadKey to fakeAttributeValue
            )
        )

        // WHEN
        val serializedEvent = testedSerializer.serialize(fakeEvent)
        val jsonObject = JsonParser.parseString(serializedEvent).asJsonObject

        // THEN
        val userObject = jsonObject.getAsJsonObject(RumEventSerializer.USER_ATTRIBUTE_PREFIX)
        assertThat(userObject)
            .hasField(
                expectedSanitisedKey,
                fakeAttributeValue
            )
        assertThat(userObject)
            .doesNotHaveField(fakeBadKey)
    }

    @Test
    fun `M use the attributes group verbose name W validateAttributes { ViewEvent }`(
        @Forgery fakeEvent: ViewEvent
    ) {
        // GIVEN
        val mockedDataConstrains: DataConstraints = mock()
        testedSerializer = RumEventSerializer(mockInternalLogger, mockedDataConstrains)

        // WHEN
        testedSerializer.serialize(fakeEvent)

        // THEN
        fakeEvent.usr?.let {
            verify(mockedDataConstrains).validateAttributes(
                it.additionalProperties,
                RumEventSerializer.USER_ATTRIBUTE_PREFIX,
                RumEventSerializer.USER_EXTRA_GROUP_VERBOSE_NAME,
                RumEventSerializer.ignoredAttributes
            )
        }
    }

    @Test
    fun `M use the attributes group verbose name W validateAttributes { ActionEvent }`(
        @Forgery fakeEvent: ActionEvent
    ) {
        // GIVEN
        val mockedDataConstrains: DataConstraints = mock()
        testedSerializer = RumEventSerializer(mockInternalLogger, mockedDataConstrains)

        // WHEN
        testedSerializer.serialize(fakeEvent)

        // THEN
        fakeEvent.usr?.let {
            verify(mockedDataConstrains).validateAttributes(
                it.additionalProperties,
                RumEventSerializer.USER_ATTRIBUTE_PREFIX,
                RumEventSerializer.USER_EXTRA_GROUP_VERBOSE_NAME,
                RumEventSerializer.ignoredAttributes
            )
        }
    }

    @Test
    fun `M use the attributes group verbose name W validateAttributes { ResourceEvent }`(
        @Forgery fakeEvent: ResourceEvent
    ) {
        // GIVEN
        val mockedDataConstrains: DataConstraints = mock()
        testedSerializer = RumEventSerializer(mockInternalLogger, mockedDataConstrains)

        // WHEN
        testedSerializer.serialize(fakeEvent)

        // THEN
        fakeEvent.usr?.let {
            verify(mockedDataConstrains).validateAttributes(
                it.additionalProperties,
                RumEventSerializer.USER_ATTRIBUTE_PREFIX,
                RumEventSerializer.USER_EXTRA_GROUP_VERBOSE_NAME,
                RumEventSerializer.ignoredAttributes
            )
        }
    }

    @Test
    fun `M use the attributes group verbose name W validateAttributes { ErrorEvent }`(
        @Forgery fakeEvent: ErrorEvent
    ) {
        // GIVEN
        val mockedDataConstrains: DataConstraints = mock()
        testedSerializer = RumEventSerializer(mockInternalLogger, mockedDataConstrains)

        // WHEN
        testedSerializer.serialize(fakeEvent)

        // THEN
        fakeEvent.usr?.let {
            verify(mockedDataConstrains).validateAttributes(
                it.additionalProperties,
                RumEventSerializer.USER_ATTRIBUTE_PREFIX,
                RumEventSerializer.USER_EXTRA_GROUP_VERBOSE_NAME,
                RumEventSerializer.ignoredAttributes
            )
        }
    }

    @Test
    fun `M use the attributes group verbose name W validateAttributes { LongTaskEvent }`(
        @Forgery fakeEvent: LongTaskEvent
    ) {
        // GIVEN
        val mockedDataConstrains: DataConstraints = mock()
        testedSerializer = RumEventSerializer(mockInternalLogger, mockedDataConstrains)

        // WHEN
        testedSerializer.serialize(fakeEvent)

        // THEN
        fakeEvent.usr?.let {
            verify(mockedDataConstrains).validateAttributes(
                it.additionalProperties,
                RumEventSerializer.USER_ATTRIBUTE_PREFIX,
                RumEventSerializer.USER_EXTRA_GROUP_VERBOSE_NAME,
                RumEventSerializer.ignoredAttributes
            )
        }
    }

    @Test
    fun `M drop the internal reserved attributes W serialize { custom global attributes }`(
        forge: Forge
    ) {
        // GIVEN
        val fakeInternalTimestamp = forge.aLong()
        val fakeErrorType = forge.aString()
        val fakeErrorSourceType = forge.aString()
        val fakeIsCrash = forge.aBool()
        val fakeEventWithInternalGlobalAttributes = forge.forgeRumEvent(
            attributes = mutableMapOf(
                RumAttributes.INTERNAL_ERROR_TYPE to fakeErrorType,
                RumAttributes.INTERNAL_TIMESTAMP to fakeInternalTimestamp,
                RumAttributes.INTERNAL_ERROR_SOURCE_TYPE to fakeErrorSourceType,
                RumAttributes.INTERNAL_ERROR_IS_CRASH to fakeIsCrash
            )
        )
        // WHEN
        val serializedEvent = testedSerializer.serialize(fakeEventWithInternalGlobalAttributes)
        val jsonObject = JsonParser.parseString(serializedEvent).asJsonObject

        // THEN
        assertThat(jsonObject)
            .doesNotHaveField(
                RumEventSerializer.GLOBAL_ATTRIBUTE_PREFIX + "." + RumAttributes.INTERNAL_TIMESTAMP
            )
        assertThat(jsonObject)
            .doesNotHaveField(
                RumEventSerializer.GLOBAL_ATTRIBUTE_PREFIX + "." + RumAttributes.INTERNAL_ERROR_TYPE
            )
        assertThat(jsonObject)
            .doesNotHaveField(
                RumEventSerializer.GLOBAL_ATTRIBUTE_PREFIX +
                    "." + RumAttributes.INTERNAL_ERROR_SOURCE_TYPE
            )
        assertThat(jsonObject)
            .doesNotHaveField(
                RumEventSerializer.GLOBAL_ATTRIBUTE_PREFIX +
                    "." + RumAttributes.INTERNAL_ERROR_IS_CRASH
            )
    }

    @Test
    fun `M silently drop the cross-platform attributes W serialize { custom global attributes }`(
        forge: Forge
    ) {
        // GIVEN
        val fakeInternalTimestamp = forge.aLong()
        val fakeErrorType = forge.aString()
        val fakeErrorSourceType = forge.aString()
        val fakeIsCrash = forge.aBool()
        val fakeEventWithInternalGlobalAttributes = forge.forgeRumEvent(
            attributes = mutableMapOf(
                RumAttributes.INTERNAL_ERROR_TYPE to fakeErrorType,
                RumAttributes.INTERNAL_TIMESTAMP to fakeInternalTimestamp,
                RumAttributes.INTERNAL_ERROR_SOURCE_TYPE to fakeErrorSourceType,
                RumAttributes.INTERNAL_ERROR_IS_CRASH to fakeIsCrash
            )
        )
        val mockedDataConstrains: DataConstraints = mock()
        testedSerializer = RumEventSerializer(mockInternalLogger, mockedDataConstrains)

        // WHEN
        testedSerializer.serialize(fakeEventWithInternalGlobalAttributes)

        // THEN
        argumentCaptor<Map<String, Any?>> {
            verify(mockedDataConstrains).validateAttributes(
                capture(),
                eq(RumEventSerializer.GLOBAL_ATTRIBUTE_PREFIX),
                isNull(),
                eq(RumEventSerializer.ignoredAttributes)
            )
            assertThat(lastValue)
                .doesNotContainKey(RumAttributes.INTERNAL_ERROR_TYPE)
                .doesNotContainKey(RumAttributes.INTERNAL_TIMESTAMP)
                .doesNotContainKey(RumAttributes.INTERNAL_ERROR_SOURCE_TYPE)
                .doesNotContainKey(RumAttributes.INTERNAL_ERROR_IS_CRASH)
        }
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M drop the internal reserved attributes W serialize { custom user attributes }`(
        forge: Forge
    ) {
        // GIVEN
        val fakeInternalTimestamp = forge.aLong()
        val fakeErrorType = forge.aString()
        val fakeErrorSourceType = forge.aString()
        val fakeIsCrash = forge.aBool()
        val fakeEventWithInternalUserAttributes = forge.forgeRumEvent(
            userAttributes = mutableMapOf(
                RumAttributes.INTERNAL_ERROR_TYPE to fakeErrorType,
                RumAttributes.INTERNAL_TIMESTAMP to fakeInternalTimestamp,
                RumAttributes.INTERNAL_ERROR_SOURCE_TYPE to fakeErrorSourceType,
                RumAttributes.INTERNAL_ERROR_IS_CRASH to fakeIsCrash
            )
        )

        // WHEN
        val serializedEvent = testedSerializer.serialize(fakeEventWithInternalUserAttributes)
        val jsonObject = JsonParser.parseString(serializedEvent).asJsonObject

        // THEN
        assertThat(jsonObject)
            .doesNotHaveField(
                RumEventSerializer.USER_ATTRIBUTE_PREFIX + "." + RumAttributes.INTERNAL_TIMESTAMP
            )
        assertThat(jsonObject)
            .doesNotHaveField(
                RumEventSerializer.USER_ATTRIBUTE_PREFIX + "." + RumAttributes.INTERNAL_ERROR_TYPE
            )
        assertThat(jsonObject)
            .doesNotHaveField(
                RumEventSerializer.GLOBAL_ATTRIBUTE_PREFIX +
                    "." + RumAttributes.INTERNAL_ERROR_SOURCE_TYPE
            )
        assertThat(jsonObject)
            .doesNotHaveField(
                RumEventSerializer.GLOBAL_ATTRIBUTE_PREFIX +
                    "." + RumAttributes.INTERNAL_ERROR_IS_CRASH
            )
    }

    @Test
    fun `𝕄 drop non-serializable attributes 𝕎 serialize() with ResourceEvent { bad usr#additionalProperties }`(
        @Forgery event: ResourceEvent,
        forge: Forge
    ) {
        // Given
        val faultyKey = forge.anAlphabeticalString()
        val faultyObject = object {
            override fun toString(): String {
                throw forge.anException()
            }
        }
        val faultyEvent = event.copy(
            usr = event.usr?.copy(
                additionalProperties = event.usr?.additionalProperties
                    ?.toMutableMap()
                    ?.apply { put(faultyKey, faultyObject) }
                    .orEmpty()
                    .toMutableMap()
            )
        )

        // When
        val serialized = testedSerializer.serialize(faultyEvent)

        // Then
        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        event.usr?.let { usr ->
            assertThat(jsonObject).hasField("usr") {
                hasNullableField("id", usr.id)
                hasNullableField("name", usr.name)
                hasNullableField("email", usr.email)
                containsAttributes(usr.additionalProperties)
            }
        }
    }

    @Test
    fun `𝕄 drop non-serializable attributes 𝕎 serialize() with ResourceEvent { bad context#additionalProperties }`(
        @Forgery event: ResourceEvent,
        forge: Forge
    ) {
        // Given
        val faultyKey = forge.anAlphabeticalString()
        val faultyObject = object {
            override fun toString(): String {
                throw forge.anException()
            }
        }
        val faultyEvent = event.copy(
            context = event.context?.copy(
                additionalProperties = event.context?.additionalProperties
                    ?.toMutableMap()
                    ?.apply { put(faultyKey, faultyObject) }
                    .orEmpty()
                    .toMutableMap()
            )
        )

        // When
        val serialized = testedSerializer.serialize(faultyEvent)

        // Then
        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        event.context?.additionalProperties?.let {
            assertThat(jsonObject).hasField("context") {
                containsAttributes(it)
            }
        }
    }

    @Test
    fun `𝕄 drop non-serializable attributes 𝕎 serialize() with ActionEvent { bad usr#additionalProperties }`(
        @Forgery event: ActionEvent,
        forge: Forge
    ) {
        // Given
        val faultyKey = forge.anAlphabeticalString()
        val faultyObject = object {
            override fun toString(): String {
                throw forge.anException()
            }
        }
        val faultyEvent = event.copy(
            usr = event.usr?.copy(
                additionalProperties = event.usr?.additionalProperties
                    ?.toMutableMap()
                    ?.apply { put(faultyKey, faultyObject) }
                    .orEmpty()
                    .toMutableMap()
            )
        )

        // When
        val serialized = testedSerializer.serialize(faultyEvent)

        // Then
        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        event.usr?.let { usr ->
            assertThat(jsonObject).hasField("usr") {
                hasNullableField("id", usr.id)
                hasNullableField("name", usr.name)
                hasNullableField("email", usr.email)
                containsAttributes(usr.additionalProperties)
            }
        }
    }

    @Test
    fun `𝕄 drop non-serializable attributes 𝕎 serialize() with ActionEvent { bad context#additionalProperties }`(
        @Forgery event: ActionEvent,
        forge: Forge
    ) {
        // Given
        val faultyKey = forge.anAlphabeticalString()
        val faultyObject = object {
            override fun toString(): String {
                throw forge.anException()
            }
        }
        val faultyEvent = event.copy(
            context = event.context?.copy(
                additionalProperties = event.context?.additionalProperties
                    ?.toMutableMap()
                    ?.apply { put(faultyKey, faultyObject) }
                    .orEmpty()
                    .toMutableMap()
            )
        )

        // When
        val serialized = testedSerializer.serialize(faultyEvent)

        // Then
        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        event.context?.additionalProperties?.let {
            assertThat(jsonObject).hasField("context") {
                containsAttributes(it)
            }
        }
    }

    @Test
    fun `𝕄 drop non-serializable attributes 𝕎 serialize() with ViewEvent { bad usr#additionalProperties }`(
        @Forgery event: ViewEvent,
        forge: Forge
    ) {
        // Given
        val faultyKey = forge.anAlphabeticalString()
        val faultyObject = object {
            override fun toString(): String {
                throw forge.anException()
            }
        }
        val faultyEvent = event.copy(
            usr = event.usr?.copy(
                additionalProperties = event.usr?.additionalProperties
                    ?.toMutableMap()
                    ?.apply { put(faultyKey, faultyObject) }
                    .orEmpty()
                    .toMutableMap()
            )
        )

        // When
        val serialized = testedSerializer.serialize(faultyEvent)

        // Then
        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        event.usr?.let { usr ->
            assertThat(jsonObject).hasField("usr") {
                hasNullableField("id", usr.id)
                hasNullableField("name", usr.name)
                hasNullableField("email", usr.email)
                containsAttributes(usr.additionalProperties)
            }
        }
    }

    @Test
    fun `𝕄 drop non-serializable attributes 𝕎 serialize() with ViewEvent { bad context#additionalProperties }`(
        @Forgery event: ViewEvent,
        forge: Forge
    ) {
        // Given
        val faultyKey = forge.anAlphabeticalString()
        val faultyObject = object {
            override fun toString(): String {
                throw forge.anException()
            }
        }
        val faultyEvent = event.copy(
            context = event.context?.copy(
                additionalProperties = event.context?.additionalProperties
                    ?.toMutableMap()
                    ?.apply { put(faultyKey, faultyObject) }
                    .orEmpty()
                    .toMutableMap()
            )
        )

        // When
        val serialized = testedSerializer.serialize(faultyEvent)

        // Then
        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        event.context?.additionalProperties?.let {
            assertThat(jsonObject).hasField("context") {
                containsAttributes(it)
            }
        }
    }

    @Test
    fun `𝕄 drop non-serializable attributes 𝕎 serialize() with ErrorEvent { bad usr#additionalProperties }`(
        @Forgery event: ErrorEvent,
        forge: Forge
    ) {
        // Given
        val faultyKey = forge.anAlphabeticalString()
        val faultyObject = object {
            override fun toString(): String {
                throw forge.anException()
            }
        }
        val faultyEvent = event.copy(
            usr = event.usr?.copy(
                additionalProperties = event.usr?.additionalProperties
                    ?.toMutableMap()
                    ?.apply { put(faultyKey, faultyObject) }
                    .orEmpty()
                    .toMutableMap()
            )
        )

        // When
        val serialized = testedSerializer.serialize(faultyEvent)

        // Then
        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        event.usr?.let { usr ->
            assertThat(jsonObject).hasField("usr") {
                hasNullableField("id", usr.id)
                hasNullableField("name", usr.name)
                hasNullableField("email", usr.email)
                containsAttributes(usr.additionalProperties)
            }
        }
    }

    @Test
    fun `𝕄 drop non-serializable attributes 𝕎 serialize() with ErrorEvent { bad context#additionalProperties }`(
        @Forgery event: ErrorEvent,
        forge: Forge
    ) {
        // Given
        val faultyKey = forge.anAlphabeticalString()
        val faultyObject = object {
            override fun toString(): String {
                throw forge.anException()
            }
        }
        val faultyEvent = event.copy(
            context = event.context?.copy(
                additionalProperties = event.context?.additionalProperties
                    ?.toMutableMap()
                    ?.apply { put(faultyKey, faultyObject) }
                    .orEmpty()
                    .toMutableMap()
            )
        )

        // When
        val serialized = testedSerializer.serialize(faultyEvent)

        // Then
        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        event.context?.additionalProperties?.let {
            assertThat(jsonObject).hasField("context") {
                containsAttributes(it)
            }
        }
    }

    @Test
    fun `𝕄 drop non-serializable attributes 𝕎 serialize() with LongTaskEvent { bad usr#additionalProperties }`(
        @Forgery event: LongTaskEvent,
        forge: Forge
    ) {
        // Given
        val faultyKey = forge.anAlphabeticalString()
        val faultyObject = object {
            override fun toString(): String {
                throw forge.anException()
            }
        }
        val faultyEvent = event.copy(
            usr = event.usr?.copy(
                additionalProperties = event.usr?.additionalProperties
                    ?.toMutableMap()
                    ?.apply { put(faultyKey, faultyObject) }
                    .orEmpty()
                    .toMutableMap()
            )
        )

        // When
        val serialized = testedSerializer.serialize(faultyEvent)

        // Then
        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        event.usr?.let { usr ->
            assertThat(jsonObject).hasField("usr") {
                hasNullableField("id", usr.id)
                hasNullableField("name", usr.name)
                hasNullableField("email", usr.email)
                containsAttributes(usr.additionalProperties)
            }
        }
    }

    @Test
    fun `𝕄 drop non-serializable attributes 𝕎 serialize() with LongTaskEvent { bad context#additionalProperties }`(
        @Forgery event: LongTaskEvent,
        forge: Forge
    ) {
        // Given
        val faultyKey = forge.anAlphabeticalString()
        val faultyObject = object {
            override fun toString(): String {
                throw forge.anException()
            }
        }
        val faultyEvent = event.copy(
            context = event.context?.copy(
                additionalProperties = event.context?.additionalProperties
                    ?.toMutableMap()
                    ?.apply { put(faultyKey, faultyObject) }
                    .orEmpty()
                    .toMutableMap()
            )
        )

        // When
        val serialized = testedSerializer.serialize(faultyEvent)

        // Then
        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        event.context?.additionalProperties?.let {
            assertThat(jsonObject).hasField("context") {
                containsAttributes(it)
            }
        }
    }

    // region Internal

    private fun Forge.forgeRumEvent(
        attributes: MutableMap<String, Any?> = mutableMapOf(),
        userAttributes: MutableMap<String, Any?> = mutableMapOf()
    ): Any {
        return when (this.anInt(min = 0, max = 5)) {
            1 -> this.getForgery(ViewEvent::class.java).let {
                it.copy(
                    context = ViewEvent.Context(additionalProperties = attributes),
                    usr = (it.usr ?: ViewEvent.Usr()).copy(additionalProperties = userAttributes)
                )
            }
            2 -> this.getForgery(ActionEvent::class.java).let {
                it.copy(
                    context = ActionEvent.Context(additionalProperties = attributes),
                    usr = (it.usr ?: ActionEvent.Usr()).copy(additionalProperties = userAttributes)
                )
            }
            3 -> this.getForgery(ErrorEvent::class.java).let {
                it.copy(
                    context = ErrorEvent.Context(additionalProperties = attributes),
                    usr = (it.usr ?: ErrorEvent.Usr()).copy(additionalProperties = userAttributes)
                )
            }
            4 -> this.getForgery(ResourceEvent::class.java).let {
                it.copy(
                    context = ResourceEvent.Context(additionalProperties = attributes),
                    usr = (it.usr ?: ResourceEvent.Usr())
                        .copy(additionalProperties = userAttributes)
                )
            }
            else -> this.getForgery(LongTaskEvent::class.java).let {
                it.copy(
                    context = LongTaskEvent.Context(additionalProperties = attributes),
                    usr = (it.usr ?: LongTaskEvent.Usr())
                        .copy(additionalProperties = userAttributes)
                )
            }
        }
    }

    // endregion
}
