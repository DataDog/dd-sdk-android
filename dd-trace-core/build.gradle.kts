import com.datadog.gradle.config.androidLibraryConfig
import com.datadog.gradle.config.dependencyUpdateConfig
import com.datadog.gradle.config.javadocConfig
import com.datadog.gradle.config.junitConfig
import com.datadog.gradle.config.kotlinConfig
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Build
    id("com.android.library")
    kotlin("android")
    id("com.google.devtools.ksp")

    // Publish
    id("org.jetbrains.dokka")

    // Analysis tools
    id("com.github.ben-manes.versions")

    // Tests
    id("de.mobilej.unmock")
    id("org.jetbrains.kotlinx.kover")

    // Internal Generation
    id("thirdPartyLicences")
    id("apiSurface")
    id("transitiveDependencies")
    id("binary-compatibility-validator")
}

android {
    namespace = "datadog.trace"
}

dependencies {
    api(project(":utils:time-utils"))
    api(project(":utils:container-utils"))
    implementation(project(":dd-trace-api"))
    implementation(project(":internal-api"))
    implementation(libs.slf4j)
    implementation(libs.moshi)
    implementation(libs.jctools)
    implementation(libs.kotlin)
    implementation(libs.okHttp)
    implementation(group = "com.datadoghq", name = "sketches-java", version = "0.8.2")
    implementation(group = "com.google.re2j", name = "re2j", version = "1.7")
    compileOnly(group = "com.github.spotbugs", name = "spotbugs-annotations", version = "4.2.0")

    // Generate NoOp implementations
    ksp(project(":tools:noopfactory"))

    // Lint rules
    lintPublish(project(":tools:lint"))

    // Testing
//
//    testAnnotationProcessor deps.autoserviceProcessor
//            testCompileOnly deps.autoserviceAnnotation
//
//            testImplementation project(":dd-java-agent:testing")
//    testImplementation group: 'org.msgpack', name: 'msgpack-core', version: '0.8.20'
//    testImplementation group: 'org.msgpack', name: 'jackson-dataformat-msgpack', version: '0.8.20'
//    testImplementation group: 'org.openjdk.jol', name: 'jol-core', version: '0.16'
//    testImplementation group: 'commons-codec', name: 'commons-codec', version: '1.3'
//    testImplementation group: 'com.amazonaws', name: 'aws-lambda-java-events', version:'3.11.0'
//    testImplementation group: 'com.google.protobuf', name: 'protobuf-java', version: '3.14.0'
//    testImplementation deps.testcontainers
//
//            traceAgentTestImplementation deps.testcontainers
    testImplementation(libs.bundles.jUnit5)
    testImplementation(libs.bundles.testTools)
    unmock(libs.robolectric)
}

kotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
androidLibraryConfig()
junitConfig()
javadocConfig()
dependencyUpdateConfig()
