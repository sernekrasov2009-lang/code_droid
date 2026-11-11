package ru.slicoo.code_droid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ru.slicoo.code_droid.core.ui.navigation.Routes
import ru.slicoo.code_droid.core.ui.theme.CodeDroidTheme
import ru.slicoo.code_droid.core.data.ChatConfig
import ru.slicoo.code_droid.core.data.SharedPrefsApiKeyStore
import ru.slicoo.code_droid.feature.chat.ChatViewModel
import ru.slicoo.code_droid.feature.chat.ChatRoute
import ru.slicoo.code_droid.feature.onboarding.OnboardingViewModel
import ru.slicoo.code_droid.feature.onboarding.OnboardingRoute

private class AppGraph(
    val chatConfig: ChatConfig,
    val chatViewModelCreator: () -> ChatViewModel,
    val onboardingViewModelCreator: () -> OnboardingViewModel,
)

@Composable
fun CodeDroidApp() {
    CodeDroidTheme {
        val context = LocalContext.current
        val graph = remember(context) {
            val keyStore = SharedPrefsApiKeyStore(context)
            val chatConfig = ChatConfig(keyStore)
            AppGraph(
                chatConfig = chatConfig,
                chatViewModelCreator = { ChatViewModel(chatConfig) },
                onboardingViewModelCreator = { OnboardingViewModel(keyStore, chatConfig) },
            )
        }
        val navController = rememberNavController()
        // First launch without a saved key starts at onboarding.
        val hasKey = remember(context) {
            !SharedPrefsApiKeyStore(context).read().isNullOrBlank()
        }

        NavHost(
            navController = navController,
            startDestination = if (hasKey) Routes.HOME else Routes.ONBOARDING,
        ) {
            composable(Routes.HOME) {
                ChatRoute(
                    viewModel = graph.chatViewModelCreator(),
                    onEnterKey = {
                        navController.navigate(Routes.ONBOARDING)
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
        }
    }
}
