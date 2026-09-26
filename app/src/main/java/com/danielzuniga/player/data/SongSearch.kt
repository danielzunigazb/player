package com.danielzuniga.player.data

import java.text.Normalizer

/**
 * Case- and accent-insensitive match ("cancion" finds "Canción") over [items]. Each item's
 * [fields] are normalized once, on the first search, not again on every keystroke.
 */
class SearchIndex<T>(private val items: List<T>, private val fields: (T) -> List<String>) {

    private val keys: List<List<String>> by lazy { items.map { item -> fields(item).map { it.normalizedForSearch() } } }

    fun filter(query: String): List<T> {
        val needle = query.normalizedForSearch()
        if (needle.isEmpty()) return items
        return items.filterIndexed { i, _ -> keys[i].any { it.contains(needle) } }
    }
}

private val DIACRITICS = "\\p{Mn}+".toRegex()

internal fun String.normalizedForSearch(): String =
    Normalizer.normalize(trim(), Normalizer.Form.NFD).replace(DIACRITICS, "").lowercase()
