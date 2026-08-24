/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.example.forgery

import com.example.model.Message
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class MessageForgeryFactory : ForgeryFactory<Message> {

    override fun getForgery(forge: Forge): Message {
        return Message(
            destination = forge.aList { aStringMatching(EMAIL_REGEX) },
            origin = forge.aStringMatching(EMAIL_REGEX),
            subject = forge.aNullable { anAlphabeticalString() },
            message = forge.aNullable { anAlphabeticalString() },
            labels = forge.aNullable { forge.aList { anAlphabeticalString() } },
            read = forge.aNullable { forge.aBool() },
            important = forge.aNullable { forge.aBool() }
        )
    }

    companion object {
        const val EMAIL_REGEX = "[a-z]{3,9}\\.[a-z]{3,9}@[a-z]{3,9}\\.[a-z]{3}"
    }
}
