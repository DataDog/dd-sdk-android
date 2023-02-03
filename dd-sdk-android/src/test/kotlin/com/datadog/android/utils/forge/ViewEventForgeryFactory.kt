/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.core.internal.system.AndroidInfoProvider
import com.datadog.android.rum.internal.domain.scope.toViewSchemaType
import com.datadog.android.rum.model.ViewEvent
import com.datadog.tools.unit.forge.exhaustiveAttributes
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import fr.xgouchet.elmyr.jvm.ext.aTimestamp
import java.net.URL
import java.util.UUID

internal class ViewEventForgeryFactory : ForgeryFactory<ViewEvent> {

    override fun getForgery(forge: Forge): ViewEvent {
        return ViewEvent(
            date = forge.aTimestamp(),
            view = ViewEvent.View(
                id = forge.getForgery<UUID>().toString(),
                url = forge.aStringMatching("https://[a-z]+.[a-z]{3}/[a-z0-9_/]+"),
                referrer = forge.aNullable { getForgery<URL>().toString() },
                name = forge.aNullable { anAlphabeticalString() },
                timeSpent = forge.aPositiveLong(),
                error = ViewEvent.Error(forge.aPositiveLong()),
                crash = forge.aNullable { ViewEvent.Crash(aPositiveLong()) },
                action = ViewEvent.Action(forge.aPositiveLong()),
                resource = ViewEvent.Resource(forge.aPositiveLong()),
                longTask = forge.aNullable { ViewEvent.LongTask(forge.aPositiveLong()) },
                frozenFrame = forge.aNullable { ViewEvent.FrozenFrame(aPositiveLong()) },
                loadingType = forge.aNullable(),
                loadingTime = forge.aNullable { aPositiveLong() },
                firstContentfulPaint = forge.aNullable { aPositiveLong() },
                largestContentfulPaint = forge.aNullable { aPositiveLong() },
                firstInputDelay = forge.aNullable { aPositiveLong() },
                firstInputTime = forge.aNullable { aPositiveLong() },
                cumulativeLayoutShift = forge.aNullable { aPositiveLong() },
                domComplete = forge.aNullable { aPositiveLong() },
                domContentLoaded = forge.aNullable { aPositiveLong() },
                domInteractive = forge.aNullable { aPositiveLong() },
                loadEvent = forge.aNullable { aPositiveLong() },
                customTimings = forge.aNullable {
                    ViewEvent.CustomTimings(
                        aMap { anAlphabeticalString() to aLong() }.toMutableMap()
                    )
                },
                isActive = forge.aNullable { aBool() },
                isSlowRendered = forge.aNullable { aBool() },
                inForegroundPeriods = forge.aNullable {
                    aList {
                        ViewEvent.InForegroundPeriod(
                            start = aPositiveLong(),
                            duration = aPositiveLong()
                        )
                    }
                },
                memoryAverage = forge.aNullable { aPositiveDouble() },
                memoryMax = forge.aNullable { aPositiveDouble() },
                cpuTicksCount = forge.aNullable { aPositiveDouble() },
                cpuTicksPerSecond = forge.aNullable { aPositiveDouble() },
                refreshRateAverage = forge.aNullable { aPositiveDouble() },
                refreshRateMin = forge.aNullable { aPositiveDouble() },
                frustration = forge.aNullable { ViewEvent.Frustration(aPositiveLong()) }
            ),
            connectivity = forge.aNullable {
                ViewEvent.Connectivity(
                    status = getForgery(),
                    interfaces = aList { getForgery() },
                    cellular = aNullable {
                        ViewEvent.Cellular(
                            technology = aNullable { anAlphabeticalString() },
                            carrierName = aNullable { anAlphabeticalString() }
                        )
                    }
                )
            },
            synthetics = forge.aNullable {
                ViewEvent.Synthetics(
                    testId = forge.anHexadecimalString(),
                    resultId = forge.anHexadecimalString()
                )
            },
            usr = forge.aNullable {
                ViewEvent.Usr(
                    id = aNullable { anHexadecimalString() },
                    name = aNullable { aStringMatching("[A-Z][a-z]+ [A-Z]\\. [A-Z][a-z]+") },
                    email = aNullable { aStringMatching("[a-z]+\\.[a-z]+@[a-z]+\\.[a-z]{3}") },
                    additionalProperties = exhaustiveAttributes()
                )
            },
            application = ViewEvent.Application(forge.getForgery<UUID>().toString()),
            service = forge.aNullable { anAlphabeticalString() },
            session = ViewEvent.ViewEventSession(
                id = forge.getForgery<UUID>().toString(),
                type = ViewEvent.ViewEventSessionType.USER,
                hasReplay = forge.aNullable { aBool() }
            ),
            source = forge.aNullable { aValueFrom(ViewEvent.Source::class.java) },
            ciTest = forge.aNullable {
                ViewEvent.CiTest(anHexadecimalString())
            },
            os = forge.aNullable {
                val androidInfoProvider = getForgery(AndroidInfoProvider::class.java)
                ViewEvent.Os(
                    name = androidInfoProvider.osName,
                    version = androidInfoProvider.osVersion,
                    versionMajor = androidInfoProvider.osMajorVersion
                )
            },
            device = forge.aNullable {
                val androidInfoProvider = getForgery(AndroidInfoProvider::class.java)
                ViewEvent.Device(
                    name = androidInfoProvider.deviceName,
                    model = androidInfoProvider.deviceModel,
                    brand = androidInfoProvider.deviceBrand,
                    type = androidInfoProvider.deviceType.toViewSchemaType(),
                    architecture = androidInfoProvider.architecture
                )
            },
            context = forge.aNullable {
                ViewEvent.Context(
                    additionalProperties = exhaustiveAttributes()
                )
            },
            dd = ViewEvent.Dd(
                session = forge.aNullable { ViewEvent.DdSession(getForgery()) },
                browserSdkVersion = forge.aNullable { aStringMatching("\\d+\\.\\d+\\.\\d+") },
                documentVersion = forge.aPositiveLong(strict = true)
            )
        )
    }
}
