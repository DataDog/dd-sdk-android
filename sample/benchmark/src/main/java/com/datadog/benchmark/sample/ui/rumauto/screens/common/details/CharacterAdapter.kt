/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.screens.common.details

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.datadog.benchmark.sample.network.rickandmorty.models.Character
import com.datadog.sample.benchmark.databinding.ItemCharacterBinding

internal class CharacterAdapter(
    private val onCharacterClicked: (Character) -> Unit
) : ListAdapter<Character, CharacterAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCharacterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onCharacterClicked)
    }

    internal class ViewHolder(private val binding: ItemCharacterBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(character: Character, onCharacterClicked: (Character) -> Unit) {
            Glide.with(binding.root.context)
                .load(character.image)
                .into(binding.characterImage)
            binding.characterName.text = character.name
            binding.root.setOnClickListener { onCharacterClicked(character) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<Character>() {
        override fun areItemsTheSame(oldItem: Character, newItem: Character): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Character, newItem: Character): Boolean = oldItem == newItem
    }
}
