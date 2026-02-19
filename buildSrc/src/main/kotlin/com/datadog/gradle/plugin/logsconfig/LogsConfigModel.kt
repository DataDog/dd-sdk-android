/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.gradle.plugin.logsconfig

internal data class LogsConfig(
    val logs: List<LogEntry>
)

internal sealed class LogEntry {
    abstract val id: String
    abstract val message: String
    abstract val onlyOnce: Boolean
    abstract val throwable: Boolean
    abstract val properties: Map<String, PropertyDefinition>
}

internal data class MetricLogEntry(
    override val id: String,
    override val message: String,
    val sampleRate: SampleRateConfig,
    val creationSampleRate: SampleRateConfig? = null,
    override val onlyOnce: Boolean = false,
    override val throwable: Boolean = false,
    override val properties: Map<String, PropertyDefinition> = emptyMap()
) : LogEntry()

internal sealed class SampleRateConfig {
    data class Fixed(val value: Float) : SampleRateConfig()
    object Dynamic : SampleRateConfig()
}

internal data class SimpleLogEntry(
    override val id: String,
    override val message: String,
    val level: LogLevel,
    val targets: List<LogTarget>,
    override val onlyOnce: Boolean = false,
    override val throwable: Boolean = false,
    override val properties: Map<String, PropertyDefinition> = emptyMap()
) : LogEntry()

internal enum class LogLevel { ERROR, WARN, INFO, DEBUG, VERBOSE }

internal enum class LogTarget { USER, TELEMETRY, MAINTAINER }

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

    data class MapDef(
        val valueType: PrimitiveType,
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
