/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.example.forgery

import com.example.model.Style
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class StyleForgeryFactory : ForgeryFactory<Style> {

    override fun getForgery(forge: Forge): Style {
        return Style(
            color = forge.getForgery()
        )
    }
}
