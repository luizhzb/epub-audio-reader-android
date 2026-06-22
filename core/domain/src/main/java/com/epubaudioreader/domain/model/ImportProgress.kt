package com.epubaudioreader.domain.model

sealed class ImportProgress {
    data object Idle : ImportProgress()
    data object Scanning : ImportProgress()
    data class Parsing(val percent: Int) : ImportProgress()
    data class Saving(val percent: Int) : ImportProgress()
    data class Success(val bookId: Long) : ImportProgress()
    data class Error(val message: String) : ImportProgress()
}
