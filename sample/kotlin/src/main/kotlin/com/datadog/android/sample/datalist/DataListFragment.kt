package com.datadog.android.sample.datalist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.datadog.android.sample.R

class DataListFragment : Fragment() {

    lateinit var viewModel: DataListViewModel
    lateinit var recyclerView: RecyclerView

    // region Fragment

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_data_list, container, false)
        recyclerView = rootView.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)
        viewModel = ViewModelProviders.of(this).get(DataListViewModel::class.java)
        recyclerView.adapter = Adapter(viewModel.data)
        return rootView
    }

    // endregion

    // region adapter

    internal inner class Adapter(val data: Array<String>) :
        RecyclerView.Adapter<Adapter.ViewHolder>() {

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
