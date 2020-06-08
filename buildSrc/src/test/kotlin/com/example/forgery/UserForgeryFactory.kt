/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.example.forgery

import com.example.model.User
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

class UserForgeryFactory : ForgeryFactory<User> {
    override fun getForgery(forge: Forge): User {
        return User(
            username = forge.anAlphabeticalString(),
            host = forge.aStringMatching("[a-z]+\\.[a-z]{3}"),
            firstname = forge.aNullable { anAlphabeticalString() },
            lastname = forge.anAlphabeticalString(),
            contactType = forge.getForgery()
        )
    }
}
