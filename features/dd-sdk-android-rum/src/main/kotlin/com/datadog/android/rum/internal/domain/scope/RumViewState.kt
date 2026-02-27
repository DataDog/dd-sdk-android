/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.tools.diff.Diff
import com.datadog.tools.diff.DiffAppend
import com.datadog.tools.diff.DiffIgnore
import com.datadog.tools.diff.DiffMerge
import com.datadog.tools.diff.DiffReplace

@Diff
internal data class RumViewState(
    @DiffIgnore val date: Long,
    @DiffIgnore val application: Application,
    val service: String? = null,
    val version: String? = null,
    val buildVersion: String? = null,
    val buildId: String? = null,
    val ddtags: String? = null,
    @DiffIgnore val session: ViewEventSession,
    val source: ViewEventSource? = null,
    @DiffMerge val view: ViewEventView,
    val usr: Usr? = null,
    val account: Account? = null,
    val connectivity: Connectivity? = null,
    val display: Display? = null,
    val synthetics: Synthetics? = null,
    val ciTest: CiTest? = null,
    val os: Os? = null,
    val device: Device? = null,
    val dd: Dd,
    val context: Context? = null,
    val container: Container? = null,
    @DiffReplace val featureFlags: Context? = null,
    val privacy: Privacy? = null,
) {
    data class Application(
        val id: String,
        val currentLocale: String?,
    ) {
    }
    data class ViewEventSession(
        val id: String,
        val type: ViewEventSessionType,
        val hasReplay: Boolean? = null,
        val isActive: Boolean? = true,
        val sampledForReplay: Boolean? = null,
    ) {
    }
    data class ViewEventView(
        @DiffIgnore val id: String,
        var referrer: String? = null,
        @DiffIgnore var url: String,
        var name: String? = null,
        val loadingTime: Long? = null,
        val networkSettledTime: Long? = null,
        val interactionToNextViewTime: Long? = null,
        val loadingType: LoadingType? = null,
        val timeSpent: Long,
        val firstContentfulPaint: Long? = null,
        val largestContentfulPaint: Long? = null,
        val largestContentfulPaintTargetSelector: String? = null,
        val firstInputDelay: Long? = null,
        val firstInputTime: Long? = null,
        val firstInputTargetSelector: String? = null,
        val interactionToNextPaint: Long? = null,
        val interactionToNextPaintTime: Long? = null,
        val interactionToNextPaintTargetSelector: String? = null,
        val cumulativeLayoutShift: Number? = null,
        val cumulativeLayoutShiftTime: Long? = null,
        val cumulativeLayoutShiftTargetSelector: String? = null,
        val domComplete: Long? = null,
        val domContentLoaded: Long? = null,
        val domInteractive: Long? = null,
        val loadEvent: Long? = null,
        val firstByte: Long? = null,
        val customTimings: CustomTimings? = null,
        val isActive: Boolean? = null,
        val isSlowRendered: Boolean? = null,
        val action: Action,
        val error: Error,
        val crash: Crash? = null,
        val longTask: LongTask? = null,
        val frozenFrame: FrozenFrame? = null,
        @DiffAppend val slowFrames: List<SlowFrame>? = null,
        val resource: Resource,
        val frustration: Frustration? = null,
        @DiffAppend val inForegroundPeriods: List<InForegroundPeriod>? = null,
        val memoryAverage: Number? = null,
        val memoryMax: Number? = null,
        val cpuTicksCount: Number? = null,
        val cpuTicksPerSecond: Number? = null,
        val refreshRateAverage: Number? = null,
        val refreshRateMin: Number? = null,
        val slowFramesRate: Number? = null,
        val freezeRate: Number? = null,
        val flutterBuildTime: FlutterBuildTime? = null,
        val flutterRasterTime: FlutterBuildTime? = null,
        val jsRefreshRate: FlutterBuildTime? = null,
        @DiffMerge val performance: Performance? = null,
        @DiffMerge val accessibility: Accessibility? = null,
    ) {
    }
    data class Usr(
        val id: String? = null,
        val name: String? = null,
        val email: String? = null,
        val anonymousId: String? = null,
        val additionalProperties: MutableMap<String, Any?> = mutableMapOf(),
    ) {
    }
    data class Account(
        val id: String,
        val name: String? = null,
        val additionalProperties: MutableMap<String, Any?> = mutableMapOf(),
    ) {
    }
    data class Connectivity(
        val status: ConnectivityStatus,
        val interfaces: List<Interface>? = null,
        val effectiveType: EffectiveType? = null,
        val cellular: Cellular? = null,
    ) {
    }
    data class Display(
        val viewport: Viewport? = null,
        val scroll: Scroll? = null,
    ) {
    }
    data class Synthetics(
        val testId: String,
        val resultId: String,
        val injected: Boolean? = null,
    ) {
    }
    data class CiTest(
        val testExecutionId: String,
    ) {
    }
    data class Os(
        val name: String,
        val version: String,
        val build: String? = null,
        val versionMajor: String,
    ) {
    }
    data class Device(
        val type: DeviceType? = null,
        val name: String? = null,
        val model: String? = null,
        val brand: String? = null,
        val architecture: String? = null,
        val locale: String? = null,
        val locales: List<String>? = null,
        val timeZone: String? = null,
        val batteryLevel: Number? = null,
        val powerSavingMode: Boolean? = null,
        val brightnessLevel: Number? = null,
        val logicalCpuCount: Number? = null,
        val totalRam: Number? = null,
        val isLowRam: Boolean? = null,
    ) {
    }
    data class Dd(
        val session: DdSession? = null,
        val configuration: Configuration? = null,
        val browserSdkVersion: String? = null,
        val sdkName: String? = null,
        @DiffIgnore val documentVersion: Long,
        val pageStates: List<PageState>? = null,
        val replayStats: ReplayStats? = null,
        val cls: DdCls? = null,
        val profiling: Profiling? = null,
    ) {
        val formatVersion: Long = 2L

    }
    data class Context(
        val additionalProperties: MutableMap<String, Any?> = mutableMapOf(),
    ) {
    }
    data class Stream(
        val id: String,
    ) {
    }
    data class Container(
        val view: ContainerView,
        val source: ViewEventSource,
    ) {
    }
    data class Privacy(
        val replayLevel: ReplayLevel,
    ) {
    }
    data class CustomTimings(
        val additionalProperties: Map<String, Long> = mapOf(),
    ) {
    }
    data class Action(
        val count: Long,
    ) {
    }
    data class Error(
        val count: Long,
    ) {
    }
    data class Crash(
        val count: Long,
    ) {
    }
    data class LongTask(
        val count: Long,
    ) {
    }
    data class FrozenFrame(
        val count: Long,
    ) {
    }
    data class SlowFrame(
        val start: Long,
        val duration: Long,
    ) {
    }
    data class Resource(
        val count: Long,
    ) {
    }
    data class Frustration(
        val count: Long? = null,
    ) {
    }
    data class InForegroundPeriod(
        val start: Long,
        val duration: Long,
    ) {
    }
    data class FlutterBuildTime(
        val min: Number,
        val max: Number,
        val average: Number,
        val metricMax: Number? = null,
    ) {
    }
    data class Performance(
        val cls: PerformanceCls? = null,
        val fcp: Fcp? = null,
        val fid: Fid? = null,
        val inp: Inp? = null,
        val lcp: Lcp? = null,
        val fbc: Fbc? = null,
    ) {
    }
    data class Accessibility(
        val textSize: String? = null,
        val screenReaderEnabled: Boolean? = null,
        val boldTextEnabled: Boolean? = null,
        val reduceTransparencyEnabled: Boolean? = null,
        val reduceMotionEnabled: Boolean? = null,
        val buttonShapesEnabled: Boolean? = null,
        val invertColorsEnabled: Boolean? = null,
        val increaseContrastEnabled: Boolean? = null,
        val assistiveSwitchEnabled: Boolean? = null,
        val assistiveTouchEnabled: Boolean? = null,
        val videoAutoplayEnabled: Boolean? = null,
        val closedCaptioningEnabled: Boolean? = null,
        val monoAudioEnabled: Boolean? = null,
        val shakeToUndoEnabled: Boolean? = null,
        val reducedAnimationsEnabled: Boolean? = null,
        val shouldDifferentiateWithoutColor: Boolean? = null,
        val grayscaleEnabled: Boolean? = null,
        val singleAppModeEnabled: Boolean? = null,
        val onOffSwitchLabelsEnabled: Boolean? = null,
        val speakScreenEnabled: Boolean? = null,
        val speakSelectionEnabled: Boolean? = null,
        val rtlEnabled: Boolean? = null,
    ) {
    }
    data class Cellular(
        val technology: String? = null,
        val carrierName: String? = null,
    ) {
    }
    data class Viewport(
        val width: Number,
        val height: Number,
    ) {
    }
    data class Scroll(
        val maxDepth: Number,
        val maxDepthScrollTop: Number,
        val maxScrollHeight: Number,
        val maxScrollHeightTime: Number,
    ) {
    }
    data class DdSession(
        val plan: Plan? = null,
        val sessionPrecondition: SessionPrecondition? = null,
    ) {
    }
    data class Configuration(
        val sessionSampleRate: Number,
        val sessionReplaySampleRate: Number? = null,
        val profilingSampleRate: Number? = null,
        val traceSampleRate: Number? = null,
        val startSessionReplayRecordingManually: Boolean? = null,
    ) {
    }
    data class PageState(
        val state: State,
        val start: Long,
    ) {
    }
    data class ReplayStats(
        val recordsCount: Long? = 0L,
        val segmentsCount: Long? = 0L,
        val segmentsTotalRawSize: Long? = 0L,
    ) {
    }
    data class DdCls(
        val devicePixelRatio: Number? = null,
    ) {
    }
    data class Profiling(
        val status: ProfilingStatus? = null,
        val errorReason: ErrorReason? = null,
    ) {
    }
    data class ContainerView(
        val id: String,
    ) {
    }
    data class PerformanceCls(
        val score: Number,
        val timestamp: Long? = null,
        val targetSelector: String? = null,
        val previousRect: PreviousRect? = null,
        val currentRect: PreviousRect? = null,
    ) {
    }
    data class Fcp(
        val timestamp: Long,
    ) {
    }
    data class Fid(
        val duration: Long,
        val timestamp: Long,
        val targetSelector: String? = null,
    ) {
    }
    data class Inp(
        val duration: Long,
        val timestamp: Long? = null,
        val targetSelector: String? = null,
        val subParts: InpSubParts? = null,
    ) {
    }
    data class Lcp(
        val timestamp: Long,
        val targetSelector: String? = null,
        var resourceUrl: String? = null,
        val subParts: LcpSubParts? = null,
    ) {
    }
    data class Fbc(
        val timestamp: Long,
    ) {
    }
    data class PreviousRect(
        val x: Number,
        val y: Number,
        val width: Number,
        val height: Number,
    ) {
    }
    data class InpSubParts(
        val inputDelay: Long,
        val processingTime: Long,
        val presentationDelay: Long,
    ) {
    }
    data class LcpSubParts(
        val loadDelay: Long,
        val loadTime: Long,
        val renderDelay: Long,
    ) {
    }
    enum class ViewEventSource {
        ANDROID,
        IOS,
        BROWSER,
        FLUTTER,
        REACT_NATIVE,
        ROKU,
        UNITY,
        KOTLIN_MULTIPLATFORM,
        ELECTRON,
        ;

    }
    enum class ViewEventSessionType {
        USER,
        SYNTHETICS,
        CI_TEST,
        ;

    }
    enum class LoadingType {
        INITIAL_LOAD,
        ROUTE_CHANGE,
        ACTIVITY_DISPLAY,
        ACTIVITY_REDISPLAY,
        FRAGMENT_DISPLAY,
        FRAGMENT_REDISPLAY,
        VIEW_CONTROLLER_DISPLAY,
        VIEW_CONTROLLER_REDISPLAY,
        ;

    }
    enum class ConnectivityStatus {
        CONNECTED,
        NOT_CONNECTED,
        MAYBE,
        ;

    }

    enum class Interface {
        BLUETOOTH,
        CELLULAR,
        ETHERNET,
        WIFI,
        WIMAX,
        MIXED,
        OTHER,
        UNKNOWN,
        NONE,
        ;

    }
    enum class EffectiveType {
        SLOW_2G,
        `2G`,
        `3G`,
        `4G`,
        ;

    }
    enum class DeviceType {
        MOBILE,
        DESKTOP,
        TABLET,
        TV,
        GAMING_CONSOLE,
        BOT,
        OTHER,
        ;

    }
    enum class ReplayLevel {
        ALLOW,
        MASK,
        MASK_USER_INPUT,
        ;

    }
    enum class Plan {
        PLAN_1,
        PLAN_2,
        ;

    }
    enum class SessionPrecondition {
        USER_APP_LAUNCH,
        INACTIVITY_TIMEOUT,
        MAX_DURATION,
        BACKGROUND_LAUNCH,
        PREWARM,
        FROM_NON_INTERACTIVE_SESSION,
        EXPLICIT_STOP,
        ;

    }
    enum class State {
        ACTIVE,
        PASSIVE,
        HIDDEN,
        FROZEN,
        TERMINATED,
        ;

    }
    enum class ProfilingStatus {
        STARTING,
        RUNNING,
        STOPPED,
        ERROR,
        ;

    }
    enum class ErrorReason {
        NOT_SUPPORTED_BY_BROWSER,
        FAILED_TO_LAZY_LOAD,
        MISSING_DOCUMENT_POLICY_HEADER,
        UNEXPECTED_EXCEPTION,
        ;

    }
}
