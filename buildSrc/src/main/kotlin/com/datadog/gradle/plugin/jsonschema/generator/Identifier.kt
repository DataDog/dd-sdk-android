/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema.generator

object Identifier {

    const val FUN_TO_JSON = "toJson"
    const val OBJECT_JSON_SERIALIZER = "JsonSerializer"
    const val FUN_TO_JSON_ELT = "toJsonElement"
    const val FUN_FROM_JSON = "fromJson"
    const val FUN_FROM_JSON_OBJ = "fromJsonObject"
    const val FUN_FROM_JSON_PRIMITIVE = "fromJsonPrimitive"
    const val FUN_FROM_JSON_ELEMENT = "fromJsonElement"

    const val PARAM_JSON_STR = "jsonString"
    const val PARAM_JSON_ARRAY = "jsonArray"
    const val PARAM_JSON_OBJ = "jsonObject"
    const val PARAM_JSON_ELEMENT = "jsonElement"
    const val PARAM_JSON_PRIMITIVE = "jsonPrimitive"
    const val PARAM_JSON_VALUE = "jsonValue"
    const val PARAM_ADDITIONAL_PROPS = "additionalProperties"
    const val PARAM_COLLECTION = "collection"

    const val PARAM_RESERVED_PROPS = "RESERVED_PROPERTIES"

    const val CAUGHT_EXCEPTION = "e"

    const val PACKAGE_UTILS = "com.datadog.android.core.internal.utils"
}
