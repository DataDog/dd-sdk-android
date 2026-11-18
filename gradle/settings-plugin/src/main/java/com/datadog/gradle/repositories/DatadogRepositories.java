/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.repositories;

import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.provider.ProviderFactory;

/**
 * Repository definitions shared by the settings scripts.
 * <p>
 * When the Magic Mirror Depot proxy properties are set (only in CI, via `.gitlab-ci.yml`), the
 * proxy replaces Maven Central and the Gradle Plugin Portal.
 */
public final class DatadogRepositories {

    /**
     * Maven repository proxy URL (Magic Mirror Depot). When set, prepended to dependency
     * repositories so artifact resolution goes through Datadog's internal mirror instead of
     * hitting public registries (e.g. Maven Central) directly. Used in CI to avoid 429s.
     * Set via -PmavenRepositoryProxy=... or in gradle.properties.
     */
    private static final String MAVEN_REPOSITORY_PROXY = "mavenRepositoryProxy";

    /**
     * Gradle plugin portal proxy URL (Magic Mirror Depot). When set, prepended to plugin
     * resolution repositories so plugin resolution goes through Datadog's internal mirror
     * instead of hitting plugins.gradle.org directly. Used in CI to avoid 429s.
     * Set via -PgradlePluginProxy=... or in gradle.properties.
     */
    private static final String GRADLE_PLUGIN_PROXY = "gradlePluginProxy";

    private static final String GOOGLE_GROUPS_REGEX = ".*(google|android|chromium).*";
    private static final String JITPACK_URL = "https://jitpack.io";
    private static final String JITPACK_GROUPS_REGEX = "^com\\.github\\..*";

    private final String mavenProxy;
    private final String gradlePluginProxy;

    public DatadogRepositories(ProviderFactory providers) {
        this.mavenProxy = readProxy(providers, MAVEN_REPOSITORY_PROXY);
        this.gradlePluginProxy = readProxy(providers, GRADLE_PLUGIN_PROXY);
    }

    public void mavenCentralDepotProxied(RepositoryHandler repositories) {
        if (mavenProxy != null) {
            repositories.maven(repository -> {
                repository.setName("Maven Datadog Depot");
                repository.setUrl(mavenProxy);
            });
        } else {
            repositories.mavenCentral();
        }
    }

    public void gradlePluginPortalDepotProxied(RepositoryHandler repositories) {
        if (gradlePluginProxy != null) {
            repositories.maven(repository -> {
                repository.setName("Gradle Plugin Datadog Depot");
                repository.setUrl(gradlePluginProxy);
            });
        }
        // Gradle Plugins repo is not cached by Depot but there are _some_ artifacts there.
        // So try to hit Depot first, and if not found use fallback, unlike in case of Maven Central.
        repositories.gradlePluginPortal();
    }

    public void googleConstrained(RepositoryHandler repositories) {
        repositories.google(repository ->
                repository.mavenContent(content -> content.includeGroupByRegex(GOOGLE_GROUPS_REGEX))
        );
    }

    public void jitpackConstrained(RepositoryHandler repositories) {
        repositories.maven(repository -> {
            repository.setUrl(JITPACK_URL);
            repository.mavenContent(content -> content.includeGroupByRegex(JITPACK_GROUPS_REGEX));
        });
    }

    private static String readProxy(ProviderFactory providers, String propertyName) {
        String value = providers.gradleProperty(propertyName).getOrNull();
        return value == null || value.isBlank() ? null : value;
    }
}
