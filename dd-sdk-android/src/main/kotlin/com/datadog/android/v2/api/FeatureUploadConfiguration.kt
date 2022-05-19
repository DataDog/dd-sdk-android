/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.api

/**
 * Contains the upload configuration for an [FeatureScope] instance.
 *
 * @property endpointUrl the url endpoint data should be uploaded to
 * @property payloadFormat the expected format of the payload
 */
data class FeatureUploadConfiguration(
    val endpointUrl: String,
    val payloadFormat: PayloadFormat
)
