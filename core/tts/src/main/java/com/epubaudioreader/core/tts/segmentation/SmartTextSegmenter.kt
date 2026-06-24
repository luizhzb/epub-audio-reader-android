package com.epubaudioreader.core.tts.segmentation

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Segmentador inteligente de texto para TTS.
 * Preserva estrutura textual: paragrafos, dialogos, headings.
 * Nao quebra no meio de frases.
 */
@Singleton
class SmartTextSegmenter @Inject constructor() {

    companion object {
        private const val MAX_CHARS_DEFAULT = 500
    }

    /**
     * Segmenta paragrafos em blocos coerentes para TTS.
     *
     * BUG-009: Paragrafos individuais maiores que maxChars sao quebrados
     * em sentencas para evitar segmentos oversized.
     */
    fun segment(paragraphs: List<String>, maxChars: Int = MAX_CHARS_DEFAULT, chapterIndex: Int = 0): List<TextSegment> {
        if (paragraphs.isEmpty()) return emptyList()

        val segments = mutableListOf<TextSegment>()
        var segmentId = 0
        val buffer = StringBuilder()
        var startParagraphIndex = 0

        for ((pIndex, paragraph) in paragraphs.withIndex()) {
            val trimmed = paragraph.trim()
            if (trimmed.isEmpty()) continue

            // BUG-009: Se o paragrafo individual ultrapassa maxChars, quebrar em sentencas
            if (trimmed.length > maxChars) {
                // Flush buffer atual antes de processar o paragrafo grande
                if (buffer.isNotEmpty()) {
                    segments.add(TextSegment(
                        id = segmentId++,
                        text = buffer.toString().trim(),
                        type = SegmentType.PARAGRAPH,
                        chapterIndex = chapterIndex,
                        paragraphIndex = startParagraphIndex
                    ))
                    buffer.clear()
                }

                val sentenceChunks = splitBySentences(trimmed, maxChars)
                for ((chunkIndex, chunk) in sentenceChunks.withIndex()) {
                    val type = classifyParagraph(chunk)
                    segments.add(TextSegment(
                        id = segmentId++,
                        text = chunk,
                        type = if (type == SegmentType.HEADING) SegmentType.PARAGRAPH else type,
                        chapterIndex = chapterIndex,
                        paragraphIndex = pIndex + chunkIndex
                    ))
                }
                startParagraphIndex = pIndex + 1
                continue
            }

            val type = classifyParagraph(trimmed)

            // Headings sempre sao segmentos individuais
            if (type == SegmentType.HEADING) {
                if (buffer.isNotEmpty()) {
                    segments.add(TextSegment(
                        id = segmentId++,
                        text = buffer.toString().trim(),
                        type = SegmentType.PARAGRAPH,
                        chapterIndex = chapterIndex,
                        paragraphIndex = startParagraphIndex
                    ))
                    buffer.clear()
                }
                segments.add(TextSegment(
                    id = segmentId++,
                    text = trimmed,
                    type = SegmentType.HEADING,
                    chapterIndex = chapterIndex,
                    paragraphIndex = pIndex
                ))
                startParagraphIndex = pIndex + 1
                continue
            }

            // Dialogos curtos sao segmentos individuais
            if (type == SegmentType.DIALOGUE && trimmed.length < maxChars / 2) {
                if (buffer.isNotEmpty()) {
                    segments.add(TextSegment(
                        id = segmentId++,
                        text = buffer.toString().trim(),
                        type = SegmentType.PARAGRAPH,
                        chapterIndex = chapterIndex,
                        paragraphIndex = startParagraphIndex
                    ))
                    buffer.clear()
                }
                segments.add(TextSegment(
                    id = segmentId++,
                    text = trimmed,
                    type = SegmentType.DIALOGUE,
                    chapterIndex = chapterIndex,
                    paragraphIndex = pIndex
                ))
                startParagraphIndex = pIndex + 1
                continue
            }

            // Verificar se adicionar ultrapassa maxChars
            val separator = if (buffer.isNotEmpty()) "\n\n" else ""
            val candidate = buffer.toString() + separator + trimmed

            if (buffer.isNotEmpty() && candidate.length > maxChars) {
                segments.add(TextSegment(
                    id = segmentId++,
                    text = buffer.toString().trim(),
                    type = SegmentType.PARAGRAPH,
                    chapterIndex = chapterIndex,
                    paragraphIndex = startParagraphIndex
                ))
                buffer.clear()
                buffer.append(trimmed)
                startParagraphIndex = pIndex
            } else {
                buffer.append(if (buffer.isEmpty()) "" else "\n\n")
                buffer.append(trimmed)
            }
        }

        // Flush buffer final
        if (buffer.isNotEmpty()) {
            segments.add(TextSegment(
                id = segmentId++,
                text = buffer.toString().trim(),
                type = SegmentType.PARAGRAPH,
                chapterIndex = chapterIndex,
                paragraphIndex = startParagraphIndex
            ))
        }

        return segments
    }

    /**
     * BUG-009: Quebra um texto grande em chunks de ate maxChars,
     * respeitando limites de sentencas.
     */
    private fun splitBySentences(text: String, maxChars: Int): List<String> {
        val sentences = text.split(Regex("(?<=[.!?])\\s+"))
        val chunks = mutableListOf<String>()
        val current = StringBuilder()

        for (sentence in sentences) {
            val trimmed = sentence.trim()
            if (trimmed.isEmpty()) continue

            if (current.isEmpty()) {
                current.append(trimmed)
            } else if (current.length + 1 + trimmed.length <= maxChars) {
                current.append(" ").append(trimmed)
            } else {
                chunks.add(current.toString())
                current.clear()
                current.append(trimmed)
            }
        }

        if (current.isNotEmpty()) {
            chunks.add(current.toString())
        }

        // Se uma sentenca individual ainda for maior que maxChars,
        // quebrar forcadamente em maxChars
        return chunks.flatMap { chunk ->
            if (chunk.length > maxChars) {
                chunk.chunked(maxChars)
            } else {
                listOf(chunk)
            }
        }
    }

    private fun classifyParagraph(text: String): SegmentType {
        val clean = text.trim()

        // BUG-016: HEADING - heuristicas mais restritas
        if (clean.length < 80 && !clean.endsWith('.')) {
            // Verificar se e numerado
            val firstChar = clean.firstOrNull()
            if (firstChar != null && (firstChar.isDigit() || "IVXivx".contains(firstChar))) {
                // Verificar se o restante parece um titulo (nao muito longo)
                if (clean.length < 50) return SegmentType.HEADING
            }
            if (clean.startsWith("Capitulo ") || clean.startsWith("Capítulo ") || clean.startsWith("Chapter ")) {
                return SegmentType.HEADING
            }
            // BUG-016: Verificar uppercase percentage e length para reduzir falsos positivos
            if (!clean.contains('.') && clean.length < 40) {
                val upperCaseCount = clean.count { it.isUpperCase() }
                val letterCount = clean.count { it.isLetter() }
                if (letterCount > 0 && (upperCaseCount.toFloat() / letterCount) > 0.5f) {
                    return SegmentType.HEADING
                }
                // Se for muito curto e sem pontuacao, provavelmente heading
                if (clean.length < 25) return SegmentType.HEADING
            }
        }

        // BUG-017: Deteccao de dialogo - removida duplicacao de clean.startsWith("- ")
        if (clean.startsWith("\"") || clean.startsWith("'") ||
            clean.startsWith("- ")) {
            return SegmentType.DIALOGUE
        }

        // BUG-013: Classificacao de dialogo - heuristica mais restrita
        // Verificar apenas no inicio ou fim do texto (contexto de fala direta)
        val lowerClean = clean.lowercase()
        val dialogIndicators = listOf("\" disse", "\" falou", "\" perguntou", "\" respondeu",
                                       "disse: \"", "falou: \"", "perguntou: \"", "respondeu: \"")
        for (indicator in dialogIndicators) {
            if (lowerClean.contains(indicator)) return SegmentType.DIALOGUE
        }

        // Palavras de dialogo sozinhas nao sao suficientes - verificar se o paragrafo
        // inteiro parece uma fala (sem contexto narrativo extenso)
        if (clean.length < 100) {
            val dialogWords = listOf("disse", "falou", "perguntou", "respondeu")
            for (word in dialogWords) {
                if (lowerClean.contains("\"$word\"") || lowerClean.contains("$word \"")) {
                    return SegmentType.DIALOGUE
                }
            }
        }

        return SegmentType.PARAGRAPH
    }
}
