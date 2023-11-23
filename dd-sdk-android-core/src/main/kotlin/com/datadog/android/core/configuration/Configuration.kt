/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.configuration

import com.datadog.android.Datadog
import com.datadog.android.DatadogSite
import com.datadog.android.core.persistence.PersistenceStrategy
import com.datadog.android.security.Encryption
import com.datadog.android.trace.TracingHeaderType
import okhttp3.Authenticator
import java.net.Proxy

/**
 * An object describing the configuration of the Datadog SDK.
 *
 * This is necessary to initialize the SDK with the [Datadog.initialize] method.
 */
data class Configuration
internal constructor(
    internal val coreConfig: Core,
    internal val clientToken: String,
    internal val env: String,
    internal val variant: String,
    internal val service: String?,
    internal val crashReportsEnabled: Boolean,
    internal val additionalConfig: Map<String, Any>
) {

    internal data class Core(
        val needsClearTextHttp: Boolean,
        val enableDeveloperModeWhenDebuggable: Boolean,
        val firstPartyHostsWithHeaderTypes: Map<String, Set<TracingHeaderType>>,
        val batchSize: BatchSize,
        val uploadFrequency: UploadFrequency,
        val proxy: Proxy?,
        val proxyAuth: Authenticator,
        val encryption: Encryption?,
        val site: DatadogSite,
        val batchProcessingLevel: BatchProcessingLevel,
        val persistenceStrategyFactory: PersistenceStrategy.Factory?
    )

    // region Builder

    /**
     * A Builder class for a [Configuration].
     *
     * @param clientToken your API key of type Client Token
     * @param env the environment name that will be sent with each event. This can be used to
     * filter your events on different environments (e.g.: "staging" vs. "production").
     * @param variant the variant of your application, which should be the value from your
     * `BuildConfig.FLAVOR` constant if you have different flavors, empty string otherwise.
     * @param service the service name (if set to null, it'll be set to your application's
     * package name, e.g.: com.example.android)
     */
    @Suppress("TooManyFunctions")
    class Builder @JvmOverloads constructor(
        private val clientToken: String,
        private val env: String,
        private val variant: String = NO_VARIANT,
        private val service: String? = null
    ) {
        private var additionalConfig: Map<String, Any> = emptyMap()

        private var coreConfig = DEFAULT_CORE_CONFIG
        private var crashReportsEnabled: Boolean = true

        internal var hostsSanitizer = HostsSanitizer()

        /**
         * Builds a [Configuration] based on the current state of this Builder.
         */
        fun build(): Configuration {
            return Configuration(
                coreConfig = coreConfig,
                clientToken = clientToken,
                env = env,
                variant = variant,
                service = service,
                crashReportsEnabled = crashReportsEnabled,
                additionalConfig = additionalConfig
            )
        }

        /**
         * Sets the DataDog SDK to be more verbose when an application is set to `debuggable`.
         * This is equivalent to setting:
         *   setSessionSampleRate(100)
         *   setBatchSize(BatchSize.SMALL)
         *   setUploadFrequency(UploadFrequency.FREQUENT)
         *   Datadog.setVerbosity(Log.VERBOSE)
         * These settings will override your configuration, but only when the application is `debuggable`
         * @param developerModeEnabled Enable or disable extra debug info when an app is debuggable
         */
        @Suppress("FunctionMaxLength")
        fun setUseDeveloperModeWhenDebuggable(developerModeEnabled: Boolean): Builder {
            coreConfig = coreConfig.copy(enableDeveloperModeWhenDebuggable = developerModeEnabled)
            return this
        }

        /**
         * Sets the list of first party hosts.
         * Requests made to a URL with any one of these hosts (or any subdomain) will:
         * - be considered a first party resource and categorised as such in your RUM dashboard;
         * - be wrapped in a Span and have DataDog trace id injected to get a full flame-graph in
         * APM in case of OkHttp instrumentation usage.
         * @param hosts a list of all the hosts that you own.
         */
        fun setFirstPartyHosts(hosts: List<String>): Builder {
            val sanitizedHosts = hostsSanitizer.sanitizeHosts(
                hosts,
                NETWORK_REQUESTS_TRACKING_FEATURE_NAME
            )
            coreConfig = coreConfig.copy(
                firstPartyHostsWithHeaderTypes = sanitizedHosts.associateWith {
                    setOf(
                        TracingHeaderType.DATADOG,
                        TracingHeaderType.TRACECONTEXT
                    )
                }
            )
            return this
        }

        /**
         * Sets the list of first party hosts and specifies the type of HTTP headers used for
         * distributed tracing.
         * Requests made to a URL with any one of these hosts (or any subdomain) will:
         * - be considered a first party resource and categorised as such in your RUM dashboard;
         * - be wrapped in a Span and have trace id of the specified types injected to get a
         * full flame-graph in APM. Multiple header types are supported for each host.
         * @param hostsWithHeaderType a list of all the hosts that you own and the tracing headers
         * to be used for each host.
         * See [DatadogInterceptor]
         */
        fun setFirstPartyHostsWithHeaderType(hostsWithHeaderType: Map<String, Set<TracingHeaderType>>): Builder {
            val sanitizedHosts = hostsSanitizer.sanitizeHosts(
                hostsWithHeaderType.keys.toList(),
                NETWORK_REQUESTS_TRACKING_FEATURE_NAME
            )
            coreConfig = coreConfig.copy(
                firstPartyHostsWithHeaderTypes = hostsWithHeaderType.filterKeys { sanitizedHosts.contains(it) }
            )
            return this
        }

        /**
         * Let the SDK target your preferred Datadog's site.
         */
        fun useSite(site: DatadogSite): Builder {
            coreConfig = coreConfig.copy(needsClearTextHttp = false, site = site)
            return this
        }

        /**
         * Defines the batch size (impacts the size and number of requests performed by Datadog).
         * @param batchSize the desired batch size
         */
        fun setBatchSize(batchSize: BatchSize): Builder {
            coreConfig = coreConfig.copy(batchSize = batchSize)
            return this
        }

        /**
         * Defines the preferred upload frequency.
         * @param uploadFrequency the desired upload frequency policy
         */
        fun setUploadFrequency(uploadFrequency: UploadFrequency): Builder {
            coreConfig = coreConfig.copy(uploadFrequency = uploadFrequency)
            return this
        }

        /**
         * Defines the Batch processing level, defining the maximum number of batches processed
         * sequentially without a delay within one reading/uploading cycle.
         * @param batchProcessingLevel the desired batch processing level. By default it's set to
         * [BatchProcessingLevel.MEDIUM].
         * @see BatchProcessingLevel
         */
        fun setBatchProcessingLevel(batchProcessingLevel: BatchProcessingLevel): Builder {
            coreConfig = coreConfig.copy(batchProcessingLevel = batchProcessingLevel)
            return this
        }

        /**
         * Allows to provide additional configuration values which can be used by the SDK.
         * @param additionalConfig Additional configuration values.
         */
        fun setAdditionalConfiguration(additionalConfig: Map<String, Any>): Builder {
            return apply {
                this.additionalConfig = additionalConfig
            }
        }

        /**
         * Enables a custom proxy for uploading tracked data to Datadog's intake.
         * @param proxy the [Proxy] configuration
         * @param authenticator the optional [Authenticator] for the proxy
         */
        fun setProxy(proxy: Proxy, authenticator: Authenticator?): Builder {
            coreConfig = coreConfig.copy(
                proxy = proxy,
                proxyAuth = authenticator ?: Authenticator.NONE
            )
            return this
        }

        /**
         * Allows to set the encryption for the local data. By default no encryption is used for
         * the local data.
         *
         * @param dataEncryption An encryption object complying [Encryption] interface.
         */
        fun setEncryption(dataEncryption: Encryption): Builder {
            coreConfig = coreConfig.copy(
                encryption = dataEncryption
            )
            return this
        }

        /**
         * Allows to use a custom persistence strategy.
         * @param persistenceStrategyFactory the persistence strategy to use (or null to use the default one)
         */
        fun setPersistenceStrategyFactory(persistenceStrategyFactory: PersistenceStrategy.Factory?): Builder {
            coreConfig = coreConfig.copy(
                persistenceStrategyFactory = persistenceStrategyFactory
            )
            return this
        }

        /**
         * Allows to control if JVM crashes are tracked or not. Default value is [true].
         *
         * @param crashReportsEnabled whether crashes are tracked and sent to Datadog
         */
        fun setCrashReportsEnabled(crashReportsEnabled: Boolean): Builder {
            this.crashReportsEnabled = crashReportsEnabled
            return this
        }

        internal fun allowClearTextHttp(): Builder {
            coreConfig = coreConfig.copy(
                needsClearTextHttp = true
            )
            return this
        }
    }

    // endregion

    companion object {

        /**
         * Value to use if application doesn't have flavors.
         */
        private const val NO_VARIANT: String = ""

        internal val DEFAULT_CORE_CONFIG = Core(
            needsClearTextHttp = false,
            enableDeveloperModeWhenDebuggable = false,
            firstPartyHostsWithHeaderTypes = emptyMap(),
            batchSize = BatchSize.MEDIUM,
            uploadFrequency = UploadFrequency.AVERAGE,
            proxy = null,
            proxyAuth = Authenticator.NONE,
            encryption = null,
            site = DatadogSite.US1,
            batchProcessingLevel = BatchProcessingLevel.MEDIUM,
            persistenceStrategyFactory = null
        )

        internal const val NETWORK_REQUESTS_TRACKING_FEATURE_NAME = "Network requests"
    }
}
