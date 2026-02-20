/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.gradle.plugin.logsconfig

import org.yaml.snakeyaml.Yaml
import java.io.File

internal object LogsConfigYamlParser {

    fun parseLogs(file: File): List<LogEntry> {
        val root = loadYaml(file)

        @Suppress("UNCHECKED_CAST")
        val logsList = root["logs"] as? List<Map<String, Any>>
            ?: error("${file.name} must contain a 'logs' list at the root level")

        return logsList.map { parseSimpleLogEntry(it) }
    }

    fun parseMetrics(file: File): List<LogEntry> {
        val root = loadYaml(file)

        @Suppress("UNCHECKED_CAST")
        val metricsList = root["metrics"] as? List<Map<String, Any>>
            ?: error("${file.name} must contain a 'metrics' list at the root level")

        return metricsList.map { parseMetricLogEntry(it) }
    }

    private fun loadYaml(file: File): Map<String, Any> {
        val yaml = Yaml()
        return file.inputStream().use { yaml.load<Map<String, Any>>(it) }
    }

    private fun parseCommonFields(map: Map<String, Any>): CommonEntryFields {
        val id = map["id"] as? String
            ?: error("Each entry must have a string 'id'")
        val message = map["message"] as? String
            ?: error("Each entry must have a string 'message'")
        val onlyOnce = map["onlyOnce"] as? Boolean ?: false
        val throwable = map["throwable"] as? Boolean ?: false

        @Suppress("UNCHECKED_CAST")
        val propertiesMap = map["properties"] as? Map<String, Map<String, Any>>
            ?: emptyMap()
        val properties = propertiesMap.mapValues { (_, v) -> parsePropertyDefinition(v) }

        return CommonEntryFields(id, message, onlyOnce, throwable, properties)
    }

    private fun parseMetricLogEntry(map: Map<String, Any>): MetricLogEntry {
        val common = parseCommonFields(map)
        val sampleRate = parseSampleRateConfig(map["sampleRate"], entryId = common.id, fieldName = "sampleRate")
        val creationSampleRate = parseSampleRateConfig(
            map["creationSampleRate"],
            entryId = common.id,
            fieldName = "creationSampleRate"
        )
        return MetricLogEntry(
            id = common.id,
            message = common.message,
            sampleRate = sampleRate,
            creationSampleRate = creationSampleRate,
            onlyOnce = common.onlyOnce,
            throwable = common.throwable,
            properties = common.properties
        )
    }

    private fun parseSimpleLogEntry(map: Map<String, Any>): SimpleLogEntry {
        val common = parseCommonFields(map)
        val level = parseLogLevel(
            map["level"] as? String
                ?: error("Log entry '${common.id}' must have a 'level' field")
        )
        @Suppress("UNCHECKED_CAST")
        val targets = (map["targets"] as? List<String>
            ?: error("Log entry '${common.id}' must have a 'targets' list"))
            .map { parseLogTarget(it) }
        @Suppress("UNCHECKED_CAST")
        val messageArgsRaw = map["message_args"] as? Map<String, Map<String, Any>> ?: emptyMap()
        val messageArgs = messageArgsRaw.mapValues { (argName, argDef) ->
            val typeName = argDef["type"] as? String
                ?: error("message_arg '$argName' in '${common.id}' must have a 'type' field")
            val argNullable = argDef["nullable"] as? Boolean ?: false
            MessageArgDefinition(
                type = parsePrimitiveType(typeName),
                nullable = argNullable
            )
        }

        return SimpleLogEntry(
            id = common.id,
            message = common.message,
            level = level,
            targets = targets,
            messageArgs = messageArgs,
            onlyOnce = common.onlyOnce,
            throwable = common.throwable,
            properties = common.properties
        )
    }

    private data class CommonEntryFields(
        val id: String,
        val message: String,
        val onlyOnce: Boolean,
        val throwable: Boolean,
        val properties: Map<String, PropertyDefinition>
    )

    private fun parseSampleRateConfig(
        value: Any?,
        entryId: String,
        fieldName: String
    ): SampleRateConfig {
        return when (value) {
            null -> SampleRateConfig.Dynamic
            is Number -> SampleRateConfig.Fixed(value.toFloat())
            else -> error("'$fieldName' in '$entryId' must be a number (or omitted to generate a parameter)")
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
        val key = map["key"] as? String
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
                PropertyDefinition.EnumDef(values = values, nullable = nullable, key = key)
            }

            "object" -> {
                @Suppress("UNCHECKED_CAST")
                val properties = map["properties"] as? Map<String, Map<String, Any>>
                    ?: error("Object property must have a 'properties' map")
                PropertyDefinition.ObjectDef(
                    properties = properties.mapValues { (_, v) -> parsePropertyDefinition(v) },
                    nullable = nullable,
                    key = key
                )
            }

            "map" -> {
                val valueType = map["value_type"] as? String
                    ?: error("Map property must have a 'value_type' field")
                PropertyDefinition.MapDef(
                    valueType = parsePrimitiveType(valueType),
                    nullable = nullable,
                    key = key
                )
            }

            else -> PropertyDefinition.Primitive(
                type = parsePrimitiveType(type),
                nullable = nullable,
                key = key
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
