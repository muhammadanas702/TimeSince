package com.timesince.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Updates the app from its own GitHub Releases page.
 *
 * The app asks GitHub for the latest release, compares its version with the installed one and, if it
 * is newer, downloads TimeSince.apk straight into an Android install session. Android then shows its
 * normal "Update this app?" prompt. Nothing is stored on the phone and no library is needed. The new
 * version is signed with the same key, so it installs over the old one and keeps every widget.
 */
object Updater {
    private const val REPO = "muhammadanas702/TimeSince"
    private const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"
    private const val APK_NAME = "TimeSince.apk"
    const val ACTION_INSTALL_STATUS = "com.timesince.app.INSTALL_STATUS"

    /** [version] has no leading "v" (tag v2.5 gives 2.5). */
    class Release(val version: String, val apkUrl: String, val size: Long)

    @Suppress("DEPRECATION")
    fun installedVersion(context: Context): String =
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
        } catch (e: Exception) {
            "0"
        }

    /** True when [remote] is a higher version than [local]. Compares 2.10 above 2.9 and 2.5.1 above 2.5. */
    fun isNewer(remote: String, local: String): Boolean {
        val a = parts(remote)
        val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** "v2.10.1" gives [2, 10, 1]. Text after the digits of a part ("2-beta") is ignored. */
    private fun parts(version: String): List<Int> =
        version.trim().trimStart('v', 'V').split('.', '-', '_').map { p ->
            p.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }

    /** Reads the release JSON of GitHub. Returns null for a draft, a pre-release or a release without the APK. */
    fun parseRelease(json: String): Release? {
        val o = JSONObject(json)
        val tag = o.optString("tag_name")
        if (tag.isEmpty() || o.optBoolean("draft") || o.optBoolean("prerelease")) return null
        val assets = o.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name") == APK_NAME) {
                val url = a.optString("browser_download_url")
                if (!url.startsWith("https://")) return null
                return Release(tag.trim().trimStart('v', 'V'), url, a.optLong("size", -1L))
            }
        }
        return null
    }

    /** Asks GitHub for the newest release. Blocking, so call it off the main thread. Throws IOException when offline. */
    @Throws(IOException::class)
    fun fetchLatest(): Release? {
        val conn = open(LATEST_URL, "application/vnd.github+json")
        try {
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            return try {
                parseRelease(text)
            } catch (e: Exception) {
                throw IOException("Unreadable answer from GitHub")
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Android 8 and newer let the user allow each app to install apps ("Install unknown apps"). */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Opens the Android screen where the user allows this app to install updates. */
    fun installPermissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))

    /**
     * Downloads the APK into an install session, then asks Android to install it. Blocking, so call it
     * off the main thread. [onProgress] gets 0..100 (or -1 when the size is not known).
     */
    @Throws(IOException::class)
    fun downloadAndInstall(context: Context, release: Release, onProgress: (Int) -> Unit) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        if (release.size > 0) params.setSize(release.size)

        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        try {
            val conn = open(release.apkUrl, null)
            try {
                val total = if (conn.contentLengthLong > 0) conn.contentLengthLong else release.size
                conn.inputStream.use { input ->
                    session.openWrite(APK_NAME, 0, if (total > 0) total else -1).use { out ->
                        val buffer = ByteArray(32 * 1024)
                        var done = 0L
                        var last = -2
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            done += n
                            val percent = if (total > 0) (done * 100 / total).toInt().coerceAtMost(100) else -1
                            if (percent != last) {
                                last = percent
                                onProgress(percent)
                            }
                        }
                        session.fsync(out)
                    }
                }
            } finally {
                conn.disconnect()
            }
            session.commit(statusSender(context, sessionId))
        } catch (e: Exception) {
            session.abandon()
            if (e is IOException) throw e
            throw IOException(e.message ?: "Could not prepare the update")
        } finally {
            session.close()
        }
    }

    /** Where Android reports how the install went. It is our own receiver, so the intent names it explicitly. */
    private fun statusSender(context: Context, sessionId: Int) =
        PendingIntent.getBroadcast(
            context,
            sessionId,
            Intent(context, UpdateInstallReceiver::class.java).setAction(ACTION_INSTALL_STATUS),
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        ).intentSender

    /** GET with a few manual redirects (GitHub sends downloads on to another host). Only https is followed. */
    @Throws(IOException::class)
    private fun open(start: String, accept: String?): HttpURLConnection {
        var url = start
        for (hop in 0 until 6) {
            if (!url.startsWith("https://")) throw IOException("Not a secure address")
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "TimeSince-Android")
            if (accept != null) c.setRequestProperty("Accept", accept)
            val code = c.responseCode
            if (code in 300..399) {
                val location = c.getHeaderField("Location")
                c.disconnect()
                if (location == null) throw IOException("Bad redirect")
                url = URL(URL(url), location).toString()
                continue
            }
            if (code != 200) {
                c.disconnect()
                throw IOException("GitHub answered $code")
            }
            return c
        }
        throw IOException("Too many redirects")
    }
}
