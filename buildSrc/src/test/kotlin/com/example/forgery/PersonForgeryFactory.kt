/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.example.forgery

import com.example.model.Person
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class PersonForgeryFactory : ForgeryFactory<Person> {

    override fun getForgery(forge: Forge): Person {
        return Person(
            firstName = forge.aNullable { anAlphabeticalString() },
        lastName = forge.aNullable { anAlphabeticalString() },
            age = forge.aNullable { aLong() }
        )
    }
}
