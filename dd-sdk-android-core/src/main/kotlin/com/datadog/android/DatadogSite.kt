/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

/**
 * Defines the Datadog sites you can send tracked data to.
 *
 * @param siteName Explicit site name property introduced in order to have a consistent SDK
 * instance ID (because this value is used there) in case if enum values are renamed.
 * @param intakeHostName the host name for the given site.
 */
enum class DatadogSite(internal val siteName: String, private val intakeHostName: String) {

    /**
     *  The US1 site: [app.datadoghq.com](https://app.datadoghq.com).
     */
    US1("us1", "browser-intake-datadoghq.com"),

    /**
     *  The US3 site: [us3.datadoghq.com](https://us3.datadoghq.com).
     */
    US3("us3"),

    /**
     *  The US5 site: [us5.datadoghq.com](https://us5.datadoghq.com).
     */
    US5("us5"),

    /**
     *  The EU1 site: [app.datadoghq.eu](https://app.datadoghq.eu).
     */
    EU1("eu1", "browser-intake-datadoghq.eu"),

    /**
     *  The AP1 site: [ap1.datadoghq.com](https://ap1.datadoghq.com).
     */
    AP1("ap1"),

    /**
     *  The US1_FED site (FedRAMP compatible): [app.ddog-gov.com](https://app.ddog-gov.com).
     */
    US1_FED("us1_fed", "browser-intake-ddog-gov.com"),

    /**
     *  The STAGING site (internal usage only): [app.datad0g.com](https://app.datad0g.com).
     */
    STAGING("staging", "browser-intake-datad0g.com");

    /**
     * Constructor using the generic way to build the intake endpoint host from the site name.
     * @param siteName Explicit site name property introduced in order to have a consistent SDK
     * instance ID (because this value is used there) in case if enum values are renamed.
     */
    constructor(siteName: String) : this(
        siteName,
        "browser-intake-$siteName-datadoghq.com"
    )

    /** The intake endpoint url. */
    val intakeEndpoint: String = "https://$intakeHostName"
}
