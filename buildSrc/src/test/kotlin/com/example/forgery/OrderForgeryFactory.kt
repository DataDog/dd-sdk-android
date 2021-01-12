/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.example.forgery

import com.example.model.Order

import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class OrderForgeryFactory : ForgeryFactory<Order> {

    override fun getForgery(forge: Forge): Order {
        return Order(
            sizes = forge.aList { forge.aValueFrom(Order.Size::class.java) }.toSet()
        )
    }
}
