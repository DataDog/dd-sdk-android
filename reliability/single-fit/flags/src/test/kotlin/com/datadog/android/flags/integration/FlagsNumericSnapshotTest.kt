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
import org.junit.jupiter.api.Test
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
    fun `M omit unrepresentable numeric literals W getFlagAssignmentsSnapshot() {network and restored cache}`(
        fakeLiteral: String,
        forge: Forge
    ) {
        // Given
        val fakeContext = EvaluationContext(forge.anAlphabeticalString())
        val fakeResponse = response(
            "\"numeric\":${assignment("number", fakeLiteral)},\"kept\":${assignment("number", "1.5")}"
        )

        // When
        val results = resolveFromNetworkThenCache(forge, fakeContext, fakeResponse) { client ->
            Pair(
                _FlagsInternalProxy(client).getFlagAssignmentsSnapshot(fakeContext),
                _FlagsInternalProxy(client).getFlagAssignmentsSnapshot()
            ) to client.resolve("numeric", -1.0)
        }

        // Then
        results.forEach { (snapshots, resolution) ->
            listOf(checkNotNull(snapshots.first), snapshots.second).forEach { snapshot ->
                assertThat(snapshot).doesNotContainKey("numeric")
                assertThat(checkNotNull(snapshot["kept"]).variationValue).isEqualTo("1.5")
            }
            assertThat(resolution.value).isEqualTo(-1.0)
            assertThat(resolution.errorCode).isEqualTo(ErrorCode.TYPE_MISMATCH)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["4503599627370497.5", "1.00000000000000001"])
    fun `M reject fractional tokens W resolve(Int) {any nesting, network and restored cache}`(
        fakeLiteral: String,
        forge: Forge
    ) {
        // Given
        val fakeContext = EvaluationContext(forge.anAlphabeticalString())
        // The same token appears at several depths and paths; each lookup must read its own token.
        val nestedObject = "{\"variationValue\":$fakeLiteral,\"inner\":{\"flags\":{\"integer\":$fakeLiteral}}}"
        val fakeResponse = response(
            "\"object\":${assignment("object", nestedObject)}," +
                "\"integer\":${assignment("integer", fakeLiteral)}," +
                "\"number\":${assignment("number", fakeLiteral)}"
        )

        // When
        val results = resolveFromNetworkThenCache(forge, fakeContext, fakeResponse) { client ->
            listOf(client.resolve("integer", -1), client.resolve("number", -1)) to
                listOf(client.resolve("integer", -1.0), client.resolve("number", -1.0))
        }

        // Then
        results.forEach { (intResults, doubleResults) ->
            intResults.forEach {
                assertThat(it.value).isEqualTo(-1)
                assertThat(it.errorCode).isEqualTo(ErrorCode.TYPE_MISMATCH)
            }
            doubleResults.forEach {
                assertThat(it.value).isEqualTo(fakeLiteral.toDouble())
                assertThat(it.errorCode).isNull()
            }
        }
    }

    @Test
    fun `M keep representable numeric values W getFlagAssignmentsSnapshot()`(forge: Forge) {
        // Given
        val fakeContext = EvaluationContext(forge.anAlphabeticalString())
        val fakeResponse = response(
            "\"whole\":${assignment("integer", "9007199254740992")}," +
                "\"fraction\":${assignment("float", "4503599627370497.5")}," +
                "\"text\":${assignment("string", "\"1e400\"")}"
        )

        // When
        val results = resolveFromNetworkThenCache(forge, fakeContext, fakeResponse) { client ->
            _FlagsInternalProxy(client).getFlagAssignmentsSnapshot()
        }

        // Then
        results.forEach { snapshot ->
            assertThat(snapshot.keys).containsExactlyInAnyOrder("whole", "fraction", "text")
            assertThat(checkNotNull(snapshot["fraction"]).variationValue).isEqualTo("4503599627370497.5")
        }
    }

    private fun <T> resolveFromNetworkThenCache(
        forge: Forge,
        context: EvaluationContext,
        body: String,
        read: (FlagsClient) -> T
    ): List<T> {
        var core = createStubSdkCore(forge)
        val store = core.stubDataStore(Feature.FLAGS_FEATURE_NAME)
        core.stubNetworkResponses { httpResponse(it, body = body) }
        Flags.enable(
            FlagsConfiguration.Builder().useCustomFlagEndpoint("https://fixture.test/precompute-assignments").build(),
            core
        )
        var client = FlagsClient.Builder(sdkCore = core).build()
        try {
            client.setEvaluationContext(context)
            assertThat(core.requestsSent()).hasSize(1)
            assertThat(client.state.getCurrentState()).isEqualTo(FlagsClientState.Ready)
            val downloaded = read(client)
            core.stopFeatures()
            core = createStubSdkCore(forge)
            core.stubDataStore(Feature.FLAGS_FEATURE_NAME, store)
            core.stubNetworkResponses { null }
            Flags.enable(FlagsConfiguration.Builder().build(), core)
            client = FlagsClient.Builder(sdkCore = core).build()
            val restored = read(client)
            assertThat(core.requestsSent()).isEmpty()
            return listOf(downloaded, restored)
        } finally {
            core.stopFeatures()
        }
    }

    private fun response(flags: String) =
        "{\"data\":{\"type\":\"precomputed-assignments\",\"attributes\":{\"flags\":{$flags}}}}"

    private fun assignment(variationType: String, rawValue: String) =
        "{\"variationType\":\"$variationType\",\"variationValue\":$rawValue,\"doLog\":false," +
            "\"allocationKey\":\"allocation\",\"variationKey\":\"variant\",\"reason\":\"TARGETING_MATCH\"}"
}
