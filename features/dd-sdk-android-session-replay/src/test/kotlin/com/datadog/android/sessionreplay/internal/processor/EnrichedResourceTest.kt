/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.processor

import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.tools.unit.ObjectTest
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
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
@ForgeConfiguration(ForgeConfigurator::class)
internal class EnrichedResourceTest : ObjectTest<EnrichedResource>() {
    override fun createInstance(forge: Forge): EnrichedResource {
        return forge.getForgery()
    }

    override fun createEqualInstance(source: EnrichedResource, forge: Forge): EnrichedResource {
        return EnrichedResource(
            resource = source.resource,
            applicationId = source.applicationId,
            filename = source.filename
        )
    }

    override fun createUnequalInstance(source: EnrichedResource, forge: Forge): EnrichedResource {
        return forge.getForgery()
    }
}
