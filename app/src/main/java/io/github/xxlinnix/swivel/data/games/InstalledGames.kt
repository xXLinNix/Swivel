package io.github.xxlinnix.swivel.data.games

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.util.LruCache
import io.github.xxlinnix.swivel.core.games.InstalledGame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The launchable apps on the phone, as seen through the manifest's `<queries>` for
 * launcher activities. That is the visibility Android's package-visibility rules grant a
 * launcher-like app without QUERY_ALL_PACKAGES
 * (developer.android.com/training/package-visibility/declaring).
 */
class InstalledGames(context: Context) {
    private val context = context.applicationContext
    private val packageManager = context.packageManager
    private val icons = LruCache<String, Bitmap>(ICON_CACHE_SIZE)

    suspend fun load(): List<InstalledGame> = withContext(Dispatchers.IO) {
        launcherPackages().mapNotNull { packageName ->
            val info = applicationInfo(packageName) ?: return@mapNotNull null
            InstalledGame(
                packageName = packageName,
                label = packageManager.getApplicationLabel(info).toString(),
                isGame = isGame(info),
                declaresGamepad = declaresGamepad(packageName),
            )
        }
    }

    /** A small bitmap of the app's icon, cached, or null if it has none. */
    suspend fun icon(packageName: String, sizePx: Int): Bitmap? = withContext(Dispatchers.IO) {
        icons.get(packageName)?.let { return@withContext it }
        val drawable = try {
            packageManager.getApplicationIcon(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            return@withContext null
        }
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(Canvas(bitmap))
        icons.put(packageName, bitmap)
        bitmap
    }

    /** Starts the game. Returns false if it has no launcher activity any more. */
    fun launch(packageName: String): Boolean {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    private fun launcherPackages(): List<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
        return activities.map { it.activityInfo.packageName }.distinct()
    }

    private fun applicationInfo(packageName: String): ApplicationInfo? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            packageManager.getApplicationInfo(packageName, 0)
        }
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private fun isGame(info: ApplicationInfo): Boolean {
        @Suppress("DEPRECATION")
        val flagged = info.flags and ApplicationInfo.FLAG_IS_GAME != 0
        return flagged || info.category == ApplicationInfo.CATEGORY_GAME
    }

    /** Reads the app's `<uses-feature>` list for the gamepad feature. */
    private fun declaresGamepad(packageName: String): Boolean = try {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_CONFIGURATIONS.toLong()))
        } else {
            packageManager.getPackageInfo(packageName, PackageManager.GET_CONFIGURATIONS)
        }
        info.reqFeatures.orEmpty().any { it.name == PackageManager.FEATURE_GAMEPAD }
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private companion object {
        const val ICON_CACHE_SIZE = 128
    }
}
