package com.wanderwildwood.iori

/*
 * The list of the house's apps that Home reads from wanderthe.dev (apps.json), and the two
 * decisions made from it: which button an app gets (Install, Update, Up to date) and whether a
 * downloaded file may be handed to Android. Pure Kotlin, no Android and no JSON library, so the
 * tests run on the JVM.
 *
 * The file is made by tools/apps-manifest.py in the site's repository:
 *
 *   { "version": 1, "apps": [ { "package", "name", "summary", "core": bool, "versionName",
 *     "versionCode", "apk": https URL, "sha256", "certSha256", "minSdk", "size" } ] }
 *
 * Reading it never throws. An entry with something missing or wrong is left out, and the rest
 * are kept.
 */
object IoriAppsManifest {

    /** The only manifest version this build reads. A newer one is refused as a whole. */
    const val VERSION = 1

    /** Where the list lives. https only. */
    const val URL = "https://wanderthe.dev/apps.json"

    /** The most a manifest entry may say an app weighs, and the most Home downloads with no size given. */
    const val MAX_SIZE = 200L * 1024 * 1024

    data class Entry(
        val pkg: String,
        val name: String,
        val summary: String,
        val core: Boolean,
        val versionName: String,
        val versionCode: Long,
        val apk: String,
        val sha256: String,
        val certSha256: String,
        val minSdk: Int,
        val size: Long,
    )

    /**
     * The apps in [json], core apps first, each list in the file's own order; one entry per
     * package (the first wins). Null when the file cannot be read at all or is of another version.
     */
    fun parse(json: String): List<Entry>? {
        val root = runCatching { Json(json).read() }.getOrNull() as? Map<*, *> ?: return null
        if ((root["version"] as? Number)?.toDouble() != VERSION.toDouble()) return null
        val apps = root["apps"] as? List<*> ?: return null
        val seen = HashSet<String>()
        val entries = apps.mapNotNull { (it as? Map<*, *>)?.let(::entry) }.filter { seen.add(it.pkg) }
        return entries.filter { it.core } + entries.filterNot { it.core }
    }

    private val PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
    private val HEX64 = Regex("^[0-9a-f]{64}$")

    /** One entry, or null when any field Home relies on is missing or wrong. */
    private fun entry(o: Map<*, *>): Entry? {
        val pkg = (o["package"] as? String)?.trim() ?: return null
        if (!PACKAGE.matches(pkg)) return null
        val name = (o["name"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val apk = (o["apk"] as? String)?.trim() ?: return null
        if (!isHttps(apk)) return null
        val sha = (o["sha256"] as? String)?.trim()?.lowercase()?.takeIf { HEX64.matches(it) } ?: return null
        val cert = (o["certSha256"] as? String)?.trim()?.lowercase()?.replace(":", "")
            ?.takeIf { HEX64.matches(it) } ?: return null
        val code = whole(o["versionCode"])?.takeIf { it > 0 } ?: return null
        val size = whole(o["size"]) ?: 0L
        if (size < 0 || size > MAX_SIZE) return null
        val minSdk = whole(o["minSdk"])?.takeIf { it in 1..1000 }?.toInt() ?: 1
        return Entry(
            pkg = pkg,
            name = name,
            summary = (o["summary"] as? String)?.trim().orEmpty(),
            core = o["core"] == true,
            versionName = (o["versionName"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: code.toString(),
            versionCode = code,
            apk = apk,
            sha256 = sha,
            certSha256 = cert,
            minSdk = minSdk,
            size = size,
        )
    }

    /** A whole number, from a JSON number or a string of digits; null for anything else. */
    private fun whole(v: Any?): Long? = when (v) {
        is Number -> v.toDouble().let { d -> if (d == Math.floor(d) && d >= Long.MIN_VALUE && d <= Long.MAX_VALUE) d.toLong() else null }
        is String -> v.trim().toLongOrNull()
        else -> null
    }

    /** True for an https address with a host, and nothing else (no http, no file, no content). */
    fun isHttps(url: String): Boolean {
        if (!url.startsWith("https://", ignoreCase = true)) return false
        val host = url.substring(8).substringBefore('/').substringBefore('?').substringBefore('#')
        return host.isNotEmpty() && host.none { it.isWhitespace() } && '@' !in host
    }

    // --- Which button ---------------------------------------------------------------------

    enum class Status { NOT_INSTALLED, UPDATE, UP_TO_DATE, TOO_OLD_ANDROID }

    /** What is on the phone of an app: null when it is not there. */
    data class Installed(val versionName: String?, val versionCode: Long)

    fun status(entry: Entry, installed: Installed?, sdk: Int): Status = when {
        installed == null -> if (sdk < entry.minSdk) Status.TOO_OLD_ANDROID else Status.NOT_INSTALLED
        !isNewer(entry.versionCode, entry.versionName, installed.versionCode, installed.versionName) -> Status.UP_TO_DATE
        sdk < entry.minSdk -> Status.TOO_OLD_ANDROID
        else -> Status.UPDATE
    }

    /**
     * True when the release (code [code], name [name]) is newer than what is on the phone.
     * The version codes decide when both are set, as Android itself does; otherwise the names,
     * number by number ("1.10.0" is newer than "1.9.3"; "1.2" and "1.2.0" are the same; a
     * "-test" or other ending is ignored).
     */
    fun isNewer(code: Long, name: String?, haveCode: Long, haveName: String?): Boolean {
        if (code > 0 && haveCode > 0) return code > haveCode
        return compareNames(name.orEmpty(), haveName.orEmpty()) > 0
    }

    /** Compares two version names number by number; anything that is not a number counts as 0. */
    fun compareNames(a: String, b: String): Int {
        fun parts(v: String) = v.trim().removePrefix("v").substringBefore('-').substringBefore('+')
            .split('.').map { it.trim().toLongOrNull() ?: 0L }
        val x = parts(a)
        val y = parts(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val c = x.getOrElse(i) { 0L }.compareTo(y.getOrElse(i) { 0L })
            if (c != 0) return c
        }
        return 0
    }

    // --- May this file be installed ---------------------------------------------------------

    /** Why a downloaded file is not handed to Android, or OK. */
    enum class Verdict {
        OK,
        /** The file is not as long as the list says. */
        WRONG_SIZE,
        /** The file's SHA-256 is not the one in the list. */
        WRONG_CHECKSUM,
        /** Android could not read the file as an app. */
        NOT_AN_APP,
        /** The file is another app than the one asked for. */
        WRONG_APP,
        /** The file is not of the version the list names. */
        WRONG_VERSION,
        /** The file is not signed with the list's certificate alone (no certificate read counts as this). */
        WRONG_KEY,
        /** Home carries no certificate for this app, or the list's is not the one it carries. */
        NOT_PINNED,
        /** The app on the phone is signed with another key: Android would refuse, and it may not be ours. */
        KEY_CHANGED,
        /** The file is not newer than the app on the phone. */
        NOT_NEWER,
    }

    /** What was read from a downloaded file and from the phone. */
    data class Facts(
        val size: Long,
        val sha256: String,
        /** The package inside the file; null when Android could not read it. */
        val pkg: String?,
        val versionCode: Long,
        /** SHA-256 of every certificate the file is signed with. */
        val signers: List<String>,
        /** SHA-256 of every certificate the app on the phone is signed with; null when it is not installed. */
        val installedSigners: List<String>?,
        val installedVersionCode: Long?,
        /** The certificate Home itself carries for this package; null when it carries none. */
        val pinned: String?,
    )

    /** Whether the downloaded file, as [facts] describe it, may be installed as [entry]. */
    fun verify(entry: Entry, facts: Facts): Verdict {
        val cert = entry.certSha256.lowercase()
        if (entry.size > 0 && facts.size != entry.size) return Verdict.WRONG_SIZE
        if (!facts.sha256.equals(entry.sha256, ignoreCase = true)) return Verdict.WRONG_CHECKSUM
        val pkg = facts.pkg ?: return Verdict.NOT_AN_APP
        if (pkg != entry.pkg) return Verdict.WRONG_APP
        if (facts.versionCode != entry.versionCode) return Verdict.WRONG_VERSION
        val signers = facts.signers.map { it.lowercase() }.toSet()
        if (signers != setOf(cert)) return Verdict.WRONG_KEY
        // Only an app whose key Home itself carries is installed: a list from the network alone
        // cannot bring in an app, however well its own fields agree with each other.
        if (facts.pinned == null || !facts.pinned.equals(cert, ignoreCase = true)) return Verdict.NOT_PINNED
        val installed = facts.installedSigners
        if (installed != null) {
            if (installed.map { it.lowercase() }.toSet() != signers) return Verdict.KEY_CHANGED
            val have = facts.installedVersionCode ?: 0L
            if (have >= facts.versionCode) return Verdict.NOT_NEWER
        }
        return Verdict.OK
    }

    /**
     * The entries of [entries] whose package Home carries a certificate for in [pins] and whose
     * certificate is that one. The rest are not shown at all: they could never be installed.
     */
    fun pinnedOnly(entries: List<Entry>, pins: Map<String, String>): List<Entry> =
        entries.filter { pins[it.pkg]?.equals(it.certSha256, ignoreCase = true) == true }

    // --- A small strict JSON reader ----------------------------------------------------------

    /** Objects become Map, arrays List, numbers Double, plus String, Boolean and null. */
    private class Json(private val s: String) {
        private var i = 0
        private var depth = 0

        fun read(): Any? {
            val v = value()
            skip()
            if (i != s.length) error("trailing text at $i")
            return v
        }

        private fun value(): Any? {
            skip()
            if (i >= s.length) error("end of input")
            return when (s[i]) {
                '{' -> nested { obj() }
                '[' -> nested { array() }
                '"' -> string()
                't' -> word("true", true)
                'f' -> word("false", false)
                'n' -> word("null", null)
                else -> number()
            }
        }

        private fun <T> nested(block: () -> T): T {
            if (++depth > 32) error("too deep")
            return block().also { depth-- }
        }

        private fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            i++; skip()
            if (peek() == '}') { i++; return m }
            while (true) {
                skip(); val k = string(); skip(); expect(':')
                m[k] = value(); skip()
                when (s.getOrNull(i++)) { ',' -> continue; '}' -> return m; else -> error("expected , or } at ${i - 1}") }
            }
        }

        private fun array(): List<Any?> {
            val l = ArrayList<Any?>()
            i++; skip()
            if (peek() == ']') { i++; return l }
            while (true) {
                l += value(); skip()
                when (s.getOrNull(i++)) { ',' -> continue; ']' -> return l; else -> error("expected , or ] at ${i - 1}") }
            }
        }

        private fun string(): String {
            expect('"')
            val b = StringBuilder()
            while (true) {
                val c = s.getOrNull(i++) ?: error("unterminated string")
                when (c) {
                    '"' -> return b.toString()
                    '\\' -> when (val e = s.getOrNull(i++)) {
                        '"', '\\', '/' -> b.append(e)
                        'b' -> b.append('\b'); 'f' -> b.append('\u000C'); 'n' -> b.append('\n'); 'r' -> b.append('\r'); 't' -> b.append('\t')
                        'u' -> {
                            if (i + 4 > s.length) error("bad escape")
                            b.append(s.substring(i, i + 4).toIntOrNull(16)?.toChar() ?: error("bad escape")); i += 4
                        }
                        else -> error("bad escape at ${i - 1}")
                    }
                    else -> b.append(c)
                }
            }
        }

        private fun number(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(start, i).toDoubleOrNull() ?: error("bad value at $start")
        }

        private fun word(w: String, v: Any?): Any? { if (!s.startsWith(w, i)) error("bad value at $i"); i += w.length; return v }
        private fun expect(c: Char) { if (s.getOrNull(i) != c) error("expected $c at $i"); i++ }
        private fun peek() = s.getOrNull(i)
        private fun skip() { while (i < s.length && s[i].isWhitespace()) i++ }
    }
}
