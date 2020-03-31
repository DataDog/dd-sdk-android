/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.unit.forge

import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryException
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InvalidObjectException

/**
 * @return a random [Throwable] instance with a forged message.
 */
fun Forge.aThrowable(): Throwable {
    val errorMessage = anAlphabeticalString()
    return anElementFrom(
        IOException(errorMessage),
        IllegalStateException(errorMessage),
        UnknownError(errorMessage),
        ArrayIndexOutOfBoundsException(errorMessage),
        NullPointerException(errorMessage),
        ForgeryException(errorMessage),
        InvalidObjectException(errorMessage),
        UnsupportedOperationException(errorMessage),
        FileNotFoundException(errorMessage)
    )
}
