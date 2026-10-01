package eu.kanade.tachiyomi.animeextension.zh.anime1

import kotlinx.serialization.json.Json

internal val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}
