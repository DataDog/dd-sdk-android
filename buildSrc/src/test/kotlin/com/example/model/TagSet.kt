package com.example.model

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.lang.IllegalStateException
import java.lang.NullPointerException
import java.lang.NumberFormatException
import kotlin.String
import kotlin.collections.HashSet
import kotlin.collections.Set
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

public data class TagSet(
    public val items: Set<Tag>,
) {
    public fun toJson(): JsonElement {
        val jsonArray = JsonArray(items.size)
        items.forEach { jsonArray.add(it.toJson()) }
        return jsonArray
    }

    public companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJson(jsonString: String): TagSet {
            try {
                val jsonArray = JsonParser.parseString(jsonString).asJsonArray
                return fromJsonElement(jsonArray)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type TagSet",
                    e
                )
            }
        }

        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJsonElement(jsonElement: JsonElement): TagSet {
            try {
                val jsonArray = jsonElement.asJsonArray
                val collection = HashSet<Tag>(jsonArray.size())
                jsonArray.forEach {
                    collection.add(Tag.fromJsonObject(it.asJsonObject))
                }
                return TagSet(collection)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type TagSet",
                    e
                )
            } catch (e: NumberFormatException) {
                throw JsonParseException(
                    "Unable to parse json into type TagSet",
                    e
                )
            } catch (e: NullPointerException) {
                throw JsonParseException(
                    "Unable to parse json into type TagSet",
                    e
                )
            }
        }
    }

    public data class Tag(
        public val name: String,
    ) {
        public fun toJson(): JsonElement {
            val json = JsonObject()
            json.addProperty("name", name)
            return json
        }

        public companion object {
            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJson(jsonString: String): Tag {
                try {
                    val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                    return fromJsonObject(jsonObject)
                } catch (e: IllegalStateException) {
                    throw JsonParseException(
                        "Unable to parse json into type Tag",
                        e
                    )
                }
            }

            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJsonObject(jsonObject: JsonObject): Tag {
                try {
                    val name = jsonObject.get("name").asString
                    return Tag(name)
                } catch (e: IllegalStateException) {
                    throw JsonParseException(
                        "Unable to parse json into type Tag",
                        e
                    )
                } catch (e: NumberFormatException) {
                    throw JsonParseException(
                        "Unable to parse json into type Tag",
                        e
                    )
                } catch (e: NullPointerException) {
                    throw JsonParseException(
                        "Unable to parse json into type Tag",
                        e
                    )
                }
            }
        }
    }
}
