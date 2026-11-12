/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.config

import com.datadog.gradle.utils.Version
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource

@ExtendWith(ForgeExtension::class)
internal class MavenConfigTest {

    @ParameterizedTest
    @EnumSource(PublishingDestination::class)
    fun `M return releases repository W internalRepositoryUrlFor() {dogfood version, any destination}`(
        destination: PublishingDestination,
        @StringForgery fakeShortSha: String
    ) {
        // When
        val url = internalRepositoryUrlFor(Version.Type.Dogfood(fakeShortSha), destination)

        // Then
        assertThat(url).isEqualTo("s3://binaries-ddbuild-io-prod/dd-sdk-android/releases")
    }

    @Test
    fun `M return snapshots repository W internalRepositoryUrlFor() {snapshot version, INTERNAL}`() {
        // When
        val url = internalRepositoryUrlFor(Version.Type.Snapshot, PublishingDestination.INTERNAL)

        // Then
        assertThat(url).isEqualTo("s3://binaries-ddbuild-io-prod/dd-sdk-android/snapshots")
    }

    @ParameterizedTest
    @MethodSource("releaseVersionTypes")
    fun `M return releases repository W internalRepositoryUrlFor() {non-snapshot version, INTERNAL}`(
        versionType: Version.Type
    ) {
        // When
        val url = internalRepositoryUrlFor(versionType, PublishingDestination.INTERNAL)

        // Then
        assertThat(url).isEqualTo("s3://binaries-ddbuild-io-prod/dd-sdk-android/releases")
    }

    @ParameterizedTest
    @MethodSource("nonDogfoodVersionTypes")
    fun `M return null W internalRepositoryUrlFor() {non-dogfood version, MAVEN_CENTRAL}`(versionType: Version.Type) {
        // When
        val url = internalRepositoryUrlFor(versionType, PublishingDestination.MAVEN_CENTRAL)

        // Then
        assertThat(url).isNull()
    }

    companion object {
        @JvmStatic
        fun releaseVersionTypes(): List<Version.Type> = listOf(
            Version.Type.Release,
            Version.Type.ReleaseCandidate(1),
            Version.Type.Beta(2),
            Version.Type.Alpha(3),
            Version.Type.Dev
        )

        @JvmStatic
        fun nonDogfoodVersionTypes(): List<Version.Type> = releaseVersionTypes() + Version.Type.Snapshot
    }
}
