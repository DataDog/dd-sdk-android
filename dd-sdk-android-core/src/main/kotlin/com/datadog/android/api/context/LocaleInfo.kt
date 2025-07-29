/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.api.context

/**
 * Provides information about locale.
 *
 * @property locales Ordered list of the user’s preferred system languages as IETF language tags.
 * @property currentLocale The user's current locale as a language tag (language + region), computed from their preferences and the app's supported languages, e.g. 'es-FR'.
 * @property timeZone The device’s current time zone identifier, e.g. 'Europe/Berlin'.
 */
data class LocaleInfo(
    val locales: List<String>,
    val currentLocale: String,
    val timeZone: String
)
