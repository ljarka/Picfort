package com.github.ljarka.picfort.ui.school

import com.github.ljarka.picfort.core.model.SchoolMembership
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** "Goes to school" in Family settings (T-068): a child row per family member on the server. */
@Singleton
class SchoolMembershipImpl @Inject constructor(private val repository: SchoolRepository) : SchoolMembership {

    override val memberIds: Flow<Set<String>> =
        repository.children.map { children -> children.mapNotNullTo(HashSet()) { it.memberId } }

    override suspend fun refresh() {
        repository.refresh()
    }

    override suspend fun setGoesToSchool(memberId: String, name: String, goes: Boolean) {
        // Fresh first: another phone may have changed it since.
        repository.refresh()?.let { throw it } // offline or an old server: change nothing
        val child = repository.children.value.firstOrNull { it.memberId == memberId }
        when {
            goes && child == null -> repository.addChild(
                name,
                defaultChildColor(repository.children.value.map { it.color }),
                memberId,
            )

            !goes && child != null -> repository.deleteChild(child.id)
        }
    }
}
