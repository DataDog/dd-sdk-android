/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.widget.CheckBox
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.tools.unit.extensions.ApiLevelExtension
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class CheckBoxMapperTest : BaseCheckableTextViewMapperTest<CheckBox>() {

    override fun setupTestedMapper(): CheckBoxMapper {
        return CheckBoxMapper(
            mockTextWireframeMapper,
            mockViewIdentifierResolver,
            mockColorStringFormatter,
            mockViewBoundsResolver,
            mockDrawableToColorMapper,
            mockInternalLogger
        )
    }

    override fun mockCheckableTextView(): CheckBox {
        return mock {
            whenever(it.textSize).thenReturn(fakeTextSize)
            whenever(it.currentTextColor).thenReturn(fakeCurrentTextColor)
            whenever(it.alpha) doReturn 1f
            whenever(it.buttonDrawable) doReturn mockButtonDrawable
            whenever(it.resources) doReturn mockResources
        }
    }
}
