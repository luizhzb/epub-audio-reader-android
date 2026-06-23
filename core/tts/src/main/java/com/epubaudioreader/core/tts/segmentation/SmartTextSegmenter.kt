package com.epubaudioreader.core.tts.segmentation

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Segmentador inteligente de texto EPUB para TTS.
 *
 * Regras:
 * - Detecta diálogos (texto entre aspas ou iniciado com travessão/hífen + maiúscula)
 * - Detecta headings (linhas curtas sem ponto final ou todas maiúsculas)
 * - Nunca quebra no meio de uma frase (respeita .  !  ?  como delimitadores)
 * - maxChars é soft limit: se uma frase inteira excede, inclui a frase completa
 * - Se um parágrafo cabe em um segmento, mantém como um só
 * - Se não cabe, divide em frases e agrupa até maxChars
 */
@Singleton
class SmartTextSegmenter @Inject constructor() {

    /**
     * Segmenta uma lista de parágrafos em [TextSegment]s otimizados para TTS.
     *
     * @param paragraphs Lista de parágrafos extraídos do EPUB
     * @param maxChars Tamanho máximo ideal por segmento (soft limit)
     * @param chapterIndex Índice do capítulo atual
     * @return Lista de segmentos prontos para síntese de voz
     */
    fun segment(
        paragraphs: List<String>,
        maxChars: Int = 500,
        chapterIndex: Int = 0
    ): List<TextSegment> {
        val result = mutableListOf<TextSegment>()
        var segmentId = 0

        paragraphs.forEachIndexed { paragraphIndex, paragraph ->
            val trimmed = paragraph.trim()
            if (trimmed.isEmpty() || trimmed.isBlank()) return@forEachIndexed

            val isHeading = isHeadingLine(trimmed)

            // Se é heading, vira um segmento próprio
            if (isHeading) {
                result.add(
                    TextSegment(
                        id = segmentId++,
                        text = trimmed,
                        type = SegmentType.HEADING,
                        chapterIndex = chapterIndex,
                        paragraphIndex = paragraphIndex
                    )
                )
                return@forEachIndexed
            }

            // Se o parágrafo inteiro cabe em um segmento, mantém unido
            if (trimmed.length <= maxChars) {
                result.add(
                    TextSegment(
                        id = segmentId++,
                        text = trimmed,
                        type = detectType(trimmed, isHeading = false),
                        chapterIndex = chapterIndex,
                        paragraphIndex = paragraphIndex
                    )
                )
                return@forEachIndexed
            }

            // Parágrafo longo: divide em frases e agrupa
            val sentences = splitIntoSentences(trimmed)
            val type = detectType(trimmed, isHeading = false)

            val currentBuffer = StringBuilder()

            for (sentence in sentences) {
                val sentenceTrimmed = sentence.trim()
                if (sentenceTrimmed.isEmpty()) continue

                val projectedLen = currentBuffer.length + sentenceTrimmed.length +
                        if (currentBuffer.isNotEmpty()) 1 else 0

                if (currentBuffer.isEmpty()) {
                    // Buffer vazio — adiciona a frase
                    currentBuffer.append(sentenceTrimmed)
                } else if (projectedLen <= maxChars) {
                    // Cabe no buffer — concatena
                    currentBuffer.append(" ").append(sentenceTrimmed)
                } else {
                    // Não cabe — flush do buffer e começa novo
                    result.add(
                        TextSegment(
                            id = segmentId++,
                            text = currentBuffer.toString(),
                            type = type,
                            chapterIndex = chapterIndex,
                            paragraphIndex = paragraphIndex
                        )
                    )
                    currentBuffer.clear()
                    currentBuffer.append(sentenceTrimmed)
                }
            }

            // Flush do último buffer
            if (currentBuffer.isNotEmpty()) {
                result.add(
                    TextSegment(
                        id = segmentId++,
                        text = currentBuffer.toString(),
                        type = type,
                        chapterIndex = chapterIndex,
                        paragraphIndex = paragraphIndex
                    )
                )
            }
        }

        return result
    }

    /**
     * Divide o texto em frases respeitando delimitadores de pontuação.
     * Preserva a pontuação no final de cada frase.
     */
    private fun splitIntoSentences(text: String): List<String> {
        val sentences = mutableListOf<String>()
        val delimiters = charArrayOf('.', '!', '?')
        val buffer = StringBuilder()
        var i = 0

        while (i < text.length) {
            buffer.append(text[i])

            if (text[i] in delimiters) {
                // Verifica se o próximo caractere é espaço ou fim de string
                // para evitar quebrar em abreviações como "Dr." ou "Sr."
                if (i + 1 < text.length && text[i + 1] == ' ') {
                    // Verifica se é abreviação comum
                    val wordBefore = getWordBefore(buffer)
                    if (!isAbbreviation(wordBefore)) {
                        sentences.add(buffer.toString().trim())
                        buffer.clear()
                        i++ // pula o espaço
                    }
                } else if (i + 1 >= text.length) {
                    // Fim do texto
                    sentences.add(buffer.toString().trim())
                    buffer.clear()
                }
            }
            i++
        }

        // Adiciona o que sobrou (última frase sem pontuação final)
        if (buffer.isNotEmpty()) {
            sentences.add(buffer.toString().trim())
        }

        return sentences
    }

    /**
     * Retorna a palavra imediatamente antes do delimitador no buffer.
     */
    private fun getWordBefore(buffer: StringBuilder): String {
        val text = buffer.toString().trim()
        val lastSpace = text.lastIndexOf(' ')
        return if (lastSpace >= 0) {
            text.substring(lastSpace + 1).trimEnd('.', '!', '?', ' ', '
', '')
        } else {
            text.trimEnd('.', '!', '?', ' ', '
', '')
        }
    }

    /**
     * Verifica se a palavra é uma abreviação comum em português/inglês
     * que não deve quebrar a frase.
     */
    private fun isAbbreviation(word: String): Boolean {
        val lower = word.lowercase()
        val commonAbbreviations = setOf(
            "dr", "dra", "sr", "sra", "srta", "prof", "profa",
            "ex", "exmo", "exma", "v", "vs", "eg", "ie",
            "vol", "pág", "pag", "cap", "sec", "fig",
            "mr", "mrs", "ms", "st", "ave", "blvd", "rd",
            "jan", "feb", "mar", "apr", "may", "jun",
            "jul", "aug", "sep", "oct", "nov", "dec",
            "a.c", "d.c", "p.m", "a.m", "etc", "obs",
            "min", "max", "approx", "no", "nos"
        )
        return lower in commonAbbreviations
    }

    /**
     * Detecta o tipo de segmento baseado no conteúdo.
     */
    private fun detectType(text: String, isHeading: Boolean): SegmentType {
        if (isHeading) return SegmentType.HEADING
        if (isDialogue(text)) return SegmentType.DIALOGUE
        return SegmentType.PARAGRAPH
    }

    /**
     * Detecta se o texto é um diálogo.
     * Critérios:
     * - Texto entre aspas
     * - Iniciado com travessão (—) ou hífen (- ) seguido de letra maiúscula
     */
    private fun isDialogue(text: String): Boolean {
        val trimmed = text.trim()

        // Texto entre aspas (aspas duplas ou simples)
        if (trimmed.contains("\"") || trimmed.contains("'")) {
            // Verifica se começa com aspas OU se há diálogo em alguma parte
            val quoteCount = trimmed.count { it == '"' }
            if (quoteCount >= 2) return true
        }

        // Iniciado com travessão (em-dash)
        if (trimmed.startsWith("—") && trimmed.length > 1) return true
        if (trimmed.startsWith("\u2014") && trimmed.length > 1) return true

        // Iniciado com hífen seguido de espaço e maiúscula
        if (trimmed.startsWith("- ") &&
            trimmed.length > 3 &&
            trimmed[2].isUpperCase()
        ) return true

        return false
    }

    /**
     * Detecta se uma linha é um heading/cabeçalho.
     * Critérios:
     * - Comprimento entre 3 e 80 caracteres
     * - Não termina com . ! ?
     * - Ou é tudo maiúscula, ou tem menos de 40 caracteres
     */
    private fun isHeadingLine(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.length in 3..80 &&
                !trimmed.endsWith('.') &&
                !trimmed.endsWith('!') &&
                !trimmed.endsWith('?') &&
                (trimmed == trimmed.uppercase() || trimmed.length < 40)
    }
}
