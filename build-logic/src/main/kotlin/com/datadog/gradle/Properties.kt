/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle

object Properties {
    // Adds java.nio.file placeholders so AssertJ can load on API 24–25.
    const val USE_API21_JAVA_BACKPORT = "use-api21-java-backport"
    const val USE_DESUGARING = "use-desugaring"
}
