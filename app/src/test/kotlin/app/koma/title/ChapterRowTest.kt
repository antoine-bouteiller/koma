package app.koma.title

import eu.kanade.tachiyomi.source.model.SChapter
import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterRowTest {
    private fun bound(vararg numbers: Float) =
        boundChapters(
            numbers.map { n ->
                SChapter.create().apply {
                    url = n.toString()
                    name = "Chapter $n"
                    chapter_number = n
                }
            },
            changeable = false,
        )

    private fun read(chapters: Chapters?) =
        (chapters as Chapters.Bound).chapters.filter { it.read }.map { it.key }

    @Test
    fun `a decimal chapter compares exactly with progress`() {
        assertEquals(listOf("10.1"), read(readChapters(bound(10.1f, 10.2f), 10.1)))
        assertEquals(emptyList<String>(), read(readChapters(bound(4.5f), 4.0)))
    }

    @Test
    fun `no chapter is read without progress, nor one without a number`() {
        assertEquals(emptyList<String>(), read(readChapters(bound(1f), null)))
        assertEquals(listOf("0.0"), read(readChapters(bound(0f, -1f), 5.0)))
    }

    @Test
    fun `hiding every scanlator says the chapters are filtered out`() {
        val chapters =
            boundChapters(
                listOf(
                    SChapter.create().apply {
                        url = "1"
                        name = "Chapter 1"
                        scanlator = "Y"
                    }
                ),
                changeable = false,
            )
        val shown = shownChapters(chapters, setOf("Y")) as Chapters.Bound
        assertEquals(emptyList<ChapterRow>(), shown.chapters)
        assertEquals("Every chapter is filtered out.", shown.note)
    }

    @Test
    fun `read state leaves other chapter states alone`() {
        assertEquals(Chapters.Loading, readChapters(Chapters.Loading, 3.0))
    }
}
