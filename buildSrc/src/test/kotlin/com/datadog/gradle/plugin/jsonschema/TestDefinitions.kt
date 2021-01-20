/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema

val Address = TypeDefinition.Class(
    name = "Address",
    properties = listOf(
        TypeProperty("street_address", TypeDefinition.Primitive(JsonPrimitiveType.STRING), false),
        TypeProperty("city", TypeDefinition.Primitive(JsonPrimitiveType.STRING), false),
        TypeProperty("state", TypeDefinition.Primitive(JsonPrimitiveType.STRING), false)
    )
)

val Article = TypeDefinition.Class(
    name = "Article",
    properties = listOf(
        TypeProperty("title", TypeDefinition.Primitive(JsonPrimitiveType.STRING), false),
        TypeProperty(
            "tags",
            TypeDefinition.Array(TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
            true
        ),
        TypeProperty(
            "authors",
            TypeDefinition.Array(TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
            false
        )
    )
)

val Book = TypeDefinition.Class(
    name = "Book",
    properties = listOf(
        TypeProperty("bookId", TypeDefinition.Primitive(JsonPrimitiveType.INTEGER), false),
        TypeProperty("title", TypeDefinition.Primitive(JsonPrimitiveType.STRING), false),
        TypeProperty("price", TypeDefinition.Primitive(JsonPrimitiveType.DOUBLE), false),
        TypeProperty(
            "author", TypeDefinition.Class(
                name = "Author",
                properties = listOf(
                    TypeProperty("firstName", TypeDefinition.Primitive(JsonPrimitiveType.STRING), false),
                    TypeProperty("lastName", TypeDefinition.Primitive(JsonPrimitiveType.STRING), false),
                    TypeProperty(
                        "contact",
                        TypeDefinition.Class(
                            name = "Contact",
                            properties = listOf(
                                TypeProperty(
                                    "phone",
                                    TypeDefinition.Primitive(JsonPrimitiveType.STRING),
                                    true
                                ),
                                TypeProperty(
                                    "email",
                                    TypeDefinition.Primitive(JsonPrimitiveType.STRING),
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
        TypeProperty("name", TypeDefinition.Primitive(JsonPrimitiveType.STRING), true),
        TypeProperty("billing_address", Address, true),
        TypeProperty("shipping_address", Address, true)
    )
)

val Comment = TypeDefinition.Class(
    name = "Comment",
    properties = listOf(
        TypeProperty("message", TypeDefinition.Primitive(JsonPrimitiveType.STRING), true),
        TypeProperty(
            "ratings",
            TypeDefinition.Class(
                name = "Ratings",
                properties = listOf(
                    TypeProperty("global", TypeDefinition.Primitive(JsonPrimitiveType.INTEGER), false)
                ),
                additionalProperties = TypeDefinition.Primitive(JsonPrimitiveType.INTEGER)
            ),
            true
        ),
        TypeProperty(
            "flags",
            TypeDefinition.Class(
                name = "Flags",
                properties = listOf(),
                additionalProperties = TypeDefinition.Primitive(JsonPrimitiveType.STRING)
            ),
            true
        )
    )
)

val Conflict = TypeDefinition.Class(
    name = "Conflict",
    properties = listOf(
        TypeProperty(
            "type",
            TypeDefinition.Class(
                name = "ConflictType",
                properties = listOf(
                    TypeProperty("id", TypeDefinition.Primitive(JsonPrimitiveType.STRING), true)
                )
            ),
            true
        ),
        TypeProperty(
            "user",
            TypeDefinition.Class(
                name = "User",
                properties = listOf(
                    TypeProperty("name", TypeDefinition.Primitive(JsonPrimitiveType.STRING), true),
                    TypeProperty(
                        "type",
                        TypeDefinition.Enum(
                            name = "UserType",
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
                    TypeProperty("year", TypeDefinition.Primitive(JsonPrimitiveType.INTEGER), true),
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
                    TypeProperty("day", TypeDefinition.Primitive(JsonPrimitiveType.INTEGER), true)
                )
            ),
            true
        ),
        TypeProperty(
            "time",
            TypeDefinition.Class(
                name = "Time",
                properties = listOf(
                    TypeProperty("hour", TypeDefinition.Primitive(JsonPrimitiveType.INTEGER), true),
                    TypeProperty("minute", TypeDefinition.Primitive(JsonPrimitiveType.INTEGER), true),
                    TypeProperty("seconds", TypeDefinition.Primitive(JsonPrimitiveType.INTEGER), true)
                )
            ),
            true
        )
    )
)
val Demo = TypeDefinition.Class(
    name = "Demo",
    properties = listOf(
        TypeProperty("s", TypeDefinition.Primitive(JsonPrimitiveType.STRING), false),
        TypeProperty("i", TypeDefinition.Primitive(JsonPrimitiveType.INTEGER), false),
        TypeProperty("n", TypeDefinition.Primitive(JsonPrimitiveType.DOUBLE), false),
        TypeProperty("b", TypeDefinition.Primitive(JsonPrimitiveType.BOOLEAN), false),
        TypeProperty("l", TypeDefinition.Null(), false),
        TypeProperty("ns", TypeDefinition.Primitive(JsonPrimitiveType.STRING), true),
        TypeProperty("ni", TypeDefinition.Primitive(JsonPrimitiveType.INTEGER), true),
        TypeProperty("nn", TypeDefinition.Primitive(JsonPrimitiveType.DOUBLE), true),
        TypeProperty("nb", TypeDefinition.Primitive(JsonPrimitiveType.BOOLEAN), true),
        TypeProperty("nl", TypeDefinition.Null(), true)

    )
)

val Delivery = TypeDefinition.Class(
    name = "Delivery",
    properties = listOf(
        TypeProperty("item", TypeDefinition.Primitive(JsonPrimitiveType.STRING), false),
        TypeProperty(
            "customer",
            TypeDefinition.Class(
                name = "Customer",
                properties = listOf(
                    TypeProperty("name", TypeDefinition.Primitive(JsonPrimitiveType.STRING), true),
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
        TypeProperty("bar", TypeDefinition.Primitive(JsonPrimitiveType.STRING), true),
        TypeProperty("baz", TypeDefinition.Primitive(JsonPrimitiveType.INTEGER), true)
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
            TypeDefinition.Array(TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
            optional = false,
            readOnly = true
        ),
        TypeProperty(
            "origin",
            TypeDefinition.Primitive(JsonPrimitiveType.STRING),
            optional = false,
            readOnly = true
        ),
        TypeProperty(
            "subject",
            TypeDefinition.Primitive(JsonPrimitiveType.STRING),
            optional = true,
            readOnly = true
        ),
        TypeProperty(
            "message",
            TypeDefinition.Primitive(JsonPrimitiveType.STRING),
            optional = true,
            readOnly = true
        ),
        TypeProperty(
            "labels",
            TypeDefinition.Array(TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
            optional = true,
            readOnly = false
        ),
        TypeProperty(
            "read",
            TypeDefinition.Primitive(JsonPrimitiveType.BOOLEAN),
            optional = true,
            readOnly = false
        ),
        TypeProperty(
            "important",
            TypeDefinition.Primitive(JsonPrimitiveType.BOOLEAN),
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
            TypeDefinition.Primitive(JsonPrimitiveType.STRING, "The opus's title."),
            true
        ),
        TypeProperty(
            "composer",
            TypeDefinition.Primitive(JsonPrimitiveType.STRING, "The opus's composer."),
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
                            TypeDefinition.Primitive(JsonPrimitiveType.STRING, "The artist's name."),
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
            TypeDefinition.Primitive(JsonPrimitiveType.INTEGER, "The opus's duration in seconds"),
            true
        )
    )
)

val Person = TypeDefinition.Class(
    name = "Person",
    properties = listOf(
        TypeProperty("firstName", TypeDefinition.Primitive(JsonPrimitiveType.STRING), true),
        TypeProperty("lastName", TypeDefinition.Primitive(JsonPrimitiveType.STRING), true),
        TypeProperty("age", TypeDefinition.Primitive(JsonPrimitiveType.INTEGER), true)
    )
)

val Product = TypeDefinition.Class(
    name = "Product",
    properties = listOf(
        TypeProperty("productId", TypeDefinition.Primitive(JsonPrimitiveType.INTEGER), false),
        TypeProperty("productName", TypeDefinition.Primitive(JsonPrimitiveType.STRING), false),
        TypeProperty("price", TypeDefinition.Primitive(JsonPrimitiveType.DOUBLE), false)
    )
)

val Shipping = TypeDefinition.Class(
    name = "Shipping",
    properties = listOf(
        TypeProperty("item", TypeDefinition.Primitive(JsonPrimitiveType.STRING), false),
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

val Order = TypeDefinition.Class(
    name = "Order",
    properties = listOf(
        TypeProperty(
            "sizes",
            TypeDefinition.Array(
                TypeDefinition.Enum(
                    "Size",
                    JsonType.STRING,
                    listOf("x small", "small", "medium", "large", "x large")
                ),
                uniqueItems = true
            ),
            false
        )
    )
)

val User = TypeDefinition.Class(
    name = "User",
    properties = listOf(
        TypeProperty("username", TypeDefinition.Primitive(JsonPrimitiveType.STRING), false),
        TypeProperty("host", TypeDefinition.Primitive(JsonPrimitiveType.STRING), false),
        TypeProperty("firstname", TypeDefinition.Primitive(JsonPrimitiveType.STRING), true),
        TypeProperty("lastname", TypeDefinition.Primitive(JsonPrimitiveType.STRING), false),
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
        TypeProperty("email", TypeDefinition.Primitive(JsonPrimitiveType.STRING), true),
        TypeProperty("phone", TypeDefinition.Primitive(JsonPrimitiveType.STRING), true),
        TypeProperty(
            "info",
            TypeDefinition.Class(
                name = "Info",
                properties = listOf(
                    TypeProperty("notes", TypeDefinition.Primitive(JsonPrimitiveType.STRING), true),
                    TypeProperty("source", TypeDefinition.Primitive(JsonPrimitiveType.STRING), true)
                )
            ),
            true
        ),
        TypeProperty("firstname", TypeDefinition.Primitive(JsonPrimitiveType.STRING), true),
        TypeProperty("lastname", TypeDefinition.Primitive(JsonPrimitiveType.STRING), false)
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
        TypeProperty("title", TypeDefinition.Primitive(JsonPrimitiveType.STRING), false),
        TypeProperty(
            "tags",
            TypeDefinition.Array(TypeDefinition.Primitive(JsonPrimitiveType.STRING), uniqueItems = true),
            true
        ),
        TypeProperty(
            "links",
            TypeDefinition.Array(TypeDefinition.Primitive(JsonPrimitiveType.STRING), uniqueItems = true),
            true
        )
    )
)
