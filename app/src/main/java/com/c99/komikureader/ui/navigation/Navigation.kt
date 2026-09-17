package com.c99.komikureader.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.c99.komikureader.KomikuApp
import com.c99.komikureader.ui.detail.MangaDetailScreen
import com.c99.komikureader.ui.home.HomeScreen
import com.c99.komikureader.ui.library.LibraryScreen
import com.c99.komikureader.ui.reader.ChapterReaderScreen
import com.c99.komikureader.ui.search.BrowseScreen
import com.c99.komikureader.ui.search.SearchScreen

import java.net.URLEncoder
import java.net.URLDecoder

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val BROWSE = "browse"
    const val MANGA_DETAIL = "manga/{slug}"
    const val CHAPTER_READER = "chapter/{slug}/{chapterUrl}"
    const val LIBRARY = "library"

    fun mangaDetail(slug: String) = "manga/$slug"
    fun chapterReader(slug: String, chapterUrl: String): String {
        val encoded = URLEncoder.encode(chapterUrl, "UTF-8")
        return "chapter/$slug/$encoded"
    }
    fun decodeChapterUrl(encoded: String) = URLDecoder.decode(encoded, "UTF-8")
}

@Composable
fun KomikuNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val repo = (context.applicationContext as KomikuApp).repository

    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                repository = repo,
                onMangaClick = { slug -> navController.navigate(Routes.mangaDetail(slug)) },
                onSearchClick = { navController.navigate(Routes.SEARCH) },
                onBrowseClick = { navController.navigate(Routes.BROWSE) },
                onLibraryClick = { navController.navigate(Routes.LIBRARY) }
            )
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                repository = repo,
                onMangaClick = { slug -> navController.navigate(Routes.mangaDetail(slug)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.BROWSE) {
            BrowseScreen(
                repository = repo,
                onMangaClick = { slug -> navController.navigate(Routes.mangaDetail(slug)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            Routes.MANGA_DETAIL,
            arguments = listOf(navArgument("slug") { type = NavType.StringType })
        ) { backStackEntry ->
            val slug = backStackEntry.arguments?.getString("slug") ?: ""
            MangaDetailScreen(
                slug = slug,
                repository = repo,
                onChapterClick = { chapterUrl ->
                    navController.navigate(Routes.chapterReader(slug, chapterUrl))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            Routes.CHAPTER_READER,
            arguments = listOf(
                navArgument("slug") { type = NavType.StringType },
                navArgument("chapterUrl") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val slug = backStackEntry.arguments?.getString("slug") ?: ""
            val chapterUrlEncoded = backStackEntry.arguments?.getString("chapterUrl") ?: ""
            val chapterUrl = Routes.decodeChapterUrl(chapterUrlEncoded)
            ChapterReaderScreen(
                slug = slug,
                chapterUrl = chapterUrl,
                repository = repo,
                onBack = { navController.popBackStack() },
                onChapterChange = { newUrl ->
                    navController.navigate(Routes.chapterReader(slug, newUrl)) {
                        popUpTo(Routes.MANGA_DETAIL)
                    }
                }
            )
        }

        composable(Routes.LIBRARY) {
            LibraryScreen(
                repository = repo,
                onMangaClick = { slug -> navController.navigate(Routes.mangaDetail(slug)) },
                onChapterClick = { slug, chapterUrl ->
                    navController.navigate(Routes.chapterReader(slug, chapterUrl))
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}