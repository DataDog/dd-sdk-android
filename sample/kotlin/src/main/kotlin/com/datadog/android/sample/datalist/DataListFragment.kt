package com.datadog.android.sample.datalist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.datadog.android.sample.R
import com.google.android.material.floatingactionbutton.FloatingActionButton

class DataListFragment : Fragment() {

    lateinit var viewModel: DataListViewModel
    lateinit var recyclerView: RecyclerView
    lateinit var fab: FloatingActionButton

    internal val adapter = Adapter()

    // region Fragment

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProviders.of(this).get(DataListViewModel::class.java)

        viewModel.observeLiveData().observe(viewLifecycleOwner, Observer {
            adapter.updateData(it)
        })
    }

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
        fab.setOnClickListener { viewModel.onAddData() }
        return rootView
    }

    // endregion

    // region adapter

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
    companion object {
        fun newInstance(): DataListFragment {
            return DataListFragment()
        }
    }
}
