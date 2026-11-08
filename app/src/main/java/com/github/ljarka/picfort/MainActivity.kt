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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
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
            PicfortTheme {
                SharedTransitionLayout {
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Init,
                    ) {
                        composable<Screen.Init> {
                            InitScreen(
                                onInitialized = {
                                    navController.navigate(Screen.Timeline) {
                                        popUpTo(Screen.Init) { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable<Screen.Timeline> {
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
                                onSettingsClick = { navController.navigate(Screen.Settings) },
                                onSearchClick = { navController.navigate(Screen.Search) },
                                onAlbumsClick = { navController.navigate(Screen.Albums) },
                            )
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
                }
            }
        }
    }
}