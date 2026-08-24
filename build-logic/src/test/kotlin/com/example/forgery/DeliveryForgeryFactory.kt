/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.example.forgery

import com.example.model.Delivery
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class DeliveryForgeryFactory : ForgeryFactory<Delivery> {
    override fun getForgery(forge: Forge): Delivery {
        return Delivery(
            item = forge.anAlphabeticalString(),
            customer = Delivery.Customer(
                name = forge.aNullable { anAlphabeticalString() },
                billingAddress = forge.aNullable {
                    Delivery.Address(
                        streetAddress = forge.anAlphabeticalString(),
                        city = forge.anAlphabeticalString(),
                        state = forge.anAlphabeticalString()
                    )
                },
                shippingAddress = forge.aNullable {
                    Delivery.Address(
                        streetAddress = forge.anAlphabeticalString(),
                        city = forge.anAlphabeticalString(),
                        state = forge.anAlphabeticalString()
                    )
                }
            )
        )
    }
}
