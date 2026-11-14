package com.wanderwildwood.iori

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract

/**
 * A contact found in search opens in Contacts (enishi), on that person's page, when Contacts is
 * installed. Otherwise, or if it cannot be opened, inkOS's own chooser comes up as before.
 */
object IoriContacts {

    const val ENISHI = "com.wanderwildwood.enishi"

    /** True when the contact was opened in Contacts; false to fall through to inkOS's chooser. */
    fun openInEnishi(context: Context, contactId: String, lookupKey: String): Boolean {
        // Not on the phone: offer to get it. "Use the phone's own" comes back through here,
        // is refused below, and inkOS's chooser comes up.
        val missing = try { context.packageManager.getPackageInfo(ENISHI, 0); false } catch (_: PackageManager.NameNotFoundException) { true }
        if (missing) {
            return IoriHandOff.handOver(context, ENISHI, null) {
                com.github.gezimos.inkos.helper.SearchHelper.launchContactQuickActions(context, contactId, lookupKey)
            }
        }
        return try {
            val id = contactId.toLongOrNull() ?: return false
            val uri = ContactsContract.Contacts.getLookupUri(id, lookupKey) ?: return false
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(ENISHI)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) == null) return false
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
