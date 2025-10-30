package com.example.model

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.lang.IllegalStateException
import java.lang.NullPointerException
import java.lang.NumberFormatException
import kotlin.Boolean
import kotlin.Long
import kotlin.String
import kotlin.collections.ArrayList
import kotlin.collections.List
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

public data class WeirdCombo(
    public val anything: Anything? = null,
) {
    public fun toJson(): JsonElement {
        val json = JsonObject()
        anything?.let { anythingNonNull ->
            json.add("anything", anythingNonNull.toJson())
        }
        return json
    }

    public companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJson(jsonString: String): WeirdCombo {
            try {
                val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                return fromJsonObject(jsonObject)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type WeirdCombo",
                    e
                )
            }
        }

        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJsonObject(jsonObject: JsonObject): WeirdCombo {
            try {
                val anything = jsonObject.get("anything")?.asJsonObject?.let {
                    Anything.fromJsonObject(it)
                }
                return WeirdCombo(anything)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type WeirdCombo",
                    e
                )
            } catch (e: NumberFormatException) {
                throw JsonParseException(
                    "Unable to parse json into type WeirdCombo",
                    e
                )
            } catch (e: NullPointerException) {
                throw JsonParseException(
                    "Unable to parse json into type WeirdCombo",
                    e
                )
            }
        }
    }

    public sealed class Anything {
        public abstract fun toJson(): JsonElement

        public data class Fish(
            public val water: Water,
            public val size: Long? = null,
        ) : Anything() {
            override fun toJson(): JsonElement {
                val json = JsonObject()
                json.add("water", water.toJson())
                size?.let { sizeNonNull ->
                    json.addProperty("size", sizeNonNull)
                }
                return json
            }

            public companion object {
                @JvmStatic
                @Throws(JsonParseException::class)
                public fun fromJson(jsonString: String): Fish {
                    try {
                        val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                        return fromJsonObject(jsonObject)
                    } catch (e: IllegalStateException) {
                        throw JsonParseException(
                            "Unable to parse json into type Fish",
                            e
                        )
                    }
                }

                @JvmStatic
                @Throws(JsonParseException::class)
                public fun fromJsonObject(jsonObject: JsonObject): Fish {
                    try {
                        val water = Water.fromJson(jsonObject.get("water").asString)
                        val size = jsonObject.get("size")?.asLong
                        return Fish(water, size)
                    } catch (e: IllegalStateException) {
                        throw JsonParseException(
                            "Unable to parse json into type Fish",
                            e
                        )
                    } catch (e: NumberFormatException) {
                        throw JsonParseException(
                            "Unable to parse json into type Fish",
                            e
                        )
                    } catch (e: NullPointerException) {
                        throw JsonParseException(
                            "Unable to parse json into type Fish",
                            e
                        )
                    }
                }
            }
        }

        public data class Bird(
            public val food: Food,
            public val canFly: Boolean,
        ) : Anything() {
            override fun toJson(): JsonElement {
                val json = JsonObject()
                json.add("food", food.toJson())
                json.addProperty("can_fly", canFly)
                return json
            }

            public companion object {
                @JvmStatic
                @Throws(JsonParseException::class)
                public fun fromJson(jsonString: String): Bird {
                    try {
                        val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                        return fromJsonObject(jsonObject)
                    } catch (e: IllegalStateException) {
                        throw JsonParseException(
                            "Unable to parse json into type Bird",
                            e
                        )
                    }
                }

                @JvmStatic
                @Throws(JsonParseException::class)
                public fun fromJsonObject(jsonObject: JsonObject): Bird {
                    try {
                        val food = Food.fromJson(jsonObject.get("food").asString)
                        val canFly = jsonObject.get("can_fly").asBoolean
                        return Bird(food, canFly)
                    } catch (e: IllegalStateException) {
                        throw JsonParseException(
                            "Unable to parse json into type Bird",
                            e
                        )
                    } catch (e: NumberFormatException) {
                        throw JsonParseException(
                            "Unable to parse json into type Bird",
                            e
                        )
                    } catch (e: NullPointerException) {
                        throw JsonParseException(
                            "Unable to parse json into type Bird",
                            e
                        )
                    }
                }
            }
        }

        public data class Paper(
            public val title: String,
            public val author: List<String>,
        ) : Anything() {
            override fun toJson(): JsonElement {
                val json = JsonObject()
                json.addProperty("title", title)
                val authorArray = JsonArray(author.size)
                author.forEach { authorArray.add(it) }
                json.add("author", authorArray)
                return json
            }

            public companion object {
                @JvmStatic
                @Throws(JsonParseException::class)
                public fun fromJson(jsonString: String): Paper {
                    try {
                        val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                        return fromJsonObject(jsonObject)
                    } catch (e: IllegalStateException) {
                        throw JsonParseException(
                            "Unable to parse json into type Paper",
                            e
                        )
                    }
                }

                @JvmStatic
                @Throws(JsonParseException::class)
                public fun fromJsonObject(jsonObject: JsonObject): Paper {
                    try {
                        val title = jsonObject.get("title").asString
                        val author = jsonObject.get("author").asJsonArray.let { jsonArray ->
                            val collection = ArrayList<String>(jsonArray.size())
                            jsonArray.forEach {
                                collection.add(it.asString)
                            }
                            collection
                        }
                        return Paper(title, author)
                    } catch (e: IllegalStateException) {
                        throw JsonParseException(
                            "Unable to parse json into type Paper",
                            e
                        )
                    } catch (e: NumberFormatException) {
                        throw JsonParseException(
                            "Unable to parse json into type Paper",
                            e
                        )
                    } catch (e: NullPointerException) {
                        throw JsonParseException(
                            "Unable to parse json into type Paper",
                            e
                        )
                    }
                }
            }
        }

        public companion object {
            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJson(jsonString: String): Anything {
                try {
                    val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                    return fromJsonObject(jsonObject)
                } catch (e: IllegalStateException) {
                    throw JsonParseException(
                        "Unable to parse json into one of type Anything",
                        e
                    )
                }
            }

            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJsonObject(jsonElement: JsonElement): Anything {
                val errors = mutableListOf<Throwable>()
                val asFish = try {
                    if (jsonElement is JsonObject) {
                        Fish.fromJsonObject(jsonElement)
                    } else {
                        throw JsonParseException("Unable to parse json into type "
                                 + "Fish")
                    }
                } catch (e: JsonParseException) {
                    errors.add(e)
                    null
                }
                val asBird = try {
                    if (jsonElement is JsonObject) {
                        Bird.fromJsonObject(jsonElement)
                    } else {
                        throw JsonParseException("Unable to parse json into type "
                                 + "Bird")
                    }
                } catch (e: JsonParseException) {
                    errors.add(e)
                    null
                }
                val asPaper = try {
                    if (jsonElement is JsonObject) {
                        Paper.fromJsonObject(jsonElement)
                    } else {
                        throw JsonParseException("Unable to parse json into type "
                                 + "Paper")
                    }
                } catch (e: JsonParseException) {
                    errors.add(e)
                    null
                }
                val result = arrayOf(
                    asFish,
                    asBird,
                    asPaper,
                ).firstOrNull { it != null }
                if (result == null) {
                    val message = "Unable to parse json into one of type \n" + "Anything\n" +
                        errors.joinToString("\n") { it.message.toString() }
                    throw JsonParseException(message)
                }
                return result
            }
        }
    }

    public enum class Water(
        private val jsonValue: String,
    ) {
        SALT("salt"),
        FRESH("fresh"),
        ;

        public fun toJson(): JsonElement = JsonPrimitive(jsonValue)

        public companion object {
            @JvmStatic
            public fun fromJson(jsonString: String): Water = values().first {
                it.jsonValue == jsonString
            }
        }
    }

    public enum class Food(
        private val jsonValue: String,
    ) {
        FISH("fish"),
        BIRD("bird"),
        RODENT("rodent"),
        INSECT("insect"),
        FRUIT("fruit"),
        SEEDS("seeds"),
        POLLEN("pollen"),
        ;

        public fun toJson(): JsonElement = JsonPrimitive(jsonValue)

        public companion object {
            @JvmStatic
            public fun fromJson(jsonString: String): Food = values().first {
                it.jsonValue == jsonString
            }
        }
    }
}
