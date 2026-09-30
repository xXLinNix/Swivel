package io.github.xxlinnix.swivel.core.games

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameCatalogTest {
    private fun app(pkg: String, label: String, game: Boolean = true, pad: Boolean = false) =
        InstalledGame(pkg, label, isGame = game, declaresGamepad = pad)

    private val apps = listOf(
        app("com.example.puzzle", "puzzle"),
        app("com.example.racer", "Racer", pad = true),
        app("com.example.arcade", "Arcade", pad = true),
        app("com.example.notes", "Notes", game = false),
        app("com.example.streaming", "Cloud Play", game = false, pad = true),
        app("com.orangepixel.meganoid", "Meganoid"),
        app("io.github.xxlinnix.swivel", "Swivel", game = false, pad = true),
    )

    @Test
    fun controllerFilterShowsDeclaredGamepadAppsAndKnownMogaGames() {
        val shown = GameCatalog.visible(apps, GameFilter.CONTROLLER_SUPPORT, "io.github.xxlinnix.swivel")
        assertEquals(listOf("Arcade", "Cloud Play", "Racer", "Meganoid"), shown.map { it.label })
    }

    @Test
    fun allGamesAddsTheRestButNeverOrdinaryApps() {
        val shown = GameCatalog.visible(apps, GameFilter.ALL_GAMES, "io.github.xxlinnix.swivel")
        assertEquals(listOf("Arcade", "Cloud Play", "Racer", "Meganoid", "puzzle"), shown.map { it.label })
    }

    @Test
    fun swivelNeverListsItself() {
        assertTrue(GameCatalog.visible(apps, GameFilter.ALL_GAMES, "io.github.xxlinnix.swivel").none { it.label == "Swivel" })
    }

    @Test
    fun theKnownListHasPivotsGames() {
        assertTrue(GameCatalog.isKnownMoga("com.ratrodstudio.driftmania2"))
        assertTrue(GameCatalog.isKnownMoga("com.NamcoNetworks.international.PacManMoga"))
        assertEquals(24, GameCatalog.KNOWN_MOGA_PACKAGES.size)
    }
}
