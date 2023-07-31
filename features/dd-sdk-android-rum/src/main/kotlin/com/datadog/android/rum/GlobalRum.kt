/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

@Deprecated(
    message = "GlobalRum object was renamed to GlobalRumMonitor.",
    replaceWith = ReplaceWith(
        expression = "GlobalRumMonitor",
        imports = ["com.datadog.android.rum.GlobalRumMonitor"]
    ),
    level = DeprecationLevel.ERROR
)
typealias GlobalRum = GlobalRumMonitor
