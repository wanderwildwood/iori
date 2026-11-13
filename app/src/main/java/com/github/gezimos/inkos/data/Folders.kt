package com.github.gezimos.inkos.data

import android.os.UserHandle
import org.json.JSONArray
import org.json.JSONObject

/**
 * Folders in the app drawer: a named list of apps, kept in the launcher's own settings.
 *
 * A folder is shown as a row of the drawer like an app, under a made-up package name
 * (com.inkos.folder.<id>), the way separators and search results already are. That is what
 * lets a folder sit in a home slot: the slot stores a package name and a label, and opening
 * it is caught in AppsRepository.launchSyntheticOrSystemApp.
 *
 * An app is in at most one folder. Nothing here touches the app itself.
 */
object Folders {

    const val PREFIX = "com.inkos.folder."

    /** What the row of a folder ends with, so it does not read as an app. */
    private const val MARK = " ›"

    data class Folder(val id: String, val name: String, val apps: List<String>)

    fun isFolder(app: AppListItem): Boolean = app.activityPackage.startsWith(PREFIX)
    fun isFolderPackage(pkg: String): Boolean = pkg.startsWith(PREFIX)
    fun idOf(pkg: String): String = pkg.removePrefix(PREFIX)

    /** What an app is known by in a folder: its package, and its shortcut if it is one. */
    fun keyOf(app: AppListItem): String =
        if (app.shortcutId != null) "${app.activityPackage}|${app.shortcutId}" else app.activityPackage

    fun load(prefs: Prefs): List<Folder> = try {
        val array = JSONArray(prefs.foldersJson)
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val apps = o.getJSONArray("apps")
            Folder(o.getString("id"), o.getString("name"), (0 until apps.length()).map { apps.getString(it) })
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun save(prefs: Prefs, folders: List<Folder>) {
        val array = JSONArray()
        folders.forEach { f ->
            array.put(JSONObject().put("id", f.id).put("name", f.name).put("apps", JSONArray(f.apps)))
        }
        prefs.foldersJson = array.toString()
    }

    fun find(prefs: Prefs, id: String?): Folder? = if (id == null) null else load(prefs).firstOrNull { it.id == id }

    /** The folder an app is in, if any. */
    fun folderOf(prefs: Prefs, app: AppListItem): Folder? {
        val key = keyOf(app)
        return load(prefs).firstOrNull { key in it.apps }
    }

    /** The row that stands for a folder in the drawer and in a home slot. */
    fun item(folder: Folder, user: UserHandle): AppListItem = AppListItem(
        activityLabel = folder.name + MARK,
        activityPackage = PREFIX + folder.id,
        activityClass = "",
        user = user,
        customLabel = "",
    )

    /** Folders first, by name, then everything else in the order it came. */
    fun first(list: List<AppListItem>): List<AppListItem> {
        if (list.none { isFolder(it) }) return list
        val (folders, rest) = list.partition { isFolder(it) }
        return folders.sortedBy { it.activityLabel.lowercase() } + rest
    }

    fun create(prefs: Prefs, name: String): Folder? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        val folders = load(prefs)
        folders.firstOrNull { it.name.equals(clean, ignoreCase = true) }?.let { return it }
        val folder = Folder(java.util.UUID.randomUUID().toString().take(8), clean, emptyList())
        save(prefs, folders + folder)
        return folder
    }

    fun rename(prefs: Prefs, id: String, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        save(prefs, load(prefs).map { if (it.id == id) it.copy(name = clean) else it })
        // A home slot keeps the label it was given, so it is told the new name.
        for (i in 0 until prefs.homeAppsNum) {
            val slot = prefs.getHomeAppModel(i)
            if (slot.activityPackage == PREFIX + id) {
                prefs.setHomeAppModel(i, slot.copy(activityLabel = clean + MARK, customLabel = ""))
            }
        }
    }

    /** The folder goes; its apps are back in the drawer's own list. */
    fun delete(prefs: Prefs, id: String) {
        save(prefs, load(prefs).filter { it.id != id })
        for (i in 0 until prefs.homeAppsNum) {
            val slot = prefs.getHomeAppModel(i)
            if (slot.activityPackage == PREFIX + id) {
                prefs.setHomeAppModel(i, slot.copy(activityLabel = "", activityPackage = "", activityClass = ""))
            }
        }
    }

    /** Puts an app in a folder, taking it out of any other; a null id only takes it out. */
    fun move(prefs: Prefs, app: AppListItem, id: String?) {
        val key = keyOf(app)
        save(prefs, load(prefs).map { f ->
            val without = f.apps - key
            if (f.id == id) f.copy(apps = without + key) else f.copy(apps = without)
        })
    }

    /**
     * Starter folders, filled with whichever of these apps are on the phone. A folder that
     * would be empty is not made, and an app already in a folder is left where it is.
     */
    private val STARTERS = listOf(
        "Communications" to listOf("kotozute", "tayori", "enishi", "itodenwa", "amime", "ibasho"),
        "Games" to listOf("chessplus", "kuroban", "solitaire", "keitaiyugi"),
        "Music" to listOf("jimeikin", "onsa", "hanauta", "utahon", "josho"),
        "Outdoors" to listOf(
            "chizu", "chikei", "soramoyo", "seizu", "tancho", "kinokocho", "zatsuno",
            "yorimichi", "sokudokei", "okiba", "tenken", "mizumori"
        ),
        "Reading" to listOf("mimidoku", "kirinuki", "shoka", "jibiki", "tsuyaku"),
    )

    /** Returns how many folders were made or added to. */
    fun addStarters(prefs: Prefs, installed: List<AppListItem>): Int {
        val onPhone = installed.filter { it.shortcutId == null }.map { it.activityPackage }.toSet()
        var touched = 0
        for ((name, ids) in STARTERS) {
            val taken = load(prefs).flatMap { it.apps }.toSet()
            val apps = ids.map { "com.wanderwildwood.$it" }.filter { it in onPhone && it !in taken }
            if (apps.isEmpty()) continue
            val folder = create(prefs, name) ?: continue
            save(prefs, load(prefs).map { if (it.id == folder.id) it.copy(apps = it.apps + apps) else it })
            touched++
        }
        return touched
    }
}
