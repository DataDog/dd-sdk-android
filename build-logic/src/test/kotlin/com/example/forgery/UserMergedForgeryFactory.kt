/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.example.forgery

import com.example.model.UserMerged
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class UserMergedForgeryFactory : ForgeryFactory<UserMerged> {
    override fun getForgery(forge: Forge): UserMerged {
        return UserMerged(
            email = forge.aNullable { aStringMatching("\\w+@[a-z]+\\.[a-z]{3}") },
            phone = forge.aNullable { aStringMatching("\\d{3,8}") },
            info = forge.aNullable {
                UserMerged.Info(
                    notes = forge.aNullable { anAlphabeticalString() },
                    source = forge.aNullable { anAlphabeticalString() }
                )
            },
            firstname = forge.aNullable { anAlphabeticalString() },
            lastname = forge.anAlphabeticalString()
        )
    }
}
