/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.view.View
import android.view.ViewTreeObserver
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.metrics.MethodCallSamplingRate
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueHandler
import com.datadog.android.sessionreplay.internal.recorder.listener.WindowsOnDrawListener

internal class DefaultOnDrawListenerProducer(
    private val snapshotProducer: SnapshotProducer,
    private val recordedDataQueueHandler: RecordedDataQueueHandler,
    private val sdkCore: FeatureSdkCore
) : OnDrawListenerProducer {

    override fun create(
        decorViews: List<View>,
        privacy: SessionReplayPrivacy,
        imagePrivacy: ImagePrivacy
    ): ViewTreeObserver.OnDrawListener {
        return WindowsOnDrawListener(
            zOrderedDecorViews = decorViews,
            recordedDataQueueHandler = recordedDataQueueHandler,
            snapshotProducer = snapshotProducer,
            privacy = privacy,
            imagePrivacy = imagePrivacy,
            internalLogger = sdkCore.internalLogger,
            methodCallSamplingRate = MethodCallSamplingRate.LOW.rate
        )
    }
}
