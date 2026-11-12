/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.network.rickandmorty.fallback

import android.content.Context
import java.io.FileNotFoundException
import java.io.InputStream

/** Opens a path relative to the rickandmorty snapshot folder, or returns null if absent. */
internal fun interface RickAndMortySnapshotSource {
    fun open(path: String): InputStream?
}

internal class AssetsRickAndMortySnapshotSource(context: Context) : RickAndMortySnapshotSource {
    private val assets = context.assets

    override fun open(path: String): InputStream? = try {
        assets.open("rickandmorty/$path")
    } catch (_: FileNotFoundException) {
        null
    }
}
