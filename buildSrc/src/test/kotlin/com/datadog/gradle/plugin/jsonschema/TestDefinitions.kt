/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema

val Address = TypeDefinition.Class(
    name = "Address",
    properties = listOf(
        TypeProperty(
            name = "street_address",
            type = TypeDefinition.Primitive(JsonPrimitiveType.STRING),
            optional = false
        ),
        TypeProperty(
            name = "city",
            type = TypeDefinition.Primitive(JsonPrimitiveType.STRING),
            optional = false
        ),
        TypeProperty(
            name = "state",
            type = TypeDefinition.Primitive(JsonPrimitiveType.STRING),
            optional = false
        )
    )
)

val Animal = TypeDefinition.OneOfClass(
    name = "Animal",
    options = listOf(
        TypeDefinition.Class(
            name = "Fish",
            properties = listOf(
                TypeProperty(
                    name = "water",
                    type = TypeDefinition.Enum("Water", JsonType.STRING, listOf("salt", "fresh")),
                    optional = false
                ),
                TypeProperty(
                    name = "size",
                    type = TypeDefinition.Primitive(JsonPrimitiveType.INTEGER),
                    optional = true
                )
            )
        ),
        TypeDefinition.Class(
            name = "Bird",
            properties = listOf(
                TypeProperty(
                    name = "food",
                    type = TypeDefinition.Enum(
                        name = "Food",
                        type = JsonType.STRING,
                        values = listOf(
                            "fish",
                            "bird",
                            "rodent",
                            "insect",
                            "fruit",
                            "seeds",
                            "pollen"
                        )
                    ),
                    optional = false
                ),
                TypeProperty("can_fly", TypeDefinition.Primitive(JsonPrimitiveType.BOOLEAN), false)
            )
        )
    ),
    description = "A representation of the animal kingdom"
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
        TypeProperty("price", TypeDefinition.Primitive(JsonPrimitiveType.NUMBER), false),
        TypeProperty(
            "author", TypeDefinition.Class(
                name = "Author",
                properties = listOf(
                    TypeProperty(
                        "firstName",
                        TypeDefinition.Primitive(JsonPrimitiveType.STRING),
                        false
                    ),
                    TypeProperty(
                        "lastName",
                        TypeDefinition.Primitive(JsonPrimitiveType.STRING),
                        false
                    ),
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
                    TypeProperty(
                        "global",
                        TypeDefinition.Primitive(JsonPrimitiveType.INTEGER),
                        false
                    )
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
                additionalProperties = TypeDefinition.Primitive(JsonPrimitiveType.BOOLEAN)
            ),
            true
        ),
        TypeProperty(
            "tags",
            TypeDefinition.Class(
                name = "Tags",
                properties = listOf(),
                additionalProperties = TypeDefinition.Primitive(JsonPrimitiveType.STRING)
            ),
            true
        )
    )
)

val Company = TypeDefinition.Class(
    name = "Company",
    properties = listOf(
        TypeProperty("name", TypeDefinition.Primitive(JsonPrimitiveType.STRING), true),
        TypeProperty(
            "ratings",
            TypeDefinition.Class(
                name = "Ratings",
                properties = listOf(
                    TypeProperty(
                        "global",
                        TypeDefinition.Primitive(JsonPrimitiveType.INTEGER),
                        false
                    )
                ),
                additionalProperties = TypeDefinition.Primitive(JsonPrimitiveType.INTEGER)
            ),
            true
        ),
        TypeProperty(
            "information",
            TypeDefinition.Class(
                name = "Information",
                properties = listOf(
                    TypeProperty(
                        "date",
                        TypeDefinition.Primitive(JsonPrimitiveType.INTEGER),
                        true
                    ),
                    TypeProperty(
                        "priority",
                        TypeDefinition.Primitive(JsonPrimitiveType.INTEGER),
                        true
                    )
                ),
                additionalProperties = TypeDefinition.Class("?", emptyList())
            ),
            true
        )
    ),
    additionalProperties = TypeDefinition.Class("?", emptyList())
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
                    TypeProperty(
                        "minute",
                        TypeDefinition.Primitive(JsonPrimitiveType.INTEGER),
                        true
                    ),
                    TypeProperty(
                        "seconds",
                        TypeDefinition.Primitive(JsonPrimitiveType.INTEGER),
                        true
                    )
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
        TypeProperty("n", TypeDefinition.Primitive(JsonPrimitiveType.NUMBER), false),
        TypeProperty("b", TypeDefinition.Primitive(JsonPrimitiveType.BOOLEAN), false),
        TypeProperty("l", TypeDefinition.Null(), false),
        TypeProperty("ns", TypeDefinition.Primitive(JsonPrimitiveType.STRING), true),
        TypeProperty("ni", TypeDefinition.Primitive(JsonPrimitiveType.INTEGER), true),
        TypeProperty("nn", TypeDefinition.Primitive(JsonPrimitiveType.NUMBER), true),
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

val Employee = TypeDefinition.Class(
    name = "Employee",
    properties = listOf(
        TypeProperty("name", TypeDefinition.Primitive(JsonPrimitiveType.STRING), true),
        TypeProperty(
            name = "contact",
            type = TypeDefinition.Class(
                name = "Contact",
                properties = listOf(
                    TypeProperty("phone", TypeDefinition.Primitive(JsonPrimitiveType.STRING), false),
                    TypeProperty("address", Address, false),
                )
            ),
            optional = true
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
        TypeProperty("planet", TypeDefinition.Constant(JsonType.STRING, "earth"), false),
        TypeProperty("solar_system", TypeDefinition.Constant(JsonType.STRING, "sol"), false)
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
                            TypeDefinition.Primitive(
                                JsonPrimitiveType.STRING,
                                "The artist's name."
                            ),
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
        TypeProperty("price", TypeDefinition.Primitive(JsonPrimitiveType.NUMBER), false)
    )
)

val Bike = TypeDefinition.Class(
    name = "Bike",
    properties = listOf(
        TypeProperty(
            "productId",
            TypeDefinition.Primitive(JsonPrimitiveType.INTEGER),
            false,
            defaultValue = 1.0
        ),
        TypeProperty(
            "productName",
            TypeDefinition.Primitive(JsonPrimitiveType.STRING),
            false
        ),
        TypeProperty(
            "type",
            TypeDefinition.Primitive(JsonPrimitiveType.STRING),
            true,
            defaultValue = "road"
        ),
        TypeProperty(
            "price",
            TypeDefinition.Primitive(JsonPrimitiveType.NUMBER),
            false,
            defaultValue = 55.5
        ),
        TypeProperty(
            "frameMaterial",
            TypeDefinition.Enum(
                name = "FrameMaterial",
                type = JsonType.STRING,
                values = listOf(
                    "carbon",
                    "light_aluminium",
                    "iron"
                )
            ),
            true,
            defaultValue = "light_aluminium"
        ),
        TypeProperty(
            "inStock",
            TypeDefinition.Primitive(JsonPrimitiveType.BOOLEAN),
            false,
            defaultValue = true
        ),
        TypeProperty(
            "color",
            TypeDefinition.Enum(
                name = "Color",
                type = JsonType.STRING,
                values = listOf(
                    "red",
                    "amber",
                    "green",
                    "dark_blue",
                    "lime green",
                    "sunburst-yellow"
                )
            ),
            false,
            defaultValue = "lime green"
        )
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
            name = "color",
            type = TypeDefinition.Enum(
                name = "Color",
                type = null,
                values = listOf(
                    "red",
                    "amber",
                    "green",
                    "dark_blue",
                    "lime green",
                    "sunburst-yellow",
                    null
                )
            ),
            optional = false
        )
    )
)

val Household = TypeDefinition.Class(
    name = "Household",
    properties = listOf(
        TypeProperty(
            name = "pets",
            type = TypeDefinition.Array(
                items = TypeDefinition.OneOfClass(
                    name = "Animal",
                    options = listOf(
                        TypeDefinition.Class(
                            name = "Fish",
                            properties = listOf(
                                TypeProperty(
                                    name = "water",
                                    type = TypeDefinition.Enum("Water", JsonType.STRING, listOf("salt", "fresh")),
                                    optional = false
                                ),
                                TypeProperty(
                                    name = "size",
                                    type = TypeDefinition.Primitive(JsonPrimitiveType.INTEGER),
                                    optional = true
                                )
                            )
                        ),
                        TypeDefinition.Class(
                            name = "Bird",
                            properties = listOf(
                                TypeProperty(
                                    name = "food",
                                    type = TypeDefinition.Enum(
                                        name = "Food",
                                        type = JsonType.STRING,
                                        values = listOf(
                                            "fish",
                                            "bird",
                                            "rodent",
                                            "insect",
                                            "fruit",
                                            "seeds",
                                            "pollen"
                                        )
                                    ),
                                    optional = false
                                ),
                                TypeProperty("can_fly", TypeDefinition.Primitive(JsonPrimitiveType.BOOLEAN), false)
                            )
                        )
                    ),
                    description = "A representation of the animal kingdom"
                )
            ),
            optional = true
        ),
        TypeProperty(
            name = "situation",
            type = TypeDefinition.OneOfClass(
                name = "Situation",
                options = listOf(
                    TypeDefinition.Class(
                        name = "Marriage",
                        properties = listOf(
                            TypeProperty(
                                name = "spouses",
                                type = TypeDefinition.Array(
                                    items = TypeDefinition.Primitive(JsonPrimitiveType.STRING)
                                ),
                                optional = false
                            )
                        )
                    ),
                    TypeDefinition.Class(
                        name = "Cotenancy",
                        properties = listOf(
                            TypeProperty(
                                name = "roommates",
                                type = TypeDefinition.Array(
                                    items = TypeDefinition.Primitive(JsonPrimitiveType.STRING)
                                ),
                                optional = false
                            )
                        )
                    )
                )
            ),
            optional = true
        )
    )
)

val Jacket = TypeDefinition.Class(
    name = "Jacket",
    properties = listOf(
        TypeProperty(
            "size",
            TypeDefinition.Enum(
                "Size",
                JsonType.NUMBER,
                listOf("1", "2", "3", "4")
            ),
            defaultValue = 1.0,
            optional = false
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

val Country = TypeDefinition.Class(
        name = "Country",
        properties = listOf(
                TypeProperty("name", TypeDefinition.Primitive(JsonPrimitiveType.STRING), true),
                TypeProperty("continent", TypeDefinition.Primitive(JsonPrimitiveType.STRING), true),
                TypeProperty("population", TypeDefinition.Primitive(JsonPrimitiveType.INTEGER), true)
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
        TypeProperty("delta", TypeDefinition.Constant(JsonType.NUMBER, 3.1415), true),
        TypeProperty(
            "id", TypeDefinition.Class(
                name = "Id",
                properties = listOf(
                    TypeProperty(
                        "serialNumber",
                        TypeDefinition.Constant(JsonType.NUMBER, 12112.0),
                        true
                    )
                )
            ),
            false
        ),
        TypeProperty(
            "date", TypeDefinition.Class(
                name = "Date",
                properties = listOf(
                    TypeProperty(
                        "year",
                        TypeDefinition.Constant(JsonType.INTEGER, 2021.0),
                        true
                    ),
                    TypeProperty(
                        "month",
                        TypeDefinition.Constant(JsonType.INTEGER, 3.0),
                        true
                    )
                )
            ),
            true
        )
    )
)

val Video = TypeDefinition.Class(
    name = "Video",
    properties = listOf(
        TypeProperty("title", TypeDefinition.Primitive(JsonPrimitiveType.STRING), false),
        TypeProperty(
            "tags",
            TypeDefinition.Array(
                TypeDefinition.Primitive(JsonPrimitiveType.STRING),
                uniqueItems = true
            ),
            true
        ),
        TypeProperty(
            "links",
            TypeDefinition.Array(
                TypeDefinition.Primitive(JsonPrimitiveType.STRING),
                uniqueItems = true
            ),
            true
        )
    )
)
