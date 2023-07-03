/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.advanced

import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.core.internal.persistence.file.NoOpFileOrchestrator
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.core.internal.utils.submitSafe
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.privacy.TrackingConsentProviderCallback
import java.io.File
import java.util.concurrent.ExecutorService

internal open class ConsentAwareFileOrchestrator(
    consentProvider: ConsentProvider,
    internal val pendingOrchestrator: FileOrchestrator,
    internal val grantedOrchestrator: FileOrchestrator,
    internal val dataMigrator: DataMigrator<TrackingConsent>,
    internal val executorService: ExecutorService,
    internal val internalLogger: InternalLogger
) : FileOrchestrator, TrackingConsentProviderCallback {

    private lateinit var delegateOrchestrator: FileOrchestrator

    init {
        handleConsentChange(null, consentProvider.getConsent())
        @Suppress("LeakingThis")
        consentProvider.registerCallback(this)
    }

    // region FileOrchestrator

    @WorkerThread
    override fun getWritableFile(forceNewFile: Boolean): File? {
        return delegateOrchestrator.getWritableFile(forceNewFile)
    }

    @WorkerThread
    override fun getReadableFile(excludeFiles: Set<File>): File? {
        return grantedOrchestrator.getReadableFile(excludeFiles)
    }

    @WorkerThread
    override fun getAllFiles(): List<File> {
        return pendingOrchestrator.getAllFiles() + grantedOrchestrator.getAllFiles()
    }

    @WorkerThread
    override fun getRootDir(): File? {
        return null
    }

    @WorkerThread
    override fun getFlushableFiles(): List<File> {
        return grantedOrchestrator.getFlushableFiles()
    }

    @WorkerThread
    override fun getMetadataFile(file: File): File? {
        return delegateOrchestrator.getMetadataFile(file)
    }

    // endregion

    // region TrackingConsentProviderCallback

    override fun onConsentUpdated(
        previousConsent: TrackingConsent,
        newConsent: TrackingConsent
    ) {
        handleConsentChange(previousConsent, newConsent)
    }

    // endregion

    // region Internal

    @AnyThread
    private fun handleConsentChange(
        previousConsent: TrackingConsent?,
        newConsent: TrackingConsent
    ) {
        val previousOrchestrator = resolveDelegateOrchestrator(previousConsent)
        val newOrchestrator = resolveDelegateOrchestrator(newConsent)
        executorService.submitSafe("Data migration", internalLogger) {
            @Suppress("ThreadSafety") // we are in the worker thread context
            dataMigrator.migrateData(
                previousConsent,
                previousOrchestrator,
                newConsent,
                newOrchestrator
            )
            delegateOrchestrator = newOrchestrator
        }
    }

    private fun resolveDelegateOrchestrator(consent: TrackingConsent?): FileOrchestrator {
        return when (consent) {
            TrackingConsent.PENDING, null -> pendingOrchestrator
            TrackingConsent.GRANTED -> grantedOrchestrator
            TrackingConsent.NOT_GRANTED -> NO_OP_ORCHESTRATOR
        }
    }

    // endregion

    companion object {
        internal val NO_OP_ORCHESTRATOR: FileOrchestrator = NoOpFileOrchestrator()
    }
}
