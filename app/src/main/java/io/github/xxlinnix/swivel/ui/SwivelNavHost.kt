package io.github.xxlinnix.swivel.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.xxlinnix.swivel.ui.home.HomeScreen
import io.github.xxlinnix.swivel.ui.pairing.PairingScreen
import io.github.xxlinnix.swivel.ui.test.ControllerTestScreen

const val TEST_DESCRIPTOR_ARG = "descriptor"

private object Routes {
    const val HOME = "home"
    const val PAIR = "pair"
    const val TEST = "test/{$TEST_DESCRIPTOR_ARG}"

    fun test(descriptor: String) = "test/${Uri.encode(descriptor)}"
}

@Composable
fun SwivelNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onPair = { nav.navigate(Routes.PAIR) },
                onTest = { descriptor -> nav.navigate(Routes.test(descriptor)) },
            )
        }
        composable(Routes.PAIR) {
            PairingScreen(
                onOpenTest = { descriptor ->
                    nav.navigate(Routes.test(descriptor)) { popUpTo(Routes.HOME) }
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
    }
}
