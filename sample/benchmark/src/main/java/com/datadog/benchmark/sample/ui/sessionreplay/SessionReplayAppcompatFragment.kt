/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.sessionreplay

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckedTextView
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.NumberPicker
import android.widget.Spinner
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.datadog.benchmark.sample.MainActivity
import com.datadog.benchmark.sample.navigation.FragmentsNavigationManager
import com.datadog.sample.benchmark.R
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import java.util.Locale
import javax.inject.Inject

internal class SessionReplayAppcompatFragment : Fragment() {

    @Inject
    internal lateinit var fragmentsNavigationManager: FragmentsNavigationManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        (requireActivity() as MainActivity).benchmarkActivityComponent.inject(this)

        return inflater.inflate(R.layout.fragment_session_replay_compat, container, false).apply {
            findViewById<CheckedTextView>(R.id.checked_text_view).apply {
                setOnClickListener { this.toggle() }
            }

            findViewById<Spinner>(R.id.default_spinner)?.let { spinner ->
                ArrayAdapter.createFromResource(
                    requireContext(),
                    R.array.planets_array,
                    android.R.layout.simple_spinner_item
                ).also { adapter ->
                    // Specify the layout to use when the list of choices appears
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    // Apply the adapter to the spinner
                    spinner.adapter = adapter
                }
            }

            val defaultNumberPicker: NumberPicker = findViewById(R.id.default_number_picker)
            val defaultPickerMinIndex = NUMBER_PICKER_INDEX_MIN
            val defaultPickerMaxIndex = NUMBER_PICKER_INDEX_MAX
            defaultNumberPicker.minValue = defaultPickerMinIndex
            defaultNumberPicker.maxValue = defaultPickerMaxIndex
            initTimePicker(this)
            findViewById<ImageView>(R.id.imageView_remote).apply {
                loadImage(this)
            }

            findViewById<Button>(R.id.button_navigation).apply {
                setOnClickListener {
                    fragmentsNavigationManager.navigateToSessionReplayMaterial()
                }
            }
        }
    }

    private fun initTimePicker(rootView: View) {
        val setTimeTextView = rootView.findViewById<EditText>(R.id.set_time_text_view)
        val setDateTextView = rootView.findViewById<EditText>(R.id.set_date_text_view)

        rootView.findViewById<ImageButton>(R.id.set_time_button).setOnClickListener {
            activity?.supportFragmentManager?.let {
                MaterialTimePicker.Builder().build().apply {
                    addOnPositiveButtonClickListener {
                        val hour = this.hour
                        val minute = this.minute
                        setTimeTextView.setText(
                            String.format(
                                Locale.US,
                                "%s : %s",
                                hour,
                                minute
                            )
                        )
                    }
                }.show(it, null)
            }
        }
        rootView.findViewById<ImageButton>(R.id.set_date_button).setOnClickListener {
            activity?.supportFragmentManager?.let {
                MaterialDatePicker.Builder.datePicker().build().apply {
                    addOnPositiveButtonClickListener {
                        setDateTextView.setText(this.headerText)
                    }
                }.show(it, null)
            }
        }
    }

    private fun loadImage(imageView: ImageView) {
        Glide.with(imageView)
            .load("https://picsum.photos/800/450")
            .placeholder(R.drawable.ic_dd_icon_rgb)
            .fitCenter()
            .into(object : CustomTarget<Drawable>() {
                override fun onLoadCleared(placeholder: Drawable?) {}
                override fun onResourceReady(
                    resource: Drawable,
                    transition: Transition<in Drawable>?
                ) {
                    imageView.setImageDrawable(resource)
                }
            })
    }

    companion object {
        private const val NUMBER_PICKER_INDEX_MIN = 0
        private const val NUMBER_PICKER_INDEX_MAX = 10
    }
}
