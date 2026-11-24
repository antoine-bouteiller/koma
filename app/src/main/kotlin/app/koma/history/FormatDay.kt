package app.koma.history

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** [at]'s day as Today, Yesterday or Tomorrow when [now] is near, else a medium date. */
fun formatDay(at: Instant, now: Instant, zone: ZoneId, locale: Locale): String {
    val day = at.atZone(zone).toLocalDate()
    return when (ChronoUnit.DAYS.between(day, now.atZone(zone).toLocalDate())) {
        0L -> "Today"
        1L -> "Yesterday"
        -1L -> "Tomorrow"
        else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(day)
    }
}

/** [at]'s short time of day, such as 10:00 AM. */
fun formatTime(at: Instant, zone: ZoneId, locale: Locale): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(locale)
        .format(at.atZone(zone))
        // CLDR versions disagree on the space before AM or PM (U+202F or U+0020); one form keeps
        // the label the same on every Android version.
        .replace('\u202F', ' ')
