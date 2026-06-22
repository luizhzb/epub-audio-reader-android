package com.epubaudioreader.core.data.local.database.converter

import android.net.Uri
import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromUriString(value: String?): Uri? = value?.let { Uri.parse(it) }

    @TypeConverter
    fun toUriString(uri: Uri?): String? = uri?.toString()
}
