/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.rum.internal

import android.content.Context
import android.os.HandlerThread
import com.datadog.android.DatadogConfig
import com.datadog.android.DatadogEndpoint
import com.datadog.android.core.internal.data.upload.DataUploadHandlerThread
import com.datadog.android.core.internal.domain.NoOpPersistenceStrategy
import com.datadog.android.core.internal.domain.PersistenceStrategy
import com.datadog.android.core.internal.net.DataUploader
import com.datadog.android.core.internal.net.NoOpDataUploader
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.system.SystemInfoProvider
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.internal.domain.RumEvent
import com.datadog.android.rum.internal.domain.RumFileStrategy
import com.datadog.android.rum.internal.instrumentation.TrackingStrategy
import com.datadog.android.rum.internal.net.RumOkHttpUploader
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.OkHttpClient

internal object RumFeature {

    internal val initialized = AtomicBoolean(false)

    internal var clientToken: String = ""
    internal var endpointUrl: String = DatadogEndpoint.RUM_US
    internal var serviceName: String = DatadogConfig.DEFAULT_SERVICE_NAME
    internal var envName: String = ""
    internal var applicationId: UUID = UUID(0, 0)

    internal var persistenceStrategy: PersistenceStrategy<RumEvent> = NoOpPersistenceStrategy()
    internal var uploader: DataUploader = NoOpDataUploader()
    internal var uploadHandlerThread: HandlerThread = HandlerThread("NoOp")
    internal var gesturesTrackingStrategy: TrackingStrategy? = null
    internal var activityTrackingStrategy: TrackingStrategy? = null
    internal var fragmentsTrackingStrategy: TrackingStrategy? = null

    @Suppress("LongParameterList")
    fun initialize(
        appContext: Context,
        config: DatadogConfig.RumConfig,
        okHttpClient: OkHttpClient,
        networkInfoProvider: NetworkInfoProvider,
        systemInfoProvider: SystemInfoProvider
    ) {
        if (initialized.get()) {
            return
        }

        GlobalRum.updateApplicationId(config.applicationId)
        clientToken = config.clientToken
        endpointUrl = config.endpointUrl
        serviceName = config.serviceName
        envName = config.envName

        persistenceStrategy = RumFileStrategy(appContext)
        setupUploader(endpointUrl, okHttpClient, networkInfoProvider, systemInfoProvider)
        setupTrackingStrategies(config)

        initialized.set(true)
    }

    fun isInitialized(): Boolean {
        return initialized.get()
    }

    fun stop() {
        if (initialized.get()) {
            uploadHandlerThread.quitSafely()

            persistenceStrategy = NoOpPersistenceStrategy()
            uploadHandlerThread = HandlerThread("NoOp")
            clientToken = ""
            endpointUrl = DatadogEndpoint.RUM_US
            serviceName = DatadogConfig.DEFAULT_SERVICE_NAME

            initialized.set(false)
        }
    }

    // region Internal

    private fun setupUploader(
        endpointUrl: String,
        okHttpClient: OkHttpClient,
        networkInfoProvider: NetworkInfoProvider,
        systemInfoProvider: SystemInfoProvider
    ) {
        uploader = RumOkHttpUploader(endpointUrl, clientToken, okHttpClient)

        uploadHandlerThread = DataUploadHandlerThread(
            RUM_UPLOAD_THREAD_NAME,
            persistenceStrategy.getReader(),
            uploader,
            networkInfoProvider,
            systemInfoProvider
        )
        uploadHandlerThread.start()
    }

    private fun setupTrackingStrategies(config: DatadogConfig.RumConfig) {
        if (config.trackGestures) {
            gesturesTrackingStrategy = TrackingStrategy.GesturesTrackingStrategy()
        }
        if (config.trackActivitiesAsScreens) {
            activityTrackingStrategy = TrackingStrategy.ActivityTrackingStrategy()
        }
        if (config.trackFragmentsAsScreens) {
            fragmentsTrackingStrategy = TrackingStrategy.FragmentsTrackingStrategy()
        }
    }

    // endregion

    // region Constants

    internal const val RUM_UPLOAD_THREAD_NAME = "ddog-rum-upload"

    // endregion
}
