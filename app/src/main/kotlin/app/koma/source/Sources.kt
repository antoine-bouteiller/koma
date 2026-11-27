package app.koma.source

import app.koma.source.atsumaru.AtsumaruFinder
import app.koma.source.atsumaru.AtsumaruSource

/** Fresh instances on each call: a source keeps the client of the container it first ran under. */
fun sources(): List<CatalogSource> {
    val atsumaru = AtsumaruSource()
    return listOf(CatalogSource(atsumaru, AtsumaruFinder(atsumaru)))
}
