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

@Suppress("TooManyFunctions")
internal class ResourcesDataStoreManager(
    private val featureSdkCore: FeatureSdkCore,
    private val resourceHashesSerializer: Serializer<Set<String>>,
    private val resourcesHashesDeserializer: Deserializer<String, Set<String>>,
    private val updateDateSerializer: Serializer<Long>,
    private val updateDateDeserializer: Deserializer<String, Long>,
    private val featureScope: FeatureScope? = featureSdkCore.getFeature(
        SESSION_REPLAY_RESOURCES_FEATURE_NAME
    )
) {
    private val knownResources = Collections.synchronizedSet(mutableSetOf<String>())

    init {
        initializeManager()
    }

    internal fun wasResourcePreviouslySent(resourceHash: String): Boolean =
        knownResources.contains(resourceHash)

    internal fun store(resourceHash: String) {
        knownResources.add(resourceHash)
        writeResources()
    }

    // region internal

    private fun initializeManager() {
        fetchLastUpdateDate(
            onFetchSuccessful = { dataStoreContentUpdateDate ->
                onFetchLastUpdateDateSuccess(dataStoreContentUpdateDate?.data)
            },
            onFetchFailure = {
                createOrOverwriteLastUpdateDateFile(featureScope)
            }
        )
    }

    private fun writeResources() {
        featureScope?.dataStore?.setValue(
            data = knownResources,
            key = DATASTORE_HASHES_CONTENT_FILENAME,
            serializer = resourceHashesSerializer
        )
    }

    private fun createOrOverwriteLastUpdateDateFile(featureScope: FeatureScope?) {
        val now = System.currentTimeMillis()
        featureScope?.dataStore?.setValue(
            key = DATASTORE_HASHES_UPDATE_DATE_FILENAME,
            data = now,
            serializer = updateDateSerializer
        )
    }

    private fun fetchLastUpdateDate(
        onFetchSuccessful: (dataStoreContent: DataStoreContent<Long>?) -> Unit = {},
        onFetchFailure: () -> Unit = {}
    ) {
        featureScope?.dataStore?.value(
            key = DATASTORE_HASHES_UPDATE_DATE_FILENAME,
            deserializer = updateDateDeserializer,
            callback = object : DataStoreCallback<Long> {
                override fun onSuccess(dataStoreContent: DataStoreContent<Long>?) {
                    onFetchSuccessful(dataStoreContent)
                }

                override fun onFailure() {
                    onFetchFailure()
                }
            }
        )
    }

    private fun fetchStoredResources(
        onFetchSuccessful: (dataStoreContent: DataStoreContent<Set<String>>?) -> Unit = {},
        onFetchFailure: () -> Unit = {}
    ) {
        featureScope?.dataStore?.value(
            key = DATASTORE_HASHES_CONTENT_FILENAME,
            deserializer = resourcesHashesDeserializer,
            callback = object : DataStoreCallback<Set<String>> {
                override fun onSuccess(dataStoreContent: DataStoreContent<Set<String>>?) {
                    onFetchSuccessful(dataStoreContent)
                }

                override fun onFailure() {
                    onFetchFailure()
                }
            }
        )
    }

    private fun onFetchLastUpdateDateSuccess(storedLastUpdateDate: Long?) {
        if (storedLastUpdateDate == null) {
            createOrOverwriteLastUpdateDateFile(featureScope)
            return
        }

        if (didDataStoreExpire(storedLastUpdateDate)) {
            handleDataStoreExpired()
            return
        }

        fetchStoredResources(
            onFetchSuccessful = { dataContentStoredHashes ->
                val newHashes = dataContentStoredHashes?.data
                newHashes?.let {
                    knownResources.addAll(it)
                }
            }
        )
    }

    private fun handleDataStoreExpired() {
        // we fetch the hashes in order not to remove everything in knownResources
        // because its possible that while the init block is executing asynchronously
        // hashes are already being stored in the manager, and if so we don't want to just clear
        // them all if the datastore expired, but only the ones that are relevant
        fetchStoredResources(
            onFetchSuccessful = { fetchedHashes ->
                val storedHashes = fetchedHashes?.data
                storedHashes?.let {
                    knownResources.removeAll(it)
                }

                createOrOverwriteLastUpdateDateFile(featureScope)
                deleteStoredHashesFile()
            }
        )
    }

    private fun deleteStoredHashesFile() =
        featureScope?.dataStore?.removeValue(DATASTORE_HASHES_CONTENT_FILENAME)

    private fun didDataStoreExpire(storedLastUpdateDate: Long): Boolean =
        System.currentTimeMillis() - storedLastUpdateDate > DATASTORE_EXPIRATION_MS

    // endregion

    internal companion object {
        internal const val DATASTORE_EXPIRATION_MS = DateUtils.DAY_IN_MILLIS * 30 // 30 days
        internal const val DATASTORE_HASHES_UPDATE_DATE_FILENAME = "resource-hash-store-update-date"
        internal const val DATASTORE_HASHES_CONTENT_FILENAME = "resource-hash-store-contents"
    }
}
