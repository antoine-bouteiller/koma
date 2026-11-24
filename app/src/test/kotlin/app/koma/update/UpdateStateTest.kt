package app.koma.update

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateStateTest {
    private val release = Release("2.0.0", "https://example.com/koma.apk")

    private fun downloading(done: Long, total: Long?) =
        checkNotNull(updateState(UpdateStep.Downloading(release, done, total), true, "0.1.0"))

    @Test
    fun `a download of known size shows its percent and megabytes`() {
        val state = downloading(3_200_000, 18_400_000)
        assertEquals("17%", state.pill)
        assertEquals(
            UpdateBody.Progress(3_200_000f / 18_400_000, "17%", "3.2 MB of 18.4 MB"),
            state.dialog?.body,
        )
    }

    @Test
    fun `a download of unknown size shows the megabytes fetched`() {
        val state = downloading(3_200_000, null)
        assertEquals("Downloading", state.pill)
        assertEquals(UpdateBody.Progress(null, null, "3.2 MB"), state.dialog?.body)
    }
}
