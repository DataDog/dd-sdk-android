/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.forge

import com.datadog.android.sessionreplay.internal.async.TouchEventRecordedDataQueueItem
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class TouchEventRecordedDataQueueItemForgeryFactory : ForgeryFactory<TouchEventRecordedDataQueueItem> {
    override fun getForgery(forge: Forge): TouchEventRecordedDataQueueItem {
        return TouchEventRecordedDataQueueItem(
            rumContextData = forge.getForgery(),
            touchData = listOf(forge.getForgery())
        )
    }
}
