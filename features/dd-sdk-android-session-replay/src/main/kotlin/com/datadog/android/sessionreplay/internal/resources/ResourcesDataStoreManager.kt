/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.resources

import android.text.format.DateUtils
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.datastore.DataStoreCallback
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.persistence.Serializer
import com.datadog.android.core.persistence.datastore.DataStoreContent
import com.datadog.android.sessionreplay.internal.ResourcesFeature.Companion.SESSION_REPLAY_RESOURCES_FEATURE_NAME
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong

internal class ResourcesDataStoreManager(
    private val featureSdkCore: FeatureSdkCore,
    private val resourceHashesSerializer: Serializer<ResourceHashesEntry>,
    private val resourcesHashesDeserializer: Deserializer<String, ResourceHashesEntry>,
    private val featureScope: FeatureScope? = featureSdkCore.getFeature(
        SESSION_REPLAY_RESOURCES_FEATURE_NAME
    )
) {
    private val knownResources = Collections.synchronizedSet(mutableSetOf<String>())
    private val storedLastUpdateDateNs = AtomicLong(System.nanoTime())

    init {
        initializeManager()
    }

    internal fun wasResourcePreviouslySent(resourceHash: String): Boolean =
        knownResources.contains(resourceHash)

    internal fun store(resourceHash: String) {
        knownResources.add(resourceHash)
        writeResourcesToStore()
    }

    // region internal

    private fun initializeManager() {
        fetchStoredResourceHashes(
            onFetchSuccessful = { storedEntry ->
                val lastUpdateDateNs = storedEntry?.data?.lastUpdateDateNs
                val storedHashes = storedEntry?.data?.resourceHashes

                lastUpdateDateNs?.let {
                    storedLastUpdateDateNs.set(lastUpdateDateNs)
                }

                if (didDataStoreExpire()) {
                    handleDataStoreExpired(storedHashes)
                } else {
                    storedHashes?.let { hashes ->
                        knownResources.addAll(hashes)
                    }
                }
            },
            onFetchFailure = {
                deleteStoredHashesEntry()
            }
        )
    }

    private fun writeResourcesToStore() {
        val data = ResourceHashesEntry(
            lastUpdateDateNs = storedLastUpdateDateNs.get(),
            resourceHashes = knownResources
        )

        featureScope?.dataStore?.setValue(
            data = data,
            key = DATASTORE_HASHES_ENTRY_NAME,
            serializer = resourceHashesSerializer
        )
    }

    private fun fetchStoredResourceHashes(
        onFetchSuccessful: (dataStoreContent: DataStoreContent<ResourceHashesEntry>?) -> Unit = {},
        onFetchFailure: () -> Unit = {}
    ) {
        featureScope?.dataStore?.value(
            key = DATASTORE_HASHES_ENTRY_NAME,
            deserializer = resourcesHashesDeserializer,
            callback = object : DataStoreCallback<ResourceHashesEntry> {
                override fun onSuccess(dataStoreContent: DataStoreContent<ResourceHashesEntry>?) {
                    onFetchSuccessful(dataStoreContent)
                }

                override fun onFailure() {
                    onFetchFailure()
                }
            }
        )
    }

    private fun handleDataStoreExpired(storedHashes: Set<String>?) {
        storedHashes?.let { knownResources.removeAll(it) }
        storedLastUpdateDateNs.set(System.nanoTime())
        deleteStoredHashesEntry()
    }

    private fun deleteStoredHashesEntry() =
        featureScope?.dataStore?.removeValue(DATASTORE_HASHES_ENTRY_NAME)

    private fun didDataStoreExpire(): Boolean =
        System.nanoTime() - storedLastUpdateDateNs.get() > DATASTORE_EXPIRATION_NS

    // endregion

    internal companion object {
        internal const val DATASTORE_EXPIRATION_NS =
            DateUtils.DAY_IN_MILLIS * 30 * 1000 * 1000 // 30 days in nanoseconds
        internal const val DATASTORE_HASHES_ENTRY_NAME = "resource-hash-store"
    }
}
