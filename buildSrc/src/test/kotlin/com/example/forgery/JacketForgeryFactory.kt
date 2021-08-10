/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.example.forgery

import com.example.model.Jacket
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class JacketForgeryFactory : ForgeryFactory<Jacket> {

    override fun getForgery(forge: Forge): Jacket {
        return if (forge.aBool()) {
            Jacket(size = forge.aValueFrom(Jacket.Size::class.java))
        } else {
            Jacket()
        }
    }
}
