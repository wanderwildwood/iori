package com.wanderwildwood.iori

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.github.gezimos.inkos.BuildConfig
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The phone's one Nextcloud account, held by Home so the apps need not each ask for it.
 *
 * Home signs in once (Nextcloud's own Login Flow v2, or an app password typed in on a phone
 * with no browser) and keeps ONE app password: Nextcloud will not make a new app password
 * from an app password, so every app is handed the same one, the server's device list shows a
 * single "Home" entry, and revoking it there signs out every app at once.
 *
 * It is kept in a file that is left out of every backup, with the password sealed by a key in
 * the phone's keystore that cannot be exported. If that key is gone the account reads as
 * signed out, and Home asks again.
 *
 * [IoriAccountProvider] hands it over, and only to a caller that is one of [CALLERS] by package
 * name AND is signed with the certificate pinned for it: the check Glance makes before it hands
 * Home its lines. Anything else gets nothing. Signing in, changing or signing out tells the
 * apps (a change notice on [uri]); they read again and never keep an account past a notice.
 */
object IoriAccount {

    /**
     * [user] is the login name: what is typed, or what Login Flow v2 gives back, and what basic
     * auth takes with [password]. [uid] is the Nextcloud user id the server keeps the files
     * under (`/remote.php/dav/files/<uid>/`), which can differ from the login name (an email
     * address, another case); null until the server has been asked for it.
     */
    data class Account(val server: String, val user: String, val password: String, val uid: String? = null) {
        /** The user id the apps build their paths with: [uid], or the login name until it is known. */
        val userId: String get() = uid ?: user

        /** Without the password, so it cannot end up in a log. */
        override fun toString(): String = "Account(server=$server, user=$user, uid=$uid)"
    }

    /** A sign-out the server has not yet been told about: the old account, kept only to end it there. */
    class Pending(val id: String, val account: Account)

    /** The account and the stamp it was kept under, read together, so neither can be from another sign-in. */
    class Held(val account: Account, val stamp: String)

    fun uri(context: Context): Uri = Uri.parse("content://${context.packageName}.accounts/nextcloud")

    /** The provider's row. `uid` came fifth, so an app reads it only when it is there. */
    val COLUMNS = arrayOf("server", "user", "password", "stamp", "uid")

    /** At most this many old sign-ins wait to be ended on their server; the oldest goes first. */
    const val MAX_PENDING = 5

    /**
     * The apps the account is handed to, each with the SHA-256 of the certificate its releases
     * are signed with (lower-case hex): the same values as in `assets/iori_apps.json`, which
     * `IoriAccountTest` holds this list to. Every app of Your apps that signs in to a Nextcloud is
     * pinned (the owner's choice, 11-18), whether or not its row "Use the phone's Nextcloud account" is built yet.
     */
    val CALLERS = mapOf(
        "com.wanderwildwood.enishi" to "def146af0a169328cee14b3cb40db62f8d352fadf1e20371dbcf68018c8a5c5b",
        "com.wanderwildwood.koyomi" to "96f0e571a4e14ce861d40e4f6c536adac2026fa271b8a4ec932eb922351957bd",
        "com.wanderwildwood.oboegaki" to "20e289f7dcdd22e11dc4dfdb46ecd1b883615f3350904c7575afc1849632514e",
        "com.wanderwildwood.garo" to "fb01c054c1e349eb5df5937c7e6c4968824dd719ada7566342dfcb1a562d96c5",
        "com.wanderwildwood.tana" to "2b3995b71fe50c7514d975b8df753b1ea9695e695c3c4d1f1ed83d64c81afe86",
        "com.wanderwildwood.kirinuki" to "bbbc92440c2333077deca468c24b98cec426626be04f49daef1a4f284fd464af",
        "com.wanderwildwood.kondate" to "f886bee796090ba4f97dd8babe7616b347c4a86cbab5954ab34dc43ed4072bd2",
        "com.wanderwildwood.utahon" to "b6f04691f0e4a0b75bad3ec496a8503661fc96c830082e7861495c1499dd3ae8",
        "com.wanderwildwood.mimidoku" to "dd932da2650dfc58b52ac6897648eb2a2e9ee8ae67d750c3869ddd3ca0d83f0e",
        "com.wanderwildwood.yumecho" to "6ce096a3c48c12e1ef0d158e0d4eb3a8c355c74be8ac84c92697de2d84f9f62f",
        "com.wanderwildwood.shoka" to "7479c85dab42dd4e97629ccff0b7eb62e1f09722b9da86ab781737286a5c6342",
    )

    /**
     * The debug certificates of the build box and the desk machine. Looked at only by a debug
     * build of Home, so two debug builds can be tried together; a release never answers them.
     */
    val DEBUG_CERTS = setOf(
        "1e7e9cc0ea37a725d0eaf622187792c425dc0351a3e228ef462f8eaacbb9db06",
        "9ae26e87553000817b4185b15d9047845e87871d8f5204dac77b82183fee248b",
    )

    /**
     * Whether a caller may have the account. [packages] are the caller's (one uid can hold
     * several); [signers] gives the SHA-256 of every certificate a package is signed with, or
     * nothing when that cannot be read. One of its packages has to be in [CALLERS] with every
     * signer the pinned one; in a debug build of Home ([debugBuild]) the same package signed
     * only with [DEBUG_CERTS] passes too, and so does its debug build (the same name with
     * `.debug` on the end), but only with [DEBUG_CERTS], never the pinned one. No certificate
     * read is a refusal, never a pass.
     */
    fun allowed(packages: List<String>, debugBuild: Boolean, signers: (String) -> List<String>?): Boolean =
        packages.any { name ->
            val pinned = CALLERS[name]
            if (pinned == null) {
                // A debug build of an app is named "<its package>.debug"; only a debug Home answers it.
                if (!debugBuild || !name.endsWith(".debug") || name.removeSuffix(".debug") !in CALLERS) return@any false
                val found = signers(name)
                return@any !found.isNullOrEmpty() && found.all { it in DEBUG_CERTS }
            }
            val found = signers(name)
            if (found.isNullOrEmpty()) return@any false
            found.all { it == pinned } || (debugBuild && found.all { it in DEBUG_CERTS })
        }

    private fun allowed(context: Context, uid: Int): Boolean {
        val pm = context.packageManager
        val packages = pm.getPackagesForUid(uid)?.toList() ?: return false
        return allowed(packages, BuildConfig.DEBUG) { name ->
            runCatching {
                pm.getPackageInfo(name, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
                    ?.map { sha256(it.toByteArray()) }
            }.getOrNull()
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    // ---- the store ---------------------------------------------------------------------------

    /** Where the account's text is kept. */
    interface Backing {
        fun read(): String?
        fun write(text: String)
    }

    /** What seals the password; either side may fail, and then answers nothing. */
    interface Sealer {
        fun seal(plain: String): String?
        fun open(sealed: String): String?
    }

    /**
     * The account as kept: server, user, user id, the sealed password, and a stamp that is new
     * after every change, so an app can tell the account it holds from the one Home has now.
     * Beside it, under `revoke`, the old accounts signed out of whose app passwords the server
     * has not yet deleted: still sealed, never handed to an app, kept only to try again.
     */
    class Store(
        private val backing: Backing,
        private val sealer: Sealer,
        private val newStamp: () -> String = { UUID.randomUUID().toString() },
    ) {
        private fun kept(): JsonObject? = try {
            backing.read()?.takeIf { it.isNotBlank() }?.let { JsonParser.parseString(it).asJsonObject }
        } catch (_: Exception) {
            null
        }

        private fun JsonObject.text(key: String): String? =
            get(key)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotEmpty() }

        private fun JsonObject.revoking(): List<JsonObject> =
            get("revoke")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }.orEmpty()

        private fun write(o: JsonObject): Boolean = try {
            backing.write(o.toString())
            true
        } catch (_: Exception) {
            false
        }

        private fun JsonObject.withRevoking(list: List<JsonObject>): JsonObject = apply {
            if (list.isNotEmpty()) add("revoke", JsonArray().also { a -> list.takeLast(MAX_PENDING).forEach { a.add(it) } })
        }

        private fun JsonObject.account(): Account? {
            val server = text("server") ?: return null
            val user = text("user") ?: return null
            val password = text("sealed")?.let { sealer.open(it) }?.takeIf { it.isNotEmpty() } ?: return null
            return Account(server, user, password, text("uid"))
        }

        /** The opened password of a waiting sign-out, or null when it does not open. */
        private fun JsonObject.openedPassword(): String? =
            text("sealed")?.let { sealer.open(it) }?.takeIf { it.isNotEmpty() }

        /**
         * Whether a waiting sign-out is the very app password [live] signs in with (same server,
         * same password): it was typed in again, so it must not be deleted on the server.
         */
        private fun JsonObject.isLive(live: Account?): Boolean =
            live != null && text("server") == live.server && openedPassword() == live.password

        /** The account, or nothing: none kept, the file unreadable, or the password not to be opened. */
        @Synchronized
        fun account(): Account? = kept()?.account()

        /** The account with its stamp, read at once; nothing when signed out. */
        @Synchronized
        fun held(): Held? {
            val o = kept() ?: return null
            val account = o.account() ?: return null
            return Held(account, o.text("stamp").orEmpty())
        }

        @Synchronized
        fun stamp(): String = kept()?.text("stamp").orEmpty()

        /**
         * Keeps [account] in place of any other, its server written one way ([IoriLogin.canonical]).
         * False, and nothing changed, when it cannot be sealed. Old sign-outs still to be ended stay,
         * except one with this very app password on this server: it is live again (typed in once
         * more after a sign-out the server never heard of), and ending it would sign out every app.
         */
        @Synchronized
        fun signIn(account: Account): Boolean {
            if (account.server.isBlank() || account.user.isBlank() || account.password.isEmpty()) return false
            val sealed = sealer.seal(account.password) ?: return false
            val server = IoriLogin.canonical(account.server)
            val o = JsonObject()
            o.addProperty("server", server)
            o.addProperty("user", account.user)
            account.uid?.takeIf { it.isNotBlank() }?.let { o.addProperty("uid", it) }
            o.addProperty("sealed", sealed)
            o.addProperty("stamp", newStamp())
            val live = account.copy(server = server)
            return write(o.withRevoking(kept()?.revoking().orEmpty().filterNot { it.isLive(live) }))
        }

        /**
         * Puts the user id the server gave in, if the account is still the one with [stamp] (not
         * signed out or replaced while the server was asked) and has none yet: a user id once kept
         * is never written over. A new stamp, so the apps read again.
         */
        @Synchronized
        fun fillUid(stamp: String, uid: String): Boolean {
            if (uid.isBlank()) return false
            val o = kept() ?: return false
            if (o.text("server") == null || o.text("stamp") != stamp || o.text("uid") != null) return false
            o.addProperty("uid", uid)
            o.addProperty("stamp", newStamp())
            return write(o)
        }

        /**
         * Forgets the account: the apps get it no more. Its sealed password goes under `revoke`,
         * for the server to be asked to delete it until it has ([revoked]). False when there was
         * none to forget, or it could not be written.
         */
        @Synchronized
        fun signOut(): Boolean {
            val o = kept() ?: return false
            if (o.text("server") == null && o.text("sealed") == null) return false
            val list = o.revoking().toMutableList()
            val server = o.text("server")
            val user = o.text("user")
            val sealed = o.text("sealed")
            if (server != null && user != null && sealed != null) {
                list += JsonObject().apply {
                    addProperty("id", o.text("stamp") ?: newStamp())
                    addProperty("server", server)
                    addProperty("user", user)
                    addProperty("sealed", sealed)
                }
            }
            val left = JsonObject()
            left.addProperty("stamp", newStamp())
            return write(left.withRevoking(list))
        }

        /**
         * The old sign-ins still to be ended on their server, oldest first. Those not to be opened
         * are left out, and so is one whose app password is the live account's (never ended).
         */
        @Synchronized
        fun pending(): List<Pending> {
            val o = kept() ?: return emptyList()
            val live = o.account()
            return o.revoking().mapNotNull { e ->
                if (e.isLive(live)) return@mapNotNull null
                val id = e.text("id") ?: return@mapNotNull null
                val server = e.text("server") ?: return@mapNotNull null
                val user = e.text("user") ?: return@mapNotNull null
                val password = e.openedPassword() ?: return@mapNotNull null
                Pending(id, Account(server, user, password))
            }
        }

        /**
         * The server has ended the old sign-in [id] (or it can never be ended from here: the
         * password no longer opens): it is forgotten for good. Entries that no longer open go too,
         * and any with the live account's app password (that one is not to be ended).
         */
        @Synchronized
        fun revoked(id: String): Boolean {
            val o = kept() ?: return false
            val live = o.account()
            val before = o.revoking()
            val after = before.filter { e ->
                e.text("id") != id && e.openedPassword() != null && !e.isLive(live)
            }
            if (after.size == before.size) return false
            o.remove("revoke")
            return write(o.withRevoking(after))
        }
    }

    @Volatile
    private var store: Store? = null

    private fun store(context: Context): Store = store ?: synchronized(this) {
        store ?: Store(FileBacking(File(context.applicationContext.noBackupFilesDir, "iori_account.json")), KeystoreSealer)
            .also { store = it }
    }

    fun account(context: Context): Account? = store(context).account()

    fun stamp(context: Context): String = store(context).stamp()

    /** The account with the stamp it is kept under, read together; nothing when signed out. */
    fun held(context: Context): Held? = store(context).held()

    /** Keeps [account] and tells the apps. False when the phone could not seal it. */
    fun signIn(context: Context, account: Account): Boolean {
        val kept = store(context).signIn(account)
        if (kept) changed(context)
        return kept
    }

    /** Forgets the account and tells the apps. True when one was forgotten; it waits in [pending] to be ended on the server. */
    fun signOut(context: Context): Boolean {
        val out = store(context).signOut()
        if (out) changed(context)
        return out
    }

    /** Puts in the user id the server gave for the account with [stamp], and tells the apps. */
    fun fillUid(context: Context, stamp: String, uid: String): Boolean {
        val filled = store(context).fillUid(stamp, uid)
        if (filled) changed(context)
        return filled
    }

    fun pending(context: Context): List<Pending> = store(context).pending()

    fun revoked(context: Context, id: String): Boolean = store(context).revoked(id)

    private fun changed(context: Context) {
        runCatching { context.contentResolver.notifyChange(uri(context), null) }
    }

    /** What an allowed caller reads: no row when nobody is signed in, else one. */
    internal fun rows(context: Context, uid: Int): MatrixCursor? {
        if (!allowed(context, uid)) return null
        val cursor = MatrixCursor(COLUMNS)
        val identity = Binder.clearCallingIdentity()
        try {
            account(context)?.let { cursor.addRow(arrayOf<Any>(it.server, it.user, it.password, stamp(context), it.userId)) }
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
        cursor.setNotificationUri(context.contentResolver, uri(context))
        return cursor
    }

    private class FileBacking(private val file: File) : Backing {
        override fun read(): String? = try {
            if (file.exists()) file.readText() else null
        } catch (_: Exception) {
            null
        }

        override fun write(text: String) {
            val next = File(file.parentFile, file.name + ".new")
            next.writeText(text)
            if (!next.renameTo(file)) {
                file.writeText(text)
                next.delete()
            }
        }
    }

    /** AES-GCM under a key made in, and never leaving, the phone's keystore: `v1:base64(iv + ciphertext)`. */
    private object KeystoreSealer : Sealer {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "iori.account.v1"
        private const val PREFIX = "v1:"
        private const val IV_BYTES = 12

        override fun seal(plain: String): String? = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            PREFIX + Base64.encodeToString(cipher.iv + sealed, Base64.NO_WRAP)
        } catch (_: Exception) {
            null // better nothing kept than a password kept in the clear
        }

        override fun open(sealed: String): String? = try {
            if (!sealed.startsWith(PREFIX)) null else {
                val blob = Base64.decode(sealed.removePrefix(PREFIX), Base64.NO_WRAP)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob, 0, IV_BYTES))
                String(cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES), Charsets.UTF_8)
            }
        } catch (_: Exception) {
            null
        }

        private fun key(): SecretKey {
            val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            generator.init(
                // Not tied to the screen being unlocked: the apps sync at night, on the charger.
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            return generator.generateKey()
        }
    }
}

/**
 * `content://<Home's package>.accounts/nextcloud`, read-only: one row (`server`, `user`,
 * `password`, `stamp`, `uid`) for a caller [IoriAccount.allowed] passes while somebody is signed in, no
 * row while nobody is, and no answer at all for anyone else. Exported, with no permission to
 * ask for: the caller's package and signing certificate are checked here.
 */
class IoriAccountProvider : ContentProvider() {
    /**
     * Made as Home's process starts: the moment to finish what an earlier run left undone (an
     * old sign-in to end on the server, a user id to ask for). Off the main thread.
     */
    override fun onCreate(): Boolean {
        context?.let { runCatching { IoriNextcloud.tidy(it) } }
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val context = context ?: return null
        if (uri.path != "/nextcloud") return null
        return try {
            IoriAccount.rows(context, Binder.getCallingUid())
        } catch (_: Exception) {
            null
        }
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
