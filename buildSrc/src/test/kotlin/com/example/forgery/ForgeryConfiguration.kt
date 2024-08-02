/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.example.forgery

import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeConfigurator

internal class ForgeryConfiguration : ForgeConfigurator {
    override fun configure(forge: Forge) {
        forge.addFactory(AnimalForgeryFactory())
        forge.addFactory(ArticleForgeryFactory())
        forge.addFactory(BikeForgeryFactory())
        forge.addFactory(BookForgeryFactory())
        forge.addFactory(ConflictForgeryFactory())
        forge.addFactory(CommentForgeryFactory())
        forge.addFactory(CompanyForgeryFactory())
        forge.addFactory(CustomerForgeryFactory())
        forge.addFactory(DateTimeForgeryFactory())
        forge.addFactory(DeliveryForgeryFactory())
        forge.addFactory(DemoForgeryFactory())
        forge.addFactory(FooForgeryFactory())
        forge.addFactory(HouseholdForgeryFactory())
        forge.addFactory(JacketForgeryFactory())
        forge.addFactory(LocationForgeryFactory())
        forge.addFactory(MessageForgeryFactory())
        forge.addFactory(OpusForgeryFactory())
        forge.addFactory(OrderForgeryFactory())
        forge.addFactory(PersonForgeryFactory())
        forge.addFactory(ProductForgeryFactory())
        forge.addFactory(ShippingForgeryFactory())
        forge.addFactory(StyleForgeryFactory())
        forge.addFactory(UserForgeryFactory())
        forge.addFactory(UserMergedForgeryFactory())
        forge.addFactory(VersionForgeryFactory())
        forge.addFactory(VideoForgeryFactory())
        forge.addFactory(WeirdComboForgeryFactory())
    }
}
