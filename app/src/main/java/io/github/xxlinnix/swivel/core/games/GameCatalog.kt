package io.github.xxlinnix.swivel.core.games

/** An installed app the games list might show. */
data class InstalledGame(
    val packageName: String,
    val label: String,
    /** The app says it is a game (android:appCategory="game" or the older isGame flag). */
    val isGame: Boolean,
    /** The app declares `<uses-feature android:name="android.hardware.gamepad">`. */
    val declaresGamepad: Boolean,
)

enum class GameFilter { CONTROLLER_SUPPORT, ALL_GAMES }

/**
 * Which installed apps the games list shows, and in what order.
 *
 * Android has no reliable "supports controllers" flag. An app that declares the gamepad
 * feature certainly does; many others do too without saying so. So the list offers both:
 * games that declare it, and all games.
 */
object GameCatalog {
    /**
     * Games the original Pivot app listed as MOGA-enhanced, taken from the icon file names
     * bundled in Pivot 1.23 (docs/RESEARCH.md). Most are long gone from the Play Store.
     */
    val KNOWN_MOGA_PACKAGES: Set<String> = setOf(
        "com.arbstudios.tikikartfree",
        "com.ayopagames.museandroid",
        "com.brisk.medievalandroid",
        "com.ezone.SnowPro",
        "com.frimastudio.android.SpaceShooterBlitz",
        "com.gameloft.android.ANMP.GloftA7HM",
        "com.gameloft.android.ANMP.GloftD3HM",
        "com.gameloft.android.ANMP.GloftG4HM",
        "com.gameloft.android.ANMP.GloftKRHM",
        "com.gameloft.android.ANMP.GloftN3HM",
        "com.gameloft.android.ANMP.GloftS3HM",
        "com.gameloft.android.ANMP.GloftSXHM",
        "com.gameloft.android.ANMP.GloftWBHM",
        "com.guildsoftware.vendetta",
        "com.hyperdevbox.blazingsouls",
        "com.kokak.HereticGLES",
        "com.namcobandaigames.rocketfox",
        "com.NamcoNetworks.international.PacManMoga",
        "com.orangepixel.chronocash",
        "com.orangepixel.inc",
        "com.orangepixel.meganoid",
        "com.orangepixel.meganoid2",
        "com.orangepixel.stardash",
        "com.ratrodstudio.driftmania2",
    )

    fun isKnownMoga(packageName: String): Boolean = packageName in KNOWN_MOGA_PACKAGES

    /** Whether an app belongs on the list at all. */
    fun isCandidate(app: InstalledGame): Boolean = app.isGame || app.declaresGamepad || isKnownMoga(app.packageName)

    fun visible(apps: List<InstalledGame>, filter: GameFilter, ownPackage: String): List<InstalledGame> =
        apps.asSequence()
            .filter { it.packageName != ownPackage && isCandidate(it) }
            .filter { filter == GameFilter.ALL_GAMES || it.declaresGamepad || isKnownMoga(it.packageName) }
            .sortedWith(compareByDescending<InstalledGame> { it.declaresGamepad }.thenBy { it.label.lowercase() })
            .toList()
}
