package com.github.ljarka.picfort.core.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Cross-feature contract for family members and current user identity.
 * Features (Shopping, Wall, Events, etc.) consume this interface instead of
 * depending on a concrete family feature or repository.
 */
interface FamilyMemberProvider {
    val myId: String
    val members: Flow<List<FamilyMember>>
    val allMembers: Flow<List<FamilyMember>> get() = members
    val hiddenIds: Flow<Set<String>> get() = emptyFlow()

    /** Member id → person id in the logged-in user's Immich people (T-052); Photos filters by it. */
    val myPersonIds: Flow<Map<String, String>> get() = flowOf(emptyMap())
    suspend fun refresh(): Any?
}
