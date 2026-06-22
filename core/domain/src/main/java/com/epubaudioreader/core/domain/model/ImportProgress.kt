package com.epubaudioreader.core.domain.model

sealed class ImportProgress {
    data object Idle : ImportProgress()
    data class Scanning(val percent: Int) : ImportProgress()
    data class Parsing(val percent: Int) : ImportProgress()
    data class Saving(val percent: Int) : ImportProgress()
    data class Importing(val stage: String, val percent: Int) : ImportProgress()
    data class Success(val bookId: Long) : ImportProgress()
    data class Error(val message: String) : ImportProgress()
}
