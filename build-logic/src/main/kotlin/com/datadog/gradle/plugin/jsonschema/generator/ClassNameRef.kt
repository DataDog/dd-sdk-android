/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema.generator

import com.squareup.kotlinpoet.ClassName

object ClassNameRef {
    val JsonArray = ClassName.bestGuess("com.google.gson.JsonArray")
    val JsonElement = ClassName.bestGuess("com.google.gson.JsonElement")
    val JsonNull = ClassName.bestGuess("com.google.gson.JsonNull")
    val JsonObject = ClassName.bestGuess("com.google.gson.JsonObject")
    val JsonParser = ClassName.bestGuess("com.google.gson.JsonParser")
    val JsonParseException = ClassName.bestGuess("com.google.gson.JsonParseException")
    val JsonPrimitive = ClassName.bestGuess("com.google.gson.JsonPrimitive")
    val IllegalStateException = ClassName.bestGuess("java.lang.IllegalStateException")
    val NumberFormatException = ClassName.bestGuess("java.lang.NumberFormatException")
    val NullPointerException = ClassName.bestGuess("java.lang.NullPointerException")
    val MutableList = ClassName.bestGuess("kotlin.collections.ArrayList")
    val MutableSet = ClassName.bestGuess("kotlin.collections.HashSet")
    val UnsupportedOperationException = ClassName.bestGuess("java.lang.UnsupportedOperationException")
}
