/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.example.forgery

import com.example.model.Book
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class BookForgeryFactory : ForgeryFactory<Book> {
    override fun getForgery(forge: Forge): Book {
        return Book(
            bookId = forge.aLong(),
            title = forge.anAlphabeticalString(),
            author = Book.Author(
                firstName = forge.anAlphabeticalString(),
                lastName = forge.anAlphabeticalString(),
                contact = Book.Contact(
                    email = forge.aNullable { aStringMatching("[a-z0-9]+@[a-z]+.com") },
                    phone = forge.aNullable { aStringMatching("\\+[0-9]{6,10}") }
                )
            ),
            price = forge.aDouble(0.0)
        )
    }
}
