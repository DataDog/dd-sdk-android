/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin

import java.io.Serializable

open class DdExtension : Serializable {
    var environmentName: String = ""
    var site: String = DdConfiguration.Site.US.name
}
