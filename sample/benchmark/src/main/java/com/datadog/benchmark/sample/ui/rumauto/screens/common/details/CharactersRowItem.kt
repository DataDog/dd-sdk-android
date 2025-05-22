/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.screens.common.details

import android.view.LayoutInflater
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.datadog.benchmark.sample.network.rickandmorty.models.Character
import com.datadog.benchmark.sample.utils.recycler.BaseRecyclerViewItem
import com.datadog.sample.benchmark.databinding.ItemCharacterBinding
import com.datadog.sample.benchmark.databinding.ItemCharactersRowBinding
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding

internal data class CharactersRowItem(
    val characters: List<Character>, // TODO WAHAHA maybe not the whole character?
    override val key: String
): BaseRecyclerViewItem

internal fun charactersRowItemDelegate(
    onCharacterClicked: (Character) -> Unit,
) = adapterDelegateViewBinding<CharactersRowItem, BaseRecyclerViewItem, ItemCharactersRowBinding>(
    { layoutInflater, root -> ItemCharactersRowBinding.inflate(layoutInflater, root, false) }
) {
    bind {
        binding.root.removeAllViews()

        item.characters.forEach { character ->
            val characterView = ItemCharacterBinding.inflate(LayoutInflater.from(context), binding.root, false)
            characterView.characterName.text = character.name

            Glide.with(context)
                .load(character.image)
                .into(characterView.characterImage)

            characterView.root.setOnClickListener {
                onCharacterClicked(character)
            }

            binding.root.addView(characterView.root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }
}
