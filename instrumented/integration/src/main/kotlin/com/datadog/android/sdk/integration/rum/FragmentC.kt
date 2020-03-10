package com.datadog.android.sdk.integration.rum

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.datadog.android.sdk.integration.R

internal class FragmentC : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_layout, container, false)
        view.findViewById<TextView>(R.id.textView).setText(R.string.fragment_c)
        return view
    }
}
