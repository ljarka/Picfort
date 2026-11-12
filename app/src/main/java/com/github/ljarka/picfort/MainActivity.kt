package com.github.ljarka.picfort

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.github.ljarka.picfort.ui.shopping.ShoppingListScreen
import com.github.ljarka.picfort.ui.shopping.ShoppingListsScreen
import com.github.ljarka.picfort.ui.shell.Tab
import com.github.ljarka.picfort.ui.shell.TabBar
import com.github.ljarka.picfort.ui.shell.TabRoot
import com.github.ljarka.picfort.core.database.AssetType
import com.github.ljarka.picfort.network.SessionManager
import com.github.ljarka.picfort.ui.image.ImageDetailsScreen
import com.github.ljarka.picfort.ui.init.InitScreen
import com.github.ljarka.picfort.core.ui.theme.PicfortTheme
import com.github.ljarka.picfort.collections.AlbumScreen
import com.github.ljarka.picfort.collections.AlbumsScreen
import com.github.ljarka.picfort.collections.GridAsset
import com.github.ljarka.picfort.collections.SearchScreen
import com.github.ljarka.picfort.ui.events.EventDetailScreen
import com.github.ljarka.picfort.ui.events.EventFormScreen
import com.github.ljarka.picfort.ui.events.EventsListScreen
import com.github.ljarka.picfort.ui.family.FamilySettingsScreen
import com.github.ljarka.picfort.ui.frame.FrameScreen
import com.github.ljarka.picfort.ui.frame.FrameSettingsDialog
import com.github.ljarka.picfort.ui.frame.PicfortServerSetupScreen
import com.github.ljarka.picfort.ui.wall.FamilyWallScreen
import com.github.ljarka.picfort.ui.settings.SettingsScreen
import com.github.ljarka.picfort.ui.timeline.AlbumRoute
import com.github.ljarka.picfort.ui.timeline.ImageDetailsRoute
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
    data class FamilySettings(val editUserId: String? = null) : Screen

    @Serializable
    object Frame : Screen

    @Serializable
    object Albums : Screen

    @Serializable
    object Search : Screen

    @Serializable
    object FamilyWall : Screen

    /** One-time, optional step after login when no Picfort server is set. */
    @Serializable
    object PicfortServerSetup : Screen

    /** School stays a placeholder until T-029. */
    @Serializable
    object Shopping : Screen

    /** `archived` is read by ShoppingListsViewModel. */
    @Serializable
    data class ArchivedShopping(val archived: Boolean = true) : Screen

    /** `id` and `focusAdd` are read by ShoppingListViewModel / the screen. */
    @Serializable
    data class ShoppingList(val id: Long, val focusAdd: Boolean = false) : Screen

    @Serializable
    object Events : Screen

    /** Reachable from any tab (T-023). `eventId` is read by EventDetailViewModel. */
    @Serializable
    data class EventDetail(val eventId: Long) : Screen

    /** `eventId` 0 = a new event; read by EventFormViewModel. */
    @Serializable
    data class EventForm(val eventId: Long = 0L) : Screen

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
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
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
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
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
                                    TabRoot {
                                        FamilyWallScreen(
                                            onSettingsClick = openSettings,
                                            onEditMember = { userId -> navController.navigate(Screen.FamilySettings(userId)) },
                                            onOpenEvent = { id -> navController.navigate(Screen.EventDetail(id)) },
                                            onOpenEvents = {
                                                navController.navigate(TabGraph.Events) {
                                                    popUpTo(Screen.FamilyWall) { saveState = true }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                            navigation<TabGraph.Shopping>(startDestination = Screen.Shopping) {
                                composable<Screen.Shopping> {
                                    TabRoot {
                                        ShoppingListsScreen(
                                            onOpen = { list, focusAdd -> navController.navigate(Screen.ShoppingList(list.id, focusAdd)) },
                                            onArchivedClick = { navController.navigate(Screen.ArchivedShopping()) },
                                            onSettingsClick = openSettings,
                                        )
                                    }
                                }
                                composable<Screen.ArchivedShopping> {
                                    ShoppingListsScreen(
                                        onOpen = { list, _ -> navController.navigate(Screen.ShoppingList(list.id)) },
                                        onBack = { navController.popBackStack() },
                                    )
                                }
                                composable<Screen.ShoppingList> { entry ->
                                    ShoppingListScreen(
                                        onBack = { navController.popBackStack() },
                                        focusAdd = entry.toRoute<Screen.ShoppingList>().focusAdd,
                                    )
                                }
                            }
                            navigation<TabGraph.Events>(startDestination = Screen.Events) {
                                composable<Screen.Events> {
                                    TabRoot {
                                        EventsListScreen(
                                            onOpenEvent = { id -> navController.navigate(Screen.EventDetail(id)) },
                                            onAddEvent = { navController.navigate(Screen.EventForm()) },
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
                                                    ImageDetailsRoute(assetId, assetType, imageIndex)
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
                                    navController.navigate(ImageDetailsRoute(asset.id, AssetType.REMOTE, index, source))
                                }
                                composable<Screen.Albums> {
                                    AlbumsScreen(
                                        onBack = { navController.popBackStack() },
                                        onAlbumClick = { id, name -> navController.navigate(AlbumRoute(id, name)) },
                                    )
                                }
                                composable<AlbumRoute> {
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
                                    onFamilyClick = { navController.navigate(Screen.FamilySettings()) },
                                )
                            }
                            composable<Screen.EventDetail> {
                                EventDetailScreen(
                                    onBack = { navController.popBackStack() },
                                    onEdit = { id -> navController.navigate(Screen.EventForm(id)) },
                                )
                            }
                            composable<Screen.EventForm> {
                                EventFormScreen(
                                    onBack = { navController.popBackStack() },
                                    // A new event returns to the list, an edit to the detail (which reloads).
                                    onSaved = { navController.popBackStack() },
                                    onEventGone = {
                                        if (!navController.popBackStack<Screen.EventDetail>(inclusive = true)) {
                                            navController.popBackStack()
                                        }
                                    },
                                )
                            }
                            composable<Screen.FamilySettings> {
                                FamilySettingsScreen(onBack = { navController.popBackStack() })
                            }
                            composable<Screen.Frame> {
                                FrameScreen(onBack = { navController.popBackStack() })
                            }
                            composable<ImageDetailsRoute> { backStackEntry ->
                                val imageDetails = backStackEntry.toRoute<ImageDetailsRoute>()
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
                                    frameSettingsDialog = { onDismiss -> FrameSettingsDialog(onDismiss = onDismiss) },
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
}

