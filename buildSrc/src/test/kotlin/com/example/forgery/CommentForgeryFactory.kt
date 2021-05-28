/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.example.forgery

import com.example.model.Comment
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class CommentForgeryFactory : ForgeryFactory<Comment> {
    override fun getForgery(forge: Forge): Comment {
        return Comment(
            message = forge.aNullable { forge.anAlphabeticalString() },
            ratings = forge.aNullable {
                Comment.Ratings(
                    global = aLong(),
                    additionalProperties = aMap { anAlphabeticalString() to aLong() }
                )
            },
            flags = forge.aNullable {
                Comment.Flags(
                    additionalProperties = aMap { anAlphabeticalString() to aBool() }
                )
            },
            tags = forge.aNullable {
                Comment.Tags(
                    additionalProperties = aMap { anAlphabeticalString() to anHexadecimalString() }
                )
            }
        )
    }
}
