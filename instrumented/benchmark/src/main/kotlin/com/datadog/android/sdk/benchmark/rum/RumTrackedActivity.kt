/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.benchmark.rum

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.datadog.android.sdk.benchmark.R

internal class RumTrackedActivity : Activity() {

    lateinit var recyclerView: RecyclerView
    private val data = arrayOf(
        "Item 1",
        "Item 2",
        "Item 3",
        "Item 4",
        "Item 5",
        "Item 6"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.rum_activity_layout)
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = Adapter(data)
    }

    internal inner class Adapter(val data: Array<String>) :
        RecyclerView.Adapter<Adapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(
                LayoutInflater.from(parent.context).inflate(
                    R.layout.item_layout,
                    parent,
                    false
                )
            )
        }

        override fun getItemCount(): Int {
            return data.size
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.render(data[position])
        }

        internal inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {

            fun render(model: String) {
                view.findViewById<TextView>(R.id.textView).setText(model)
            }
        }
    }
}
