package com.example.model

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.lang.IllegalStateException
import java.lang.NullPointerException
import java.lang.NumberFormatException
import kotlin.Long
import kotlin.String
import kotlin.collections.ArrayList
import kotlin.collections.List
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

/**
 * A flat list of tasks.
 */
public data class Tasks(
    public val items: List<Task>,
) {
    public fun toJson(): JsonElement {
        val jsonArray = JsonArray(items.size)
        items.forEach { jsonArray.add(it.toJson()) }
        return jsonArray
    }

    public companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJson(jsonString: String): Tasks {
            try {
                val jsonArray = JsonParser.parseString(jsonString).asJsonArray
                return fromJsonElement(jsonArray)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type Tasks",
                    e
                )
            }
        }

        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJsonElement(jsonElement: JsonElement): Tasks {
            try {
                val jsonArray = jsonElement.asJsonArray
                val collection = ArrayList<Task>(jsonArray.size())
                jsonArray.forEach {
                    collection.add(Task.fromJsonObject(it.asJsonObject))
                }
                return Tasks(collection)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type Tasks",
                    e
                )
            } catch (e: NumberFormatException) {
                throw JsonParseException(
                    "Unable to parse json into type Tasks",
                    e
                )
            } catch (e: NullPointerException) {
                throw JsonParseException(
                    "Unable to parse json into type Tasks",
                    e
                )
            }
        }
    }

    /**
     * A single task.
     * @param id Task identifier.
     * @param title Human-readable task title.
     * @param priority Optional priority.
     */
    public data class Task(
        public val id: String,
        public val title: String,
        public val priority: Long? = null,
    ) {
        public fun toJson(): JsonElement {
            val json = JsonObject()
            json.addProperty("id", id)
            json.addProperty("title", title)
            priority?.let { priorityNonNull ->
                json.addProperty("priority", priorityNonNull)
            }
            return json
        }

        public companion object {
            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJson(jsonString: String): Task {
                try {
                    val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                    return fromJsonObject(jsonObject)
                } catch (e: IllegalStateException) {
                    throw JsonParseException(
                        "Unable to parse json into type Task",
                        e
                    )
                }
            }

            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJsonObject(jsonObject: JsonObject): Task {
                try {
                    val id = jsonObject.get("id").asString
                    val title = jsonObject.get("title").asString
                    val priority = jsonObject.get("priority")?.asLong
                    return Task(id, title, priority)
                } catch (e: IllegalStateException) {
                    throw JsonParseException(
                        "Unable to parse json into type Task",
                        e
                    )
                } catch (e: NumberFormatException) {
                    throw JsonParseException(
                        "Unable to parse json into type Task",
                        e
                    )
                } catch (e: NullPointerException) {
                    throw JsonParseException(
                        "Unable to parse json into type Task",
                        e
                    )
                }
            }
        }
    }
}
