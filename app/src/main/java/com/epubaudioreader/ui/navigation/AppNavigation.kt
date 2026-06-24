package com.epubaudioreader.ui.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
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
import com.epubaudioreader.ui.screens.ttstest.TtsTestScreen

private const val TAG = "AppNavigation"

@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    // Stop TTS when navigating away from the Reader screen (BUG-READ-014)
    DisposableEffect(navController) {
        val listener =
            androidx.navigation.NavController.OnDestinationChangedListener { _, destination, _ ->
                val currentRoute = destination.route ?: ""
                // If we are navigating away from the Reader screen, stop any playing TTS
                if (!currentRoute.startsWith(Screen.Reader.route.substringBefore("/"))) {
                    // Access the current ReaderViewModel from the back stack and stop TTS
                    try {
                        val previousEntry = navController.previousBackStackEntry
                        if (previousEntry != null) {
                            val readerVm = androidx.hilt.navigation.compose.hiltViewModel<ReaderViewModel>(
                                previousEntry
                            )
                            readerVm.stopTts()
                            Log.d(TAG, "Navigated away from Reader, TTS stopped")
                        }
                    } catch (e: Exception) {
                        // No ReaderViewModel in the back stack, ignore
                    }
                }
            }
        navController.addOnDestinationChangedListener(listener)
        onDispose {
            navController.removeOnDestinationChangedListener(listener)
        }
    }

    NavHost(navController = navController, startDestination = Screen.Library.route) {
        composable(Screen.Library.route) {
            val viewModel: LibraryViewModel = hiltViewModel()
            LibraryScreen(
                viewModel = viewModel,
                onBookClick = { bookId ->
                    navController.navigate(Screen.BookDetail.createRoute(bookId))
                },
                onTtsTestClick = {
                    navController.navigate(Screen.TtsTest.route)
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
                onBackClick = { navController.popBackStack() },
                // Navigate to next chapter when TTS finishes the last paragraph (BUG-READ-017)
                onNavigateToNextChapter = { currentChapterId ->
                    val nextChapterId = currentChapterId + 1
                    Log.i(TAG, "Advancing to next chapter: $nextChapterId")
                    navController.navigate(Screen.Reader.createRoute(bookId, nextChapterId)) {
                        // Pop the current reader screen so back button goes to book detail
                        popUpTo(Screen.Reader.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.TtsTest.route) {
            TtsTestScreen(onBackClick = { navController.popBackStack() })
        }
    }
}
