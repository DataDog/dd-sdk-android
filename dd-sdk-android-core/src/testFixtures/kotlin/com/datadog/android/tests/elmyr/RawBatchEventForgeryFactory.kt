/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tests.elmyr

import com.datadog.android.api.storage.RawBatchEvent
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

class RawBatchEventForgeryFactory : ForgeryFactory<RawBatchEvent> {
    override fun getForgery(forge: Forge): RawBatchEvent {
        return RawBatchEvent(
            data = forge.aString().toByteArray(),
            metadata = forge.aString(
                forge.anInt(min = 0, max = DATA_SIZE_LIMIT + 1)
            ).toByteArray()
        )
    }

    companion object {
        const val DATA_SIZE_LIMIT = 32
    }
}
