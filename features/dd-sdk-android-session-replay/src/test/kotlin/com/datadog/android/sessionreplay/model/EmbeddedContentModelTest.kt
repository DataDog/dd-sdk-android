/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.model

import com.datadog.android.sessionreplay.WIREFRAME_TYPE_EMBEDDED_CONTENT
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(ForgeConfigurator::class)
internal class EmbeddedContentModelTest {

    @Test
    fun `M round trip embedded content W serialize and deserialize`(
        @Forgery fakeWireframe: MobileSegment.Wireframe.EmbeddedContentWireframe
    ) {
        // Given
        val wireframeWithVisibility = fakeWireframe.copy(isVisible = false)

        // When
        val json = wireframeWithVisibility.toJson()

        // Then
        assertThat(json.asJsonObject.get("type").asString)
            .isEqualTo(WIREFRAME_TYPE_EMBEDDED_CONTENT)
        assertThat(MobileSegment.Wireframe.fromJsonElement(json)).isEqualTo(wireframeWithVisibility)
    }

    @Test
    fun `M round trip embedded content update W serialize and deserialize`(
        @Forgery fakeUpdate: MobileSegment.WireframeUpdateMutation.EmbeddedContentWireframeUpdate
    ) {
        // When
        val json = fakeUpdate.toJson()

        // Then
        assertThat(json.asJsonObject.get("type").asString)
            .isEqualTo(WIREFRAME_TYPE_EMBEDDED_CONTENT)
        assertThat(MobileSegment.WireframeUpdateMutation.fromJsonElement(json)).isEqualTo(fakeUpdate)
    }

    @Test
    fun `M round trip slot id W serialize and deserialize { full snapshot }`(
        @LongForgery fakeTimestamp: Long,
        @StringForgery fakeSlotId: String
    ) {
        // Given
        val recordWithSlot = MobileSegment.MobileRecord.MobileFullSnapshotRecord(
            timestamp = fakeTimestamp,
            data = MobileSegment.Data(emptyList()),
            slotId = fakeSlotId
        )

        // When
        val deserialized = MobileSegment.MobileRecord.MobileFullSnapshotRecord.fromJson(
            recordWithSlot.toJson().toString()
        )

        // Then
        assertThat(deserialized).isEqualTo(recordWithSlot)
    }

    @Test
    fun `M round trip slot id W serialize and deserialize { incremental snapshot }`(
        @LongForgery fakeTimestamp: Long,
        @LongForgery fakeWidth: Long,
        @LongForgery fakeHeight: Long,
        @StringForgery fakeSlotId: String
    ) {
        // Given
        val recordWithSlot = MobileSegment.MobileRecord.MobileIncrementalSnapshotRecord(
            timestamp = fakeTimestamp,
            data = MobileSegment.MobileIncrementalData.ViewportResizeData(
                width = fakeWidth,
                height = fakeHeight
            ),
            slotId = fakeSlotId
        )

        // When
        val deserialized = MobileSegment.MobileRecord.MobileIncrementalSnapshotRecord.fromJson(
            recordWithSlot.toJson().toString()
        )

        // Then
        assertThat(deserialized).isEqualTo(recordWithSlot)
    }
}
