/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.gradle.plugin.logsconfig

internal data class LogsConfig(
    val logs: List<LogEntry>
)

internal data class LogEntry(
    val id: String,
    val message: String,
    val sampleRate: Float,
    val properties: Map<String, PropertyDefinition>
)

internal sealed class PropertyDefinition {

    abstract val nullable: Boolean

    data class Primitive(
        val type: PrimitiveType,
        override val nullable: Boolean = false
    ) : PropertyDefinition()

    data class Const(
        val type: PrimitiveType,
        val value: String
    ) : PropertyDefinition() {
        override val nullable: Boolean = false
    }

    data class EnumDef(
        val values: List<String>,
        override val nullable: Boolean = false
    ) : PropertyDefinition()

    data class ObjectDef(
        val properties: Map<String, PropertyDefinition>,
        override val nullable: Boolean = false
    ) : PropertyDefinition()
}

internal enum class PrimitiveType {
    STRING,
    INT,
    LONG,
    FLOAT,
    DOUBLE,
    BOOLEAN
}
