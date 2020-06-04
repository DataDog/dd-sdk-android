package com.example.model

import com.google.gson.annotations.SerializedName
import kotlin.Int
import kotlin.String
import kotlin.collections.List

/**
 * A musical opus.
 * @param title The opus's title.
 * @param composer The opus's composer.
 * @param artists The opus's artists.
 * @param duration The opus's duration in seconds
 */
data class Opus(
    @SerializedName("title")
    val title: String?,
    @SerializedName("composer")
    val composer: String?,
    @SerializedName("artists")
    val artists: List<Artist>?,
    @SerializedName("duration")
    val duration: Int?
)

/**
 * An artist and their role in an opus.
 * @param name The artist's name.
 * @param role The artist's role.
 */
data class Artist(
    @SerializedName("name")
    val name: String?,
    @SerializedName("role")
    val role: Role?
)

enum class Role {
    @SerializedName("singer")
    SINGER,

    @SerializedName("guitarist")
    GUITARIST,

    @SerializedName("pianist")
    PIANIST,

    @SerializedName("drummer")
    DRUMMER,

    @SerializedName("bassist")
    BASSIST,

    @SerializedName("viloinist")
    VILOINIST,

    @SerializedName("dj")
    DJ,

    @SerializedName("vocals")
    VOCALS,

    @SerializedName("other")
    OTHER
}
