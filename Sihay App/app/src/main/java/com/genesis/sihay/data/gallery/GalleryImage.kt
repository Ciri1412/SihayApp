package com.genesis.sihay.data.gallery

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GalleryImage(
    val uri: Uri,
    val displayName: String,
    val timestamp: Long
)

suspend fun loadGalleryImages(
    context: Context,
    limit: Int = 120
): List<GalleryImage> = withContext(Dispatchers.IO) {
    val images = mutableListOf<GalleryImage>()
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_TAKEN
    )
    val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"
    context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        null,
        null,
        sortOrder
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
        var count = 0
        while (cursor.moveToNext() && count < limit) {
            val id = cursor.getLong(idColumn)
            val contentUri = Uri.withAppendedPath(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                id.toString()
            )
            val name = cursor.getString(nameColumn) ?: "Egg ${count + 1}"
            val dateTaken = cursor.getLong(dateColumn)
            images.add(
                GalleryImage(
                    uri = contentUri,
                    displayName = name,
                    timestamp = dateTaken
                )
            )
            count++
        }
    }
    images
}

