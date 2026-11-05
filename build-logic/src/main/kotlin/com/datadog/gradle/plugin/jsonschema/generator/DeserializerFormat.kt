/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema.generator

/** KotlinPoet format arguments: target type, deserializer name, and raw argument expression. */
internal const val DESERIALIZER_CALL_FORMAT = "%T.%L(%L)"
