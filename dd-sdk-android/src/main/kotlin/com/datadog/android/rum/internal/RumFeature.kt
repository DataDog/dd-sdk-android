/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal

import android.content.Context
import com.datadog.android.Configuration
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.SdkFeature
import com.datadog.android.core.internal.domain.PersistenceStrategy
import com.datadog.android.core.internal.event.NoOpEventMapper
import com.datadog.android.core.internal.net.DataUploader
import com.datadog.android.event.EventMapper
import com.datadog.android.rum.internal.domain.RumFileStrategy
import com.datadog.android.rum.internal.domain.event.RumEvent
import com.datadog.android.rum.internal.instrumentation.gestures.GesturesTracker
import com.datadog.android.rum.internal.instrumentation.gestures.NoOpGesturesTracker
import com.datadog.android.rum.internal.net.RumOkHttpUploader
import com.datadog.android.rum.internal.tracking.NoOpUserActionTrackingStrategy
import com.datadog.android.rum.internal.tracking.UserActionTrackingStrategy
import com.datadog.android.rum.internal.tracking.ViewTreeChangeTrackingStrategy
import com.datadog.android.rum.tracking.NoOpViewTrackingStrategy
import com.datadog.android.rum.tracking.TrackingStrategy
import com.datadog.android.rum.tracking.ViewTrackingStrategy

internal object RumFeature : SdkFeature<RumEvent, Configuration.Feature.RUM>(
    authorizedFolderName = RumFileStrategy.AUTHORIZED_FOLDER
) {

    internal var samplingRate: Float = 0f

    internal var gesturesTracker: GesturesTracker = NoOpGesturesTracker()
    internal var viewTrackingStrategy: ViewTrackingStrategy = NoOpViewTrackingStrategy()
    internal var actionTrackingStrategy: UserActionTrackingStrategy =
        NoOpUserActionTrackingStrategy()
    internal var viewTreeTrackingStrategy: TrackingStrategy = ViewTreeChangeTrackingStrategy()
    internal var rumEventMapper: EventMapper<RumEvent> = NoOpEventMapper()

    // region SdkFeature

    override fun onInitialize(context: Context, configuration: Configuration.Feature.RUM) {
        samplingRate = configuration.samplingRate
        rumEventMapper = configuration.rumEventMapper

        configuration.gesturesTracker?.let { gesturesTracker = it }
        configuration.viewTrackingStrategy?.let { viewTrackingStrategy = it }
        configuration.userActionTrackingStrategy?.let { actionTrackingStrategy = it }

        registerTrackingStrategies(context)
    }

    override fun onStop() {
        unregisterTrackingStrategies(CoreFeature.contextRef.get())
        gesturesTracker = NoOpGesturesTracker()
        viewTrackingStrategy = NoOpViewTrackingStrategy()
        actionTrackingStrategy = NoOpUserActionTrackingStrategy()
        rumEventMapper = NoOpEventMapper()
    }

    override fun createPersistenceStrategy(
        context: Context,
        configuration: Configuration.Feature.RUM
    ): PersistenceStrategy<RumEvent> {
        return RumFileStrategy(
            context,
            trackingConsentProvider = CoreFeature.trackingConsentProvider,
            dataPersistenceExecutorService = CoreFeature.persistenceExecutorService,
            eventMapper = configuration.rumEventMapper
        )
    }

    override fun createUploader(): DataUploader {
        return RumOkHttpUploader(endpointUrl, CoreFeature.clientToken, CoreFeature.okHttpClient)
    }

    // endregion

    // region Internal

    private fun registerTrackingStrategies(appContext: Context) {
        actionTrackingStrategy.register(appContext)
        viewTrackingStrategy.register(appContext)
        viewTreeTrackingStrategy.register(appContext)
    }

    private fun unregisterTrackingStrategies(appContext: Context?) {
        actionTrackingStrategy.unregister(appContext)
        viewTrackingStrategy.unregister(appContext)
        viewTreeTrackingStrategy.unregister(appContext)
    }

    // endregion
}
