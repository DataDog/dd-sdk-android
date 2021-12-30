/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import java.nio.charset.Charset

class CharsetForgeryFactory : ForgeryFactory<Charset> {
    /**
     * @param forge the forge instance to use to generate a forgery
     * @return a new instance of type T, randomly generated with the help of the forge instance
     */
    override fun getForgery(forge: Forge): Charset {
        return forge.anElementFrom(
            Charsets.ISO_8859_1,
            Charsets.US_ASCII,
            Charsets.UTF_8,
            Charsets.UTF_16,
            Charsets.UTF_32
        )
    }
}
