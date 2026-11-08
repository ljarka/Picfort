package com.github.ljarka.picfort.ui.timeline

import kotlinx.serialization.Serializable

@Serializable
data class BucketResponse(
    val id: List<String>,
    val ratio: List<Double>,
    val fileCreatedAt: List<String>,
)