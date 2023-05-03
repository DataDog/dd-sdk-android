/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.forge

import android.view.MotionEvent
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class MotionEventForgeryFactory : ForgeryFactory<MotionEvent> {

    override fun getForgery(forge: Forge): MotionEvent {
        return mock {
            whenever(it.x).thenReturn(
                forge.aFloat(
                    min = 0f,
                    max = XY_MAX_VALUE
                )
            )
            whenever(it.y).thenReturn(
                forge.aFloat(
                    min = 0f,
                    max = XY_MAX_VALUE
                )
            )
        }
    }

    companion object {
        const val XY_MAX_VALUE = 1000f
    }
}
