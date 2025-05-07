/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tracking

import android.view.View
import com.datadog.tools.unit.ObjectTest
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.lang.ref.WeakReference

@Extensions(ExtendWith(ForgeExtension::class))
@MockitoSettings(strictness = Strictness.LENIENT)
class ViewTargetTest : ObjectTest<ViewTarget>() {

    @Mock
    private lateinit var mockView: View

    @Mock
    private lateinit var mockAnotherView: View

    override fun createInstance(forge: Forge): ViewTarget {
        return ViewTarget(
            viewRef = WeakReference(mockView),
            tag = forge.anAlphabeticalString()
        )
    }

    override fun createUnequalInstance(source: ViewTarget, forge: Forge): ViewTarget? {
        return ViewTarget(
            viewRef = WeakReference(mockAnotherView),
            tag = forge.anAlphabeticalString()
        )
    }

    override fun createEqualInstance(source: ViewTarget, forge: Forge): ViewTarget {
        return ViewTarget(
            viewRef = WeakReference(source.viewRef.get()),
            tag = source.tag
        )
    }
}
