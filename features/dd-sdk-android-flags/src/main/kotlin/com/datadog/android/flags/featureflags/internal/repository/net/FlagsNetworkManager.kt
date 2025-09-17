/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal.repository.net

import com.datadog.android.flags.featureflags.model.ProviderContext
import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
internal interface FlagsNetworkManager {
    fun downloadPrecomputedFlags(context: ProviderContext): String?
}
