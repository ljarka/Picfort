package com.github.ljarka.picfort.core.model

import kotlinx.coroutines.flow.StateFlow

/**
 * Cross-feature contract for reading family events.
 * The Wall feature consumes this interface to render the today panel without
 * depending on :feature:events.
 */
interface FamilyEventsProvider {
    val upcoming: StateFlow<List<FamilyEvent>>
    suspend fun refreshUpcoming(): Any?
}
