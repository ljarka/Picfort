package com.github.ljarka.picfort.core.model

import androidx.compose.ui.graphics.Color

/** A family member: an Immich user or manual member (T-002, T-034). */
data class FamilyMember(
    val id: String,
    val name: String,
    /** Profile picture URL on Immich or Picfort server, or null to show initials. */
    val avatarUrl: String?,
    val color: Color,
    val isImmichUser: Boolean = true,
    val birthDate: BirthDate? = null,
)
