/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder

import android.view.View
import android.view.ViewGroup
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge

internal fun Forge.aViewWithChildren(
    numberOfChildren: Int,
    currentLevel: Int,
    maxLevel: Int = 10
): View {
    if (currentLevel >= maxLevel) {
        return aMockView()
    }
    val level = currentLevel + 1
    val mockViewGroup: ViewGroup = aMockView()
    whenever(mockViewGroup.childCount).thenReturn(numberOfChildren)
    for (i in 0 until numberOfChildren) {
        val mockChildGroup = aViewWithChildren(
            numberOfChildren,
            level,
            maxLevel
        )
        whenever(mockViewGroup.id).thenReturn(currentLevel * 10 + i)
        whenever(mockViewGroup.getChildAt(i)).thenReturn(mockChildGroup)
    }
    return mockViewGroup
}

internal inline fun <reified T : View> Forge.aMockView(): T {
    return mock {
        val absX = anInt(min = 0)
        val absY = anInt(min = 0)
        val locationInWindow = intArrayOf(absX, absY)
        whenever(it.getLocationInWindow(locationInWindow)).then {
            val location = (
                it.arguments[0]
                    as IntArray
                )
            location[0] = absX
            location[1] = absY
            null
        }
        whenever(it.x).thenReturn(aFloat(min = 0f))
        whenever(it.y).thenReturn(aFloat(min = 0f))
        whenever(it.width).thenReturn(anInt(min = 1))
        whenever(it.height).thenReturn(anInt(min = 1))
        whenever(it.isShown).thenReturn(true)
        whenever(it.alpha).thenReturn(1f)
    }
}
