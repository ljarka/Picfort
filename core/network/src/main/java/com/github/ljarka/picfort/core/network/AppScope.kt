package com.github.ljarka.picfort.core.network

import javax.inject.Qualifier

/**
 * Qualifier for the process-wide CoroutineScope (typically matching the Application lifecycle).
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class AppScope
