/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.example.forgery

import com.example.model.Conflict
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

class ConflictForgeryFactory : ForgeryFactory<Conflict> {
    override fun getForgery(forge: Forge): Conflict {
        return Conflict(
            type = forge.aNullable {
                Conflict.Type(
                    aNullable { anAlphabeticalString() }
                )
            },
            user = forge.aNullable {
                Conflict.User(
                    name = aNullable { anAlphabeticalString() },
                    type = aNullable()
                )
            }
        )
    }
}
