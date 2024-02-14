/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.processor

import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.processor.EnrichedResource.Companion.APPLICATION_ID_RESOURCE_KEY
import com.datadog.android.sessionreplay.internal.processor.EnrichedResource.Companion.FILENAME_KEY
import com.datadog.android.sessionreplay.internal.utils.MiscUtils.safeDeserializeToJsonObject
import com.datadog.android.sessionreplay.internal.utils.MiscUtils.safeGetStringFromJsonObject
import com.datadog.tools.unit.ObjectTest
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
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
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class EnrichedResourceTest : ObjectTest<EnrichedResource>() {

    @Mock
    lateinit var mockInternalLogger: InternalLogger

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

    @Test
    fun `M return valid binary metadata W asBinaryMetadat`(
        @Forgery enrichedResource: EnrichedResource
    ) {
        // Given
        val expectedIdentifier = enrichedResource.filename
        val expectedApplicationId = enrichedResource.applicationId

        // When
        val metadata = enrichedResource.asBinaryMetadata()

        // Then
        val deserializedData = safeDeserializeToJsonObject(
            mockInternalLogger,
            metadata
        )

        requireNotNull(deserializedData)

        val actualIdentifier = safeGetStringFromJsonObject(
            mockInternalLogger,
            deserializedData,
            FILENAME_KEY
        )

        val actualApplicationId = safeGetStringFromJsonObject(
            mockInternalLogger,
            deserializedData,
            APPLICATION_ID_RESOURCE_KEY
        )

        assertThat(actualIdentifier).isEqualTo(expectedIdentifier)
        assertThat(actualApplicationId).isEqualTo(expectedApplicationId)
    }
}
