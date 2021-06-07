/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.example.forgery

import com.example.model.Company
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class CompanyForgeryFactory : ForgeryFactory<Company> {
    override fun getForgery(forge: Forge): Company {
        return Company(
            name = forge.aNullable { forge.anAlphabeticalString() },
            ratings = forge.aNullable {
                Company.Ratings(
                    global = aLong(),
                    additionalProperties = aMap { anAlphabeticalString() to aLong() }
                )
            },
            information = forge.aNullable {
                Company.Information(
                    forge.aNullable { forge.aLong() },
                    forge.aNullable { forge.aLong() },
                    additionalProperties = forge.aMap { anAlphabeticalString() to aMap { anHexadecimalString() to aLong() } }
                )
            },
            additionalProperties = forge.aMap { anAlphabeticalString() to aNullable { anHexadecimalString() } }
        )
    }
}
