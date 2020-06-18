/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.example.forgery

import com.example.model.Customer
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class CustomerForgeryFactory : ForgeryFactory<Customer> {
    override fun getForgery(forge: Forge): Customer {
        return Customer(
            name = forge.aNullable { anAlphabeticalString() },
            billingAddress = forge.aNullable {
                Customer.Address(
                    streetAddress = forge.anAlphabeticalString(),
                    city = forge.anAlphabeticalString(),
                    state = forge.anAlphabeticalString()
                )
            },
            shippingAddress = forge.aNullable {
                Customer.Address(
                    streetAddress = forge.anAlphabeticalString(),
                    city = forge.anAlphabeticalString(),
                    state = forge.anAlphabeticalString()
                )
            }
        )
    }
}
