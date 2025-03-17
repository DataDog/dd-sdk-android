/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.forge

import com.datadog.android.internal.telemetry.UploadQualityEvent
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

class UploadQualityFailureEventFactory : ForgeryFactory<UploadQualityEvent.UploadQualityFailureEvent> {
    override fun getForgery(forge: Forge): UploadQualityEvent.UploadQualityFailureEvent {
        return UploadQualityEvent.UploadQualityFailureEvent(
            track = forge.aTrack(),
            uploadDelay = forge.anInt(min = 0),
            batchCount = forge.anInt(min = 0),
            failure = forge.aNumericalString(size = 3)
        )
    }
}
