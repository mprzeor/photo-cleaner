package pl.przeor.photocleaner.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import pl.przeor.photocleaner.di.AppContainer
import pl.przeor.photocleaner.ui.home.HomeScreen
import pl.przeor.photocleaner.ui.home.HomeViewModel
import pl.przeor.photocleaner.ui.results.ResultsScreen
import pl.przeor.photocleaner.ui.results.ResultsViewModel

object Routes {
    const val HOME = "home"
    const val RESULTS = "results"
}

@Composable
fun AppNavGraph(container: AppContainer) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            val vm: HomeViewModel = viewModel { HomeViewModel(container) }
            HomeScreen(
                vm = vm,
                onShowResults = {
                    navController.navigate(Routes.RESULTS) { launchSingleTop = true }
                },
            )
        }
        composable(Routes.RESULTS) {
            val vm: ResultsViewModel = viewModel { ResultsViewModel(container) }
            ResultsScreen(vm = vm, onBack = { navController.popBackStack() })
        }
    }
}
