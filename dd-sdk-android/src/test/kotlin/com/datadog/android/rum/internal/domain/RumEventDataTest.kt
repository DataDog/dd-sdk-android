/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import com.datadog.android.rum.assertj.RumEventDataViewAssert.Companion.assertThat
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumEventDataTest {

    @Test
    fun incrementErrorCount(
        @Forgery view: RumEventData.View
    ) {
        val incremented = view.incrementErrorCount()

        assertThat(incremented)
            .hasName(view.name)
            .hasVersion(view.version)
            .hasDuration(view.durationNanoSeconds)
            .hasMeasures {
                hasErrorCount(view.measures.errorCount + 1)
                hasResourceCount(view.measures.resourceCount)
                hasUserActionCount(view.measures.userActionCount)
            }
    }

    @Test
    fun incrementResourceCount(
        @Forgery view: RumEventData.View
    ) {
        val incremented = view.incrementResourceCount()

        assertThat(incremented)
            .hasName(view.name)
            .hasVersion(view.version)
            .hasDuration(view.durationNanoSeconds)
            .hasMeasures {
                hasErrorCount(view.measures.errorCount)
                hasResourceCount(view.measures.resourceCount + 1)
                hasUserActionCount(view.measures.userActionCount)
            }
    }

    @Test
    fun incrementUserActionCount(
        @Forgery view: RumEventData.View
    ) {
        val incremented = view.incrementUserActionCount()

        assertThat(incremented)
            .hasName(view.name)
            .hasVersion(view.version)
            .hasDuration(view.durationNanoSeconds)
            .hasMeasures {
                hasErrorCount(view.measures.errorCount)
                hasResourceCount(view.measures.resourceCount)
                hasUserActionCount(view.measures.userActionCount + 1)
            }
    }
}
