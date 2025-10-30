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

public data class Household(
    public val pets: List<Animal>? = null,
    public val situation: Situation? = null,
) {
    public fun toJson(): JsonElement {
        val json = JsonObject()
        pets?.let { petsNonNull ->
            val petsArray = JsonArray(petsNonNull.size)
            petsNonNull.forEach { petsArray.add(it.toJson()) }
            json.add("pets", petsArray)
        }
        situation?.let { situationNonNull ->
            json.add("situation", situationNonNull.toJson())
        }
        return json
    }

    public companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJson(jsonString: String): Household {
            try {
                val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                return fromJsonObject(jsonObject)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type Household",
                    e
                )
            }
        }

        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJsonObject(jsonObject: JsonObject): Household {
            try {
                val pets = jsonObject.get("pets")?.asJsonArray?.let { jsonArray ->
                    val collection = ArrayList<Animal>(jsonArray.size())
                    jsonArray.forEach {
                        collection.add(Animal.fromJsonObject(it))
                    }
                    collection
                }
                val situation = jsonObject.get("situation")?.asJsonObject?.let {
                    Situation.fromJsonObject(it)
                }
                return Household(pets, situation)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type Household",
                    e
                )
            } catch (e: NumberFormatException) {
                throw JsonParseException(
                    "Unable to parse json into type Household",
                    e
                )
            } catch (e: NullPointerException) {
                throw JsonParseException(
                    "Unable to parse json into type Household",
                    e
                )
            }
        }
    }

    /**
     * A representation of the animal kingdom
     */
    public sealed class Animal {
        public abstract fun toJson(): JsonElement

        public data class Fish(
            public val water: Water,
            public val size: Long? = null,
        ) : Animal() {
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
        ) : Animal() {
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

        public companion object {
            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJson(jsonString: String): Animal {
                try {
                    val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                    return fromJsonObject(jsonObject)
                } catch (e: IllegalStateException) {
                    throw JsonParseException(
                        "Unable to parse json into one of type Animal",
                        e
                    )
                }
            }

            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJsonObject(jsonElement: JsonElement): Animal {
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
                val result = arrayOf(
                    asFish,
                    asBird,
                ).firstOrNull { it != null }
                if (result == null) {
                    val message = "Unable to parse json into one of type \n" + "Animal\n" +
                        errors.joinToString("\n") { it.message.toString() }
                    throw JsonParseException(message)
                }
                return result
            }
        }
    }

    public sealed class Situation {
        public abstract fun toJson(): JsonElement

        public data class Marriage(
            public val spouses: List<String>,
        ) : Situation() {
            override fun toJson(): JsonElement {
                val json = JsonObject()
                val spousesArray = JsonArray(spouses.size)
                spouses.forEach { spousesArray.add(it) }
                json.add("spouses", spousesArray)
                return json
            }

            public companion object {
                @JvmStatic
                @Throws(JsonParseException::class)
                public fun fromJson(jsonString: String): Marriage {
                    try {
                        val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                        return fromJsonObject(jsonObject)
                    } catch (e: IllegalStateException) {
                        throw JsonParseException(
                            "Unable to parse json into type Marriage",
                            e
                        )
                    }
                }

                @JvmStatic
                @Throws(JsonParseException::class)
                public fun fromJsonObject(jsonObject: JsonObject): Marriage {
                    try {
                        val spouses = jsonObject.get("spouses").asJsonArray.let { jsonArray ->
                            val collection = ArrayList<String>(jsonArray.size())
                            jsonArray.forEach {
                                collection.add(it.asString)
                            }
                            collection
                        }
                        return Marriage(spouses)
                    } catch (e: IllegalStateException) {
                        throw JsonParseException(
                            "Unable to parse json into type Marriage",
                            e
                        )
                    } catch (e: NumberFormatException) {
                        throw JsonParseException(
                            "Unable to parse json into type Marriage",
                            e
                        )
                    } catch (e: NullPointerException) {
                        throw JsonParseException(
                            "Unable to parse json into type Marriage",
                            e
                        )
                    }
                }
            }
        }

        public data class Cotenancy(
            public val roommates: List<String>,
        ) : Situation() {
            override fun toJson(): JsonElement {
                val json = JsonObject()
                val roommatesArray = JsonArray(roommates.size)
                roommates.forEach { roommatesArray.add(it) }
                json.add("roommates", roommatesArray)
                return json
            }

            public companion object {
                @JvmStatic
                @Throws(JsonParseException::class)
                public fun fromJson(jsonString: String): Cotenancy {
                    try {
                        val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                        return fromJsonObject(jsonObject)
                    } catch (e: IllegalStateException) {
                        throw JsonParseException(
                            "Unable to parse json into type Cotenancy",
                            e
                        )
                    }
                }

                @JvmStatic
                @Throws(JsonParseException::class)
                public fun fromJsonObject(jsonObject: JsonObject): Cotenancy {
                    try {
                        val roommates = jsonObject.get("roommates").asJsonArray.let { jsonArray ->
                            val collection = ArrayList<String>(jsonArray.size())
                            jsonArray.forEach {
                                collection.add(it.asString)
                            }
                            collection
                        }
                        return Cotenancy(roommates)
                    } catch (e: IllegalStateException) {
                        throw JsonParseException(
                            "Unable to parse json into type Cotenancy",
                            e
                        )
                    } catch (e: NumberFormatException) {
                        throw JsonParseException(
                            "Unable to parse json into type Cotenancy",
                            e
                        )
                    } catch (e: NullPointerException) {
                        throw JsonParseException(
                            "Unable to parse json into type Cotenancy",
                            e
                        )
                    }
                }
            }
        }

        public companion object {
            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJson(jsonString: String): Situation {
                try {
                    val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                    return fromJsonObject(jsonObject)
                } catch (e: IllegalStateException) {
                    throw JsonParseException(
                        "Unable to parse json into one of type Situation",
                        e
                    )
                }
            }

            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJsonObject(jsonElement: JsonElement): Situation {
                val errors = mutableListOf<Throwable>()
                val asMarriage = try {
                    if (jsonElement is JsonObject) {
                        Marriage.fromJsonObject(jsonElement)
                    } else {
                        throw JsonParseException("Unable to parse json into type "
                                 + "Marriage")
                    }
                } catch (e: JsonParseException) {
                    errors.add(e)
                    null
                }
                val asCotenancy = try {
                    if (jsonElement is JsonObject) {
                        Cotenancy.fromJsonObject(jsonElement)
                    } else {
                        throw JsonParseException("Unable to parse json into type "
                                 + "Cotenancy")
                    }
                } catch (e: JsonParseException) {
                    errors.add(e)
                    null
                }
                val result = arrayOf(
                    asMarriage,
                    asCotenancy,
                ).firstOrNull { it != null }
                if (result == null) {
                    val message = "Unable to parse json into one of type \n" + "Situation\n" +
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
