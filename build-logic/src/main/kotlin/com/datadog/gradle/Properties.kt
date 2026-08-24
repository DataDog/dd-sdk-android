/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle

object Properties {
    // needed to bring some classes into classpath which are missing on the lower APIs
    const val USE_API21_JAVA_BACKPORT = "use-api21-java-backport"
    const val USE_DESUGARING = "use-desugaring"
}
