/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers.semantics

import com.datadog.android.sessionreplay.compose.test.elmyr.SessionReplayComposeForgeConfigurator
import com.datadog.android.sessionreplay.model.MobileSegment
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(SessionReplayComposeForgeConfigurator::class)
internal class ComposeTagWireframeExtTest {

    @Test
    fun `M prefix the tag W composePermanentIdFor()`(
        @StringForgery fakeTag: String
    ) {
        assertThat(composePermanentIdFor(fakeTag)).isEqualTo("compose:$fakeTag")
    }

    @Test
    fun `M set permanentId W copyWithComposePermanentId() { shape wireframe }`(
        @Forgery fakeWireframe: MobileSegment.Wireframe.ShapeWireframe,
        @StringForgery fakeId: String
    ) {
        val result = fakeWireframe.copyWithComposePermanentId(fakeId)

        assertThat(result).isInstanceOf(MobileSegment.Wireframe.ShapeWireframe::class.java)
        assertThat((result as MobileSegment.Wireframe.ShapeWireframe).permanentId).isEqualTo(fakeId)
        assertThat(result.copy(permanentId = fakeWireframe.permanentId)).isEqualTo(fakeWireframe)
    }

    @Test
    fun `M set permanentId W copyWithComposePermanentId() { text wireframe }`(
        @Forgery fakeWireframe: MobileSegment.Wireframe.TextWireframe,
        @StringForgery fakeId: String
    ) {
        val result = fakeWireframe.copyWithComposePermanentId(fakeId)

        assertThat((result as MobileSegment.Wireframe.TextWireframe).permanentId).isEqualTo(fakeId)
    }

    @Test
    fun `M set permanentId W copyWithComposePermanentId() { image wireframe }`(
        @Forgery fakeWireframe: MobileSegment.Wireframe.ImageWireframe,
        @StringForgery fakeId: String
    ) {
        val result = fakeWireframe.copyWithComposePermanentId(fakeId)

        assertThat((result as MobileSegment.Wireframe.ImageWireframe).permanentId).isEqualTo(fakeId)
    }

    @Test
    fun `M set permanentId W copyWithComposePermanentId() { placeholder wireframe }`(
        @Forgery fakeWireframe: MobileSegment.Wireframe.PlaceholderWireframe,
        @StringForgery fakeId: String
    ) {
        val result = fakeWireframe.copyWithComposePermanentId(fakeId)

        assertThat((result as MobileSegment.Wireframe.PlaceholderWireframe).permanentId)
            .isEqualTo(fakeId)
    }

    @Test
    fun `M set permanentId W copyWithComposePermanentId() { webview wireframe }`(
        @LongForgery fakeBound: Long,
        @StringForgery fakeSlotId: String,
        @StringForgery fakeId: String
    ) {
        // WebviewWireframe has no forgery factory registered in
        // SessionReplayComposeForgeConfigurator, so it is built by hand here.
        val fakeWireframe = MobileSegment.Wireframe.WebviewWireframe(
            id = fakeBound,
            x = fakeBound,
            y = fakeBound,
            width = fakeBound,
            height = fakeBound,
            slotId = fakeSlotId
        )

        val result = fakeWireframe.copyWithComposePermanentId(fakeId)

        assertThat((result as MobileSegment.Wireframe.WebviewWireframe).permanentId)
            .isEqualTo(fakeId)
    }
}
