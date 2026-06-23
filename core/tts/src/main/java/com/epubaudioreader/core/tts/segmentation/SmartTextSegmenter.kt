package com.epubaudioreader.core.tts.segmentation

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Segmentador inteligente de texto para TTS.
 * Recebe lista de paragrafos e retorna segmentos coerentes.
 * Preserva estrutura: paragrafos, dialogos, headings.
 * Nao quebra no meio de frases.
 */
@Singleton
class SmartTextSegmenter @Inject constructor() {

    /**
     * Segmenta paragrafos em blocos coerentes para TTS.
     * @param paragraphs Lista de paragrafos do capitulo
     * @param maxChars Tamanho maximo ideal por segmento (soft limit)
     * @param chapterIndex Indice do capitulo
     * @return Lista de segmentos ordenados
     */
    fun segment(
        paragraphs: List<String>,
        maxChars: Int = 500,
        chapterIndex: Int = 0
    ): List<TextSegment> {
        if (paragraphs.isEmpty()) return emptyList()

        val segments = mutableListOf<TextSegment>()
        var segmentId = 0
        val buffer = StringBuilder()
        var currentParagraphIndex = 0
        var startParagraphIndex = 0

        for ((pIndex, paragraph) in paragraphs.withIndex()) {
            val trimmed = paragraph.trim()
            if (trimmed.isEmpty()) continue

            val type = classifyParagraph(trimmed)

            // Headings sempre sao segmentos individuais
            if (type == SegmentType.HEADING) {
                // Flush buffer atual
                if (buffer.isNotEmpty()) {
                    segments.add(
                        TextSegment(
                            id = segmentId++,
                            text = buffer.toString().trim(),
                            type = SegmentType.PARAGRAPH,
                            chapterIndex = chapterIndex,
                            paragraphIndex = startParagraphIndex
                        )
                    )
                    buffer.clear()
                }
                segments.add(
                    TextSegment(
                        id = segmentId++,
                        text = trimmed,
                        type = SegmentType.HEADING,
                        chapterIndex = chapterIndex,
                        paragraphIndex = pIndex
                    )
                )
                startParagraphIndex = pIndex + 1
                continue
            }

            // Dialogos curtos (< maxChars/2) sao segmentos individuais
            if (type == SegmentType.DIALOGUE && trimmed.length < maxChars / 2) {
                if (buffer.isNotEmpty()) {
                    segments.add(
                        TextSegment(
                            id = segmentId++,
                            text = buffer.toString().trim(),
                            type = SegmentType.PARAGRAPH,
                            chapterIndex = chapterIndex,
                            paragraphIndex = startParagraphIndex
                        )
                    )
                    buffer.clear()
                }
                segments.add(
                    TextSegment(
                        id = segmentId++,
                        text = trimmed,
                        type = SegmentType.DIALOGUE,
                        chapterIndex = chapterIndex,
                        paragraphIndex = pIndex
                    )
                )
                startParagraphIndex = pIndex + 1
                continue
            }

            // Verificar se adicionar este paragrafo ultrapassa maxChars
            val separator = if (buffer.isNotEmpty()) "\n\n" else ""
            val candidate = buffer.toString() + separator + trimmed

            if (buffer.isNotEmpty() && candidate.length > maxChars) {
                // Flush buffer e comecar novo
                segments.add(
                    TextSegment(
                        id = segmentId++,
                        text = buffer.toString().trim(),
                        type = SegmentType.PARAGRAPH,
                        chapterIndex = chapterIndex,
                        paragraphIndex = startParagraphIndex
                    )
                )
                buffer.clear()
                buffer.append(trimmed)
                startParagraphIndex = pIndex
            } else {
                buffer.append(separator)
                buffer.append(trimmed)
            }

            currentParagraphIndex = pIndex
        }

        // Flush buffer final
        if (buffer.isNotEmpty()) {
            segments.add(
                TextSegment(
                    id = segmentId++,
                    text = buffer.toString().trim(),
                    type = SegmentType.PARAGRAPH,
                    chapterIndex = chapterIndex,
                    paragraphIndex = startParagraphIndex
                )
            )
        }

        return segments
    }

    /**
     * Classifica um paragrafo como PARAGRAPH, DIALOGUE ou HEADING.
     */
    private fun classifyParagraph(text: String): SegmentType {
        // Heading: curto, sem ponto final, ou com marcadores
        val clean = text.trim()
        if (clean.length < 80 && !clean.endsWith('.')) {
            // Verificar se e numerado (ex: "1.", "I.", "Capitulo 1")
            if (clean.matches(Regex("""^(\d+\.?|[IVXivx]+\.?|Cap[íi]tulo\s+\d+|Chapter\s+\d+).*"""))) {
                return SegmentType.HEADING
            }
            // Se for uma unica frase sem ponto final, pode ser heading
            if (!clean.contains('.') && clean.length < 60) {
                return SegmentType.HEADING
            }
        }

        // Dialogo: comecado com "- ", aspas, ou " dialogo "
        val dialogPatterns = listOf(
            Regex("""^["']"""),  // Comeca com aspas
            Regex("""^[-—–]\s+"""),  // Comeca com traco ou em-dash
            Regex("""^\w+\s+(disse|falou|perguntou|respondeu)[,;:]"""),  // "Joao disse:"
        )
        for (pattern in dialogPatterns) {
            if (pattern.containsMatchIn(clean)) {
                return SegmentType.DIALOGUE
            }
        }

        return SegmentType.PARAGRAPH
    }
}
