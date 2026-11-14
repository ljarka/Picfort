package com.github.ljarka.picfort.core.model

import kotlinx.coroutines.flow.Flow

/**
 * Who goes to school (T-068): Family settings sets it per member ("Goes to school"), the School
 * tab shows those members. Implemented by `:feature:school`, bound with Hilt.
 */
interface SchoolMembership {
    /** Family member ids in School; empty until [refresh] (or the School tab) has loaded it. */
    val memberIds: Flow<Set<String>>

    /** Loads who is in School from the Picfort server. */
    suspend fun refresh()

    /**
     * Adds [memberId] to School ([name] is kept by the server for other phones) or removes them,
     * deleting their timetable. Throws on failure (e.g. the server can't be reached).
     */
    suspend fun setGoesToSchool(memberId: String, name: String, goes: Boolean)
}
