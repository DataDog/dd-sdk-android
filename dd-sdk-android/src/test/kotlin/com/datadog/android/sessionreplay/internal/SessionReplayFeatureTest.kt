/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.internal.SdkFeatureTest
import com.datadog.android.sessionreplay.internal.domain.SessionReplayRecordPersistenceStrategy
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class SessionReplayFeatureTest : SdkFeatureTest<Any, Configuration.Feature.SessionReplay,
    SessionReplayFeature>() {

    override fun createTestedFeature(): SessionReplayFeature {
        return SessionReplayFeature(coreFeature.mockInstance)
    }

    override fun forgeConfiguration(forge: Forge): Configuration.Feature.SessionReplay {
        return forge.getForgery()
    }

    override fun featureDirName(): String {
        return SessionReplayFeature.SESSION_REPLAY_FEATURE_NAME
    }

    @Test
    fun `𝕄 initialize persistence strategy 𝕎 initialize()`() {
        // When
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // Then
        assertThat(testedFeature.persistenceStrategy)
            .isInstanceOf(SessionReplayRecordPersistenceStrategy::class.java)
    }
}
