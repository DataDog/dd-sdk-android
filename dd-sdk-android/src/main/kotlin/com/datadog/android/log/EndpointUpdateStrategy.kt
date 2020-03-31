/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log

/**
 * The strategy on how to deal with older data when the
 * endpoint needs to be changed.
 */
@Deprecated("This was only meant as an internal feature and is not needed anymore.")
enum class EndpointUpdateStrategy {

    /**
     * All previous unsent data will be deleted and lost forever.
     */
    DISCARD_OLD_DATA,
    /**
     * All previous unsent data will be sent to the new endpoint.
     */
    SEND_OLD_DATA_TO_NEW_ENDPOINT
}
