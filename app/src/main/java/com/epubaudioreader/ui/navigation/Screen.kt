package com.epubaudioreader.ui.navigation

sealed class Screen(val route: String) {
    data object Library : Screen("library")
    data object BookDetail : Screen("bookDetail/{bookId}") {
        fun createRoute(bookId: Long) = "bookDetail/$bookId"
    }
    data object Reader : Screen("reader/{bookId}/{chapterId}") {
        fun createRoute(bookId: Long, chapterId: Long) = "reader/$bookId/$chapterId"
    }
}
