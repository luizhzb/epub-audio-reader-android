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

    private fun classifyParagraph(text: String): SegmentType {
        val clean = text.trim()

        // Heading: curto, sem ponto final
        if (clean.length < 80 && !clean.endsWith('.')) {
            if (clean.matches(Regex("^(\d+\.?|[IVXivx]+\.?|Capitulo\s+\d+|Chapter\s+\d+).*"))) {
                return SegmentType.HEADING
            }
            if (!clean.contains('.') && clean.length < 60) {
                return SegmentType.HEADING
            }
        }

        // Dialogo
        if (clean.startsWith("\"") || clean.startsWith("'") ||
            clean.startsWith("- ") || clean.startsWith("- ")) {
            return SegmentType.DIALOGUE
        }

        val dialogWords = listOf("disse", "falou", "perguntou", "respondeu")
        for (word in dialogWords) {
            if (clean.contains(" $word ")) return SegmentType.DIALOGUE
        }

        return SegmentType.PARAGRAPH
    }
}
