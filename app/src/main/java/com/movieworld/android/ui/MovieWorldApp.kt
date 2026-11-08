package com.movieworld.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.movieworld.android.Playback
import com.movieworld.android.ui.components.AppIcons
import com.movieworld.android.ui.detail.DetailScreen
import com.movieworld.android.ui.history.HistoryScreen
import com.movieworld.android.ui.home.HomeScreen
import com.movieworld.android.ui.list.MyListScreen
import com.movieworld.android.ui.player.MiniPlayer
import com.movieworld.android.ui.player.PlaybackPhase
import com.movieworld.android.ui.player.PlayerSheet
import com.movieworld.android.ui.player.phase
import com.movieworld.android.ui.search.SearchScreen
import com.movieworld.android.ui.settings.TvSettingsScreen
import com.movieworld.android.ui.theme.Surface1
import com.movieworld.android.ui.theme.Surface2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIST = "list"
    const val HISTORY = "history"
    const val MOVIE = "movie/{id}"
    const val SETTINGS = "settings"
    fun movie(id: String) = "movie/$id"
}

/** Shows a transient message in the app-level snackbar. */
val LocalMessages = staticCompositionLocalOf<(String) -> Unit> { {} }

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Filled.Home),
    Tab(Routes.SEARCH, "Search", Icons.Filled.Search),
    Tab(Routes.LIST, "My List", AppIcons.Bookmark),
    Tab(Routes.HISTORY, "History", AppIcons.History),
)

@Composable
fun MovieWorldApp(graph: AppGraph) {
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val messages = remember<(String) -> Unit> { { text -> scope.launch { snackbar.showSnackbar(text) } } }
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showTabs = route in tabs.map { it.route }
    val cast by Playback.state.collectAsStateWithLifecycle()
    val phase = cast.phase()
    var dismissedMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val showPlayer = route != Routes.SETTINGS && phase != PlaybackPhase.Idle && (cast.active || dismissedMessage != cast.message)
    var expanded by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    var poster by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(cast.movieId) {
        poster = cast.movieId?.let { id -> withContext(Dispatchers.IO) { catalogueResult { graph.catalogue.movie(id)?.poster } } }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, graph) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) graph.cloud.reconcileNow()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    CompositionLocalProvider(LocalAppGraph provides graph, LocalMessages provides messages) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                Column {
                    AnimatedVisibility(showPlayer, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        MiniPlayer(
                            state = cast, poster = poster,
                            onExpand = { expanded = true },
                            onPlayPause = { Playback.control(context, if (cast.playing) "pause" else "resume") },
                            onStop = { Playback.control(context, "stop") },
                            onDismiss = { dismissedMessage = cast.message },
                            modifier = if (showTabs) Modifier else Modifier.background(Surface2).navigationBarsPadding(),
                        )
                    }
                    AnimatedVisibility(showTabs, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        BottomTabs(navController, route)
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController, startDestination = Routes.HOME,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.96f) },
                exitTransition = { fadeOut(tween(180)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.96f) },
            ) {
                composable(Routes.HOME) {
                    HomeScreen(onOpen = { navController.navigate(Routes.movie(it)) },
                        onSettings = { navController.navigate(Routes.SETTINGS) }, contentPadding = padding)
                }
                composable(Routes.SEARCH) { SearchScreen(onOpen = { navController.navigate(Routes.movie(it)) }, contentPadding = padding) }
                composable(Routes.LIST) { MyListScreen(onOpen = { navController.navigate(Routes.movie(it)) }, contentPadding = padding) }
                composable(Routes.HISTORY) { HistoryScreen(onOpen = { navController.navigate(Routes.movie(it)) }, contentPadding = padding) }
                composable(Routes.MOVIE) { backStack ->
                    val id = backStack.arguments?.getString("id").orEmpty()
                    DetailScreen(id, onBack = { navController.popBackStack() },
                        onOpen = { navController.navigate(Routes.movie(it)) }, contentPadding = padding)
                }
                composable(Routes.SETTINGS) { TvSettingsScreen(onBack = { navController.popBackStack() }) }
            }
        }
    }
    if (expanded && phase != PlaybackPhase.Idle) {
        PlayerSheet(
            state = cast, poster = poster,
            onPlayPause = { Playback.control(context, if (cast.playing) "pause" else "resume") },
            onSeek = { Playback.control(context, "seek", it) },
            onStop = { Playback.control(context, "stop") },
            onDismiss = { expanded = false },
        )
    }
}

@Composable
private fun BottomTabs(navController: NavHostController, route: String?) {
    NavigationBar(containerColor = Surface1, tonalElevation = 0.dp) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = route == tab.route,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = Color.Transparent,
                ),
            )
        }
    }
}