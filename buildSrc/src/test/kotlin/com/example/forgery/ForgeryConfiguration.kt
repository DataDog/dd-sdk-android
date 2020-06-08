/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.example.forgery

import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeConfigurator

class ForgeryConfiguration : ForgeConfigurator {
    override fun configure(forge: Forge) {
        forge.addFactory(ArticleForgeryFactory())
        forge.addFactory(BookForgeryFactory())
        forge.addFactory(CartForgeryFactory())
        forge.addFactory(CustomerForgeryFactory())
        forge.addFactory(DateTimeForgeryFactory())
        forge.addFactory(DemoForgeryFactory())
        forge.addFactory(FooForgeryFactory())
        forge.addFactory(LocationForgeryFactory())
        forge.addFactory(OpusForgeryFactory())
        forge.addFactory(PersonForgeryFactory())
        forge.addFactory(ProductForgeryFactory())
        forge.addFactory(UserForgeryFactory())
        forge.addFactory(StyleForgeryFactory())
        forge.addFactory(VideoForgeryFactory())
    }
}
