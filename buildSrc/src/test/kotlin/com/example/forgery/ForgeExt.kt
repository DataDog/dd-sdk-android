/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.example.forgery

import fr.xgouchet.elmyr.Forge

internal fun Forge.aNumber(): Number {
    return anElementFrom(anInt(), aDouble(), aFloat(), aLong())
}
