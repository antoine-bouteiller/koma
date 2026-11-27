package app.koma.source

import app.koma.data.MediaDetails
import java.text.Normalizer
import java.util.Locale

/** Every title of [details], its main one first, each once. */
fun variants(details: MediaDetails): List<String> =
    (listOf(details.media.title) + details.titles).distinct()

/** [title] without case, accents or punctuation, so two spellings of one title compare equal. */
fun normalize(title: String): String =
    Normalizer.normalize(title, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
