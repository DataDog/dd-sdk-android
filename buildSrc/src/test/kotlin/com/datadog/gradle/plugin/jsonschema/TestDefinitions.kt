/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema

val Address = TypeDefinition.Class(
    name = "Address",
    properties = listOf(
        TypeProperty("street_address", TypeDefinition.Primitive(JsonType.STRING), false),
        TypeProperty("city", TypeDefinition.Primitive(JsonType.STRING), false),
        TypeProperty("state", TypeDefinition.Primitive(JsonType.STRING), false)
    )
)

val Article = TypeDefinition.Class(
    name = "Article",
    properties = listOf(
        TypeProperty("title", TypeDefinition.Primitive(JsonType.STRING), false),
        TypeProperty(
            "tags",
            TypeDefinition.Array(TypeDefinition.Primitive(JsonType.STRING)),
            true
        ),
        TypeProperty(
            "authors",
            TypeDefinition.Array(TypeDefinition.Primitive(JsonType.STRING)),
            false
        )
    )
)

val Book = TypeDefinition.Class(
    name = "Book",
    properties = listOf(
        TypeProperty("bookId", TypeDefinition.Primitive(JsonType.INTEGER), false),
        TypeProperty("title", TypeDefinition.Primitive(JsonType.STRING), false),
        TypeProperty("price", TypeDefinition.Primitive(JsonType.NUMBER), false),
        TypeProperty(
            "author", TypeDefinition.Class(
                name = "Author",
                properties = listOf(
                    TypeProperty("firstName", TypeDefinition.Primitive(JsonType.STRING), false),
                    TypeProperty("lastName", TypeDefinition.Primitive(JsonType.STRING), false),
                    TypeProperty(
                        "contact",
                        TypeDefinition.Class(
                            name = "Contact",
                            properties = listOf(
                                TypeProperty(
                                    "phone",
                                    TypeDefinition.Primitive(JsonType.STRING),
                                    true
                                ),
                                TypeProperty(
                                    "email",
                                    TypeDefinition.Primitive(JsonType.STRING),
                                    true
                                )
                            )
                        ),
                        false
                    )
                )
            ),
            false
        )
    )
)

val Customer = TypeDefinition.Class(
    name = "Customer",
    properties = listOf(
        TypeProperty("name", TypeDefinition.Primitive(JsonType.STRING), true),
        TypeProperty("billing_address", Address, true),
        TypeProperty("shipping_address", Address, true)
    )
)

val Conflict = TypeDefinition.Class(
    name = "Conflict",
    properties = listOf(
        TypeProperty(
            "type",
            TypeDefinition.Class(
                name = "Type",
                properties = listOf(
                    TypeProperty("id", TypeDefinition.Primitive(JsonType.STRING), true)
                )
            ),
            true
        ),
        TypeProperty(
            "user",
            TypeDefinition.Class(
                name = "User",
                properties = listOf(
                    TypeProperty("name", TypeDefinition.Primitive(JsonType.STRING), true),
                    TypeProperty(
                        "type",
                        TypeDefinition.Enum(
                            name = "Type",
                            type = JsonType.STRING,
                            values = listOf("unknown", "customer", "partner")
                        ),
                        true
                    )
                )
            ),
            true
        )
    )
)

val DateTime = TypeDefinition.Class(
    name = "DateTime",
    properties = listOf(
        TypeProperty(
            "date",
            TypeDefinition.Class(
                name = "Date",
                properties = listOf(
                    TypeProperty("year", TypeDefinition.Primitive(JsonType.INTEGER), true),
                    TypeProperty(
                        "month", TypeDefinition.Enum(
                            "Month",
                            JsonType.STRING,
                            listOf(
                                "jan", "feb", "mar", "apr", "may", "jun",
                                "jul", "aug", "sep", "oct", "nov", "dec"
                            )
                        ), true
                    ),
                    TypeProperty("day", TypeDefinition.Primitive(JsonType.INTEGER), true)
                )
            ),
            true
        ),
        TypeProperty(
            "time",
            TypeDefinition.Class(
                name = "Time",
                properties = listOf(
                    TypeProperty("hour", TypeDefinition.Primitive(JsonType.INTEGER), true),
                    TypeProperty("minute", TypeDefinition.Primitive(JsonType.INTEGER), true),
                    TypeProperty("seconds", TypeDefinition.Primitive(JsonType.INTEGER), true)
                )
            ),
            true
        )
    )
)
val Demo = TypeDefinition.Class(
    name = "Demo",
    properties = listOf(
        TypeProperty("s", TypeDefinition.Primitive(JsonType.STRING), false),
        TypeProperty("i", TypeDefinition.Primitive(JsonType.INTEGER), false),
        TypeProperty("n", TypeDefinition.Primitive(JsonType.NUMBER), false),
        TypeProperty("b", TypeDefinition.Primitive(JsonType.BOOLEAN), false),
        TypeProperty("l", TypeDefinition.Null(), false),
        TypeProperty("ns", TypeDefinition.Primitive(JsonType.STRING), true),
        TypeProperty("ni", TypeDefinition.Primitive(JsonType.INTEGER), true),
        TypeProperty("nn", TypeDefinition.Primitive(JsonType.NUMBER), true),
        TypeProperty("nb", TypeDefinition.Primitive(JsonType.BOOLEAN), true),
        TypeProperty("nl", TypeDefinition.Null(), true)

    )
)

val Delivery = TypeDefinition.Class(
    name = "Delivery",
    properties = listOf(
        TypeProperty("item", TypeDefinition.Primitive(JsonType.STRING), false),
        TypeProperty(
            "customer",
            TypeDefinition.Class(
                name = "Customer",
                properties = listOf(
                    TypeProperty("name", TypeDefinition.Primitive(JsonType.STRING), true),
                    TypeProperty("billing_address", Address, true),
                    TypeProperty("shipping_address", Address, true)
                )
            ),
            false
        )
    )
)

val Foo = TypeDefinition.Class(
    name = "Foo",
    properties = listOf(
        TypeProperty("bar", TypeDefinition.Primitive(JsonType.STRING), true),
        TypeProperty("baz", TypeDefinition.Primitive(JsonType.INTEGER), true)
    )
)

val Location = TypeDefinition.Class(
    name = "Location",
    properties = listOf(
        TypeProperty("planet", TypeDefinition.Constant(JsonType.STRING, "earth"), false)
    )
)

val Message = TypeDefinition.Class(
    name = "Message",
    properties = listOf(
        TypeProperty(
            "destination",
            TypeDefinition.Array(TypeDefinition.Primitive(JsonType.STRING)),
            optional = false,
            readOnly = true
        ),
        TypeProperty(
            "origin",
            TypeDefinition.Primitive(JsonType.STRING),
            optional = false,
            readOnly = true
        ),
        TypeProperty(
            "subject",
            TypeDefinition.Primitive(JsonType.STRING),
            optional = true,
            readOnly = true
        ),
        TypeProperty(
            "message",
            TypeDefinition.Primitive(JsonType.STRING),
            optional = true,
            readOnly = true
        ),
        TypeProperty(
            "labels",
            TypeDefinition.Array(TypeDefinition.Primitive(JsonType.STRING)),
            optional = true,
            readOnly = false
        ),
        TypeProperty(
            "read",
            TypeDefinition.Primitive(JsonType.BOOLEAN),
            optional = true,
            readOnly = false
        ),
        TypeProperty(
            "important",
            TypeDefinition.Primitive(JsonType.BOOLEAN),
            optional = true,
            readOnly = false
        )
    )
)

val Opus = TypeDefinition.Class(
    name = "Opus",
    description = "A musical opus.",
    properties = listOf(
        TypeProperty(
            "title",
            TypeDefinition.Primitive(JsonType.STRING, "The opus's title."),
            true
        ),
        TypeProperty(
            "composer",
            TypeDefinition.Primitive(JsonType.STRING, "The opus's composer."),
            true
        ),
        TypeProperty(
            "artists",
            TypeDefinition.Array(
                TypeDefinition.Class(
                    name = "Artist",
                    description = "An artist and their role in an opus.",
                    properties = listOf(
                        TypeProperty(
                            "name",
                            TypeDefinition.Primitive(JsonType.STRING, "The artist's name."),
                            true
                        ),
                        TypeProperty(
                            "role",
                            TypeDefinition.Enum(
                                "Role",
                                JsonType.STRING,
                                listOf(
                                    "singer", "guitarist", "pianist", "drummer", "bassist",
                                    "violinist", "dj", "vocals", "other"
                                ),
                                "The artist's role."
                            ),
                            true
                        )
                    )
                ),
                description = "The opus's artists."
            ),
            true
        ),
        TypeProperty(
            "duration",
            TypeDefinition.Primitive(JsonType.INTEGER, "The opus's duration in seconds"),
            true
        )
    )
)

val Person = TypeDefinition.Class(
    name = "Person",
    properties = listOf(
        TypeProperty("firstName", TypeDefinition.Primitive(JsonType.STRING), true),
        TypeProperty("lastName", TypeDefinition.Primitive(JsonType.STRING), true),
        TypeProperty("age", TypeDefinition.Primitive(JsonType.INTEGER), true)
    )
)

val Product = TypeDefinition.Class(
    name = "Product",
    properties = listOf(
        TypeProperty("productId", TypeDefinition.Primitive(JsonType.INTEGER), false),
        TypeProperty("productName", TypeDefinition.Primitive(JsonType.STRING), false),
        TypeProperty("price", TypeDefinition.Primitive(JsonType.NUMBER), false)
    )
)

val Shipping = TypeDefinition.Class(
    name = "Shipping",
    properties = listOf(
        TypeProperty("item", TypeDefinition.Primitive(JsonType.STRING), false),
        TypeProperty("destination", Address, false)
    )
)

val Style = TypeDefinition.Class(
    name = "Style",
    properties = listOf(
        TypeProperty(
            "color",
            TypeDefinition.Enum(
                "Color",
                JsonType.STRING,
                listOf("red", "amber", "green", "dark_blue", "lime green", "sunburst-yellow")
            ),
            false
        )
    )
)

val User = TypeDefinition.Class(
    name = "User",
    properties = listOf(
        TypeProperty("username", TypeDefinition.Primitive(JsonType.STRING), false),
        TypeProperty("host", TypeDefinition.Primitive(JsonType.STRING), false),
        TypeProperty("firstname", TypeDefinition.Primitive(JsonType.STRING), true),
        TypeProperty("lastname", TypeDefinition.Primitive(JsonType.STRING), false),
        TypeProperty(
            "contact_type",
            TypeDefinition.Enum(
                name = "ContactType",
                type = null,
                values = listOf("personal", "professional")
            ),
            false
        )
    )
)

val UserMerged = TypeDefinition.Class(
    name = "UserMerged",
    properties = listOf(
        TypeProperty("email", TypeDefinition.Primitive(JsonType.STRING), true),
        TypeProperty("phone", TypeDefinition.Primitive(JsonType.STRING), true),
        TypeProperty(
            "info",
            TypeDefinition.Class(
                name = "Info",
                properties = listOf(
                    TypeProperty("notes", TypeDefinition.Primitive(JsonType.STRING), true),
                    TypeProperty("source", TypeDefinition.Primitive(JsonType.STRING), true)
                )
            ),
            true
        ),
        TypeProperty("firstname", TypeDefinition.Primitive(JsonType.STRING), true),
        TypeProperty("lastname", TypeDefinition.Primitive(JsonType.STRING), false)
    )
)

val Version = TypeDefinition.Class(
    name = "Version",
    properties = listOf(
        TypeProperty("version", TypeDefinition.Constant(JsonType.INTEGER, 42.0), false),
        TypeProperty("delta", TypeDefinition.Constant(JsonType.NUMBER, 3.1415), true)
    )
)

val Video = TypeDefinition.Class(
    name = "Video",
    properties = listOf(
        TypeProperty("title", TypeDefinition.Primitive(JsonType.STRING), false),
        TypeProperty(
            "tags",
            TypeDefinition.Array(TypeDefinition.Primitive(JsonType.STRING), uniqueItems = true),
            true
        ),
        TypeProperty(
            "links",
            TypeDefinition.Array(TypeDefinition.Primitive(JsonType.STRING), uniqueItems = true),
            true
        )
    )
)
