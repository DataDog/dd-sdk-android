/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.utils

import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InvalidObjectException

class ThrowableForgeryFactory :
    ForgeryFactory<Throwable> {
    override fun getForgery(forge: Forge): Throwable {
        val message = forge.anAlphabeticalString()
        return forge.anElementFrom(
            IOException(message),
            IllegalStateException(message),
            UnknownError(message),
            ArrayIndexOutOfBoundsException(message),
            NullPointerException(message),
            InvalidObjectException(message),
            UnsupportedOperationException(message),
            FileNotFoundException(message)
        )
    }
}
