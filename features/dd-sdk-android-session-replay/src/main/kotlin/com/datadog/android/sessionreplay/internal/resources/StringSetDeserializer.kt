/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.resources

import com.datadog.android.core.internal.persistence.Deserializer

internal class StringSetDeserializer : Deserializer<String, Set<String>> {
    override fun deserialize(model: String): Set<String> =
        model.split(",").toSet()
}
