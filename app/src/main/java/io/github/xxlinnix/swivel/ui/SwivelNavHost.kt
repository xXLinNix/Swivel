package io.github.xxlinnix.swivel.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.xxlinnix.swivel.ui.games.GamesScreen
import io.github.xxlinnix.swivel.ui.home.HomeScreen
import io.github.xxlinnix.swivel.ui.pairing.PairingScreen
import io.github.xxlinnix.swivel.ui.test.ControllerTestScreen
import io.github.xxlinnix.swivel.ui.test.ModeATestScreen

const val TEST_DESCRIPTOR_ARG = "descriptor"
const val MODE_A_ADDRESS_ARG = "address"

private object Routes {
    const val HOME = "home"
    const val PAIR = "pair"
    const val GAMES = "games"
    const val TEST = "test/{$TEST_DESCRIPTOR_ARG}"
    const val MODE_A_TEST = "modea/{$MODE_A_ADDRESS_ARG}"

    fun test(descriptor: String) = "test/${Uri.encode(descriptor)}"
    fun modeATest(address: String) = "modea/${Uri.encode(address)}"
}

@Composable
fun SwivelNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onPair = { nav.navigate(Routes.PAIR) },
                onTest = { descriptor -> nav.navigate(Routes.test(descriptor)) },
                onTestModeA = { address -> nav.navigate(Routes.modeATest(address)) },
                onGames = { nav.navigate(Routes.GAMES) },
            )
        }
        composable(Routes.GAMES) {
            GamesScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.PAIR) {
            PairingScreen(
                onOpenTest = { descriptor ->
                    nav.navigate(Routes.test(descriptor)) { popUpTo(Routes.HOME) }
                },
                onOpenModeATest = { address ->
                    nav.navigate(Routes.modeATest(address)) { popUpTo(Routes.HOME) }
                },
                onClose = { nav.popBackStack(Routes.HOME, inclusive = false) },
            )
        }
        composable(
            route = Routes.TEST,
            arguments = listOf(navArgument(TEST_DESCRIPTOR_ARG) { type = NavType.StringType }),
        ) {
            ControllerTestScreen(onBack = { nav.popBackStack() })
        }
        composable(
            route = Routes.MODE_A_TEST,
            arguments = listOf(navArgument(MODE_A_ADDRESS_ARG) { type = NavType.StringType }),
        ) {
            ModeATestScreen(onBack = { nav.popBackStack() })
        }
    }
}
