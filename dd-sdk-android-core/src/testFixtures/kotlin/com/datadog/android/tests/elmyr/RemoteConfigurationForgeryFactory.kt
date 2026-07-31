/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tests.elmyr

import com.datadog.android.core.internal.remote.model.RemoteConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import java.util.UUID

class RemoteConfigurationForgeryFactory : ForgeryFactory<RemoteConfiguration> {
    override fun getForgery(forge: Forge): RemoteConfiguration {
        return RemoteConfiguration(
            rum = forge.aNullable {
                RemoteConfiguration.Rum(
                    applicationId = getForgery<UUID>().toString(),
                    telemetrySampleRate = aNullable { anInt(min = 0, max = 100) },
                    trackAnonymousUser = aNullable { aBool() },
                    trackUserInteractions = aNullable { aBool() },
                    trackBackgroundEvents = aNullable { aBool() },
                    trackFrustrations = aNullable { aBool() },
                    longTask = aNullable {
                        RemoteConfiguration.LongTask(
                            enabled = aNullable { aBool() },
                            threshold = aNullable { anInt(min = 100) }
                        )
                    },
                    vitalsUpdateFrequency = aNullable {
                        aValueFrom(RemoteConfiguration.VitalsUpdateFrequency::class.java)
                    },
                    trackSlowFrames = aNullable { aBool() },
                    crashReportsEnabled = aNullable { aBool() },
                    trackNonFatalAnrs = aNullable { aBool() }
                )
            },
            sessionReplay = forge.aNullable {
                RemoteConfiguration.SessionReplay(
                    sampleRate = aNullable { anInt(min = 0, max = 100) },
                    textAndInputPrivacy = aNullable {
                        aValueFrom(RemoteConfiguration.TextAndInputPrivacy::class.java)
                    },
                    touchPrivacy = aNullable {
                        aValueFrom(RemoteConfiguration.TouchPrivacy::class.java)
                    },
                    imagePrivacy = aNullable {
                        aValueFrom(RemoteConfiguration.ImagePrivacy::class.java)
                    }
                )
            },
            profiling = forge.aNullable {
                RemoteConfiguration.Profiling(
                    continuousSampleRate = aNullable { anInt(min = 0, max = 100) },
                    applicationLaunchSampleRate = aNullable { anInt(min = 0, max = 100) }
                )
            },
            trace = forge.aNullable {
                RemoteConfiguration.Trace(
                    sampleRate = aNullable { anInt(min = 0, max = 100) },
                    traceContextInjection = aNullable {
                        aValueFrom(RemoteConfiguration.TraceContextInjection::class.java)
                    },
                    tracedHosts = aNullable {
                        aList {
                            RemoteConfiguration.TracedHost(
                                host = anAlphabeticalString(),
                                propagatorTypes = aList(size = anInt(min = 1, max = 4)) {
                                    aValueFrom(RemoteConfiguration.PropagatorType::class.java)
                                }
                            )
                        }
                    }
                )
            }
        )
    }
}
