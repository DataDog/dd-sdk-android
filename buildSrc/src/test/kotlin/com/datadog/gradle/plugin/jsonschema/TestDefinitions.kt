/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema

val Address = TypeDefinition.Class(
    name = "Address",
    required = setOf("street_address", "city", "state"),
    properties = listOf(
        TypeProperty(
            name = "street_address",
            type = TypeDefinition.Primitive(JsonPrimitiveType.STRING)
        ),
        TypeProperty(
            name = "city",
            type = TypeDefinition.Primitive(JsonPrimitiveType.STRING)
        ),
        TypeProperty(
            name = "state",
            type = TypeDefinition.Primitive(JsonPrimitiveType.STRING)
        )
    )
)

val Animal = TypeDefinition.OneOfClass(
    name = "Animal",
    options = listOf(
        TypeDefinition.Class(
            name = "Fish",
            required = setOf("water"),
            properties = listOf(
                TypeProperty(
                    name = "water",
                    type = TypeDefinition.Enum("Water", JsonType.STRING, listOf("salt", "fresh"))
                ),
                TypeProperty(
                    name = "size",
                    type = TypeDefinition.Primitive(JsonPrimitiveType.INTEGER)
                )
            )
        ),
        TypeDefinition.Class(
            name = "Bird",
            required = setOf("food", "can_fly"),
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
                    )
                ),
                TypeProperty(
                    name = "can_fly",
                    type = TypeDefinition.Primitive(JsonPrimitiveType.BOOLEAN)
                )
            )
        )
    ).map { TypeDefinition.OneOfClass.Option.Class(it) },
    description = "A representation of the animal kingdom"
)

val Article = TypeDefinition.Class(
    name = "Article",
    required = setOf("title", "authors"),
    properties = listOf(
        TypeProperty(name = "title", type = TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
        TypeProperty(
            name = "tags",
            type = TypeDefinition.Array(TypeDefinition.Primitive(JsonPrimitiveType.STRING))
        ),
        TypeProperty(
            name = "authors",
            type = TypeDefinition.Array(TypeDefinition.Primitive(JsonPrimitiveType.STRING))
        )
    )
)

val Book = TypeDefinition.Class(
    name = "Book",
    required = setOf("bookId", "title", "price", "author"),
    properties = listOf(
        TypeProperty("bookId", TypeDefinition.Primitive(JsonPrimitiveType.INTEGER)),
        TypeProperty("title", TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
        TypeProperty("price", TypeDefinition.Primitive(JsonPrimitiveType.NUMBER)),
        TypeProperty(
            "author",
            TypeDefinition.Class(
                name = "Author",
                required = setOf("firstName", "lastName", "contact"),
                properties = listOf(
                    TypeProperty(
                        "firstName",
                        TypeDefinition.Primitive(JsonPrimitiveType.STRING)
                    ),
                    TypeProperty(
                        "lastName",
                        TypeDefinition.Primitive(JsonPrimitiveType.STRING)
                    ),
                    TypeProperty(
                        "contact",
                        TypeDefinition.Class(
                            name = "Contact",
                            required = emptySet(),
                            properties = listOf(
                                TypeProperty(
                                    "phone",
                                    TypeDefinition.Primitive(JsonPrimitiveType.STRING)
                                ),
                                TypeProperty(
                                    "email",
                                    TypeDefinition.Primitive(JsonPrimitiveType.STRING)
                                )
                            )
                        )
                    )
                )
            )
        )
    )
)

val Customer = TypeDefinition.Class(
    name = "Customer",
    required = emptySet(),
    properties = listOf(
        TypeProperty("name", TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
        TypeProperty("billing_address", Address),
        TypeProperty("shipping_address", Address)
    )
)

val Comment = TypeDefinition.Class(
    name = "Comment",
    required = emptySet(),
    properties = listOf(
        TypeProperty("message", TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
        TypeProperty(
            "ratings",
            TypeDefinition.Class(
                name = "Ratings",
                required = setOf("global"),
                properties = listOf(
                    TypeProperty(
                        "global",
                        TypeDefinition.Primitive(JsonPrimitiveType.INTEGER)
                    )
                ),
                additionalProperties = TypeProperty(
                    name = "",
                    type = TypeDefinition.Primitive(JsonPrimitiveType.INTEGER),
                    readOnly = true
                )
            )
        ),
        TypeProperty(
            "flags",
            TypeDefinition.Class(
                name = "Flags",
                required = emptySet(),
                properties = listOf(),
                additionalProperties = TypeProperty(
                    name = "",
                    type = TypeDefinition.Primitive(JsonPrimitiveType.BOOLEAN),
                    readOnly = false
                )
            )
        ),
        TypeProperty(
            "tags",
            TypeDefinition.Class(
                name = "Tags",
                required = emptySet(),
                properties = listOf(),
                additionalProperties = TypeProperty(
                    name = "",
                    type = TypeDefinition.Primitive(JsonPrimitiveType.STRING),
                    readOnly = false
                )
            )
        )
    )
)

val Company = TypeDefinition.Class(
    name = "Company",
    required = emptySet(),
    properties = listOf(
        TypeProperty("name", TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
        TypeProperty(
            "ratings",
            TypeDefinition.Class(
                name = "Ratings",
                required = setOf("global"),
                properties = listOf(
                    TypeProperty(
                        "global",
                        TypeDefinition.Primitive(JsonPrimitiveType.INTEGER)
                    )
                ),
                additionalProperties = TypeProperty(
                    name = "",
                    type = TypeDefinition.Primitive(JsonPrimitiveType.INTEGER),
                    readOnly = false
                )
            )
        ),
        TypeProperty(
            "information",
            TypeDefinition.Class(
                name = "Information",
                required = emptySet(),
                properties = listOf(
                    TypeProperty(
                        "date",
                        TypeDefinition.Primitive(JsonPrimitiveType.INTEGER)
                    ),
                    TypeProperty(
                        "priority",
                        TypeDefinition.Primitive(JsonPrimitiveType.INTEGER)
                    )
                ),
                additionalProperties = TypeProperty(
                    name = "",
                    type = TypeDefinition.Class("?", emptyList(), emptySet()),
                    readOnly = false
                )
            )
        )
    ),
    additionalProperties = TypeProperty(
        name = "",
        type = TypeDefinition.Class("?", emptyList(), emptySet()),
        readOnly = false
    )
)

val Conflict = TypeDefinition.Class(
    name = "Conflict",
    required = emptySet(),
    properties = listOf(
        TypeProperty(
            "type",
            TypeDefinition.Class(
                name = "ConflictType",
                required = emptySet(),
                properties = listOf(
                    TypeProperty("id", TypeDefinition.Primitive(JsonPrimitiveType.STRING))
                )
            )
        ),
        TypeProperty(
            "user",
            TypeDefinition.Class(
                name = "User",
                required = emptySet(),
                properties = listOf(
                    TypeProperty("name", TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
                    TypeProperty(
                        "type",
                        TypeDefinition.Enum(
                            name = "UserType",
                            type = JsonType.STRING,
                            values = listOf("unknown", "customer", "partner")
                        )
                    )
                )
            )
        )
    )
)

val DateTime = TypeDefinition.Class(
    name = "DateTime",
    required = emptySet(),
    properties = listOf(
        TypeProperty(
            "date",
            TypeDefinition.Class(
                name = "Date",
                required = emptySet(),
                properties = listOf(
                    TypeProperty("year", TypeDefinition.Primitive(JsonPrimitiveType.INTEGER)),
                    TypeProperty(
                        "month",
                        TypeDefinition.Enum(
                            "Month",
                            JsonType.STRING,
                            listOf(
                                "jan", "feb", "mar", "apr", "may", "jun",
                                "jul", "aug", "sep", "oct", "nov", "dec"
                            )
                        )
                    ),
                    TypeProperty("day", TypeDefinition.Primitive(JsonPrimitiveType.INTEGER))
                )
            )
        ),
        TypeProperty(
            "time",
            TypeDefinition.Class(
                name = "Time",
                required = emptySet(),
                properties = listOf(
                    TypeProperty("hour", TypeDefinition.Primitive(JsonPrimitiveType.INTEGER)),
                    TypeProperty(
                        "minute",
                        TypeDefinition.Primitive(JsonPrimitiveType.INTEGER)
                    ),
                    TypeProperty(
                        "seconds",
                        TypeDefinition.Primitive(JsonPrimitiveType.INTEGER)
                    )
                )
            )
        )
    )
)
val Demo = TypeDefinition.Class(
    name = "Demo",
    required = setOf("s", "i", "n", "b", "l"),
    properties = listOf(
        TypeProperty("s", TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
        TypeProperty("i", TypeDefinition.Primitive(JsonPrimitiveType.INTEGER)),
        TypeProperty("n", TypeDefinition.Primitive(JsonPrimitiveType.NUMBER)),
        TypeProperty("b", TypeDefinition.Primitive(JsonPrimitiveType.BOOLEAN)),
        TypeProperty("l", TypeDefinition.Null()),
        TypeProperty("ns", TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
        TypeProperty("ni", TypeDefinition.Primitive(JsonPrimitiveType.INTEGER)),
        TypeProperty("nn", TypeDefinition.Primitive(JsonPrimitiveType.NUMBER)),
        TypeProperty("nb", TypeDefinition.Primitive(JsonPrimitiveType.BOOLEAN)),
        TypeProperty("nl", TypeDefinition.Null())

    )
)

val Delivery = TypeDefinition.Class(
    name = "Delivery",
    required = setOf("item", "customer"),
    properties = listOf(
        TypeProperty("item", TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
        TypeProperty(
            "customer",
            TypeDefinition.Class(
                name = "Customer",
                required = emptySet(),
                properties = listOf(
                    TypeProperty("name", TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
                    TypeProperty("billing_address", Address),
                    TypeProperty("shipping_address", Address)
                )
            )
        )
    )
)

val Employee = TypeDefinition.Class(
    name = "Employee",
    required = emptySet(),
    properties = listOf(
        TypeProperty("name", TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
        TypeProperty(
            name = "contact",
            type = TypeDefinition.Class(
                name = "Contact",
                required = setOf("phone", "address"),
                properties = listOf(
                    TypeProperty(
                        "phone",
                        TypeDefinition.Primitive(JsonPrimitiveType.STRING)
                    ),
                    TypeProperty("address", Address)
                )
            )
        )
    )
)

val Foo = TypeDefinition.Class(
    name = "Foo",
    required = emptySet(),
    properties = listOf(
        TypeProperty("bar", TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
        TypeProperty("baz", TypeDefinition.Primitive(JsonPrimitiveType.INTEGER))
    )
)

val Location = TypeDefinition.Class(
    name = "Location",
    required = setOf("planet", "solar_system"),
    properties = listOf(
        TypeProperty("planet", TypeDefinition.Constant(JsonType.STRING, "earth")),
        TypeProperty("solar_system", TypeDefinition.Constant(JsonType.STRING, "sol"))
    )
)

val Message = TypeDefinition.Class(
    name = "Message",
    required = setOf("destination", "origin"),
    properties = listOf(
        TypeProperty(
            "destination",
            TypeDefinition.Array(TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
            readOnly = true
        ),
        TypeProperty(
            "origin",
            TypeDefinition.Primitive(JsonPrimitiveType.STRING),
            readOnly = true
        ),
        TypeProperty(
            "subject",
            TypeDefinition.Primitive(JsonPrimitiveType.STRING),
            readOnly = true
        ),
        TypeProperty(
            "message",
            TypeDefinition.Primitive(JsonPrimitiveType.STRING),
            readOnly = true
        ),
        TypeProperty(
            "labels",
            TypeDefinition.Array(TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
            readOnly = false
        ),
        TypeProperty(
            "read",
            TypeDefinition.Primitive(JsonPrimitiveType.BOOLEAN),
            readOnly = false
        ),
        TypeProperty(
            "important",
            TypeDefinition.Primitive(JsonPrimitiveType.BOOLEAN),
            readOnly = false
        )
    )
)

val Opus = TypeDefinition.Class(
    name = "Opus",
    description = "A musical opus.",
    required = emptySet(),
    properties = listOf(
        TypeProperty(
            "title",
            TypeDefinition.Primitive(JsonPrimitiveType.STRING, "The opus's title.")
        ),
        TypeProperty(
            "composer",
            TypeDefinition.Primitive(JsonPrimitiveType.STRING, "The opus's composer.")
        ),
        TypeProperty(
            "artists",
            TypeDefinition.Array(
                TypeDefinition.Class(
                    name = "Artist",
                    description = "An artist and their role in an opus.",
                    required = emptySet(),
                    properties = listOf(
                        TypeProperty(
                            "name",
                            TypeDefinition.Primitive(
                                JsonPrimitiveType.STRING,
                                "The artist's name."
                            )
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
                            )
                        )
                    )
                ),
                description = "The opus's artists."
            )
        ),
        TypeProperty(
            "duration",
            TypeDefinition.Primitive(JsonPrimitiveType.INTEGER, "The opus's duration in seconds")
        )
    )
)

val Person = TypeDefinition.Class(
    name = "Person",
    required = emptySet(),
    properties = listOf(
        TypeProperty("firstName", TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
        TypeProperty("lastName", TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
        TypeProperty("age", TypeDefinition.Primitive(JsonPrimitiveType.INTEGER))
    )
)

val Product = TypeDefinition.Class(
    name = "Product",
    required = setOf("productId", "productName", "price"),
    properties = listOf(
        TypeProperty("productId", TypeDefinition.Primitive(JsonPrimitiveType.INTEGER)),
        TypeProperty("productName", TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
        TypeProperty("price", TypeDefinition.Primitive(JsonPrimitiveType.NUMBER))
    )
)

val Paper = TypeDefinition.Class(
    name = "Paper",
    required = setOf("title", "author"),
    properties = listOf(
        TypeProperty("title", TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
        TypeProperty(
            "author",
            TypeDefinition.Array(TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
            readOnly = true
        )
    )
)

val Bike = TypeDefinition.Class(
    name = "Bike",
    required = setOf("productId", "productName", "price", "inStock", "color"),
    properties = listOf(
        TypeProperty(
            "productId",
            TypeDefinition.Primitive(JsonPrimitiveType.INTEGER),
            defaultValue = 1.0
        ),
        TypeProperty(
            "productName",
            TypeDefinition.Primitive(JsonPrimitiveType.STRING)
        ),
        TypeProperty(
            "type",
            TypeDefinition.Primitive(JsonPrimitiveType.STRING),
            defaultValue = "road"
        ),
        TypeProperty(
            "price",
            TypeDefinition.Primitive(JsonPrimitiveType.NUMBER),
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
            defaultValue = "light_aluminium"
        ),
        TypeProperty(
            "inStock",
            TypeDefinition.Primitive(JsonPrimitiveType.BOOLEAN),
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
            defaultValue = "lime green"
        )
    )
)

val Shipping = TypeDefinition.Class(
    name = "Shipping",
    required = setOf("item", "destination"),
    properties = listOf(
        TypeProperty("item", TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
        TypeProperty("destination", Address)
    )
)

val Style = TypeDefinition.Class(
    name = "Style",
    required = setOf("color"),
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
            )
        )
    )
)

val Household = TypeDefinition.Class(
    name = "Household",
    required = emptySet(),
    properties = listOf(
        TypeProperty(
            name = "pets",
            type = TypeDefinition.Array(
                items = TypeDefinition.OneOfClass(
                    name = "Animal",
                    options = listOf(
                        TypeDefinition.Class(
                            name = "Fish",
                            required = setOf("water"),
                            properties = listOf(
                                TypeProperty(
                                    name = "water",
                                    type = TypeDefinition.Enum(
                                        "Water",
                                        JsonType.STRING,
                                        listOf("salt", "fresh")
                                    )
                                ),
                                TypeProperty(
                                    name = "size",
                                    type = TypeDefinition.Primitive(JsonPrimitiveType.INTEGER)
                                )
                            )
                        ),
                        TypeDefinition.Class(
                            name = "Bird",
                            required = setOf("food", "can_fly"),
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
                                    )
                                ),
                                TypeProperty("can_fly", TypeDefinition.Primitive(JsonPrimitiveType.BOOLEAN))
                            )
                        )
                    ).map { TypeDefinition.OneOfClass.Option.Class(it) },
                    description = "A representation of the animal kingdom"
                )
            )
        ),
        TypeProperty(
            name = "situation",
            type = TypeDefinition.OneOfClass(
                name = "Situation",
                options = listOf(
                    TypeDefinition.OneOfClass.Option.Class(
                        TypeDefinition.Class(
                            name = "Marriage",
                            required = setOf("spouses"),
                            properties = listOf(
                                TypeProperty(
                                    name = "spouses",
                                    type = TypeDefinition.Array(
                                        items = TypeDefinition.Primitive(JsonPrimitiveType.STRING)
                                    )
                                )
                            )
                        )
                    ),
                    TypeDefinition.OneOfClass.Option.Class(
                        TypeDefinition.Class(
                            name = "Cotenancy",
                            required = setOf("roommates"),
                            properties = listOf(
                                TypeProperty(
                                    name = "roommates",
                                    type = TypeDefinition.Array(
                                        items = TypeDefinition.Primitive(JsonPrimitiveType.STRING)
                                    )
                                )
                            )
                        )
                    ),
                    TypeDefinition.OneOfClass.Option.Primitive(
                        primitive = TypeDefinition.Primitive(type = JsonPrimitiveType.INTEGER)
                    )
                )
            )
        )
    )
)

val Jacket = TypeDefinition.Class(
    name = "Jacket",
    required = setOf("size"),
    properties = listOf(
        TypeProperty(
            "size",
            TypeDefinition.Enum(
                "Size",
                JsonType.NUMBER,
                listOf("1", "2", "3", "4")
            ),
            defaultValue = 1.0,
            readOnly = true
        )
    )
)

val Order = TypeDefinition.Class(
    name = "Order",
    required = setOf("sizes"),
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
            )
        )
    )
)

val User = TypeDefinition.Class(
    name = "User",
    required = setOf("username", "host", "lastname", "contact_type"),
    properties = listOf(
        TypeProperty("username", TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
        TypeProperty("host", TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
        TypeProperty("firstname", TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
        TypeProperty("lastname", TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
        TypeProperty(
            "contact_type",
            TypeDefinition.Enum(
                name = "ContactType",
                type = null,
                values = listOf("personal", "professional")
            )
        )
    )
)

val Country = TypeDefinition.Class(
    name = "Country",
    required = emptySet(),
    properties = listOf(
        TypeProperty("name", TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
        TypeProperty("continent", TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
        TypeProperty("population", TypeDefinition.Primitive(JsonPrimitiveType.INTEGER))
    )
)

// both items have additionalProperties explicitly defined
val AdditionalPropsMerged = TypeDefinition.Class(
    name = "AdditionalPropsMerged",
    required = setOf("lastname"),
    properties = listOf(
        TypeProperty("email", TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
        TypeProperty("phone", TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
        TypeProperty(
            "info",
            TypeDefinition.Class(
                name = "Info",
                required = emptySet(),
                properties = listOf(
                    TypeProperty("notes", TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
                    TypeProperty("source", TypeDefinition.Primitive(JsonPrimitiveType.STRING))
                ),
                additionalProperties = TypeProperty(
                    name = "",
                    type = TypeDefinition.Class("?", emptyList(), emptySet()),
                    readOnly = false
                )
            )
        ),
        TypeProperty("firstname", TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
        TypeProperty("lastname", TypeDefinition.Primitive(JsonPrimitiveType.STRING))
    )
)

// only one item has additionalProperties explicitly defined
val AdditionalPropsSingleMerge = TypeDefinition.Class(
    name = "AdditionalPropsSingleMerge",
    required = setOf("lastname"),
    properties = listOf(
        TypeProperty("email", TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
        TypeProperty("phone", TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
        TypeProperty(
            "info",
            TypeDefinition.Class(
                name = "Info",
                required = emptySet(),
                properties = listOf(
                    TypeProperty("notes", TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
                    TypeProperty("source", TypeDefinition.Primitive(JsonPrimitiveType.STRING))
                ),
                additionalProperties = TypeProperty(
                    name = "",
                    type = TypeDefinition.Class("?", emptyList(), emptySet()),
                    readOnly = false
                )
            )
        ),
        TypeProperty("firstname", TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
        TypeProperty("lastname", TypeDefinition.Primitive(JsonPrimitiveType.STRING))
    )
)

val UserMerged = TypeDefinition.Class(
    name = "UserMerged",
    required = setOf("lastname"),
    properties = listOf(
        TypeProperty("email", TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
        TypeProperty("phone", TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
        TypeProperty(
            "info",
            TypeDefinition.Class(
                name = "Info",
                required = emptySet(),
                properties = listOf(
                    TypeProperty("notes", TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
                    TypeProperty("source", TypeDefinition.Primitive(JsonPrimitiveType.STRING))
                )
            )
        ),
        TypeProperty("firstname", TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
        TypeProperty("lastname", TypeDefinition.Primitive(JsonPrimitiveType.STRING))
    )
)

val Version = TypeDefinition.Class(
    name = "Version",
    required = setOf("major", "id"),
    properties = listOf(
        TypeProperty("major", TypeDefinition.Constant(JsonType.INTEGER, 42.0)),
        TypeProperty("delta", TypeDefinition.Constant(JsonType.NUMBER, 3.1415)),
        TypeProperty(
            "id",
            TypeDefinition.Class(
                name = "Id",
                required = emptySet(),
                properties = listOf(
                    TypeProperty(
                        "serialNumber",
                        TypeDefinition.Constant(JsonType.NUMBER, 12112.0)
                    )
                )
            )
        ),
        TypeProperty(
            "date",
            TypeDefinition.Class(
                name = "Date",
                required = emptySet(),
                properties = listOf(
                    TypeProperty(
                        "year",
                        TypeDefinition.Constant(JsonType.INTEGER, 2021.0)
                    ),
                    TypeProperty(
                        "month",
                        TypeDefinition.Constant(JsonType.INTEGER, 3.0)
                    )
                )
            )
        )
    )
)

val Video = TypeDefinition.Class(
    name = "Video",
    required = setOf("title"),
    properties = listOf(
        TypeProperty("title", TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
        TypeProperty(
            "tags",
            TypeDefinition.Array(
                TypeDefinition.Primitive(JsonPrimitiveType.STRING),
                uniqueItems = true
            )
        ),
        TypeProperty(
            "links",
            TypeDefinition.Array(
                TypeDefinition.Primitive(JsonPrimitiveType.STRING),
                uniqueItems = true
            )
        )
    )
)

val WeirdCombo = TypeDefinition.Class(
    name = "WeirdCombo",
    required = emptySet(),
    properties = listOf(
        TypeProperty(
            name = "anything",
            type = TypeDefinition.OneOfClass(
                name = "Anything",
                options = listOf(
                    TypeDefinition.Class(
                        name = "Fish",
                        required = setOf("water"),
                        properties = listOf(
                            TypeProperty(
                                name = "water",
                                type = TypeDefinition.Enum("Water", JsonType.STRING, listOf("salt", "fresh")),
                                readOnly = true
                            ),
                            TypeProperty(
                                name = "size",
                                type = TypeDefinition.Primitive(JsonPrimitiveType.INTEGER)
                            )
                        )
                    ),
                    TypeDefinition.Class(
                        name = "Bird",
                        required = setOf("food", "can_fly"),
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
                                )
                            ),
                            TypeProperty("can_fly", TypeDefinition.Primitive(JsonPrimitiveType.BOOLEAN))
                        )
                    ),
                    TypeDefinition.Class(
                        name = "Paper",
                        required = setOf("title", "author"),
                        properties = listOf(
                            TypeProperty("title", TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
                            TypeProperty(
                                "author",
                                TypeDefinition.Array(TypeDefinition.Primitive(JsonPrimitiveType.STRING))
                            )
                        )
                    )
                ).map { TypeDefinition.OneOfClass.Option.Class(it) }
            )
        )
    )
)

val RequiredForOtherAllOf = TypeDefinition.Class(
    name = "RequiredForOtherAllOf",
    required = setOf("key_1", "key_2"),
    properties = listOf(
        TypeProperty(name = "key_1", type = TypeDefinition.Primitive(JsonPrimitiveType.STRING)),
        TypeProperty(name = "key_2", type = TypeDefinition.Primitive(JsonPrimitiveType.STRING))
    )
)

val PathArrayWithInteger = TypeDefinition.Class(
    name = "PathArrayWithInteger",
    properties = listOf(
        TypeProperty(
            name = "path",
            type = TypeDefinition.Array(
                items = TypeDefinition.OneOfClass(
                    name = "Path",
                    options = listOf(
                        TypeDefinition.OneOfClass.Option.Primitive(
                            primitive = TypeDefinition.Primitive(
                                type = JsonPrimitiveType.BOOLEAN,
                                description = "boolean element"
                            )
                        ),
                        TypeDefinition.OneOfClass.Option.Primitive(
                            primitive = TypeDefinition.Primitive(
                                type = JsonPrimitiveType.STRING,
                                description = "string element"
                            )
                        ),
                        TypeDefinition.OneOfClass.Option.Primitive(
                            primitive = TypeDefinition.Primitive(
                                type = JsonPrimitiveType.INTEGER,
                                description = "integer element"
                            )
                        ),
                        TypeDefinition.OneOfClass.Option.Class(
                            cls = TypeDefinition.Class(
                                name = "Point",
                                properties = listOf(
                                    TypeProperty(
                                        name = "x",
                                        type = TypeDefinition.Primitive(JsonPrimitiveType.INTEGER),
                                        readOnly = true
                                    ),
                                    TypeProperty(
                                        name = "y",
                                        type = TypeDefinition.Primitive(JsonPrimitiveType.INTEGER),
                                        readOnly = true
                                    )
                                ),
                                required = setOf("x", "y"),
                                description = "object element"
                            )
                        )
                    ),
                    description = "This is a definition of a path"
                ),
                uniqueItems = false
            ),
            readOnly = true
        )
    ),
    required = setOf("path")
)

val PathArrayWithNumber = TypeDefinition.Class(
    name = "PathArrayWithNumber",
    properties = listOf(
        TypeProperty(
            name = "path",
            type = TypeDefinition.Array(
                items = TypeDefinition.OneOfClass(
                    name = "Path",
                    options = listOf(
                        TypeDefinition.OneOfClass.Option.Primitive(
                            primitive = TypeDefinition.Primitive(
                                type = JsonPrimitiveType.BOOLEAN,
                                description = "boolean element"
                            )
                        ),
                        TypeDefinition.OneOfClass.Option.Primitive(
                            primitive = TypeDefinition.Primitive(
                                type = JsonPrimitiveType.STRING,
                                description = "string element"
                            )
                        ),
                        TypeDefinition.OneOfClass.Option.Primitive(
                            primitive = TypeDefinition.Primitive(
                                type = JsonPrimitiveType.NUMBER,
                                description = "number element"
                            )
                        ),
                        TypeDefinition.OneOfClass.Option.Class(
                            cls = TypeDefinition.Class(
                                name = "Point",
                                properties = listOf(
                                    TypeProperty(
                                        name = "x",
                                        type = TypeDefinition.Primitive(JsonPrimitiveType.INTEGER),
                                        readOnly = true
                                    ),
                                    TypeProperty(
                                        name = "y",
                                        type = TypeDefinition.Primitive(JsonPrimitiveType.INTEGER),
                                        readOnly = true
                                    )
                                ),
                                required = setOf("x", "y"),
                                description = "object element"
                            )
                        )
                    ),
                    description = "This is a definition of a path"
                ),
                uniqueItems = false
            ),
            readOnly = true
        )
    ),
    required = setOf("path")
)
