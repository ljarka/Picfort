package com.github.ljarka.picfort.core.model

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

fun localImageUri(id: Long): Uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

fun localVideoUri(id: Long): Uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
