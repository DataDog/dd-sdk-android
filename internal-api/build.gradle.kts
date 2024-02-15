// plugins {
//  id 'me.champeau.jmh'
// }
//
// apply from: "$rootDir/gradle/publish.gradle"
// description = 'dd-trace-internal-api'
//
// ext {
//  // need access to sun.misc.SharedSecrets
//  skipSettingCompilerRelease = true
// }
//
// // sun.misc.SharedSecrets is gone in later versions
// compileJava {
//  javaCompiler = javaToolchains.compilerFor {
//    languageVersion = JavaLanguageVersion.of(8)
//  }
// }
//
// apply from: "$rootDir/gradle/java.gradle"
// apply from: "$rootDir/gradle/tries.gradle"
//
// tasks.compileTestJava.configure {
//  setJavaVersion(it, 8)
// }
//
// minimumBranchCoverage = 0.7
// minimumInstructionCoverage = 0.8
// excludedClassesCoverage += [
//  "datadog.trace.api.EndpointTracker",
//  "datadog.trace.api.Platform",
//  "datadog.trace.api.Platform.GC",
//  "datadog.trace.api.StatsDClient",
//  "datadog.trace.api.NoOpStatsDClient",
//  "datadog.trace.api.TraceSegment.NoOp",
//  "datadog.trace.api.intake.TrackType",
//  "datadog.trace.api.gateway.Events.ET",
//  "datadog.trace.api.profiling.ProfilingSnapshot.Kind",
//  "datadog.trace.api.WithGlobalTracer.1",
//  "datadog.trace.api.naming.**",
//  "datadog.trace.api.gateway.RequestContext.Noop",
//  // an enum
//  "datadog.trace.api.sampling.AdaptiveSampler",
//  "datadog.trace.api.sampling.ConstantSampler",
//  "datadog.trace.api.sampling.SamplingRule.TraceSamplingRule.TargetSpan",
//  "datadog.trace.api.EndpointCheckpointerHolder",
//  "datadog.trace.api.iast.IastAdvice.Kind",
//  "datadog.trace.api.UserEventTrackingMode",
//  // This is almost fully abstract class so nothing to test
//  'datadog.trace.api.profiling.RecordingData',
//  // A plain enum
//  'datadog.trace.api.profiling.RecordingType',
//  "datadog.trace.bootstrap.ActiveSubsystems",
//  "datadog.trace.bootstrap.config.provider.ConfigProvider.Singleton",
//  "datadog.trace.bootstrap.instrumentation.api.java.lang.ProcessImplInstrumentationHelpers",
//  "datadog.trace.bootstrap.instrumentation.api.Tags",
//  "datadog.trace.bootstrap.instrumentation.api.CommonTagValues",
//  // Caused by empty 'default' interface method
//  "datadog.trace.bootstrap.instrumentation.api.AgentSpan.Context",
//  "datadog.trace.bootstrap.instrumentation.api.AgentTracer.NoopAgentPropagation",
//  "datadog.trace.bootstrap.instrumentation.api.AgentTracer",
//  "datadog.trace.bootstrap.instrumentation.api.AgentTracer.NoopContext",
//  "datadog.trace.bootstrap.instrumentation.api.InstrumentationTags",
//  "datadog.trace.bootstrap.instrumentation.api.AgentTracer.NoopContinuation",
//  "datadog.trace.bootstrap.instrumentation.api.AgentTracer.NoopAgentSpan",
//  "datadog.trace.bootstrap.instrumentation.api.DDComponents",
//  "datadog.trace.bootstrap.instrumentation.api.AgentTracer.NoopAgentScope",
//  "datadog.trace.bootstrap.instrumentation.api.AgentTracer.NoopTracerAPI",
//  "datadog.trace.bootstrap.instrumentation.api.AgentTracer.NoopAgentDataStreamsMonitoring",
//  "datadog.trace.bootstrap.instrumentation.api.AgentTracer.NoopAgentTrace",
//  "datadog.trace.bootstrap.instrumentation.api.AgentTracer.NoopPathwayContext",
//  "datadog.trace.bootstrap.instrumentation.api.AgentTracer.NoopAgentHistogram",
//  "datadog.trace.bootstrap.instrumentation.api.AgentTracer.NoopTraceConfig",
//  "datadog.trace.bootstrap.instrumentation.api.AgentTracer.TracerAPI",
//  "datadog.trace.bootstrap.instrumentation.api.Backlog",
//  "datadog.trace.bootstrap.instrumentation.api.StatsPoint",
//  "datadog.trace.bootstrap.instrumentation.api.ScopeSource",
//  "datadog.trace.bootstrap.instrumentation.api.InternalSpanTypes",
//  "datadog.trace.bootstrap.instrumentation.api.TagContext",
//  "datadog.trace.bootstrap.instrumentation.api.TagContext.HttpHeaders",
//  "datadog.trace.bootstrap.instrumentation.api.ForwardedTagContext",
//  "datadog.trace.bootstrap.instrumentation.api.ResourceNamePriorities",
//  "datadog.trace.bootstrap.instrumentation.api.ErrorPriorities",
//  'datadog.trace.api.civisibility.config.Configurations',
//  "datadog.trace.api.civisibility.config.ModuleExecutionSettings",
//  "datadog.trace.api.civisibility.config.TestIdentifier",
//  "datadog.trace.api.civisibility.coverage.CoverageBridge",
//  "datadog.trace.api.civisibility.coverage.TestReport",
//  "datadog.trace.api.civisibility.coverage.TestReportFileEntry",
//  "datadog.trace.api.civisibility.coverage.TestReportFileEntry.Segment",
//  "datadog.trace.api.civisibility.InstrumentationBridge",
//  "datadog.trace.api.civisibility.events.BuildEventsHandler.ModuleInfo",
//  // POJO
//  "datadog.trace.api.git.GitInfo",
//  "datadog.trace.api.git.GitInfoProvider",
//  // POJO
//  "datadog.trace.api.git.PersonInfo",
//  // POJO
//  "datadog.trace.api.git.CommitInfo",
//  // POJO
//  "datadog.trace.api.git.GitUtils",
//  // tested indirectly by dependent modules
//  "datadog.trace.api.git.RawParseUtils",
//  // tested indirectly by dependent modules
//  "datadog.trace.logging.LoggingSettingsDescription",
//  "datadog.trace.util.AgentProxySelector",
//  "datadog.trace.util.AgentTaskScheduler",
//  "datadog.trace.util.AgentTaskScheduler.PeriodicTask",
//  "datadog.trace.util.AgentTaskScheduler.ShutdownHook",
//  "datadog.trace.util.AgentThreadFactory",
//  "datadog.trace.util.AgentThreadFactory.1",
//  "datadog.trace.util.ClassNameTrie.Builder",
//  "datadog.trace.util.ClassNameTrie.JavaGenerator",
//  "datadog.trace.util.CollectionUtils",
//  "datadog.trace.util.MethodHandles",
//  "datadog.trace.util.PidHelper",
//  "datadog.trace.util.PidHelper.Fallback",
//  "datadog.trace.util.ProcessUtils",
//  "datadog.trace.util.UnsafeUtils",
//  "datadog.trace.api.ConfigCollector",
//  "datadog.trace.api.Config.HostNameHolder",
//  "datadog.trace.api.Config.RuntimeIdHolder",
//  "datadog.trace.api.DynamicConfig",
//  "datadog.trace.api.DynamicConfig.Builder",
//  "datadog.trace.api.DynamicConfig.Snapshot",
//  "datadog.trace.api.InstrumenterConfig",
//  "datadog.trace.api.ResolverCacheConfig.*",
//  // can't reliably force same identity hash for different instance to cover branch
//  "datadog.trace.api.cache.FixedSizeCache.IdentityHash",
//  "datadog.trace.api.cache.FixedSizeWeakKeyCache",
//  // Interface with default method
//  "datadog.trace.api.StatsDClientManager",
//  "datadog.trace.api.iast.Taintable",
//  "datadog.trace.api.Stateful",
//  "datadog.trace.api.Stateful.1",
//  // a stub
//  "datadog.trace.bootstrap.instrumentation.api.ProfilingContextIntegration",
//  "datadog.trace.bootstrap.instrumentation.api.ProfilingContextIntegration.NoOp",
//  // debug
//  'datadog.trace.api.iast.Taintable.DebugLogger',
//  // POJO
//  'datadog.trace.api.iast.util.Cookie',
//  'datadog.trace.api.iast.util.Cookie.Builder',
//  'datadog.trace.api.telemetry.LogCollector.RawLogMessage',
//  // stubs
//  'datadog.trace.api.profiling.Timing.NoOp',
//  'datadog.trace.api.profiling.Timer.NoOp',
//  'datadog.trace.api.profiling.Timer.TimerType',
//  // tested in agent-logging
//  'datadog.trace.logging.LogLevel',
//  'datadog.trace.logging.GlobalLogLevelSwitcher'
// ]
//
// excludedClassesBranchCoverage = [
//  'datadog.trace.api.ProductActivationConfig',
//  'datadog.trace.api.Config',
//  'datadog.trace.util.stacktrace.HotSpotStackWalker',
//  'datadog.trace.util.stacktrace.StackWalkerFactory'
// ]
// excludedClassesInstructionCoverage = ['datadog.trace.util.stacktrace.StackWalkerFactory']
//
// compileTestJava.dependsOn 'generateTestClassNameTries'
//
// dependencies {
//  // references TraceScope and Continuation from public api
//  api project(':dd-trace-api')
//  api deps.slf4j
//  api project(":utils:time-utils")
//
//  // has to be loaded by system classloader:
//  // it contains annotations that are also present in the instrumented application classes
//  api "com.datadoghq:dd-javac-plugin-client:0.1.7"
//
//  testImplementation project(":utils:test-utils")
//  testImplementation("org.assertj:assertj-core:3.20.2")
//  testImplementation deps.junit5
//  testImplementation("org.junit.vintage:junit-vintage-engine:${versions.junit5}")
//  testImplementation deps.commonsMath
//  testImplementation deps.mockito
//  testImplementation deps.truth
// }
//
// jmh {
//  jmhVersion = '1.32'
//  duplicateClassesStrategy = DuplicatesStrategy.EXCLUDE
// }
//

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
    namespace = "datadog.trace.api"
}

dependencies {
    implementation(project(":utils:time-utils"))
    api(libs.slf4j)
    implementation(project(":dd-trace-api"))
    implementation(libs.kotlin)
    implementation(libs.androidXAnnotation)
    // it contains annotations that are also present in the instrumented application classes
    api("com.datadoghq:dd-javac-plugin-client:0.1.7")

    // Generate NoOp implementations
    ksp(project(":tools:noopfactory"))

    // Lint rules
    lintPublish(project(":tools:lint"))

    // Testing

//  testImplementation project(":utils:test-utils")
//  testImplementation("org.assertj:assertj-core:3.20.2")
//  testImplementation deps.junit5
//  testImplementation("org.junit.vintage:junit-vintage-engine:${versions.junit5}")
//  testImplementation deps.commonsMath
//  testImplementation deps.mockito
//  testImplementation deps.truth
//    testImplementation(project(":tools:unit")) {
//        attributes {
//            attribute(
//                com.android.build.api.attributes.ProductFlavorAttr.of("platform"),
//                objects.named("jvm")
//            )
//        }
//    }
    testImplementation(libs.bundles.jUnit5)
    testImplementation(libs.bundles.testTools)
    unmock(libs.robolectric)
//    testImplementation project(':utils:test-utils')
}

kotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
androidLibraryConfig()
junitConfig()
javadocConfig()
dependencyUpdateConfig()
