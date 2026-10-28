package com.timesince.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast

/** Receives the result of the update install: shows Android's confirm prompt, or tells the user what failed. */
class UpdateInstallReceiver : BroadcastReceiver() {

    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Updater.ACTION_INSTALL_STATUS) return

        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm != null) {
                    try {
                        context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            "Open Time Since and tap Update again to finish.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            // The app was replaced; Android restarts it by itself.
            PackageInstaller.STATUS_SUCCESS -> {}
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                Toast.makeText(context, "Update cancelled.", Toast.LENGTH_SHORT).show()
            }
            else -> {
                val why = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                Toast.makeText(
                    context,
                    "The update could not be installed." + (if (why.isEmpty()) "" else " ($why)"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
