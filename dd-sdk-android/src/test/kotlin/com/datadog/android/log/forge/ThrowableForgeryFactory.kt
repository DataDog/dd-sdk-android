/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.forge

import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryException
import fr.xgouchet.elmyr.ForgeryFactory
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InvalidObjectException
import java.lang.IllegalStateException
import java.lang.NullPointerException
import java.lang.UnsupportedOperationException

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
            ForgeryException(message),
            InvalidObjectException(message),
            UnsupportedOperationException(message),
            FileNotFoundException(message)
        )
    }
}
