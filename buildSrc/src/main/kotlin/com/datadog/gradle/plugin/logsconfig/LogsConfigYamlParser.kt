/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.gradle.plugin.logsconfig

import org.yaml.snakeyaml.Yaml
import java.io.File

internal object LogsConfigYamlParser {

    fun parse(file: File): LogsConfig {
        val yaml = Yaml()
        val root = file.inputStream().use { yaml.load<Map<String, Any>>(it) }

        @Suppress("UNCHECKED_CAST")
        val logsList = root["logs"] as? List<Map<String, Any>>
            ?: error("logs_config.yaml must contain a 'logs' list at the root level")

        return LogsConfig(logs = logsList.map { parseLogEntry(it) })
    }

    private fun parseLogEntry(map: Map<String, Any>): LogEntry {
        val id = map["id"] as? String
            ?: error("Each log entry must have a string 'id'")
        val message = map["message"] as? String
            ?: error("Each log entry must have a string 'message'")
        val type = map["type"] as? String ?: "metric"
        val onlyOnce = map["onlyOnce"] as? Boolean ?: false
        val throwable = map["throwable"] as? Boolean ?: false

        @Suppress("UNCHECKED_CAST")
        val propertiesMap = map["properties"] as? Map<String, Map<String, Any>>
            ?: emptyMap()
        val properties = propertiesMap.mapValues { (_, v) -> parsePropertyDefinition(v) }

        return when (type) {
            "metric" -> {
                val sampleRate = parseSampleRateConfig(map["sampleRate"], required = true, entryId = id)!!
                val creationSampleRate = parseSampleRateConfig(
                    map["creationSampleRate"],
                    required = false,
                    entryId = id
                )
                MetricLogEntry(
                    id = id,
                    message = message,
                    sampleRate = sampleRate,
                    creationSampleRate = creationSampleRate,
                    onlyOnce = onlyOnce,
                    throwable = throwable,
                    properties = properties
                )
            }

            "log" -> {
                val level = parseLogLevel(
                    map["level"] as? String
                        ?: error("Log entry '$id' must have a 'level' field")
                )
                @Suppress("UNCHECKED_CAST")
                val targets = (map["targets"] as? List<String>
                    ?: error("Log entry '$id' must have a 'targets' list"))
                    .map { parseLogTarget(it) }

                SimpleLogEntry(
                    id = id,
                    message = message,
                    level = level,
                    targets = targets,
                    onlyOnce = onlyOnce,
                    throwable = throwable,
                    properties = properties
                )
            }

            else -> error("Unknown log type: '$type'. Supported: metric, log")
        }
    }

    private fun parseSampleRateConfig(
        value: Any?,
        required: Boolean,
        entryId: String
    ): SampleRateConfig? {
        return when (value) {
            null -> if (required) error("Metric log entry '$entryId' must have a 'sampleRate'") else null
            is Number -> SampleRateConfig.Fixed(value.toFloat())
            is Boolean -> if (value) SampleRateConfig.Dynamic else null
            else -> error("'sampleRate'/'creationSampleRate' in '$entryId' must be a number or true")
        }
    }

    private fun parseLogLevel(level: String): LogLevel = when (level) {
        "error" -> LogLevel.ERROR
        "warn" -> LogLevel.WARN
        "info" -> LogLevel.INFO
        "debug" -> LogLevel.DEBUG
        "verbose" -> LogLevel.VERBOSE
        else -> error("Unknown log level: '$level'. Supported: error, warn, info, debug, verbose")
    }

    private fun parseLogTarget(target: String): LogTarget = when (target) {
        "user" -> LogTarget.USER
        "telemetry" -> LogTarget.TELEMETRY
        "maintainer" -> LogTarget.MAINTAINER
        else -> error("Unknown log target: '$target'. Supported: user, telemetry, maintainer")
    }

    private fun parsePropertyDefinition(map: Map<String, Any>): PropertyDefinition {
        val type = map["type"] as? String
            ?: error("Each property must have a 'type' field")
        val nullable = map["nullable"] as? Boolean ?: false
        val constRawValue = map["const"]

        if (constRawValue != null) {
            return PropertyDefinition.Const(
                type = parsePrimitiveType(type),
                value = constRawValue.toString()
            )
        }

        return when (type) {
            "enum" -> {
                @Suppress("UNCHECKED_CAST")
                val values = map["values"] as? List<String>
                    ?: error("Enum property must have a 'values' list")
                PropertyDefinition.EnumDef(values = values, nullable = nullable)
            }

            "object" -> {
                @Suppress("UNCHECKED_CAST")
                val properties = map["properties"] as? Map<String, Map<String, Any>>
                    ?: error("Object property must have a 'properties' map")
                PropertyDefinition.ObjectDef(
                    properties = properties.mapValues { (_, v) -> parsePropertyDefinition(v) },
                    nullable = nullable
                )
            }

            "map" -> {
                val valueType = map["value_type"] as? String
                    ?: error("Map property must have a 'value_type' field")
                PropertyDefinition.MapDef(
                    valueType = parsePrimitiveType(valueType),
                    nullable = nullable
                )
            }

            else -> PropertyDefinition.Primitive(
                type = parsePrimitiveType(type),
                nullable = nullable
            )
        }
    }

    private fun parsePrimitiveType(type: String): PrimitiveType = when (type) {
        "string" -> PrimitiveType.STRING
        "int" -> PrimitiveType.INT
        "long" -> PrimitiveType.LONG
        "float" -> PrimitiveType.FLOAT
        "double" -> PrimitiveType.DOUBLE
        "boolean" -> PrimitiveType.BOOLEAN
        else -> error("Unknown primitive type: '$type'. Supported: string, int, long, float, double, boolean")
    }
}
