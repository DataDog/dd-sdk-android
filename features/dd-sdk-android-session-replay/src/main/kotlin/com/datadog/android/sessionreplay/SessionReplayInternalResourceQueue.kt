/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import com.datadog.android.lint.InternalApi

/**
 * Internal API for queuing custom resources in Session Replay.
 *
 * This interface provides access to the Session Replay resource queue, allowing clients
 * to manually add custom resources (e.g., SVG images, custom assets) that will be
 * uploaded alongside standard Session Replay data.
 *
 * Note: This is an internal API intended for advanced use cases where standard image
 * capture mechanisms don't apply. For regular image resources, the SDK handles
 * resource capture automatically.
 */
@InternalApi
interface SessionReplayInternalResourceQueue {
    /**
     * Adds a custom resource item to the Session Replay upload queue.
     *
     * Resources are deduplicated based on their identifier. If a resource with the same
     * identifier has already been queued or uploaded in this session, it will not be
     * queued again.
     *
     * @param identifier A unique identifier for this resource. Typically an MD5 hash of
     * the resource content to ensure deduplication across identical resources.
     * @param resourceData The raw binary data of the resource to upload.
     * @param mimeType Optional MIME type of the resource (e.g., "image/svg+xml").
     * If null, the resource will be uploaded without a specific content type.
     */
    fun addResourceItem(
        identifier: String,
        resourceData: ByteArray,
        mimeType: String? = null
    )
}
