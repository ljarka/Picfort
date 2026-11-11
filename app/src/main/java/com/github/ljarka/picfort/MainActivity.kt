package com.github.ljarka.picfort

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.github.ljarka.picfort.ui.shell.ComingSoonScreen
import com.github.ljarka.picfort.ui.shell.Tab
import com.github.ljarka.picfort.ui.shell.TabBar
import com.github.ljarka.picfort.ui.shell.TabRoot
import com.github.ljarka.picfort.db.AssetType
import com.github.ljarka.picfort.network.SessionManager
import com.github.ljarka.picfort.ui.image.ImageDetailsScreen
import com.github.ljarka.picfort.ui.init.InitScreen
import com.github.ljarka.picfort.ui.theme.PicfortTheme
import com.github.ljarka.picfort.collections.AlbumScreen
import com.github.ljarka.picfort.collections.AlbumsScreen
import com.github.ljarka.picfort.collections.GridAsset
import com.github.ljarka.picfort.collections.SearchScreen
import com.github.ljarka.picfort.ui.frame.FrameScreen
import com.github.ljarka.picfort.ui.frame.PicfortServerSetupScreen
import com.github.ljarka.picfort.ui.wall.FamilyWallScreen
import com.github.ljarka.picfort.ui.settings.SettingsScreen
import com.github.ljarka.picfort.ui.timeline.TimelineScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.Serializable
import javax.inject.Inject

sealed interface Screen {
    @Serializable
    object Init : Screen

    @Serializable
    object Timeline : Screen

    @Serializable
    object Settings : Screen

    @Serializable
    object Frame : Screen

    @Serializable
    data class ImageDetails(
        val assetId: String,
        val assetType: AssetType,
        val imageIndex: Int,
        /** CollectionsRepository list key for albums/search; null = timeline. */
        val source: String? = null,
    ) : Screen

    @Serializable
    object Albums : Screen

    @Serializable
    data class Album(val id: String, val name: String) : Screen

    @Serializable
    object Search : Screen

    @Serializable
    object FamilyWall : Screen

    /** One-time, optional step after login when no Picfort server is set. */
    @Serializable
    object PicfortServerSetup : Screen

    /** Placeholders until T-019, T-023 and T-029. */
    @Serializable
    object Shopping : Screen

    @Serializable
    object Events : Screen

    @Serializable
    object School : Screen
}

/** One nested graph per bottom-bar tab (T-017), so each tab keeps its own back stack. */
sealed interface TabGraph {
    @Serializable
    object Wall : TabGraph

    @Serializable
    object Shopping : TabGraph

    @Serializable
    object Events : TabGraph

    @Serializable
    object School : TabGraph

    @Serializable
    object Photos : TabGraph
}

private val Tab.graph: TabGraph
    get() = when (this) {
        Tab.WALL -> TabGraph.Wall
        Tab.SHOPPING -> TabGraph.Shopping
        Tab.EVENTS -> TabGraph.Events
        Tab.SCHOOL -> TabGraph.School
        Tab.PHOTOS -> TabGraph.Photos
    }

/** The tab's first screen: the bar is shown only on these. */
private val Tab.root: Screen
    get() = when (this) {
        Tab.WALL -> Screen.FamilyWall
        Tab.SHOPPING -> Screen.Shopping
        Tab.EVENTS -> Screen.Events
        Tab.SCHOOL -> Screen.School
        Tab.PHOTOS -> Screen.Timeline
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val navController = rememberNavController()
            LaunchedEffect(Unit) {
                // Token rejected (revoked, expired, server reset): back to login.
                sessionManager.loggedOut.collect {
                    navController.navigate(Screen.Init) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                }
            }
            val backStackEntry by navController.currentBackStackEntryAsState()
            val destination = backStackEntry?.destination
            val currentTab = Tab.entries.firstOrNull { tab ->
                destination?.hierarchy?.any { it.hasRoute(tab.graph::class) } == true
            }
            val onTabRoot = currentTab != null && destination?.hasRoute(currentTab.root::class) == true
            // Bumped when Photos is reselected at its root: the timeline scrolls to the top.
            var photosScrollToTop by remember { mutableIntStateOf(0) }
            val openSettings = { navController.navigate(Screen.Settings) }

            PicfortTheme {
                SharedTransitionLayout {
                    Box(Modifier.fillMaxSize()) {
                        NavHost(
                            navController = navController,
                            startDestination = Screen.Init,
                        ) {
                            composable<Screen.Init> {
                                InitScreen(
                                    onInitialized = { picfortServerStep ->
                                        if (picfortServerStep) {
                                            navController.navigate(Screen.PicfortServerSetup) {
                                                popUpTo(Screen.Init) { inclusive = true }
                                            }
                                        } else {
                                            navController.navigate(TabGraph.Wall) {
                                                popUpTo(Screen.Init) { inclusive = true }
                                            }
                                        }
                                    }
                                )
                            }
                            composable<Screen.PicfortServerSetup> {
                                PicfortServerSetupScreen(
                                    onDone = {
                                        navController.navigate(TabGraph.Wall) {
                                            popUpTo(Screen.PicfortServerSetup) { inclusive = true }
                                        }
                                    }
                                )
                            }
                            navigation<TabGraph.Wall>(startDestination = Screen.FamilyWall) {
                                composable<Screen.FamilyWall> {
                                    TabRoot { FamilyWallScreen(onSettingsClick = openSettings) }
                                }
                            }
                            navigation<TabGraph.Shopping>(startDestination = Screen.Shopping) {
                                composable<Screen.Shopping> {
                                    TabRoot {
                                        ComingSoonScreen(
                                            title = "Shopping",
                                            message = "Shopping lists are coming soon.",
                                            serverMessage = "Connect your Picfort server to use shopping lists.",
                                            onSettingsClick = openSettings,
                                        )
                                    }
                                }
                            }
                            navigation<TabGraph.Events>(startDestination = Screen.Events) {
                                composable<Screen.Events> {
                                    TabRoot {
                                        ComingSoonScreen(
                                            title = "Events",
                                            message = "Events are coming soon.",
                                            serverMessage = "Connect your Picfort server to use events.",
                                            onSettingsClick = openSettings,
                                        )
                                    }
                                }
                            }
                            navigation<TabGraph.School>(startDestination = Screen.School) {
                                composable<Screen.School> {
                                    TabRoot {
                                        ComingSoonScreen(
                                            title = "School",
                                            message = "School timetables are coming soon.",
                                            serverMessage = "Connect your Picfort server to use school timetables.",
                                            onSettingsClick = openSettings,
                                        )
                                    }
                                }
                            }
                            navigation<TabGraph.Photos>(startDestination = Screen.Timeline) {
                                composable<Screen.Timeline> {
                                    TabRoot {
                                        TimelineScreen(
                                            sharedTransitionScope = this@SharedTransitionLayout,
                                            animatedVisibilityScope = this@composable,
                                            onImageClick = { assetId, assetType, imageIndex ->
                                                navController.navigate(
                                                    Screen.ImageDetails(assetId, assetType, imageIndex)
                                                ) {
                                                    popUpTo(Screen.Timeline)
                                                }
                                            },
                                            onSettingsClick = openSettings,
                                            onSearchClick = { navController.navigate(Screen.Search) },
                                            onAlbumsClick = { navController.navigate(Screen.Albums) },
                                            scrollToTop = photosScrollToTop,
                                        )
                                    }
                                }
                                val openFromCollection: (GridAsset, Int, String) -> Unit = { asset, index, source ->
                                    navController.navigate(Screen.ImageDetails(asset.id, AssetType.REMOTE, index, source))
                                }
                                composable<Screen.Albums> {
                                    AlbumsScreen(
                                        onBack = { navController.popBackStack() },
                                        onAlbumClick = { id, name -> navController.navigate(Screen.Album(id, name)) },
                                    )
                                }
                                composable<Screen.Album> {
                                    AlbumScreen(onBack = { navController.popBackStack() }, onAssetClick = openFromCollection)
                                }
                                composable<Screen.Search> {
                                    SearchScreen(onBack = { navController.popBackStack() }, onAssetClick = openFromCollection)
                                }
                            }
                            composable<Screen.Settings> {
                                SettingsScreen(
                                    onBack = { navController.popBackStack() },
                                    onFrameClick = { navController.navigate(Screen.Frame) },
                                )
                            }
                            composable<Screen.Frame> {
                                FrameScreen(onBack = { navController.popBackStack() })
                            }
                            composable<Screen.ImageDetails> { backStackEntry ->
                                val imageDetails = backStackEntry.toRoute<Screen.ImageDetails>()
                                val assetId = imageDetails.assetId
                                val assetType = imageDetails.assetType
                                val imageIndex = imageDetails.imageIndex
                                var isPopBackStackCalled by remember { mutableStateOf(false) }
                                ImageDetailsScreen(
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = this@composable,
                                    onDismissRequest = {
                                        if (!isPopBackStackCalled) {
                                            isPopBackStackCalled = true
                                            navController.popBackStack()
                                        }
                                    },
                                    assetId = requireNotNull(assetId),
                                    assetType = requireNotNull(assetType),
                                    imageIndex = requireNotNull(imageIndex),
                                )
                            }
                        }
                        // Drawn over the screens and removed at once on other screens, so the
                        // photo viewer is edge to edge and its transition is unchanged.
                        if (onTabRoot) {
                            TabBar(
                                selected = currentTab,
                                onSelect = { tab ->
                                    when {
                                        tab != currentTab -> navController.navigate(tab.graph) {
                                            // Each tab keeps its stack; back from a tab root goes to Wall.
                                            popUpTo(Screen.FamilyWall) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                        tab == Tab.PHOTOS -> photosScrollToTop++
                                    }
                                },
                                modifier = Modifier.align(Alignment.BottomCenter),
                            )
                        }
                    }
                }
            }
        }
    }
}
