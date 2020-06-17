/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.example.forgery

import com.example.model.Demo
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class DemoForgeryFactory : ForgeryFactory<Demo> {

    override fun getForgery(forge: Forge): Demo {
        return Demo(
            s = forge.anAlphabeticalString(),
            i = forge.aLong(),
            n = forge.aDouble(),
            b = forge.aBool(),
            l = null
        )
    }
}
