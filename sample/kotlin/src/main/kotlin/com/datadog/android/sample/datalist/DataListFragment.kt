/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.datalist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.datadog.android.Datadog
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.sample.R
import com.datadog.android.sample.SampleApplication
import com.datadog.android.sample.data.model.Log
import com.google.android.material.floatingactionbutton.FloatingActionButton

internal class DataListFragment : Fragment() {

    lateinit var viewModel: DataListViewModel
    lateinit var recyclerView: RecyclerView
    lateinit var fab: FloatingActionButton

    internal val adapter = Adapter()
    private var firstDataWasLoaded = false

    // region Fragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    @OptIn(ExperimentalRumApi::class)
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_data_list, container, false)
        recyclerView = rootView.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
        fab = rootView.findViewById(R.id.fab)
        viewModel =
            SampleApplication.getViewModelFactory(requireContext()).create(
                DataListViewModel::class.java
            )
        viewModel.observeLiveData().observe(
            viewLifecycleOwner,
            Observer {
                when (it) {
                    is DataListViewModel.UIResponse.Success -> {
                        if (!firstDataWasLoaded) {
                            GlobalRumMonitor.get(Datadog.getInstance()).addTiming("logs_data_loaded")
                            GlobalRumMonitor.get().addViewLoadingTime(overwrite = false)
                            firstDataWasLoaded = true
                        }
                        adapter.updateData(it.data)
                    }
                    is DataListViewModel.UIResponse.Error -> {
                        Toast.makeText(context, it.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
        fab.setOnClickListener { loadData() }
        loadData()
        return rootView
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        val currentType = viewModel.getDataSource()
        inflater.inflate(R.menu.data_list, menu)
        val disabled = when (currentType) {
            DataSourceType.REALM -> R.id.data_source_realm
            DataSourceType.ROOM -> R.id.data_source_room
            DataSourceType.SQLITE -> R.id.data_source_sqlite
            DataSourceType.SQLDELIGHT -> R.id.data_source_sqldelight
        }
        menu.findItem(disabled).isEnabled = false
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val type = when (item.itemId) {
            R.id.data_source_realm -> DataSourceType.REALM
            R.id.data_source_room -> DataSourceType.ROOM
            R.id.data_source_sqlite -> DataSourceType.SQLITE
            R.id.data_source_sqldelight -> DataSourceType.SQLDELIGHT
            else -> null
        }
        return if (type == null) {
            super.onOptionsItemSelected(item)
        } else {
            viewModel.selectDataSource(type)
            Toast.makeText(
                context,
                "Change will be effective after the application restarts.",
                Toast.LENGTH_LONG
            ).show()
            true
        }
    }

    private fun loadData() {
        viewModel.performRequest(DataListViewModel.UIRequest.FetchData)
    }

    // endregion

    // region adapter

    internal inner class Adapter :
        RecyclerView.Adapter<Adapter.ViewHolder>() {

        private val data: MutableList<Log> = mutableListOf()

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

        internal fun updateData(newData: List<Log>) {
            data.clear()
            data.addAll(newData)
            notifyDataSetChanged()
        }

        internal inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
            lateinit var model: Log

            init {
                view.setOnClickListener {
                    Toast.makeText(
                        view.context,
                        "${model.attributes.message} was clicked",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            fun render(model: Log) {
                this.model = model
                view.findViewById<TextView>(R.id.title).text = model.attributes.timestamp
                view.findViewById<TextView>(R.id.body).text = model.attributes.message
            }
        }
    }

    // endregion
}
