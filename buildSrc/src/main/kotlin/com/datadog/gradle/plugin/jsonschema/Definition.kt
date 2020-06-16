/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema

import com.google.gson.annotations.SerializedName

data class Definition(
    @SerializedName("title") val title: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("type") val type: Type?,
    @SerializedName("enum") val enum: List<String>?,
    @SerializedName("const") val constant: String?,
    @SerializedName("\$ref") val ref: String?,
    @SerializedName("\$id") val id: String?,
    @SerializedName("required") val required: List<String>?,
    @SerializedName("uniqueItems") val uniqueItems: Boolean?,
    @SerializedName("items") val items: Definition?,
    @SerializedName("allOf") val allOf: List<Definition>?,
    @SerializedName("properties") val properties: Map<String, Definition>?,
    @SerializedName("definitions") val definitions: Map<String, Definition>?
)

enum class Type {
    @SerializedName("null")
    NULL,

    @SerializedName("boolean")
    BOOLEAN,

    @SerializedName("object")
    OBJECT,

    @SerializedName("array")
    ARRAY,

    @SerializedName("number")
    NUMBER,

    @SerializedName("string")
    STRING,

    @SerializedName("integer")
    INTEGER
}
