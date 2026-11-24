package app.koma.version

private val RELEASE = Regex("""^(\d+)\.(\d+)\.(\d+)(?:-beta\.(\d+))?$""")

/**
 * Android `versionCode` of a release version: MAJOR·10⁶ + MINOR·10⁴ + PATCH·10² + the beta number,
 * or 99 for a stable release, so a stable release outranks its betas.
 */
fun versionCode(version: String): Int {
    val match =
        RELEASE.matchEntire(version)
            ?: throw IllegalArgumentException("$version is not a release version.")
    val (major, minor, patch) = match.groupValues.drop(1).take(3).map { it.toInt() }
    val beta = match.groupValues[4].ifEmpty { "99" }.toInt()
    // Android caps versionCode at 2100000000.
    require(minor <= 99 && patch <= 99 && beta <= 99 && major <= 2099) {
        "$version has no versionCode."
    }
    return major * 1_000_000 + minor * 10_000 + patch * 100 + beta
}
