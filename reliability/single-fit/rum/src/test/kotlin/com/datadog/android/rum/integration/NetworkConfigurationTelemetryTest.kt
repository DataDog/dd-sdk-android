/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.integration

import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureEventReceiver
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.internal.net.DefaultFirstPartyHostHeaderTypeResolver
import com.datadog.android.core.stub.StubSDKCore
import com.datadog.android.internal.telemetry.InternalTelemetryEvent
import com.datadog.android.internal.telemetry.NetworkInstrumentationConfiguration
import com.datadog.android.okhttp.DatadogInterceptor
import com.datadog.android.okhttp.configureDatadogInstrumentation
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum._RumInternalProxy
import com.datadog.android.rum.configuration.RumNetworkInstrumentationConfiguration
import com.datadog.android.rum.integration.tests.elmyr.RumIntegrationForgeConfigurator
import com.datadog.android.rum.integration.tests.utils.MainLooperTestConfiguration
import com.datadog.android.rum.resource.ResourceHeadersExtractor
import com.datadog.android.tests.config.DatadogSingletonTestConfiguration
import com.datadog.android.tests.config.createMockSdkCore
import com.datadog.android.tests.elmyr.aHostName
import com.datadog.android.trace.ApmNetworkInstrumentationConfiguration
import com.datadog.android.trace.ExperimentalTraceApi
import com.datadog.android.trace.internal._TraceInternalProxy
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@Extensions(
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@ForgeConfiguration(RumIntegrationForgeConfigurator::class)
internal class NetworkConfigurationTelemetryTest {
    @StringForgery
    lateinit var fakeApplicationId: String

    private lateinit var fakeCoreConfiguration: InternalTelemetryEvent.Configuration

    private lateinit var fakeConfiguration: RumConfiguration

    @BeforeEach
    fun setUp(forge: Forge) {
        datadogCore.clearRegistry()
        fakeCoreConfiguration = InternalTelemetryEvent.Configuration(
            trackErrors = forge.aBool(),
            batchSize = forge.aLong(min = 1),
            batchUploadFrequency = forge.aLong(min = 1),
            useProxy = forge.aBool(),
            useLocalEncryption = forge.aBool(),
            batchProcessingLevel = forge.anInt(min = 1)
        )
        val builder = RumConfiguration.Builder(fakeApplicationId)
            .trackNonFatalAnrs(false)
            .setTelemetrySampleRate(100f)
        _RumInternalProxy.setAdditionalConfiguration(
            builder,
            mapOf("_dd.telemetry.configuration_sample_rate" to 100f)
        )
        fakeConfiguration = builder.build()
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `M retain network configuration W RUM lifecycle {setup before or after enable}`(
        setupBeforeRum: Boolean,
        forge: Forge
    ) {
        // Given
        val stubSdkCore =
            StubSDKCore(forge, firstPartyHostResolver = DefaultFirstPartyHostHeaderTypeResolver(emptyMap()))
        val sdkCore = createMockSdkCore(NetworkInstrumentationConfiguration(), stubSdkCore)
        datadogCore.register(sdkCore, null)
        if (!setupBeforeRum) Rum.enable(fakeConfiguration, sdkCore)
        DatadogInterceptor.Builder(listOf(forge.aHostName()))
            .trackResourceHeaders()
            .build()
        if (setupBeforeRum) Rum.enable(fakeConfiguration, sdkCore)
        // An empty integration must not erase the configuration reported by an earlier one.
        DatadogInterceptor.Builder(emptyList<String>()).build()
        val rumMonitor = GlobalRumMonitor.get(sdkCore)
        val fakeViewKey = forge.anAlphabeticalString()

        // When
        rumMonitor.startView(fakeViewKey, forge.anAlphabeticalString())
        rumMonitor.stopView(fakeViewKey)
        rumMonitor.stopSession()
        rumMonitor.startView(forge.anAlphabeticalString(), forge.anAlphabeticalString())
        val configuration = sendConfiguration(sdkCore, stubSdkCore)

        // Then
        assertThat(configuration["track_network_requests"].asBoolean).isTrue()
        assertThat(configuration["use_first_party_hosts"].asBoolean).isTrue()
        assertThat(configuration["track_resource_headers"].asString).isEqualTo("default_headers")
    }

    @OptIn(ExperimentalRumApi::class, ExperimentalTraceApi::class)
    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `M retain network configuration W SDK initialization {client built before SDK and no requests}`(
        fakeUseLegacyInstrumentation: Boolean,
        forge: Forge
    ) {
        // Given
        val fakeHeadersExtractor = ResourceHeadersExtractor.Builder()
            .captureHeaders(forge.anAlphabeticalString())
            .build()
        val fakeHosts = listOf(forge.aHostName())
        val fakeClient = if (fakeUseLegacyInstrumentation) {
            OkHttpClient.Builder()
                .addInterceptor(
                    DatadogInterceptor.Builder(fakeHosts)
                        .trackResourceHeaders(fakeHeadersExtractor)
                        .build()
                )
                .build()
        } else {
            OkHttpClient.Builder()
                .configureDatadogInstrumentation(
                    rumInstrumentationConfiguration = RumNetworkInstrumentationConfiguration()
                        .trackResourceHeaders(fakeHeadersExtractor),
                    apmInstrumentationConfiguration = ApmNetworkInstrumentationConfiguration(fakeHosts)
                )
                .build()
        }
        val stubSdkCore =
            StubSDKCore(forge, firstPartyHostResolver = DefaultFirstPartyHostHeaderTypeResolver(emptyMap()))
        val sdkCore = createMockSdkCore(NetworkInstrumentationConfiguration(), stubSdkCore)

        // When
        datadogCore.register(sdkCore, null)
        Rum.enable(fakeConfiguration, sdkCore)
        val configuration = sendConfiguration(sdkCore, stubSdkCore)

        // Then
        assertThat(configuration["track_network_requests"].asBoolean).isTrue()
        assertThat(configuration["use_first_party_hosts"].asBoolean).isTrue()
        assertThat(configuration["track_resource_headers"].asString).isEqualTo("custom")
        // Keep the client reachable through the configuration snapshot without executing a request.
        assertThat(fakeClient.dispatcher.runningCallsCount()).isZero()
    }

    @OptIn(ExperimentalTraceApi::class)
    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `M retain APM hosts W integration setup {before RUM and before first request}`(
        headerPropagationOnly: Boolean,
        forge: Forge
    ) {
        // Given
        val stubSdkCore =
            StubSDKCore(forge, firstPartyHostResolver = DefaultFirstPartyHostHeaderTypeResolver(emptyMap()))
        val sdkCore = createMockSdkCore(NetworkInstrumentationConfiguration(), stubSdkCore)
        datadogCore.register(sdkCore, null)
        val fakeApmConfiguration = ApmNetworkInstrumentationConfiguration(listOf(forge.aHostName())).apply {
            if (headerPropagationOnly) setHeaderPropagationOnly()
        }

        // When
        _TraceInternalProxy.createApmNetworkInstrumentation(forge.anAlphabeticalString(), fakeApmConfiguration)
        Rum.enable(fakeConfiguration, sdkCore)
        GlobalRumMonitor.get(sdkCore).startView(forge.anAlphabeticalString(), forge.anAlphabeticalString())
        val configuration = sendConfiguration(sdkCore, stubSdkCore)

        // Then
        assertThat(configuration["use_first_party_hosts"].asBoolean).isTrue()
        assertThat(configuration["track_network_requests"].asBoolean).isFalse()
    }

    @Test
    fun `M isolate network configuration W RUM lifecycle {two SDK instances}`(forge: Forge) {
        // Given
        val stubFirstSdkCore =
            StubSDKCore(forge, firstPartyHostResolver = DefaultFirstPartyHostHeaderTypeResolver(emptyMap()))
        val stubSecondSdkCore =
            StubSDKCore(forge, firstPartyHostResolver = DefaultFirstPartyHostHeaderTypeResolver(emptyMap()))
        val firstSdkCore = createMockSdkCore(NetworkInstrumentationConfiguration(), stubFirstSdkCore)
        val secondSdkCore = createMockSdkCore(NetworkInstrumentationConfiguration(), stubSecondSdkCore)
        val fakeFirstSdkName = forge.anAlphabeticalString()
        val fakeSecondSdkName = forge.anAlphabeticalString() + fakeFirstSdkName
        datadogCore.register(firstSdkCore, fakeFirstSdkName)
        datadogCore.register(secondSdkCore, fakeSecondSdkName)
        DatadogInterceptor.Builder(listOf(forge.aHostName()))
            .setSdkInstanceName(fakeFirstSdkName)
            .trackResourceHeaders()
            .build()
        Rum.enable(fakeConfiguration, firstSdkCore)
        Rum.enable(fakeConfiguration, secondSdkCore)

        // When
        listOf(firstSdkCore, secondSdkCore).forEach { sdkCore ->
            GlobalRumMonitor.get(sdkCore).startView(forge.anAlphabeticalString(), forge.anAlphabeticalString())
        }
        val firstConfiguration = sendConfiguration(firstSdkCore, stubFirstSdkCore)
        val secondConfiguration = sendConfiguration(secondSdkCore, stubSecondSdkCore)

        // Then
        assertThat(firstConfiguration["track_network_requests"].asBoolean).isTrue()
        assertThat(firstConfiguration["use_first_party_hosts"].asBoolean).isTrue()
        assertThat(firstConfiguration["track_resource_headers"].asString).isEqualTo("default_headers")
        assertThat(secondConfiguration["track_network_requests"].asBoolean).isFalse()
        assertThat(secondConfiguration["use_first_party_hosts"].asBoolean).isFalse()
        assertThat(secondConfiguration.has("track_resource_headers")).isFalse()
    }

    private fun sendConfiguration(sdkCore: InternalSdkCore, stubSdkCore: StubSDKCore): JsonObject {
        val feature = checkNotNull(sdkCore.getFeature(Feature.RUM_FEATURE_NAME)).unwrap<Feature>()
        check(feature is FeatureEventReceiver)
        feature.onReceive(fakeCoreConfiguration)
        return stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
            .map { JsonParser.parseString(it.eventData).asJsonObject }
            .single { it.getAsJsonObject("telemetry")?.get("type")?.asString == "configuration" }
            .getAsJsonObject("telemetry").getAsJsonObject("configuration")
    }

    companion object {
        private val datadogCore = DatadogSingletonTestConfiguration()

        @JvmStatic
        @TestConfigurationsProvider
        fun configurations(): List<TestConfiguration> = listOf(datadogCore, MainLooperTestConfiguration())
    }
}
