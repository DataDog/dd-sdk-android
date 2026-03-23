/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.integration.tests.ddApiClient.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class RumSearchResponse(
    @SerialName("data") val data: List<ViewEvent> = emptyList(),
    @SerialName("meta") val meta: Meta? = null
) {
    @Serializable
    data class ViewEvent(
        @SerialName("id") val id: String,
        @SerialName("type") val type: String,
        @SerialName("attributes") val attributes: RumEventAttributes
    )

    @Serializable
    data class RumEventAttributes(
        @SerialName("service") val service: String? = null,
        @SerialName("attributes") val attributes: RumAttributes,
        @SerialName("timestamp") val timestamp: String,
        @SerialName("tags") val tags: List<String> = emptyList()
    )

    @Serializable
    data class RumAttributes(
        @SerialName("os") val os: Os? = null,
        @SerialName("version") val version: String? = null,
        @SerialName("build_version") val buildVersion: String? = null,
        @SerialName("session") val session: Session? = null,
        @SerialName("type") val type: String? = null,
        @SerialName("geo") val geo: Geo? = null,
        @SerialName("view") val view: ViewAttributes? = null,
        @SerialName("application") val application: Application? = null,
        @SerialName("connectivity") val connectivity: Connectivity? = null,
        @SerialName("profiling") val profiling: Profiling? = null,
        @SerialName("usr") val usr: Usr? = null,
        @SerialName("service") val service: String? = null,
        @SerialName("context") val context: JsonObject? = null,
        @SerialName("feature_flags") val featureFlags: JsonObject? = null,
        @SerialName("device") val device: Device? = null,
        @SerialName("_dd") val dd: Dd? = null
    )

    @Serializable
    data class Os(
        @SerialName("name") val name: String,
        @SerialName("version") val version: String,
        @SerialName("version_major") val versionMajor: String
    )

    @Serializable
    data class Session(
        @SerialName("id") val id: String,
        @SerialName("type") val type: String,
        @SerialName("is_replay_available") val isReplayAvailable: Boolean? = null,
        @SerialName("ip") val ip: String? = null,
        @SerialName("useragent") val useragent: String? = null,
        @SerialName("retention_reason") val retentionReason: String? = null,
        @SerialName("matching_retention_filter") val matchingRetentionFilter: RetentionFilter? = null,
        @SerialName("plan") val plan: String? = null,
        @SerialName("has_full_snapshot") val hasFullSnapshot: Boolean? = null
    )

    @Serializable
    data class RetentionFilter(
        @SerialName("name") val name: String,
        @SerialName("id") val id: String
    )

    @Serializable
    data class Geo(
        @SerialName("continent") val continent: String? = null,
        @SerialName("country") val country: String? = null,
        @SerialName("country_iso_code") val countryIsoCode: String? = null,
        @SerialName("city") val city: String? = null,
        @SerialName("latitude") val latitude: Double? = null,
        @SerialName("continent_code") val continentCode: String? = null,
        @SerialName("country_subdivision_iso_code") val countrySubdivisionIsoCode: String? = null,
        @SerialName("location") val location: List<Double>? = null,
        @SerialName("country_subdivision") val countrySubdivision: String? = null,
        @SerialName("longitude") val longitude: Double? = null,
        @SerialName("as") val autonomousSystem: AutonomousSystem? = null
    )

    @Serializable
    data class AutonomousSystem(
        @SerialName("domain") val domain: String? = null,
        @SerialName("name") val name: String? = null,
        @SerialName("type") val type: String? = null
    )

    @Serializable
    data class ViewAttributes(
        @SerialName("is_active") val isActive: Boolean,
        @SerialName("resource") val resource: CountWrapper? = null,
        @SerialName("accessibility") val accessibility: Accessibility? = null,
        @SerialName("frustration") val frustration: CountWrapper? = null,
        @SerialName("frozen_frame") val frozenFrame: CountWrapper? = null,
        @SerialName("long_task") val longTask: CountWrapper? = null,
        @SerialName("error") val error: CountWrapper? = null,
        @SerialName("is_slow_rendered") val isSlowRendered: Boolean? = null,
        @SerialName("url") val url: String? = null,
        @SerialName("time_spent") val timeSpent: Long? = null,
        @SerialName("crash") val crash: CountWrapper? = null,
        @SerialName("url_path_group") val urlPathGroup: String? = null,
        @SerialName("name") val name: String? = null,
        @SerialName("action") val action: CountWrapper? = null,
        @SerialName("id") val id: String? = null,
        @SerialName("url_path") val urlPath: String? = null,
        @SerialName("loading_time") val loadingTime: Long? = null,
        @SerialName("network_settled_time") val networkSettledTime: Long? = null,
        @SerialName("cpu_ticks_count") val cpuTicksCount: Double? = null,
        @SerialName("cpu_ticks_per_second") val cpuTicksPerSecond: Double? = null,
        @SerialName("custom_timings") val customTimings: JsonObject? = null,
        @SerialName("memory_average") val memoryAverage: Double? = null,
        @SerialName("memory_max") val memoryMax: Double? = null,
        @SerialName("refresh_rate_average") val refreshRateAverage: Double? = null,
        @SerialName("refresh_rate_min") val refreshRateMin: Double? = null
    )

    @Serializable
    data class CountWrapper(
        @SerialName("count") val count: Int
    )

    @Serializable
    data class Accessibility(
        @SerialName("rtl_enabled") val rtlEnabled: Boolean? = null,
        @SerialName("single_app_mode_enabled") val singleAppModeEnabled: Boolean? = null,
        @SerialName("invert_colors_enabled") val invertColorsEnabled: Boolean? = null,
        @SerialName("text_size") val textSize: String? = null,
        @SerialName("closed_captioning_enabled") val closedCaptioningEnabled: Boolean? = null,
        @SerialName("screen_reader_enabled") val screenReaderEnabled: Boolean? = null
    )

    @Serializable
    data class Application(
        @SerialName("id") val id: String,
        @SerialName("name") val name: String? = null,
        @SerialName("current_locale") val currentLocale: String? = null,
        @SerialName("short_name") val shortName: String? = null
    )

    @Serializable
    data class Connectivity(
        @SerialName("interfaces") val interfaces: List<String>? = null,
        @SerialName("status") val status: String? = null
    )

    @Serializable
    data class Profiling(
        @SerialName("has_profile") val hasProfile: Boolean? = null
    )

    @Serializable
    data class Usr(
        @SerialName("id") val id: String? = null,
        @SerialName("name") val name: String? = null,
        @SerialName("email") val email: String? = null,
        @SerialName("anonymous_id") val anonymousId: String? = null,
        @SerialName("id_from_anonymous") val idFromAnonymous: Boolean? = null
    )

    @Serializable
    data class Device(
        @SerialName("name") val name: String? = null,
        @SerialName("model") val model: String? = null,
        @SerialName("brand") val brand: String? = null,
        @SerialName("type") val type: String? = null,
        @SerialName("architecture") val architecture: String? = null,
        @SerialName("locale") val locale: String? = null,
        @SerialName("battery_level") val batteryLevel: Double? = null,
        @SerialName("power_saving_mode") val powerSavingMode: Boolean? = null,
        @SerialName("is_low_end") val isLowEnd: Boolean? = null,
        @SerialName("time_zone") val timeZone: String? = null,
        @SerialName("brightness_level") val brightnessLevel: Double? = null,
        @SerialName("locales") val locales: List<String>? = null,
        @SerialName("logical_cpu_count") val logicalCpuCount: Int? = null,
        @SerialName("total_ram") val totalRam: Long? = null
    )

    @Serializable
    data class Dd(
        @SerialName("format_version") val formatVersion: Int? = null
    )

    @Serializable
    data class Meta(
        @SerialName("elapsed") val elapsed: Long,
        @SerialName("request_id") val requestId: String,
        @SerialName("status") val status: String
    )
}
