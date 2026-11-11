package ru.slicoo.code_droid.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ru.slicoo.code_droid.core.ui.navigation.Routes
import ru.slicoo.code_droid.core.ui.theme.CodeDroidTheme
import ru.slicoo.code_droid.feature.chat.ChatRoute
import ru.slicoo.code_droid.feature.onboarding.OnboardingRoute

@Composable
fun CodeDroidApp() {
    CodeDroidTheme {
        val navController = rememberNavController()

        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
        ) {
            composable(Routes.HOME) {
                ChatRoute()
            }
            composable(Routes.ONBOARDING) {
                OnboardingRoute(
                    onDone = { navController.navigate(Routes.HOME) },
                )
            }
        }
    }
}
