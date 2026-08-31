/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.sessionreplay.model.MobileSegment

internal sealed interface CaptureWireMappingResult<out T> {
    data class Success<T>(val value: T) : CaptureWireMappingResult<T>

    data class Invalid(
        val failures: List<CaptureValidationFailure>
    ) : CaptureWireMappingResult<Nothing>
}

internal interface CapturedTreeWireMapper {
    fun mapFullSnapshot(
        snapshot: CapturedFullSnapshot
    ): CaptureWireMappingResult<MobileSegment.MobileRecord.MobileFullSnapshotRecord>

    fun mapMutation(
        mutation: CapturedMutationSet,
        base: CapturedFullSnapshot
    ): CaptureWireMappingResult<MobileSegment.MobileRecord.MobileIncrementalSnapshotRecord>

    /**
     * Diffs [previous] and [current]'s flat wireframe lists - independently of, and in addition
     * to, [mapMutation]'s layer-only diff - and returns a ready-to-write mutation record for
     * whatever wireframe content actually changed (added, removed, or altered), or null if nothing
     * did. This is the same wireframe-level diff/mutation the legacy pipeline already emits
     * ([MutationResolver]), reused as-is so a text or image content change never has to force a
     * full composition-tree snapshot just to be delivered.
     */
    fun mapWireframeMutation(
        previous: CapturedFullSnapshot,
        current: CapturedFullSnapshot
    ): MobileSegment.MobileRecord.MobileIncrementalSnapshotRecord?
}
