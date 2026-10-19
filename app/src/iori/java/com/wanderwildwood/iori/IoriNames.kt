package com.wanderwildwood.iori

/**
 * Names as the reader sees them. A source of the home lines that is not installed has no label to
 * ask Android for, so the apps Glance takes lines from are named here as their launchers name
 * them; anything else falls back to its installed label, then to its package.
 *
 * And this app's own name in upstream's few words that are written into code rather than into
 * strings.xml, where a resource override cannot reach them.
 */
object IoriNames {

    const val SELF = "inkOS:wndr"

    private val SHOWN = mapOf(
        "com.wanderwildwood.fukuyaku" to "Medicine",
        "com.wanderwildwood.jimeikin" to "Music Box",
        "com.wanderwildwood.kotozute" to "Messaging",
        "com.wanderwildwood.koyomi" to "Calendar",
        "com.wanderwildwood.mimidoku" to "Audiobooks",
        "com.wanderwildwood.oboegaki" to "Notes",
        "com.wanderwildwood.satsuire" to "Wallet",
        "com.wanderwildwood.soramoyo" to "Sky",
        "com.wanderwildwood.tayori" to "Email",
        "com.wanderwildwood.tokei" to "Clock",
        "com.wanderwildwood.zatsuno" to "Field Kit",
        "com.wanderwildwood.zeni" to "Bitcoin",
    )

    /** [pkg]'s shown name: the fixed one, else [installed] (its label, if it is installed), else [pkg]. */
    fun shown(pkg: String, installed: String?): String =
        SHOWN[pkg] ?: SHOWN[pkg.removeSuffix(".debug")] ?: installed?.takeIf { it.isNotBlank() } ?: pkg

    // "inkOS" as a word on its own: not already "inkOS:wndr", not part of a longer word or a link.
    private val UPSTREAM_NAME = Regex("""(?<![\w/.])inkOS(?![\w:/])""")

    /** [text] with upstream's name for this app replaced by [SELF]. */
    fun named(text: String): String = UPSTREAM_NAME.replace(text, SELF)
}
