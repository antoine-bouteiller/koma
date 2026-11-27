package app.koma.source

import app.koma.data.MediaDetails
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource

/** Proves that a source manga is a backend media. */
fun interface Finder {
    /** The source's manga for [details], or null when the source cannot prove one is. */
    suspend fun find(details: MediaDetails): SManga?
}

/** A source Koma reads chapters from, and how it finds a backend media there. */
class CatalogSource(val source: HttpSource, val finder: Finder? = null)
