package com.github.ljarka.picfort.network

import com.github.ljarka.picfort.core.network.isPicfortKeyRejected as coreIsPicfortKeyRejected
import com.github.ljarka.picfort.core.network.picfortKey as corePicfortKey
import okhttp3.Request
import okhttp3.Response

typealias PicfortKeyInterceptor = com.github.ljarka.picfort.core.network.PicfortKeyInterceptor

const val PICFORT_KEY_HEADER = com.github.ljarka.picfort.core.network.PICFORT_KEY_HEADER

fun Response.isPicfortKeyRejected(): Boolean = coreIsPicfortKeyRejected()

fun Request.Builder.picfortKey(key: String): Request.Builder = corePicfortKey(key)

fun picfortKeyFor(request: Request, savedServer: String, savedKey: String): String? =
    com.github.ljarka.picfort.core.network.picfortKeyFor(request, savedServer, savedKey)
