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
        val sampleRate = (map["sampleRate"] as? Number)?.toFloat()
            ?: error("Each log entry must have a numeric 'sampleRate'")

        @Suppress("UNCHECKED_CAST")
        val propertiesMap = map["properties"] as? Map<String, Map<String, Any>>
            ?: emptyMap()

        return LogEntry(
            id = id,
            message = message,
            sampleRate = sampleRate,
            properties = propertiesMap.mapValues { (_, v) -> parsePropertyDefinition(v) }
        )
    }

    private fun parsePropertyDefinition(map: Map<String, Any>): PropertyDefinition {
        val type = map["type"] as? String
            ?: error("Each property must have a 'type' field")
        val nullable = map["nullable"] as? Boolean ?: false
        val constValue = map["const"] as? String

        if (constValue != null) {
            return PropertyDefinition.Const(
                type = parsePrimitiveType(type),
                value = constValue
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
