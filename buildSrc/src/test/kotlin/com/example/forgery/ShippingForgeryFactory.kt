/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.example.forgery

import com.example.model.Shipping
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

class ShippingForgeryFactory : ForgeryFactory<Shipping> {
    override fun getForgery(forge: Forge): Shipping {
        return Shipping(
            item = forge.anAlphabeticalString(),
            destination = Shipping.Address(
                streetAddress = forge.anAlphabeticalString(),
                city = forge.anAlphabeticalString(),
                state = forge.anAlphabeticalString()
            )
        )
    }
}
