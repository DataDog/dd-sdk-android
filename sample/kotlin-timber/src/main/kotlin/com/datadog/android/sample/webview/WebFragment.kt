/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */
package com.datadog.android.sample.webview

import android.annotation.TargetApi
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JsResult
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import com.datadog.android.sample.R
import timber.log.Timber

class WebFragment : Fragment() {
    private lateinit var viewModel: WebViewModel
    private lateinit var webView: WebView

    // region Fragment Lifecycle

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_web, container, false)
        webView = rootView.findViewById(R.id.webview)
        webView.webViewClient = mWebViewClient
        webView.webChromeClient = webChromeClient
        return rootView
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProviders.of(this).get(WebViewModel::class.java)
    }

    override fun onStart() {
        super.onStart()
        webView.loadUrl(viewModel.url)
    }

    // endregion

    // region WebViewClient
    private val mWebViewClient: WebViewClient = object : WebViewClient() {
        override fun onPageStarted(
            view: WebView,
            url: String,
            favicon: Bitmap?
        ) {
            super.onPageStarted(view, url, favicon)
            Timber.d("onPageStarted")
        }

        override fun onPageFinished(
            view: WebView,
            url: String
        ) {
            super.onPageFinished(view, url)
            Timber.d("onPageFinished")
        }

        override fun onLoadResource(
            view: WebView,
            url: String
        ) {
            super.onLoadResource(view, url)
            Timber.d("loading resource")
        }

        @TargetApi(Build.VERSION_CODES.M)
        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            super.onReceivedError(view, request, error)
            Timber.e("received error")
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse
        ) {
            super.onReceivedHttpError(view, request, errorResponse)
            Timber.e("received HTTP error")
        }

        override fun onReceivedSslError(
            view: WebView,
            handler: SslErrorHandler,
            error: SslError
        ) {
            super.onReceivedSslError(view, handler, error)
            Timber.e("received SSL error")
        }
    }

    // endregion

    // region WebChromeClient

    private val webChromeClient: WebChromeClient = object : WebChromeClient() {

        override fun onProgressChanged(
            view: WebView,
            newProgress: Int
        ) {
            super.onProgressChanged(view, newProgress)
            Timber.v("onProgressChanged")
        }

        override fun onReceivedTitle(
            view: WebView,
            title: String
        ) {
            super.onReceivedTitle(view, title)
            Timber.v("onReceivedTitle $title")
        }

        override fun onReceivedIcon(
            view: WebView,
            icon: Bitmap
        ) {
            super.onReceivedIcon(view, icon)
            Timber.v("event: onReceivedIcon")
        }

        override fun onJsAlert(
            view: WebView,
            url: String,
            message: String,
            result: JsResult
        ): Boolean {
            Timber.w("onJsAlert")
            return super.onJsAlert(view, url, message, result)
        }

        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            Timber.w("onConsoleMessage")
            return super.onConsoleMessage(consoleMessage)
        }
    }

    // region

    companion object {
        fun newInstance(): WebFragment {
            return WebFragment()
        }
    }
}
