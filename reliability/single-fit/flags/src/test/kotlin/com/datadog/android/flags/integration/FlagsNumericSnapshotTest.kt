/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.integration

import com.datadog.android.api.feature.Feature
import com.datadog.android.flags.Flags
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.FlagsConfiguration
import com.datadog.android.flags._FlagsInternalProxy
import com.datadog.android.flags.integration.FlagsClientTest.Companion.createStubSdkCore
import com.datadog.android.flags.integration.tests.elmyr.FlagsIntegrationForgeConfigurator
import com.datadog.android.flags.integration.tests.utils.httpResponse
import com.datadog.android.flags.model.ErrorCode
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagsClientState
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@ForgeConfiguration(FlagsIntegrationForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class FlagsNumericSnapshotTest {
    @ParameterizedTest
    @ValueSource(strings = ["100000000000000000001", "1e400", "1e-400"])
    fun `M preserve rejected numeric literals W getFlagAssignmentsSnapshot() {network and restored cache}`(
        fakeLiteral: String,
        forge: Forge
    ) {
        // Given
        val fakeContext = EvaluationContext(forge.anAlphabeticalString())
        val fakeResponse = """
            {"data":{"type":"precomputed-assignments","attributes":{"flags":{"numeric":{
                "variationType":"number","variationValue":$fakeLiteral,"doLog":false,
                "allocationKey":"allocation","variationKey":"variant","reason":"TARGETING_MATCH"
            }}}}}
        """.trimIndent()
        var core = createStubSdkCore(forge)
        val store = core.stubDataStore(Feature.FLAGS_FEATURE_NAME)
        core.stubNetworkResponses { httpResponse(it, body = fakeResponse) }
        Flags.enable(
            FlagsConfiguration.Builder().useCustomFlagEndpoint("https://fixture.test/precompute-assignments").build(),
            core
        )
        var client = FlagsClient.Builder(sdkCore = core).build()

        // When
        try {
            client.setEvaluationContext(fakeContext)
            assertThat(core.requestsSent()).hasSize(1)
            val downloadedState = client.state.getCurrentState()
            val downloaded = _FlagsInternalProxy(client).getFlagAssignmentsSnapshot(fakeContext)
            val downloadedDouble = client.resolve("numeric", -1.0)
            core.stopFeatures()
            core = createStubSdkCore(forge)
            core.stubDataStore(Feature.FLAGS_FEATURE_NAME, store)
            core.stubNetworkResponses { null }
            Flags.enable(FlagsConfiguration.Builder().build(), core)
            client = FlagsClient.Builder(sdkCore = core).build()
            val restored = _FlagsInternalProxy(client).getFlagAssignmentsSnapshot(fakeContext)
            val restoredDouble = client.resolve("numeric", -1.0)

            // Then
            assertThat(core.requestsSent()).isEmpty()
            assertThat(downloadedState).isEqualTo(FlagsClientState.Ready)
            listOf(downloaded, restored).forEach { snapshot ->
                val flag = checkNotNull(checkNotNull(snapshot)["numeric"])
                assertThat(flag.variationType).isEqualTo("number")
                assertThat(flag.variationValue).isEqualTo(fakeLiteral)
            }
            listOf(downloadedDouble, restoredDouble).forEach { result ->
                assertThat(result.value).isEqualTo(-1.0)
                assertThat(result.errorCode).isEqualTo(ErrorCode.TYPE_MISMATCH)
            }
        } finally {
            core.stopFeatures()
        }
    }
}
