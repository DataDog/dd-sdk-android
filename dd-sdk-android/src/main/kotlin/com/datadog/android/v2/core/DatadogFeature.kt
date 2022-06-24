/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.core

import com.datadog.android.v2.api.FeatureScope
import com.datadog.android.v2.core.internal.net.DataUploader
import com.datadog.android.v2.core.internal.storage.Storage

internal abstract class DatadogFeature(
    val storage: Storage,
    val uploader: DataUploader
) : FeatureScope
