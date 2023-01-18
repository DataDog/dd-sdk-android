/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence

/**
 * The Deserializer<P, R> generic interface. Should be implemented by any custom deserializer.
 */
internal interface Deserializer<P : Any, R : Any> {

    /**
     * Deserializes the data from the given payload type into given output type.
     * @return the model represented by the given payload, or null when deserialization
     * is impossible.
     */
    fun deserialize(model: P): R?
}
