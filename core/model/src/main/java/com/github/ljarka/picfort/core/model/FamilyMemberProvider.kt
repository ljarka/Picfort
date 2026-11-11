package com.github.ljarka.picfort.core.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

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
    suspend fun refresh(): Any?
}
