/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.metric.networksettled

import com.datadog.tools.annotation.NoOpImplementation

/**
 * Interface for identifying initial network resources.
 */
@NoOpImplementation
interface InitialResourceIdentifier {
    /**
     * Validates whether the given network resource context meets the criteria for an initial resource.
     *
     * @param context The context of the network resource to validate.
     * @return `true` if the context meets the criteria, `false` otherwise.
     */
    fun validate(
        context: NetworkSettledResourceContext
    ): Boolean
}
