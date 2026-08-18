/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.internal.sessionreplay.composition.CapturedChild
import com.datadog.android.internal.sessionreplay.composition.CapturedCompositeOperation
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentity
import com.datadog.android.internal.sessionreplay.composition.CapturedLayer
import com.datadog.android.internal.sessionreplay.composition.CapturedModifier
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope

internal sealed interface CapturedChange<out T> {
    object Unchanged : CapturedChange<Nothing>

    data class Set<T>(val value: T) : CapturedChange<T>
}

internal data class CapturedLayerUpdate(
    val identity: CapturedIdentity,
    val x: CapturedChange<Long> = CapturedChange.Unchanged,
    val y: CapturedChange<Long> = CapturedChange.Unchanged,
    val width: CapturedChange<Long> = CapturedChange.Unchanged,
    val height: CapturedChange<Long> = CapturedChange.Unchanged,
    val children: CapturedChange<List<CapturedChild>> = CapturedChange.Unchanged,
    val modifiers: CapturedChange<List<CapturedModifier>> = CapturedChange.Unchanged,
    val compositeOperation: CapturedChange<CapturedCompositeOperation?> = CapturedChange.Unchanged
)

internal data class CapturedMutationSet(
    val timestamp: Long,
    val scope: RumViewIdentityScope,
    val root: CapturedChange<CapturedLayer> = CapturedChange.Unchanged,
    val adds: CapturedChange<List<CapturedLayer>> = CapturedChange.Unchanged,
    val removes: CapturedChange<List<CapturedIdentity>> = CapturedChange.Unchanged,
    val updates: CapturedChange<List<CapturedLayerUpdate>> = CapturedChange.Unchanged
)
