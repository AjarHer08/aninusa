package id.aninusa.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import id.aninusa.app.data.LocalStore
import id.aninusa.app.ui.anime.AnimeDetailScreen
import id.aninusa.app.ui.home.HomeScreen
import id.aninusa.app.ui.library.LibraryScreen
import id.aninusa.app.ui.manga.MangaDetailScreen
import id.aninusa.app.ui.reader.ReaderScreen
import id.aninusa.app.ui.search.SearchScreen
import id.aninusa.app.ui.settings.SettingsScreen
import id.aninusa.app.ui.theme.AniNusaTheme

object Routes {
    const val MAIN = "main"
    const val ANIME = "anime/{id}"
    const val MANGA = "manga/{id}"
    const val READER = "reader/{mangaId}/{chapterId}/{lang}/{startPage}/{saver}/{vertical}"

    fun anime(id: Int) = "anime/$id"
    fun manga(id: String) = "manga/$id"

    fun reader(
        mangaId: String,
        chapterId: String,
        lang: String,
        startPage: Int,
        dataSaver: Boolean,
        vertical: Boolean
    ) = "reader/$mangaId/$chapterId/$lang/$startPage/$dataSaver/$vertical"
}

@Composable
fun AniNusaRoot() {
    val userData by LocalStore.get().data.collectAsState()

    AniNusaTheme(mode = userData.prefs.themeMode) {
        val navController = rememberNavController()
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding()
        ) {
            NavHost(
                navController = navController,
                startDestination = Routes.MAIN
            ) {
                composable(Routes.MAIN) {
                    MainScreen(
                        onOpenAnime = { id -> navController.navigate(Routes.anime(id)) },
                        onOpenManga = { id -> navController.navigate(Routes.manga(id)) },
                        onOpenReader = { mangaId, chapterId, lang, page, saver ->
                            navController.navigate(
                                Routes.reader(
                                    mangaId = mangaId,
                                    chapterId = chapterId,
                                    lang = lang,
                                    startPage = page,
                                    dataSaver = saver,
                                    vertical = userData.prefs.verticalReader
                                )
                            )
                        }
                    )
                }

                composable(
                    route = Routes.ANIME,
                    arguments = listOf(navArgument("id") { type = NavType.IntType })
                ) { entry ->
                    AnimeDetailScreen(
                        id = entry.arguments?.getInt("id") ?: 0,
                        onBack = { navController.popBackStack() },
                        onOpenAnime = { id -> navController.navigate(Routes.anime(id)) }
                    )
                }

                composable(
                    route = Routes.MANGA,
                    arguments = listOf(navArgument("id") { type = NavType.StringType })
                ) { entry ->
                    val mangaId = entry.arguments?.getString("id").orEmpty()
                    MangaDetailScreen(
                        mangaId = mangaId,
                        onBack = { navController.popBackStack() },
                        onRead = { chapterId, lang, startPage, saver ->
                            navController.navigate(
                                Routes.reader(
                                    mangaId = mangaId,
                                    chapterId = chapterId,
                                    lang = lang,
                                    startPage = startPage,
                                    dataSaver = saver,
                                    vertical = userData.prefs.verticalReader
                                )
                            )
                        }
                    )
                }

                composable(
                    route = Routes.READER,
                    arguments = listOf(
                        navArgument("mangaId") { type = NavType.StringType },
                        navArgument("chapterId") { type = NavType.StringType },
                        navArgument("lang") { type = NavType.StringType },
                        navArgument("startPage") { type = NavType.IntType },
                        navArgument("saver") { type = NavType.BoolType },
                        navArgument("vertical") { type = NavType.BoolType }
                    )
                ) { entry ->
                    val args = entry.arguments
                    ReaderScreen(
                        mangaId = args?.getString("mangaId").orEmpty(),
                        chapterId = args?.getString("chapterId").orEmpty(),
                        lang = args?.getString("lang") ?: "id",
                        startPage = args?.getInt("startPage") ?: 0,
                        dataSaver = args?.getBoolean("saver") ?: false,
                        vertical = args?.getBoolean("vertical") ?: true,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

private data class NavItem(val label: String, val icon: ImageVector)

@Composable
fun MainScreen(
    onOpenAnime: (Int) -> Unit,
    onOpenManga: (String) -> Unit,
    onOpenReader: (String, String, String, Int, Boolean) -> Unit
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val userData by LocalStore.get().data.collectAsState()

    val items = listOf(
        NavItem("Beranda", Icons.Default.Home),
        NavItem("Cari", Icons.Default.Search),
        NavItem("Koleksi", Icons.Default.Favorite),
        NavItem("Setelan", Icons.Default.Settings)
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                windowInsets = WindowInsets(0, 0, 0, 0)
            ) {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label, fontSize = 11.sp) }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (tab) {
                0 -> HomeScreen(
                    onOpenAnime = onOpenAnime,
                    onOpenManga = onOpenManga,
                    onOpenSearch = { tab = 1 }
                )

                1 -> SearchScreen(
                    onOpenAnime = onOpenAnime,
                    onOpenManga = onOpenManga
                )

                2 -> LibraryScreen(
                    onOpenAnime = onOpenAnime,
                    onOpenManga = onOpenManga,
                    onContinueReading = { progress ->
                        onOpenReader(
                            progress.mangaId,
                            progress.chapterId,
                            progress.lang,
                            progress.page,
                            userData.prefs.dataSaver
                        )
                    },
                    onOpenDownloaded = { mangaId, chapterId, lang ->
                        onOpenReader(mangaId, chapterId, lang, 0, userData.prefs.dataSaver)
                    }
                )

                else -> SettingsScreen()
            }
        }
    }
}
