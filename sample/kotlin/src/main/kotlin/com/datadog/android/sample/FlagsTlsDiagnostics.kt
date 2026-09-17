/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.sample

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.http.X509TrustManagerExtensions
import android.os.Build
import android.util.Log
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.OkHttpClient
import org.xmlpull.v1.XmlPullParser
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** Credential-free probes only. Never installed on the SDK or authenticated POST client. */
@SuppressLint("LogNotTimber")
@Suppress("MagicNumber", "TooGenericExceptionCaught")
internal object FlagsTlsDiagnostics {
    private const val TAG = "FlagsDiagnostics"

    fun logEnvironment(context: Context) {
        val info = context.applicationInfo
        Log.i(
            TAG,
            "Android: release=${Build.VERSION.RELEASE} api=${Build.VERSION.SDK_INT} " +
                "securityPatch=${Build.VERSION.SECURITY_PATCH} model=${Build.MODEL} hardware=${Build.HARDWARE}"
        )
        Log.i(
            TAG,
            "App: targetSdk=${info.targetSdkVersion} " +
                "debuggable=${info.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0}"
        )
        Log.i(TAG, "Clock: ${Date()} epochMillis=${System.currentTimeMillis()}")
        try {
            context.assets.openXmlResourceParser("AndroidManifest.xml").use { manifest ->
                while (manifest.next() != XmlPullParser.END_DOCUMENT) {
                    if (manifest.eventType == XmlPullParser.START_TAG && manifest.name == "application") {
                        val config = manifest.getAttributeResourceValue(
                            "http://schemas.android.com/apk/res/android",
                            "networkSecurityConfig",
                            0
                        )
                        val name = if (config == 0) "platform default" else context.resources.getResourceName(config)
                        Log.i(TAG, "Network security config: $name")
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Network security config resource unavailable", e)
        }
        try {
            val store = KeyStore.getInstance("AndroidCAStore").apply { load(null) }
            val aliases = store.aliases().toList()
            Log.i(
                TAG,
                "Android CA store: total=${aliases.size} " +
                    "system=${aliases.count { it.startsWith("system:") }} " +
                    "user=${aliases.count { it.startsWith("user:") }} " +
                    "(installed CAs, not necessarily trusted by this app)"
            )
        } catch (e: Exception) {
            Log.w(TAG, "CA inventory unavailable", e)
        }
    }

    fun client(base: OkHttpClient, label: String, url: String): OkHttpClient {
        val host = URI(url).host
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        val platform = factory.trustManagers.filterIsInstance<X509TrustManager>().single()
        val extensions = X509TrustManagerExtensions(platform)
        val recording = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = platform.acceptedIssuers

            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                platform.checkClientTrusted(chain, authType)
            }

            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                Log.i(TAG, "TLS [$label] presented chain: count=${chain.size} authType=$authType host=$host")
                chain.forEachIndexed { index, certificate -> logCertificate(label, index, certificate) }
                // Use Android's hostname-aware validation, including domain-specific trust configuration.
                // Never swallow a validation exception or accept an untrusted certificate.
                try {
                    val validated = extensions.checkServerTrusted(chain, authType, host)
                    Log.i(TAG, "TLS [$label] platform trust PASSED; validated chain length=${validated.size}")
                    validated.lastOrNull()?.let {
                        Log.i(
                            TAG,
                            "TLS [$label] validated chain terminus: ${it.subjectX500Principal} " +
                                "sha256=${fingerprint(it.encoded)}"
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "TLS [$label] platform trust FAILED", e)
                    throw e
                }
            }

            // OkHttp's Android chain cleaner uses this hostname-aware overload via reflection.
            @Suppress("unused")
            fun checkServerTrusted(
                chain: Array<X509Certificate>,
                authType: String,
                hostname: String
            ): List<X509Certificate> = extensions.checkServerTrusted(chain, authType, hostname)
        }
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(recording), null) }
        Log.i(TAG, "TLS [$label] provider=${ssl.provider.name} trustManager=${platform.javaClass.name}")
        val proxies = base.proxy?.let { listOf(it) } ?: base.proxySelector.select(URI(url))
        Log.i(TAG, "Proxy [$label] selected=$proxies (DIRECT does not rule out transparent inspection/VPN)")
        return base.newBuilder()
            .sslSocketFactory(ssl.socketFactory, recording)
            .connectionPool(okhttp3.ConnectionPool())
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .callTimeout(20, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .eventListener(object : EventListener() {
                override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
                    Log.i(TAG, "Route [$label] remote=$inetSocketAddress proxy=$proxy")
                }

                override fun secureConnectEnd(call: Call, handshake: Handshake?) {
                    Log.i(TAG, "TLS [$label] negotiated=${handshake?.tlsVersion} cipher=${handshake?.cipherSuite}")
                }
            })
            .build()
    }

    private fun logCertificate(label: String, index: Int, certificate: X509Certificate) {
        try {
            val prefix = "TLS [$label] certificate[$index]"
            Log.i(TAG, "$prefix subject=${certificate.subjectX500Principal}")
            Log.i(TAG, "$prefix issuer=${certificate.issuerX500Principal}")
            Log.i(
                TAG,
                "$prefix serial=${certificate.serialNumber.toString(16)} " +
                    "CA=${certificate.basicConstraints >= 0} signature=${certificate.sigAlgName}"
            )
            Log.i(TAG, "$prefix validFrom=${certificate.notBefore} validUntil=${certificate.notAfter}")
            Log.i(TAG, "$prefix certificateSha256=${fingerprint(certificate.encoded)}")
            Log.i(TAG, "$prefix spkiSha256Hex=${fingerprint(certificate.publicKey.encoded)}")
            certificate.subjectAlternativeNames?.forEach { Log.i(TAG, "$prefix SAN=$it") }
        } catch (e: Exception) {
            // Metadata logging must not replace the actual trust decision.
            Log.w(TAG, "TLS [$label] certificate[$index] metadata unavailable", e)
        }
    }

    private fun fingerprint(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(":") { "%02X".format(it) }
}
