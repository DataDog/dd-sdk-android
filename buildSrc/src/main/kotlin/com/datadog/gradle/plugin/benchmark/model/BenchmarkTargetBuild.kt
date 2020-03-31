/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.benchmark.model

import com.google.gson.annotations.SerializedName

data class BenchmarkTargetBuild(
    @SerializedName("device") var device: String,
    @SerializedName("fingerprint") var fingerprint: String,
    @SerializedName("model") var model: String,
    @SerializedName("version") var version: Version
) {
    data class Version(
        @SerializedName("version") var sdk: Int
    )
}
