/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.embedded.internal.rum

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.sessionreplay.embedded.forge.ForgeConfigurator
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.util.UUID

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class EmbeddedRumEventContextProviderTest {

    private lateinit var testedContextProvider: EmbeddedRumEventContextProvider

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @Forgery
    lateinit var fakeApplicationId: UUID

    @Forgery
    lateinit var fakeSessionId: UUID

    @StringForgery
    lateinit var fakeSessionState: String

    @BeforeEach
    fun `set up`() {
        fakeDatadogContext = fakeDatadogContext.copy(
            featuresContext = fakeDatadogContext.featuresContext.toMutableMap().apply {
                put(
                    Feature.RUM_FEATURE_NAME,
                    mapOf(
                        "application_id" to fakeApplicationId.toString(),
                        "session_id" to fakeSessionId.toString(),
                        "session_state" to fakeSessionState
                    )
                )
            }
        )
        testedContextProvider = EmbeddedRumEventContextProvider(mockInternalLogger)
    }

    @Test
    fun `M return the active context W getRumContext()`() {
        // When
        val rumContext = testedContextProvider.getRumContext(fakeDatadogContext)

        // Then
        assertThat(rumContext?.applicationId).isEqualTo(fakeApplicationId.toString())
        assertThat(rumContext?.sessionId).isEqualTo(fakeSessionId.toString())
        assertThat(rumContext?.sessionState).isEqualTo(fakeSessionState)
    }

    @ParameterizedTest
    @EnumSource(RumContextValueMissingType::class)
    fun `M return null and log a warning W getRumContext() { applicationId missing }`(
        missingType: RumContextValueMissingType
    ) {
        // Given
        val rumContext = mutableMapOf<String, Any?>("session_id" to fakeSessionId.toString())
        when (missingType) {
            RumContextValueMissingType.NULL -> rumContext["application_id"] = null
            RumContextValueMissingType.NULL_UUID -> rumContext["application_id"] = RumContext.NULL_UUID
            RumContextValueMissingType.NOT_REGISTERED -> Unit
        }
        fakeDatadogContext = fakeDatadogContext.copy(
            featuresContext = fakeDatadogContext.featuresContext.toMutableMap().apply {
                put(Feature.RUM_FEATURE_NAME, rumContext)
            }
        )

        // When
        val result = testedContextProvider.getRumContext(fakeDatadogContext)

        // Then
        assertThat(result).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            EmbeddedRumEventContextProvider.RUM_NOT_INITIALIZED_WARNING_MESSAGE
        )
    }

    @ParameterizedTest
    @EnumSource(RumContextValueMissingType::class)
    fun `M return null and log a warning W getRumContext() { sessionId missing }`(
        missingType: RumContextValueMissingType
    ) {
        // Given
        val rumContext = mutableMapOf<String, Any?>("application_id" to fakeApplicationId.toString())
        when (missingType) {
            RumContextValueMissingType.NULL -> rumContext["session_id"] = null
            RumContextValueMissingType.NULL_UUID -> rumContext["session_id"] = RumContext.NULL_UUID
            RumContextValueMissingType.NOT_REGISTERED -> Unit
        }
        fakeDatadogContext = fakeDatadogContext.copy(
            featuresContext = fakeDatadogContext.featuresContext.toMutableMap().apply {
                put(Feature.RUM_FEATURE_NAME, rumContext)
            }
        )

        // When
        val result = testedContextProvider.getRumContext(fakeDatadogContext)

        // Then
        assertThat(result).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            EmbeddedRumEventContextProvider.RUM_NOT_INITIALIZED_WARNING_MESSAGE
        )
    }

    @Test
    fun `M only log the warning once W getRumContext() { called repeatedly, missing context }`(
        forge: Forge
    ) {
        // Given
        fakeDatadogContext = fakeDatadogContext.copy(featuresContext = mapOf())

        // When
        repeat(forge.anInt(min = 2, max = 10)) {
            testedContextProvider.getRumContext(fakeDatadogContext)
        }

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            EmbeddedRumEventContextProvider.RUM_NOT_INITIALIZED_WARNING_MESSAGE,
            mode = org.mockito.kotlin.times(1)
        )
    }

    @Test
    fun `M recover the context W getRumContext() { context becomes available after being missing }`() {
        // Given
        val fakeContextWithoutRum = fakeDatadogContext.copy(featuresContext = mapOf())

        // When
        val firstResult = testedContextProvider.getRumContext(fakeContextWithoutRum)
        val secondResult = testedContextProvider.getRumContext(fakeDatadogContext)

        // Then
        assertThat(firstResult).isNull()
        assertThat(secondResult?.applicationId).isEqualTo(fakeApplicationId.toString())
        assertThat(secondResult?.sessionId).isEqualTo(fakeSessionId.toString())
        assertThat(secondResult?.sessionState).isEqualTo(fakeSessionState)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            EmbeddedRumEventContextProvider.RUM_NOT_INITIALIZED_WARNING_MESSAGE,
            mode = org.mockito.kotlin.times(1)
        )
    }

    enum class RumContextValueMissingType {
        NOT_REGISTERED,
        NULL,
        NULL_UUID
    }
}
