/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.privacy

/**
 * The Consent enum class providing the possible values for the Data Privacy Consent flag.
 * @see Consent.GRANTED
 * @see Consent.NOT_GRANTED
 * @see Consent.PENDING
 */
enum class Consent {
    /**
     * The permission to persist and dispatch data to the Datadog Endpoints was granted.
     * Any previously stored pending data will be marked as ready for sent.
     */
    GRANTED,

    /**
     * Any previously stored pending data will be deleted and. Any Log, Rum, Trace event will
     * be dropped from now on without persisting it in any way.
     */
    NOT_GRANTED,

    /**
     * Any Log, Rum, Trace event will be persisted in a special location and will be pending there
     * until we will receive one of the [Consent.GRANTED] or [Consent.NOT_GRANTED] flags. Based
     * on the value of the consent flag we will decide what to do with the pending stored data.
     */
    PENDING
}
