package com.github.ljarka.picfort.ui.timeline

import kotlinx.serialization.Serializable

@Serializable
data class TimeBucket(val timeBucket: String, val count: Int)
