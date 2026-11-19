package com.wanderwildwood.iori

import com.github.gezimos.inkos.R
import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * The house's apps as Home itself knows them: the list inside Home (assets/iori_apps.json,
 * written by tools/make-apps-list.py), with each app's name, package, the certificate its
 * releases are signed with, the section it is listed under, and the oldest release Home takes.
 *
 * This list is not where apps are installed from. It gives "Your apps" ([IoriYourApps]) its
 * section folders, the first run its page of tick boxes, and [IoriAppInstaller] the certificates
 * it pins: an app is installed only from the list on wanderthe.dev, and only when that list's
 * certificate is the one here.
 */
object IoriApps {

    private const val TAG = "IoriApps"

    data class App(
        val name: String,
        val pkg: String,
        val cert: String,
        /** The section it is listed under, as `tools/make-apps-list.py` wrote it: a key of [SECTIONS]. */
        val section: String,
        /**
         * The versionCode of the release out when this Home was built; Home installs nothing of
         * this app older than that (IoriAppsManifest.Verdict.TOO_OLD). 0 when the list gives none.
         */
        val minVersionCode: Long = 0,
    ) {
        val core: Boolean get() = section == CORE
    }

    const val CORE = "core"

    /** The sections, in the order they are listed, each with its heading. The list file names them by key. */
    val SECTIONS: List<Pair<String, Int>> = listOf(
        CORE to R.string.iori_apps_core,
        "outdoors" to R.string.iori_apps_outdoors,
        "road" to R.string.iori_apps_road,
        "daily" to R.string.iori_apps_daily,
        "music" to R.string.iori_apps_music,
        "reading" to R.string.iori_apps_reading,
        "games" to R.string.iori_apps_games,
        "tools" to R.string.iori_apps_tools,
    )

    /**
     * [items] under their headings, empty sections left out, each in its own order. An item whose
     * section this build does not know is listed under the last heading, so a newer list never
     * hides an app.
     */
    fun <T> sections(items: List<T>, sectionOf: (T) -> String): List<Pair<Int, List<T>>> {
        val known = SECTIONS.map { it.first }.toSet()
        val last = SECTIONS.last().first
        return SECTIONS.map { (key, head) ->
            head to items.filter { val s = sectionOf(it); s == key || (key == last && s !in known) }
        }.filter { it.second.isNotEmpty() }
    }

    fun sections(apps: List<App>): List<Pair<Int, List<App>>> = sections(apps) { it.section }

    /**
     * The section an app of the list on wanderthe.dev is shown under: the one Home's own list
     * gives it, or, for an app Home's list does not name, Core when the site marks it core.
     */
    fun sectionOf(context: Context, pkg: String, core: Boolean): String =
        find(context, pkg)?.section ?: if (core) CORE else SECTIONS.last().first

    @Volatile private var loaded: List<App>? = null

    fun apps(context: Context): List<App> = loaded ?: runCatching {
        val json = context.assets.open("iori_apps.json").bufferedReader().use { it.readText() }
        val array = JSONObject(json).getJSONArray("apps")
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            App(
                name = o.getString("name"),
                pkg = o.getString("package"),
                cert = o.getString("cert").lowercase(),
                section = o.getString("section"),
                minVersionCode = o.optLong("minVersionCode", 0L),
            )
        }
    }.getOrElse {
        Log.w(TAG, "the app list could not be read", it)
        emptyList()
    }.also { loaded = it }

    fun find(context: Context, pkg: String): App? = apps(context).firstOrNull { it.pkg == pkg }

    /** What a first run offers ticked: the core set, less what is already on the phone. */
    internal fun firstPick(apps: List<App>, installed: Set<String>): Set<String> =
        apps.filter { it.core && it.pkg !in installed }.map { it.pkg }.toSet()
}
