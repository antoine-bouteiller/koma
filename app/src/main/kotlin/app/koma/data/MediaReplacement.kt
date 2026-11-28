package app.koma.data

internal fun KomaDatabase.canonical(ref: MediaRef): MediaRef {
    val survivor = mediaReplacementQueries.survivor(ref.backend.name, ref.id).executeAsOneOrNull()
    return survivor?.let { MediaRef(ref.backend, it) } ?: ref
}

// Every table storing a media id must participate here.
internal fun KomaDatabase.replaceMedia(from: MediaRef, to: MediaRef) {
    require(from.backend == to.backend && from != to)
    val old = canonical(from)
    val survivor = canonical(to)
    if (old == survivor) return
    val backend = old.backend.name
    with(mediaReplacementQueries) {
        replaceLibrary(survivor.id, backend, old.id)
        deleteLibrary(backend, old.id)
        deleteMedia(backend, old.id)
        deleteDetails(backend, old.id)
        replaceEvents(survivor.id, backend, old.id)
        replaceBinding(survivor.id, backend, old.id)
        deleteBinding(backend, old.id)
        replaceUnmatched(survivor.id, backend, old.id)
        deleteUnmatched(backend, old.id)
        if (bindingQueries.one(backend, survivor.id).executeAsOneOrNull() != null) {
            deleteUnmatched(backend, survivor.id)
        }
        replaceExcluded(survivor.id, backend, old.id)
        deleteExcluded(backend, old.id)
        redirectAncestors(survivor.id, backend, old.id)
        redirect(backend, old.id, survivor.id)
    }
}
