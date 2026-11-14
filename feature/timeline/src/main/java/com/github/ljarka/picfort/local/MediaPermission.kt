package com.github.ljarka.picfort.local

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** Permissions needed to show the phone's photos and videos in the timeline. */
val mediaPermissions: Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )

    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
    )

    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

/**
 * Photo and video locations (Android 10+). Without it Android strips GPS from what the app reads,
 * so backup's checksums don't match the originals in Immich: duplicates, and copies with no place
 * (T-067). Granted with media access; asked for again where it's missing.
 */
val locationPermission: String? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Manifest.permission.ACCESS_MEDIA_LOCATION else null

/** Whether the original files (with their location) can be read. */
fun hasOriginalAccess(context: Context): Boolean = locationPermission == null ||
    ContextCompat.checkSelfPermission(context, locationPermission) == PackageManager.PERMISSION_GRANTED

/** True if at least some photos are readable (full or Android 14 partial access). */
fun hasMediaAccess(context: Context): Boolean = mediaPermissions.any {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

/**
 * Asks for media access (and photo locations) once per screen entry if missing; [onGranted] runs
 * after a grant.
 */
@Composable
fun RequestMediaPermission(onGranted: () -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result -> if (result.values.any { it }) onGranted() }

    LaunchedEffect(Unit) {
        if (!hasMediaAccess(context) || !hasOriginalAccess(context)) {
            launcher.launch(mediaPermissions + listOfNotNull(locationPermission))
        }
    }
}
