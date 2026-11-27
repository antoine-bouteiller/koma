package app.koma.source.atsumaru

import eu.kanade.tachiyomi.extension.en.atsumaru.Atsumaru

/**
 * What keiyoushi's build generates from Atsumaru's `build.gradle.kts`. Getters, so the values exist
 * while `KeiSource` initializes.
 */
internal class AtsumaruSource : Atsumaru() {
    override val name: String
        get() = "Atsumaru"

    override val lang: String
        get() = "en"

    // The published extension's, generated from "atsumaru/en/2", so bindings outlive a switch to
    // it.
    override val id: Long
        get() = 2327480808438768017L

    override val baseUrl: String
        get() = "https://atsu.moe"
}
