package com.epubaudioreader.domain.model

data class BookWithChapters(
    val book: Book,
    val chapters: List<Chapter>
)
