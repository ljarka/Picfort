package com.github.ljarka.immich.android

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
import com.github.ljarka.immich.android.db.AssetType
import com.github.ljarka.immich.android.network.SessionManager
import com.github.ljarka.immich.android.ui.image.ImageDetailsScreen
import com.github.ljarka.immich.android.ui.init.InitScreen
import com.github.ljarka.immich.android.ui.theme.ImmichAndroidTheme
import com.github.ljarka.immich.android.ui.timeline.TimelineScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.Serializable
import javax.inject.Inject

sealed interface Screen {
    @Serializable
    object Init : Screen

    @Serializable
    object Timeline : Screen

    @Serializable
    data class ImageDetails(
        val assetId: String,
        val assetType: AssetType,
        val imageIndex: Int
    ) : Screen
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
            ImmichAndroidTheme {
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
                                }
                            )
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