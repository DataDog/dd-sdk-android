/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal

import android.content.Context
import com.datadog.android.Configuration
import com.datadog.android.core.internal.data.upload.DataUploadScheduler
import com.datadog.android.core.internal.data.upload.NoOpUploadScheduler
import com.datadog.android.core.internal.data.upload.UploadScheduler
import com.datadog.android.core.internal.domain.NoOpPersistenceStrategy
import com.datadog.android.core.internal.domain.PersistenceStrategy
import com.datadog.android.core.internal.net.DataUploader
import com.datadog.android.core.internal.net.NoOpDataUploader
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.plugin.DatadogPlugin
import com.datadog.android.plugin.DatadogPluginConfig
import java.util.concurrent.atomic.AtomicBoolean

internal abstract class SdkFeature<T : Any, C : Configuration.Feature>(
    internal val authorizedFolderName: String
) {

    internal val initialized = AtomicBoolean(false)

    internal var endpointUrl: String = ""

    internal var persistenceStrategy: PersistenceStrategy<T> = NoOpPersistenceStrategy()
    internal var uploader: DataUploader = NoOpDataUploader()
    internal var uploadScheduler: UploadScheduler = NoOpUploadScheduler()
    internal val featurePlugins: MutableList<DatadogPlugin> = mutableListOf()

    // region SDK Feature

    fun initialize(context: Context, configuration: C) {
        if (initialized.get()) {
            return
        }

        endpointUrl = configuration.endpointUrl
        persistenceStrategy = createPersistenceStrategy(context, configuration)

        setupUploader()

        registerPlugins(
            configuration.plugins,
            DatadogPluginConfig(
                context = context,
                envName = CoreFeature.envName,
                serviceName = CoreFeature.serviceName,
                featurePersistenceDirName = authorizedFolderName,
                trackingConsent = CoreFeature.trackingConsentProvider.getConsent()
            ),
            CoreFeature.trackingConsentProvider
        )

        onInitialize(context, configuration)

        initialized.set(true)
    }

    fun isInitialized(): Boolean {
        return initialized.get()
    }

    fun clearAllData() {
        persistenceStrategy.clearAllData()
    }

    fun stop() {
        if (initialized.get()) {
            unregisterPlugins()
            uploadScheduler.stopScheduling()
            persistenceStrategy = NoOpPersistenceStrategy()
            uploadScheduler = NoOpUploadScheduler()
            endpointUrl = ""

            onStop()

            initialized.set(false)
        }
    }

    fun getPlugins(): List<DatadogPlugin> {
        return featurePlugins
    }

    // endregion

    // region Abstract

    open fun onInitialize(context: Context, configuration: C) {}

    open fun onStop() {}

    abstract fun createPersistenceStrategy(
        context: Context,
        configuration: C
    ): PersistenceStrategy<T>

    abstract fun createUploader(): DataUploader

    // endregion

    // region Internal

    private fun registerPlugins(
        plugins: List<DatadogPlugin>,
        config: DatadogPluginConfig,
        trackingConsentProvider: ConsentProvider
    ) {
        plugins.forEach {
            featurePlugins.add(it)
            it.register(config)
            trackingConsentProvider.registerCallback(it)
        }
    }

    private fun unregisterPlugins() {
        featurePlugins.forEach {
            it.unregister()
        }
        featurePlugins.clear()
    }

    private fun setupUploader() {
        uploadScheduler = if (CoreFeature.isMainProcess) {
            uploader = createUploader()
            DataUploadScheduler(
                persistenceStrategy.getReader(),
                uploader,
                CoreFeature.networkInfoProvider,
                CoreFeature.systemInfoProvider,
                CoreFeature.uploadExecutorService
            )
        } else {
            NoOpUploadScheduler()
        }
        uploadScheduler.startScheduling()
    }

    // endregion
}
