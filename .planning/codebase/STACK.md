# Technology Stack

**Analysis Date:** 2026-01-21

## Languages

**Primary:**
- Kotlin 2.0.21 - Main language for SDK implementation, used across all core and feature modules
- Java - Android platform compatibility layer, legacy integrations

**Secondary:**
- Gradle Kotlin DSL (build system scripting)

## Runtime

**Environment:**
- Android SDK (API level configurable, target SDK 34 by default as per `AndroidConfig.TARGET_SDK`)
- Android Runtime (Kotlin stdlib on ART)

**Package Manager:**
- Gradle 8.x (inferred from Android Gradle Plugin 8.13.2)
- Gradle dependency management via `gradle/libs.versions.toml` version catalog
- Lockfile: implicit via Gradle wrapper

## Frameworks

**Core:**
- Android Framework - Base platform
- AndroidX/Jetpack libraries - Modern Android development:
  - `androidx.core:core-ktx` 1.3.1 - Core functionality
  - `androidx.fragment:fragment` 1.2.4 - Fragment management
  - `androidx.lifecycle:*` 2.8.7 - Lifecycle management
  - `androidx.work:work-runtime` 2.8.1 - Background task scheduling
  - `androidx.navigation:navigation-*` 2.7.7 - Navigation framework
  - `androidx.compose:*` 2023.10.01 (BOM) - Compose UI framework
  - `androidx.annotation:annotation` 1.9.1 - Annotations
  - `androidx.collection:collection` 1.4.5 - Collection utilities

**Build/Dev:**
- Gradle Build System 8.13.2 - Build orchestration
- Kotlin Gradle Plugin 2.0.21 - Kotlin build support
- KSP (Kotlin Symbol Processing) 2.0.21-1.0.28 - Code generation
- Dokka 2.0.0 - Documentation generation

**Testing:**
- JUnit 5 (Jupiter) 5.9.3 - Unit testing framework
- Robolectric 4.4_r1 - Android runtime simulation for unit tests
- Mockito 5.12.0 (Android) + 5.1.0 (Kotlin) - Mocking framework
- AssertJ 3.18.1 - Assertion library
- Elmyr 1.3.1 - Data generation for tests
- Espresso 3.5.1 - UI testing framework
- AndroidX Test libraries 1.5.0+ - Android instrumentation testing

## Key Dependencies

**Critical:**
- `com.squareup.okhttp3:okhttp` 4.12.0 - HTTP client for network requests (instrumented for RUM/APM)
- `com.google.code.gson:gson` 2.10.1 - JSON serialization/deserialization
- `com.lyft.kronos:kronos-android` 0.0.1-alpha11 - NTP time synchronization for accurate timestamps
- Kotlin stdlib 2.0.21 - Kotlin standard library

**Infrastructure:**
- `org.jetbrains.kotlin:kotlin-reflect` 2.0.21 - Reflection utilities
- `org.jetbrains.kotlinx:kotlinx-coroutines-core` 1.4.2 - Coroutines async/await
- `com.google.devtools.ksp:symbol-processing-api` 2.0.21-1.0.28 - Symbol processing for code generation
- `com.squareup:kotlinpoet` 1.14.2 - Kotlin code generation
- `io.opentelemetry:opentelemetry-*` 1.40.0 - OpenTelemetry instrumentation support
- `org.jctools:jctools-core` 3.3.0 - Concurrent data structures
- `com.google.re2j:re2j` 1.7 - Regex pattern matching

## Configuration

**Environment:**
- SDK configuration via `Configuration.Builder` at runtime
- Client token and environment settings required at initialization
- Multi-site support via `DatadogSite` enum (US1, US3, US5, EU1, AP1, AP2, US1_FED, STAGING)
- Environment variables for CI/CD: `CENTRAL_PUBLISHER_USERNAME`, `CENTRAL_PUBLISHER_PASSWORD`, `ANDROID_SDK_ROOT`

**Build:**
- `build.gradle.kts` (root) - Root build configuration with publishing setup
- `gradle/libs.versions.toml` - Version catalog for dependency management
- `gradle.properties` - Gradle JVM arguments and Android configuration
- `local.properties` - Local SDK path configuration (`sdk.dir`)

## Platform Requirements

**Development:**
- Android SDK 34 (target API level)
- JDK 17+ (Kotlin with JVM bytecode target 11, supports JDK 21 with `kotlin.jvm.target.validation.mode = IGNORE`)
- Android Studio or IntelliJ IDEA

**Production:**
- Target Android API 34, minimum API level varies by feature (typically API 21+)
- Publication targets: Maven Central (via Sonatype OSSRH), JitPack

---

*Stack analysis: 2026-01-21*
