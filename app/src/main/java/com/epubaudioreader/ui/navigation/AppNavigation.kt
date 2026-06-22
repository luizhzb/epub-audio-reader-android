package com.epubaudioreader.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.epubaudioreader.ui.screens.bookdetail.BookDetailScreen
import com.epubaudioreader.ui.screens.bookdetail.BookDetailViewModel
import com.epubaudioreader.ui.screens.library.LibraryScreen
import com.epubaudioreader.ui.screens.library.LibraryViewModel
import com.epubaudioreader.ui.screens.reader.ReaderScreen
import com.epubaudioreader.ui.screens.reader.ReaderViewModel

@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Screen.Library.route) {
        composable(Screen.Library.route) {
            val viewModel: LibraryViewModel = hiltViewModel()
            LibraryScreen(
                viewModel = viewModel,
                onBookClick = { bookId ->
                    navController.navigate(Screen.BookDetail.createRoute(bookId))
                }
            )
        }
        composable(
            route = Screen.BookDetail.route,
            arguments = listOf(navArgument("bookId") { type = NavType.LongType })
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getLong("bookId") ?: 0L
            val viewModel: BookDetailViewModel = hiltViewModel()
            BookDetailScreen(
                viewModel = viewModel,
                bookId = bookId,
                onBackClick = { navController.popBackStack() },
                onChapterClick = { chapterId ->
                    navController.navigate(Screen.Reader.createRoute(bookId, chapterId))
                }
            )
        }
        composable(
            route = Screen.Reader.route,
            arguments = listOf(
                navArgument("bookId") { type = NavType.LongType },
                navArgument("chapterId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getLong("bookId") ?: 0L
            val chapterId = backStackEntry.arguments?.getLong("chapterId") ?: 0L
            val viewModel: ReaderViewModel = hiltViewModel()
            ReaderScreen(
                viewModel = viewModel,
                bookId = bookId,
                chapterId = chapterId,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
