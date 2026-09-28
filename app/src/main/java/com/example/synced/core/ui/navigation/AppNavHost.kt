package com.example.synced.core.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.synced.feature.content.presentation.detail.DetailScreen
import com.example.synced.feature.content.presentation.list.ContentScreen
import com.example.synced.feature.settings.presentation.SettingsScreen

object Routes {
    const val CONTENT = "content"
    const val SETTINGS = "settings"
    const val DETAIL = "detail/{postId}"
    fun detail(postId: Int) = "detail/$postId"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.CONTENT) {
        composable(Routes.CONTENT) {
            ContentScreen(
                onPostClick = { postId -> navController.navigate(Routes.detail(postId)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("postId") { type = NavType.IntType }),
        ) {
            DetailScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
