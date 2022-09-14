/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.utils

import com.datadog.android.sessionreplay.model.MobileSegment
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class IncrementalSnapshotRecordForgeryFactory :
    ForgeryFactory<MobileSegment.MobileRecord.MobileIncrementalSnapshotRecord> {
    override fun getForgery(forge: Forge):
        MobileSegment.MobileRecord.MobileIncrementalSnapshotRecord {
        return MobileSegment.MobileRecord.MobileIncrementalSnapshotRecord(
            forge.aPositiveLong(),
            forge.getForgery()
        )
    }
}
