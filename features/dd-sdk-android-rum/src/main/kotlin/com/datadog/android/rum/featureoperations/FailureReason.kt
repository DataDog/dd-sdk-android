/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.featureoperations

/**
 * Represents the possible reasons for a failed feature operation.
 *
 * [ERROR]: Represents a failure caused by an error during execution.
 * [ABANDONED]: Represents a failure caused by user or process abandonment.
 * [OTHER]: Represents a failure due to other unspecified reasons.
 */
enum class FailureReason {
    ERROR,
    ABANDONED,
    OTHER
}
