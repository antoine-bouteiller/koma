package app.koma.source

import app.koma.data.BackendId
import app.koma.data.Kind
import app.koma.data.Media
import app.koma.data.MediaDetails
import app.koma.data.MediaRef
import app.koma.data.Publication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TitlesTest {
    @Test
    fun `titles that differ only in case, accents and punctuation normalize alike`() {
        assertEquals("na honjaman level up", normalize("NA HONJAMAN — LÉVEL UP!"))
        assertEquals(normalize("Na Honjaman Level Up"), normalize("  na-honjaman: level up…"))
    }

    @Test
    fun `normalizing keeps letters and digits of every script`() {
        assertEquals("我独自升级 2", normalize("我独自升级 (2)!"))
        assertEquals(normalize("나 혼자만 레벨업 2"), normalize("나 혼자만 레벨업 (2)"))
        assertNotEquals(normalize("나 혼자만 레벨업"), normalize("나 혼자만 레벨업 2"))
        assertNotEquals(normalize("Solo Leveling"), normalize("Solo Leveling: Ragnarok"))
    }

    @Test
    fun `variants list the main title first, each title once`() {
        val details = details("Solo Leveling", "Na Honjaman Level Up", "Solo Leveling")
        assertEquals(listOf("Solo Leveling", "Na Honjaman Level Up"), variants(details))
    }
}

fun details(title: String, vararg titles: String, id: String = "1") =
    MediaDetails(
        media =
            Media(MediaRef(BackendId.MangaBaka, id), title, "", Kind.Manhwa, null, false, false),
        titles = titles.toList(),
        authors = emptyList(),
        publication = Publication.Releasing,
        startYear = null,
        chapters = null,
        description = "",
        tags = emptyList(),
    )
