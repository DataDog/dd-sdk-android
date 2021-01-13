/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.rum

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.datadog.android.Datadog
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.sdk.integration.R
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.utils.getTrackingConsent

internal class GesturesTrackingPlaygroundActivity : AppCompatActivity() {

    lateinit var showHide: View
    lateinit var button: Button
    lateinit var recyclerView: RecyclerView
    private var adapter = Adapter()
    internal var adapterData: MutableList<String> = MutableList(100) {
        "Item $it"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val credentials = RuntimeConfig.credentials()
        val config = RuntimeConfig.configBuilder()
            .trackInteractions()
            .useViewTrackingStrategy(ActivityViewTrackingStrategy(true))
            .build()
        val trackingConsent = intent.getTrackingConsent()

        Datadog.initialize(this, credentials, config, trackingConsent)
        Datadog.setVerbosity(Log.VERBOSE)

        GlobalRum.registerIfAbsent(RumMonitor.Builder().build())

        setContentView(R.layout.gestures_tracking_layout)

        showHide = findViewById(R.id.show_hide)
        button = findViewById(R.id.button)
        button.setOnClickListener { toggleVisibility() }
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    toggleVisibility()
                }
            }
        )
        adapter.updateData(adapterData)
    }

    private fun toggleVisibility() {
        if (showHide.visibility == View.VISIBLE) {
            showHide.visibility = View.GONE
        } else {
            showHide.visibility = View.VISIBLE
        }
    }

    // region Adapter

    internal inner class Adapter :
        RecyclerView.Adapter<Adapter.ViewHolder>() {

        private val data: MutableList<String> = mutableListOf()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val itemView = LayoutInflater.from(parent.context).inflate(
                R.layout.item_layout,
                parent,
                false
            )
            return ViewHolder(
                itemView
            )
        }

        override fun getItemCount(): Int {
            return data.size
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.render(data[position])
        }

        internal fun updateData(newData: List<String>) {
            data.clear()
            data.addAll(newData)
            notifyDataSetChanged()
        }

        internal inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
            lateinit var model: String

            init {
                view.setOnClickListener {
                    toggleVisibility()
                    Toast.makeText(view.context, "$model was clicked", Toast.LENGTH_SHORT)
                        .show()
                }
            }

            fun render(model: String) {
                this.model = model
                view.findViewById<TextView>(R.id.textView).setText(model)
            }
        }
    }

    // endregion
}
