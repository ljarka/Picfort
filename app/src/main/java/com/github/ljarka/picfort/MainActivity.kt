package com.github.ljarka.picfort

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.github.ljarka.picfort.cleanup.CleanupScreen
import com.github.ljarka.picfort.collections.AlbumScreen
import com.github.ljarka.picfort.collections.AlbumsScreen
import com.github.ljarka.picfort.collections.GridAsset
import com.github.ljarka.picfort.collections.SearchScreen
import com.github.ljarka.picfort.core.database.AssetType
import com.github.ljarka.picfort.core.ui.theme.AccentTheme
import com.github.ljarka.picfort.core.ui.theme.PicfortTheme
import com.github.ljarka.picfort.memories.MemoriesScreen
import com.github.ljarka.picfort.network.SessionManager
import com.github.ljarka.picfort.ui.events.EventDetailScreen
import com.github.ljarka.picfort.ui.events.EventFormScreen
import com.github.ljarka.picfort.ui.events.EventsListScreen
import com.github.ljarka.picfort.ui.events.reminders.EXTRA_OPEN_EVENT_ID
import com.github.ljarka.picfort.ui.family.FamilySettingsScreen
import com.github.ljarka.picfort.ui.frame.FrameScreen
import com.github.ljarka.picfort.ui.frame.FrameSettingsDialog
import com.github.ljarka.picfort.ui.frame.PicfortServerSetupScreen
import com.github.ljarka.picfort.ui.image.ImageDetailsScreen
import com.github.ljarka.picfort.ui.init.InitScreen
import com.github.ljarka.picfort.ui.school.SchoolScreen
import com.github.ljarka.picfort.ui.schooljournal.EXTRA_OPEN_SCHOOL_JOURNAL
import com.github.ljarka.picfort.ui.schooljournal.JournalTab
import com.github.ljarka.picfort.ui.schooljournal.SchoolJournalMessageScreen
import com.github.ljarka.picfort.ui.schooljournal.SchoolJournalScreen
import com.github.ljarka.picfort.ui.schooljournal.SchoolJournalTopBarAction
import com.github.ljarka.picfort.ui.settings.AppearanceSettingsStore
import com.github.ljarka.picfort.ui.settings.ExperimentalFeaturesScreen
import com.github.ljarka.picfort.ui.settings.SettingsScreen
import com.github.ljarka.picfort.ui.shell.ServerStatusViewModel
import com.github.ljarka.picfort.ui.shell.Tab
import com.github.ljarka.picfort.ui.shell.TabBar
import com.github.ljarka.picfort.ui.shell.TabRoot
import com.github.ljarka.picfort.ui.shopping.ShoppingListScreen
import com.github.ljarka.picfort.ui.shopping.ShoppingListsScreen
import com.github.ljarka.picfort.ui.shopping.SpendingScreen
import com.github.ljarka.picfort.ui.shopping.reminders.EXTRA_OPEN_SHOPPING_LIST_ID
import com.github.ljarka.picfort.ui.timeline.AlbumRoute
import com.github.ljarka.picfort.ui.timeline.ImageDetailsRoute
import com.github.ljarka.picfort.ui.timeline.TimelineScreen
import com.github.ljarka.picfort.ui.timeline.TimelineSettingsScreen
import com.github.ljarka.picfort.ui.wall.FamilyWallScreen
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionManager: SessionManager

    @Inject
    lateinit var appearanceSettings: AppearanceSettingsStore

    /** An event to open from a tapped reminder (T-039), once the app is past login. */
    private val openEventId = MutableStateFlow<Long?>(null)

    /** A shopping list to open from a tapped reminder (T-040). */
    private val openShoppingListId = MutableStateFlow<Long?>(null)

    /** A school-journal screen to open from a tapped notification (T-082, ADR 0017). */
    private val openSchoolJournal = MutableStateFlow<String?>(null)

    private fun takeEventFrom(intent: Intent?) {
        intent?.getLongExtra(EXTRA_OPEN_EVENT_ID, -1L)?.takeIf { it > 0 }?.let { openEventId.value = it }
        intent?.getLongExtra(EXTRA_OPEN_SHOPPING_LIST_ID, -1L)?.takeIf { it > 0 }?.let {
            openShoppingListId.value = it
        }
        intent?.getStringExtra(EXTRA_OPEN_SCHOOL_JOURNAL)?.takeIf { it.isNotBlank() }?.let {
            openSchoolJournal.value = it
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        takeEventFrom(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) takeEventFrom(intent)
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
            val pendingEvent by openEventId.collectAsStateWithLifecycle()
            LaunchedEffect(pendingEvent, currentTab != null) {
                // Waits for a tab: Init's navigation to the Wall would pop a detail opened before it.
                val id = pendingEvent ?: return@LaunchedEffect
                if (currentTab != null) {
                    openEventId.value = null
                    navController.navigate(Screen.EventDetail(id))
                }
            }
            val pendingList by openShoppingListId.collectAsStateWithLifecycle()
            LaunchedEffect(pendingList, currentTab != null) {
                val id = pendingList ?: return@LaunchedEffect
                if (currentTab != null) {
                    openShoppingListId.value = null
                    navController.navigate(Screen.ShoppingList(id))
                }
            }
            val pendingJournal by openSchoolJournal.collectAsStateWithLifecycle()
            LaunchedEffect(pendingJournal, currentTab != null) {
                val target = pendingJournal ?: return@LaunchedEffect
                if (currentTab != null) {
                    openSchoolJournal.value = null
                    if (target.startsWith("messages:")) {
                        navController.navigate(Screen.SchoolJournalMessage(target.removePrefix("messages:")))
                    } else {
                        navController.navigate(Screen.SchoolJournal(tab = target))
                    }
                }
            }
            val onDayJump = destination?.hasRoute(Screen.Timeline::class) == true &&
                backStackEntry?.toRoute<Screen.Timeline>()?.date != null
            // Bumped when Photos is reselected at its root: the timeline scrolls to the top.
            var photosScrollToTop by remember { mutableIntStateOf(0) }
            val openSettings = { navController.navigate(Screen.Settings) }

            val wallpaperColors by appearanceSettings.wallpaperColors.collectAsStateWithLifecycle()
            PicfortTheme(dynamicColor = wallpaperColors) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    // Each tab and its screens wear the tab's aperture color; Settings and login stay navy.
                    AccentTheme(currentTab?.accent) {
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
                                            },
                                        )
                                    }
                                    composable<Screen.PicfortServerSetup> {
                                        PicfortServerSetupScreen(
                                            onDone = {
                                                navController.navigate(TabGraph.Wall) {
                                                    popUpTo(Screen.PicfortServerSetup) { inclusive = true }
                                                }
                                            },
                                        )
                                    }
                                    navigation<TabGraph.Wall>(startDestination = Screen.FamilyWall) {
                                        composable<Screen.FamilyWall> {
                                            TabRoot {
                                                FamilyWallScreen(
                                                    onSettingsClick = openSettings,
                                                    onEditMember = { userId ->
                                                        navController.navigate(Screen.FamilySettings(userId))
                                                    },
                                                    onOpenFamily = { navController.navigate(Screen.FamilySettings()) },
                                                    onOpenEvent = { id ->
                                                        navController.navigate(Screen.EventDetail(id))
                                                    },
                                                    onOpenEvents = {
                                                        navController.navigate(TabGraph.Events) {
                                                            popUpTo(Screen.FamilyWall) { saveState = true }
                                                            launchSingleTop = true
                                                            restoreState = true
                                                        }
                                                    },
                                                    onOpenFrame = { navController.navigate(Screen.Frame) },
                                                    onOpenMemories = { navController.navigate(Screen.Memories) },
                                                    topBarActions = {
                                                        SchoolJournalTopBarAction {
                                                            navController.navigate(Screen.SchoolJournal())
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
                                                    onOpen = { list, focusAdd ->
                                                        navController.navigate(Screen.ShoppingList(list.id, focusAdd))
                                                    },
                                                    onArchivedClick = {
                                                        navController.navigate(Screen.ArchivedShopping())
                                                    },
                                                    onSpendingClick = {
                                                        navController.navigate(Screen.Spending)
                                                    },
                                                    onOpenEvent = { id ->
                                                        navController.navigate(Screen.EventDetail(id))
                                                    },
                                                    onSettingsClick = openSettings,
                                                )
                                            }
                                        }
                                        composable<Screen.Spending> {
                                            SpendingScreen(onBack = { navController.popBackStack() })
                                        }
                                        composable<Screen.ArchivedShopping> {
                                            ShoppingListsScreen(
                                                onOpen = { list, _ ->
                                                    navController.navigate(Screen.ShoppingList(list.id))
                                                },
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
                                                    onOpenEvent = { id ->
                                                        navController.navigate(Screen.EventDetail(id))
                                                    },
                                                    onAddEvent = { navController.navigate(Screen.EventForm()) },
                                                    onSettingsClick = openSettings,
                                                )
                                            }
                                        }
                                    }
                                    navigation<TabGraph.School>(startDestination = Screen.School) {
                                        composable<Screen.School> {
                                            TabRoot {
                                                SchoolScreen(
                                                    onSettingsClick = openSettings,
                                                    onOpenFamily = { navController.navigate(Screen.FamilySettings()) },
                                                    topBarActions = {
                                                        SchoolJournalTopBarAction {
                                                            navController.navigate(Screen.SchoolJournal())
                                                        }
                                                    },
                                                )
                                            }
                                        }
                                    }
                                    navigation<TabGraph.Photos>(startDestination = Screen.Timeline()) {
                                        composable<Screen.Timeline> { entry ->
                                            val picfortServer = hiltViewModel<ServerStatusViewModel>()
                                            // Back from Settings, where the address may have been set or removed.
                                            LifecycleEventEffect(Lifecycle.Event.ON_START) { picfortServer.recheck() }
                                            TabRoot {
                                                TimelineScreen(
                                                    sharedTransitionScope = this@SharedTransitionLayout,
                                                    animatedVisibilityScope = this@composable,
                                                    onImageClick = { assetId, assetType, imageIndex ->
                                                        navController.navigate(
                                                            ImageDetailsRoute(assetId, assetType, imageIndex),
                                                        ) {
                                                            popUpTo<Screen.Timeline>()
                                                        }
                                                    },
                                                    onSettingsClick = openSettings,
                                                    onSearchClick = { navController.navigate(Screen.Search()) },
                                                    onAlbumsClick = { navController.navigate(Screen.Albums) },
                                                    scrollToTop = photosScrollToTop,
                                                    onPersonAssetClick = { asset, index, source ->
                                                        navController.navigate(
                                                            ImageDetailsRoute(
                                                                asset.id,
                                                                AssetType.REMOTE,
                                                                index,
                                                                source,
                                                            ),
                                                        )
                                                    },
                                                    // Family settings need the Picfort server: no way in without one.
                                                    onEditMember = if (picfortServer.configured) {
                                                        { id -> navController.navigate(Screen.FamilySettings(id)) }
                                                    } else {
                                                        null
                                                    },
                                                    onOpenFamily = if (picfortServer.configured) {
                                                        { navController.navigate(Screen.FamilySettings()) }
                                                    } else {
                                                        null
                                                    },
                                                    scrollToDate = entry.toRoute<Screen.Timeline>().date?.let(
                                                        LocalDate::parse,
                                                    ),
                                                )
                                            }
                                        }
                                        val openFromCollection: (
                                            GridAsset,
                                            Int,
                                            String,
                                        ) -> Unit = { asset, index, source ->
                                            navController.navigate(
                                                ImageDetailsRoute(asset.id, AssetType.REMOTE, index, source),
                                            )
                                        }
                                        composable<Screen.Cleanup> {
                                            CleanupScreen(
                                                onBack = { navController.popBackStack() },
                                                onAssetClick = openFromCollection,
                                            )
                                        }
                                        composable<Screen.Albums> {
                                            AlbumsScreen(
                                                onBack = { navController.popBackStack() },
                                                onAlbumClick = { id, name, canDelete ->
                                                    navController.navigate(AlbumRoute(id, name, canDelete))
                                                },
                                            )
                                        }
                                        composable<AlbumRoute> {
                                            AlbumScreen(onBack = {
                                                navController.popBackStack()
                                            }, onAssetClick = openFromCollection)
                                        }
                                        composable<Screen.Search> {
                                            SearchScreen(onBack = {
                                                navController.popBackStack()
                                            }, onAssetClick = openFromCollection)
                                        }
                                    }
                                    composable<Screen.Settings> {
                                        SettingsScreen(
                                            onBack = { navController.popBackStack() },
                                            onFrameClick = { navController.navigate(Screen.Frame) },
                                            onFamilyClick = { navController.navigate(Screen.FamilySettings()) },
                                            onExperimentalClick = {
                                                navController.navigate(Screen.ExperimentalFeatures)
                                            },
                                            onTimelineClick = {
                                                navController.navigate(Screen.TimelineSettings)
                                            },
                                        )
                                    }
                                    composable<Screen.TimelineSettings> {
                                        val picfortServer = hiltViewModel<ServerStatusViewModel>()
                                        TimelineSettingsScreen(
                                            onBack = { navController.popBackStack() },
                                            onCleanupClick = if (picfortServer.configured) {
                                                { navController.navigate(Screen.Cleanup) }
                                            } else {
                                                null
                                            },
                                        )
                                    }
                                    composable<Screen.ExperimentalFeatures> {
                                        ExperimentalFeaturesScreen(onBack = { navController.popBackStack() })
                                    }
                                    composable<Screen.EventDetail> {
                                        EventDetailScreen(
                                            onBack = { navController.popBackStack() },
                                            onEdit = { id -> navController.navigate(Screen.EventForm(id)) },
                                            onEditBirthday = { memberId ->
                                                navController.navigate(Screen.FamilySettings(memberId))
                                            },
                                            onOpenPhoto = { id, index, source ->
                                                navController.navigate(
                                                    ImageDetailsRoute(id, AssetType.REMOTE, index, source),
                                                )
                                            },
                                            // On top of the detail, so Back returns to the birthday.
                                            onOpenDay = { day ->
                                                navController.navigate(Screen.Timeline(day.toString()))
                                            },
                                            onOpenAlbum = { id, name, canDelete ->
                                                navController.navigate(AlbumRoute(id, name, canDelete))
                                            },
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
                                    composable<Screen.SchoolJournal> { entry ->
                                        val tab = entry.toRoute<Screen.SchoolJournal>().tab
                                        SchoolJournalScreen(
                                            onBack = { navController.popBackStack() },
                                            onOpenMessage = { id ->
                                                navController.navigate(Screen.SchoolJournalMessage(id))
                                            },
                                            initialTab = if (tab == "grades") JournalTab.GRADES else null,
                                        )
                                    }
                                    composable<Screen.SchoolJournalMessage> { entry ->
                                        SchoolJournalMessageScreen(
                                            messageId = entry.toRoute<Screen.SchoolJournalMessage>().id,
                                            onBack = { navController.popBackStack() },
                                        )
                                    }
                                    composable<Screen.FamilySettings> {
                                        FamilySettingsScreen(onBack = { navController.popBackStack() })
                                    }
                                    composable<Screen.Memories> {
                                        MemoriesScreen(
                                            onBack = { navController.popBackStack() },
                                            onAssetClick = { asset, index, source ->
                                                navController.navigate(
                                                    ImageDetailsRoute(asset.id, AssetType.REMOTE, index, source),
                                                )
                                            },
                                        )
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
                                            frameSettingsDialog = { onDismiss ->
                                                FrameSettingsDialog(onDismiss = onDismiss)
                                            },
                                            onTagClick = { tagId, label ->
                                                navController.navigate(Screen.Search(tagId = tagId, tagLabel = label))
                                            },
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
                                                    // Photos opened on a day from an event (T-056) sits on top of
                                                    // that event: not saved, or the event's tab would restore it.
                                                    popUpTo(Screen.FamilyWall) { saveState = !onDayJump }
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
}
