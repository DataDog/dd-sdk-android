/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import com.datadog.android.sessionreplay.internal.recorder.mapper.AllowAllWireframeMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.MaskAllWireframeMapper
import com.nhaarman.mockitokotlin2.mock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class SessionReplayPrivacyTest {

    @Test
    fun `M return the AllowAll mapper W rule(ALLOW_ALL)`() {
        assertThat(SessionReplayPrivacy.ALLOW_ALL.mapper(mock()))
            .isInstanceOf(AllowAllWireframeMapper::class.java)
    }

    @Test
    fun `M return the MASK_ALL mapper W rule(MASK_ALL)`() {
        assertThat(SessionReplayPrivacy.MASK_ALL.mapper(mock()))
            .isInstanceOf(MaskAllWireframeMapper::class.java)
    }
}
