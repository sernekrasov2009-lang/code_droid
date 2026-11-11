package ru.slicoo.code_droid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ru.slicoo.code_droid.core.ui.navigation.Routes
import ru.slicoo.code_droid.core.ui.theme.CodeDroidTheme
import ru.slicoo.code_droid.core.data.AppConfigRepository
import ru.slicoo.code_droid.core.data.ChatConfig
import ru.slicoo.code_droid.feature.chat.ChatViewModel
import ru.slicoo.code_droid.feature.chat.ChatRoute
import ru.slicoo.code_droid.feature.onboarding.OnboardingViewModel
import ru.slicoo.code_droid.feature.onboarding.OnboardingRoute
import ru.slicoo.code_droid.feature.settings.SettingsViewModel
import ru.slicoo.code_droid.feature.settings.SettingsRoute

private class AppGraph(
    val chatConfig: ChatConfig,
    val chatViewModelCreator: () -> ChatViewModel,
    val onboardingViewModelCreator: () -> OnboardingViewModel,
    val settingsViewModelCreator: () -> SettingsViewModel,
    val isConfigured: Boolean,
)

@Composable
fun CodeDroidApp() {
    CodeDroidTheme {
        val context = LocalContext.current
        val graph = remember(context) {
            val repository = AppConfigRepository(context)
            val chatConfig = ChatConfig(repository)
            AppGraph(
                chatConfig = chatConfig,
                chatViewModelCreator = { ChatViewModel(chatConfig) },
                onboardingViewModelCreator = { OnboardingViewModel(repository, chatConfig) },
                settingsViewModelCreator = { SettingsViewModel(repository, chatConfig) },
                isConfigured = chatConfig.activeSetup() != null,
            )
        }
        val navController = rememberNavController()

        NavHost(
            navController = navController,
            startDestination = if (graph.isConfigured) Routes.HOME else Routes.ONBOARDING,
        ) {
            composable(Routes.HOME) {
                ChatRoute(
                    viewModel = graph.chatViewModelCreator(),
                    onEnterKey = {
                        navController.navigate(Routes.SETTINGS)
                    },
                )
            }
            composable(Routes.ONBOARDING) {
                OnboardingRoute(
                    viewModel = graph.onboardingViewModelCreator(),
                    onDone = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsRoute(
                    viewModel = graph.settingsViewModelCreator(),
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
