# Auditoria Tecnica Independente — Fase 1 EPUB Audio Reader

**Auditor**: IA Auditor Independente  
**Data**: 2025  
**Versao auditada**: Fase 1 (sem TTS/audio/cache)  
**Total de arquivos auditados**: 60 (.kt, .xml, .kts, .toml)  

---

## 1. Resumo Executivo

### Estado Geral: NAO COMPILAVEL — Bloqueado por multiplos erros criticos

A base arquitetural do projeto e solida (Clean Architecture, MVVM, Hilt DI, Room, Compose), mas **o codigo contem erros sistematicos de imports e referencias que impedem a compilacao**. Os problemas se concentram no modulo `:core:data` ( Repository + DAO cross-references ) e `:app` ( ViewModel -> UseCase imports ), indicando que o codigo foi gerado ou refatorado sem validacao cruzada entre modulos.

**Estatisticas**:
| Categoria | Contagem |
|---|---|
| Issues CRITICOS | 14 |
| Issues ALTO | 10 |
| Issues MEDIO | 10 |
| Issues BAIXO | 7 |
| **Total** | **41** |

**Acoes prioritarias para desbloqueio**:
1. Corrigir TODOS os imports de pacotes no modulo `:core:data` (RepositoryImpl + Mappers)
2. Corrigir imports no `AppModule.kt` (DispatcherProvider)
3. Corrigir imports no `LibraryViewModel.kt` (UseCases em subpacotes `.library`)
4. Adicionar metodos faltantes no `BookDao` e `ChapterDao`
5. Implementar `@Transaction` no importBook do RepositoryImpl
6. Corrigir inconsistencia `ImportProgress` sealed class vs uso no LibraryScreen/LibraryViewModel
7. Corrigir `BookDetailViewModel` (acesso a campos de Pair como named properties)
8. Corrigir invocacao do `ImportBookUseCase` (assinatura e fluxo de retorno)
9. Corrigir `ReaderViewModel.onParagraphVisible` (chapterId via hashCode)

---

## 2. Issues CRITICOS (impedem compilacao ou causam crash em runtime)

### C1. Imports incorretos em `LibraryViewModel.kt` — UseCases em subpacotes
- **Arquivo**: `app/.../ui/screens/library/LibraryViewModel.kt`
- **Linhas**: 7-9
- **Problema**: Os imports apontam para `com.epubaudioreader.core.domain.usecase.XxxUseCase`, mas as classes estao no subpacote `.library` ou `.reader`:
  - `DeleteBookUseCase` esta em `...usecase.library.DeleteBookUseCase`
  - `GetBooksUseCase` esta em `...usecase.library.GetBooksUseCase`
  - `ImportBookUseCase` esta em `...usecase.library.ImportBookUseCase`
- **Impacto**: Falha de compilacao — classes nao encontradas
- **Correcao**: Adicionar `.library` ao import: `import com.epubaudioreader.core.domain.usecase.library.XxxUseCase`

### C2. Imports incorretos em `AppModule.kt` — DispatcherProvider pacote errado
- **Arquivo**: `app/.../di/AppModule.kt`
- **Linhas**: 3-4
- **Problema**: `DefaultDispatcherProvider` e `DispatcherProvider` estao no pacote `com.epubaudioreader.core.common.dispatcher`, nao `com.epubaudioreader.common.di`
- **Impacto**: Falha de compilacao
- **Correcao**: `import com.epubaudioreader.core.common.dispatcher.DispatcherProvider` e `import com.epubaudioreader.core.common.dispatcher.DefaultDispatcherProvider`

### C3. Multiplos imports incorretos em `BookRepositoryImpl.kt`
- **Arquivo**: `core/data/.../repository/BookRepositoryImpl.kt`
- **Linhas**: 3-7, 15
- **Problema**:
  - `com.epubaudioreader.core.common.DispatcherProvider` -> correto: `...common.dispatcher.DispatcherProvider`
  - `com.epubaudioreader.core.data.local.database.BookDao` -> correto: `...database.dao.BookDao`
  - `com.epubaudioreader.core.data.local.database.BookEntity` -> correto: `...database.entity.BookEntity`
  - `com.epubaudioreader.core.data.local.database.ChapterDao` -> correto: `...database.dao.ChapterDao`
  - `com.epubaudioreader.core.data.local.database.ChapterEntity` -> correto: `...database.entity.ChapterEntity`
  - `com.epubaudioreader.core.domain.model.Result` -> correto: `...common.result.Result`
- **Impacto**: 6 falhas de compilacao em um unico arquivo
- **Correcao**: Corrigir todos os imports

### C4. Imports incorretos em `ChapterRepositoryImpl.kt`
- **Arquivo**: `core/data/.../repository/ChapterRepositoryImpl.kt`
- **Linhas**: 3-4
- **Problema**:
  - `com.epubaudioreader.core.common.DispatcherProvider` -> `...common.dispatcher.DispatcherProvider`
  - `com.epubaudioreader.core.data.local.database.ChapterDao` -> `...database.dao.ChapterDao`
- **Impacto**: Falha de compilacao
- **Correcao**: Corrigir imports

### C5. Import incorreto em `BookMapper.kt` — BookEntity pacote errado
- **Arquivo**: `core/data/.../repository/mapper/BookMapper.kt`
- **Linha**: 3
- **Problema**: `com.epubaudioreader.core.data.local.database.BookEntity` -> correto: `...database.entity.BookEntity`
- **Impacto**: Falha de compilacao

### C6. Referencias a campos INEXISTENTES em `BookMapper.kt`
- **Arquivo**: `core/data/.../repository/mapper/BookMapper.kt`
- **Linhas**: 16-17, 24-25, 35-36, 43-44
- **Problema**: O mapper referencia campos que NAO existem nas entities/domain models:
  - `entity.coverPath` -> campo correto: `entity.coverImagePath`
  - `entity.lastReadAt` -> campo INEXISTENTE na `BookEntity`
  - `entity.addedAt` -> campo INEXISTENTE na `BookEntity`
  - `domain.lastReadAt` / `domain.addedAt` -> campos INEXISTENTES no `Book` domain model
- **Impacto**: Falha de compilacao — unresolved references
- **Correcao**: Mapear apenas campos que existem em ambos os modelos. Adicionar `importDate` e `lastReadDate` se necessario.

### C7. Metodo `findBookByHash()` INEXISTENTE em `BookDao`
- **Arquivo**: `core/data/.../repository/BookRepositoryImpl.kt` (linha 66) e `core/data/.../local/database/dao/BookDao.kt`
- **Problema**: `bookDao.findBookByHash(hash)` e chamado, mas `BookDao` so tem `findBookByFilePath(filePath): Long?`
- **Impacto**: Falha de compilacao — unresolved reference
- **Correcao**: Adicionar `@Query("SELECT id FROM books WHERE hash = :hash LIMIT 1") suspend fun findBookByHash(hash: String): Long?` ao `BookDao`

### C8. Metodo `updateContentFilePath()` INEXISTENTE em `ChapterDao`
- **Arquivo**: `core/data/.../repository/BookRepositoryImpl.kt` (linha 145)
- **Problema**: `chapterDao.updateContentFilePath(chapterId, path)` e chamado, mas `ChapterDao` nao tem este metodo
- **Impacto**: Falha de compilacao
- **Correcao**: Adicionar `@Query("UPDATE chapters SET contentFilePath = :path WHERE id = :chapterId") suspend fun updateContentFilePath(chapterId: Long, path: String)` ao `ChapterDao`

### C9. Metodo `updateBook()` INEXISTENTE em `BookDao`
- **Arquivo**: `core/data/.../repository/BookRepositoryImpl.kt` (linha 155)
- **Problema**: `bookDao.updateBook(finalBookEntity)` e chamado, mas `BookDao` so tem `insertBook`, `deleteBook`, `updateLastRead` — nao tem `updateBook`
- **Impacto**: Falha de compilacao
- **Correcao**: Adicionar `@Update suspend fun updateBook(book: BookEntity)` ou `@Query` manual ao `BookDao`

### C10. Metodo `copyBookFile()` INEXISTENTE em `EpubStorageManager`
- **Arquivo**: `core/data/.../repository/BookRepositoryImpl.kt` (linha 94)
- **Problema**: `storageManager.copyBookFile(originalFile, bookId)` e chamado, mas `EpubStorageManager` so tem `copyEpubFromUri(uri: Uri, bookId: Long)`
- **Impacto**: Falha de compilacao
- **Correcao**: Adicionar `fun copyBookFile(sourceFile: File, bookId: Long): File` ao `EpubStorageManager`

### C11. Assinatura de `ImportBookUseCase` nao bate com chamada no `LibraryViewModel`
- **Arquivo**: `app/.../ui/screens/library/LibraryViewModel.kt` (linha 50) + `core/domain/.../usecase/library/ImportBookUseCase.kt`
- **Problema**: O ViewModel chama `importBookUseCase(uri.toString())` (1 argumento), mas a assinatura e `invoke(filePath: String, fileSize: Long)` (2 argumentos). Falta `fileSize`.
- **Impacto**: Falha de compilacao — no value passed for parameter 'fileSize'
- **Correcao**: Passar fileSize obtido do DocumentFile ou File: `importBookUseCase(uri.toString(), fileSize)`

### C12. `ImportBookUseCase` retorna `Result<Book>`, nao `Flow<ImportProgress>` — chamada `.collect{}` invalida
- **Arquivo**: `app/.../ui/screens/library/LibraryViewModel.kt` (linhas 50-57)
- **Problema**: O codigo faz `importBookUseCase(uri.toString()).collect { progress -> ... }`, mas `ImportBookUseCase.invoke` retorna `Result<Book>` (do sealed class `Result`), que NAO e um Flow/Collection. Nao ha metodo `.collect()` em `Result<T>`.
- **Impacto**: Falha de compilacao grave
- **Correcao**: O `ImportBookUseCase` deveria retornar `Flow<ImportProgress>` e emitir progresso incremental, OU o ViewModel deve tratar o `Result<Book>` diretamente sem `.collect()`

### C13. `LibraryScreen` verifica subclasses de `ImportProgress` que NAO EXISTEM
- **Arquivo**: `app/.../ui/screens/library/LibraryScreen.kt` (linhas 121-124)
- **Problema**: Verifica `is ImportProgress.Scanning`, `is ImportProgress.Parsing`, `is ImportProgress.Saving`, mas o sealed class `ImportProgress` (em domain) so define: `Idle`, `Importing`, `Success`, `Error`
- **Impacto**: Falha de compilacao — subclasses nao encontradas
- **Correcao**: Alinhar o sealed class `ImportProgress` com as subclasses usadas na UI (adicionar `Scanning`, `Parsing`, `Saving`), OU alterar a UI para usar apenas as existentes

### C14. `BookDetailViewModel` acessa campos nomeados em `Pair` (que nao existem)
- **Arquivo**: `app/.../ui/screens/bookdetail/BookDetailViewModel.kt` (linhas 27-33)
- **Problema**: `GetBookWithChaptersUseCase` retorna `Flow<Pair<Book?, List<Chapter>>>` (tipo Pair). No ViewModel, o codigo faz `result.book` e `result.chapters` — mas `Pair` so tem `.first` e `.second`.
- **Impacto**: Falha de compilacao — unresolved references `.book` e `.chapters` on Pair
- **Correcao**: Usar destructuring: `.onEach { (book, chapters) -> ... }` ou `result.first` / `result.second`

---

## 3. Issues ALTO (causam comportamento errado, memory leaks, ou race conditions)

### A1. `ReaderViewModel.onParagraphVisible()` gera chapterId incorretamente via `hashCode()`
- **Arquivo**: `app/.../ui/screens/reader/ReaderViewModel.kt` (linha 86)
- **Problema**: `val chapterId = _uiState.value.paragraphs.hashCode().toLong()` gera um ID aleatorio baseado no conteudo dos paragrafos. O `SaveProgressUseCase` recebe um chapterId que NAO corresponde ao chapterId real do banco.
- **Impacto**: O progresso de leitura NUNCA sera recuperado corretamente. Dados salvos ficam "orfãos".
- **Correcao**: Armazenar o `currentChapterId` (recebido em `loadChapter()`) como propriedade do ViewModel e usa-lo no `progressFlow`

### A2. Falta de atomicidade real em `BookRepositoryImpl.importBook()`
- **Arquivo**: `core/data/.../repository/BookRepositoryImpl.kt`
- **Problema**: O comentario diz que todas as operacoes sao "wrapped in a Room transaction", mas nao ha `@Transaction`, `runInTransaction`, nem `@Insert` com lista atomic. Se falhar apos `insertBook` mas antes do `updateBook`, o DB ficara inconsistente (livro sem capitulos/capa). Alem disso, arquivos de file system sao gravados ANTES do commit final do DB — se o DB falhar, os arquivos ficam orfaos.
- **Impacto**: Estado inconsistente entre DB e file system; possivel duplicacao de livros
- **Correcao**: 
  1. Usar `@Transaction` no `BookDao` para metodo que faz insert+update
  2. Fazer operacoes de file system APENAS apos o commit no DB, OU implementar rollback manual de arquivos em caso de falha

### A3. `CoverExtractor.findCoverBytes()` abre ZipFile manualmente sem `.use{}`
- **Arquivo**: `core/data/.../epub/extractor/CoverExtractor.kt` (linhas 61-118)
- **Problema**: `val zip = ZipFile(parsedEpub.bookFile)` e aberto manualmente e fechado no `finally`, mas se `ZipFile` lancar excecao na abertura, o `finally` nao e executado.
- **Impacto**: Potencial vazamento de recurso nativo (file descriptor) em caso de erro
- **Correcao**: Usar `ZipFile(parsedEpub.bookFile).use { zip -> ... }`

### A4. `TextExtractor` usa regex `Regex("\s+")` que NAO funciona como esperado
- **Arquivo**: `core/data/.../epub/extractor/TextExtractor.kt` (linhas 81, 120)
- **Problema**: Em Kotlin, `"\s"` em string regular e interpretado como escape invalido (backlash + s). O Kotlin compiler converte `"\s+"` para a string `s+` (apenas a letra 's' repetida), NAO para o pattern de whitespace regex. Deveria ser `"\\s+"` ou string raw `"""\s+"""`.
- **Impacto**: A normalizacao de whitespace NAO funciona — multiplos espacos nao sao colapsados, e tags HTML nao sao removidas corretamente na linha 119
- **Correcao**: Usar `"""\s+""".toRegex()` ou `Regex("\\s+")`

### A5. `ChapterExtractor` abre segundo ZipFile redundante
- **Arquivo**: `core/data/.../epub/extractor/ChapterExtractor.kt` (linha 25)
- **Problema**: `EpubParserImpl` ja abre o ZipFile e o fecha. `ChapterExtractor` abre um NOVO `ZipFile` sobre o mesmo arquivo (`parsedEpub.bookFile`). Isso e uma dupla abertura desnecessaria.
- **Impacto**: Overhead de I/O e duplicacao de recursos
- **Correcao**: Passar o conteudo XHTML ja extraido do Zip para o extractor, ou reter o ZipFile aberto durante todo o processo de import

### A6. `EpubStorageManager` — arquivos orfaos em caso de falha de import
- **Arquivo**: `core/data/.../local/storage/EpubStorageManager.kt`
- **Problema**: O `saveChapterText`, `copyBookFile` e `saveCover` gravam arquivos no file system. Se ocorrer uma falha no DB depois, esses arquivos ficam orfaos sem registro no DB. Nao ha mecanismo de cleanup.
- **Impacto**: Acumulo de arquivos "mortos" no armazenamento interno ao longo do tempo
- **Correcao**: Implementar rollback manual que deleta arquivos gravados em caso de falha, ou fazer cleanup periodico

### A7. `ReaderViewModel` nao persiste o `chapterId` para progresso
- **Arquivo**: `app/.../ui/screens/reader/ReaderViewModel.kt`
- **Problema**: O `currentBookId` e salvo, mas nao ha `currentChapterId`. O metodo `onParagraphVisible` inventa um chapterId via `hashCode()`.
- **Impacto**: Progresso de leitura completamente quebrado (ja detalhado em C14)

### A8. `LibraryScreen` importa `DocumentFile` e icones nao utilizados
- **Arquivo**: `app/.../ui/screens/library/LibraryScreen.kt` (linhas 45-47)
- **Problema**: `import androidx.documentfile.DocumentFile` e icones Delete nao sao usados
- **Impacto**: Warnings de compilação, codigo morto
- **Correcao**: Remover imports nao utilizados

### A9. `Converters.kt` define conversores de `Uri` desnecessarios
- **Arquivo**: `core/data/.../local/database/converter/Converters.kt`
- **Problema**: Converte `Uri <-> String`, mas NENHUMA entity (`BookEntity` nem `ChapterEntity`) usa o tipo `Uri`. Ambas usam apenas tipos basicos.
- **Impacto**: Codigo morto; overhead desnecessario no schema Room
- **Correcao**: Remover `Converters.kt` e a anotacao `@TypeConverters` do `AppDatabase`, OU adicionar campo Uri se necessario para Fase 2

### A10. `deleteBook` no `BookRepositoryImpl` pode deixar capitulos orfaos temporariamente
- **Arquivo**: `core/data/.../repository/BookRepositoryImpl.kt` (linhas 166-179)
- **Problema**: Ordem de delecao: (1) `storageManager.deleteBookFiles(id)`, (2) `bookDao.deleteBook(book)`. Se (1) falhar, o livro NAO e deletado do DB. Mas se (2) falhar, os arquivos ja foram deletados. Alem disso, os capitulos sao deletados via CASCADE da FK, mas a FK so funciona se o constraint estiver ativo.
- **Impacto**: Possivel inconsistencia entre file system e DB
- **Correcao**: Deletar do DB primeiro (com CASCADE), depois do file system

---

## 4. Issues MEDIO (problemas de design, performance, ou manutencao)

### M1. `ImportProgress` sealed class inconsistente entre domain e UI
- **Arquivo**: `core/domain/.../model/ImportProgress.kt` + `app/.../LibraryScreen.kt` + `app/.../LibraryViewModel.kt`
- **Problema**: O domain define `Idle | Importing | Success | Error`, mas a UI espera `Scanning | Parsing | Saving | Success | Error`. O `LibraryViewModel` tenta emitir `ImportProgress.Scanning` que nao existe.
- **Impacto**: Nao compilavel (ja reportado em C13) + falta de granularidade de progresso ao usuario
- **Correcao**: Expandir o sealed class `ImportProgress` no domain para incluir `Scanning`, `Parsing`, `Saving`

### M2. `Result<T>` do `common` nao e usado de forma consistente
- **Arquivo**: Multiplos
- **Problema**: `ImportBookUseCase` retorna `Result<Book>`, mas `GetBooksUseCase` retorna `Flow<List<Book>>` sem Result. `DeleteBookUseCase` retorna `Result<Unit>`. Nao ha padrao uniforme.
- **Impacto**: Inconsistencia de API dificulta tratamento de erro nos ViewModels
- **Correcao**: Padronizar: UseCases que retornam Flow devem emitir `Result<T>`, ou eliminar `Result` e usar excecoes + `catch` no Flow

### M3. `BookDetailScreen` carrega imagem da capa via `AsyncImage(model = File(...))` sem tratamento de erro
- **Arquivo**: `app/.../screens/bookdetail/BookDetailScreen.kt` (linhas 114-120)
- **Problema**: Se o arquivo da capa for deletado ou corrompido, AsyncImage pode ficar em estado de erro sem fallback visual (alem do placeholder inicial).
- **Impacto**: UI pode mostrar espaco vazio ou loading infinito
- **Correcao**: Adicionar `error` placeholder ao AsyncImage ou verificar `File.exists()` antes

### M4. `ReaderViewModel.parseParagraphs()` usa regex simples para HTML parsing
- **Arquivo**: `app/.../screens/reader/ReaderViewModel.kt` (linhas 92-108)
- **Problema**: Regex para strip HTML e fragil. Conteudo HTML complexo (comentarios, CDATA, scripts inline) pode quebrar o parser.
- **Impacto**: Conteudo mal formatado ou incompleto na tela de leitura
- **Correcao**: Usar `TextExtractor` ja existente no modulo `:core:data` (reutilizar via DI)

### M5. `AndroidManifest.xml` tem permissao `POST_NOTIFICATIONS` sem uso
- **Arquivo**: `app/.../AndroidManifest.xml` (linha 5)
- **Problema**: A permissao `android.permission.POST_NOTIFICATIONS` e declarada mas nao ha codigo de notificacao na Fase 1
- **Impacto**: Permissao desnecessaria solicitada ao usuario (Android 13+)
- **Correcao**: Remover ate a Fase de implementacao de notificacoes

### M6. `AppNavigation` usa string-based routing (nao type-safe)
- **Arquivo**: `app/.../navigation/AppNavigation.kt`
- **Problema**: Rotas sao strings, argumentos sao passados via `navArgument` manualmente. Erros de digitacao so sao detectados em runtime.
- **Impacto**: Manutencao fragil, sem type-safety
- **Correcao**: Migrar para Navigation Compose type-safe API (Kotlin Serialization) quando possivel

### M7. `BookCard.kt` nao recicla Bitmaps (uso via Coil)
- **Arquivo**: `app/.../components/BookCard.kt`
- **Problema**: AsyncImage do Coil gerencia bitmaps automaticamente, mas nao ha configuracao de cache ou lifecycle-aware disposal
- **Impacto**: Potencial uso excessivo de memoria com muitas capas na grade
- **Correcao**: Configurar `ImageLoader` do Coil com limites de memoria cache, ou usar `rememberImagePainter` com `crossfade`

### M8. `LazyVerticalGrid` na LibraryScreen nao tem `contentType` para diferenciar itens
- **Arquivo**: `app/.../screens/library/LibraryScreen.kt`
- **Problema**: Todos os itens sao do mesmo tipo (BookCard), mas nao ha `contentType` no LazyVerticalGrid
- **Impacto**: Recomposicao menos eficiente
- **Correcao**: Adicionar `contentType = { "book" }` ao items do LazyVerticalGrid (baixo impacto ja que so tem um tipo)

### M9. `LibraryViewModel` delay(2000) bloqueia o StateFlow update
- **Arquivo**: `app/.../screens/library/LibraryViewModel.kt` (linhas 54, 62)
- **Problema**: `kotlinx.coroutines.delay(2000)` e chamado dentro do `try/catch` antes do update final do `importProgress`. Isso atrasa a UI por 2 segundos desnecessariamente.
- **Impacto**: UX degradada — usuario ve spinner por 2s extra
- **Correcao**: Usar `viewModelScope.launch { delay(2000); ... }` separado ou remover o delay

### M10. `EpubParserImpl.parse()` usa `Dispatchers.IO` hardcoded em vez do `DispatcherProvider`
- **Arquivo**: `core/data/.../epub/parser/EpubParserImpl.kt` (linha 19)
- **Problema**: `withContext(Dispatchers.IO)` hardcoded, ignorando o `DispatcherProvider` injetado em outros lugares
- **Impacto**: Inconsistencia de testabilidade — nao da para mockar/trocar o dispatcher em testes
- **Correcao**: Injetar `DispatcherProvider` e usar `dispatcher.io`

---

## 5. Issues BAIXO (melhorias, code style, otimizacoes menores)

### L1. Versao do Coil no TOML esta fora da ordem padrao
- **Arquivo**: `gradle/libs.versions.toml` (linha 66)
- **Problema**: `coil = "2.7.0"` esta entre a secao `[plugins]` e a definicao da library, fora da secao `[versions]`
- **Impacto**: TOML ainda funciona, mas e desorganizado
- **Correcao**: Mover `coil` para a secao `[versions]`

### L2. `file_paths.xml` so define path para covers
- **Arquivo**: `app/.../res/xml/file_paths.xml`
- **Problema**: So expoe `covers/`, mas o FileProvider pode precisar acessar outros paths no futuro
- **Impacto**: Restricao desnecessaria para Fase 2+
- **Correcao**: Adicionar paths para `books/` e `chapters/` se necessario para sharing

### L3. `BookEntity` usa `System.currentTimeMillis()` como default, mas nao usa `Instant`
- **Arquivo**: `core/data/.../local/database/entity/BookEntity.kt`
- **Problema**: Usa Long para timestamps em vez de `java.time.Instant` (com converter)
- **Impacto**: Baixo — funciona, mas menos type-safe
- **Correcao**: Usar `Instant` com `@TypeConverter` para melhor type safety

### L4. `ReaderScreen` usa `key = { index, _ -> index }` para paragrafos
- **Arquivo**: `app/.../screens/reader/ReaderScreen.kt` (linha 135)
- **Problema**: Usar indice como key nao e ideal para recomposicao eficiente
- **Impacto**: Baixo — lista de strings simples, impacto minimo
- **Correcao**: Usar hash do conteudo ou identificador unico se disponivel

### L5. `LibraryScreen` mostra `CircularProgressIndicator` sem texto de status
- **Arquivo**: `app/.../screens/library/LibraryScreen.kt` (linhas 121-131)
- **Problema**: Durante importacao, so mostra um spinner girando sem indicar qual etapa (escaneando, parseando, salvando)
- **Impacto**: UX pobre — usuario nao sabe o progresso real
- **Correcao**: Adicionar texto abaixo do progress indicator mostrando a etapa atual

### L6. `BookDetailScreen` exibe `uiState.chapters.size` sem verificar null corretamente
- **Arquivo**: `app/.../screens/bookdetail/BookDetailScreen.kt` (linhas 96-193)
- **Problema**: Acesso a `uiState.book!!` (non-null assertion) na linha 97. Se `book` for null apos o loading, crasha.
- **Impacto**: Potencial NPE em edge case
- **Correcao**: Usar smart cast ou elvis operator em vez de `!!`

### L7. `TopAppBar` em `ReaderScreen` usa `containerColor = surface` (sem primaryContainer)
- **Arquivo**: `app/.../screens/reader/ReaderScreen.kt` (linhas 82-84)
- **Problema**: Inconsistencia visual com as outras telas (Library e BookDetail usam `primaryContainer`)
- **Impacto**: Minimo — questao de design
- **Correcao**: Padronizar cores do TopAppBar entre todas as telas

---

## 6. Recomendacoes

### 6.1 Prioridade 1 — Desbloquear Compilacao

Execute estas correcoes na ORDEM abaixo para obter um build passando:

1. **Corriger todos os imports do modulo `:core:data`**:
   - `BookRepositoryImpl.kt`: 6 imports errados
   - `ChapterRepositoryImpl.kt`: 2 imports errados
   - `BookMapper.kt`: 1 import errado
   
2. **Corriger `BookMapper.kt`**: Remover referencias a campos inexistentes (`coverPath -> coverImagePath`, `lastReadAt`, `addedAt`)

3. **Adicionar metodos faltantes aos DAOs**:
   - `BookDao.findBookByHash(hash: String): Long?`
   - `BookDao.updateBook(book: BookEntity)` (com `@Update`)
   - `ChapterDao.updateContentFilePath(chapterId: Long, path: String)`

4. **Adicionar metodo faltante ao `EpubStorageManager`**:
   - `copyBookFile(sourceFile: File, bookId: Long): File`

5. **Corriger `AppModule.kt`**: Imports do `DispatcherProvider`

6. **Corriger `LibraryViewModel.kt`**: 
   - Imports dos UseCases (adicionar `.library`)
   - Assinatura da chamada `importBookUseCase(uri.toString(), fileSize)`
   - Refatorar para nao usar `.collect()` em `Result<T>`

7. **Alinhar `ImportProgress`**: Adicionar subclasses `Scanning`, `Parsing`, `Saving` ao sealed class

8. **Corriger `BookDetailViewModel.kt`**: Usar destructuring `.onEach { (book, chapters) -> }` para Pair

### 6.2 Prioridade 2 — Correcoes de Runtime

- **Criar `@Transaction` no importBook**: Garantir atomicidade DB-FS ou implementar rollback
- **Corrigir `ReaderViewModel.onParagraphVisible()`**: Usar chapterId real em vez de hashCode
- **Corrigir `TextExtractor` regex**: `"\s+"` -> `"""\s+""".toRegex()`
- **Remover permissao `POST_NOTIFICATIONS`**: Ate ser necessaria
- **Remover `Converters.kt` desnecessario`**: Ou usar campo Uri se necessario

### 6.3 Prioridade 3 — Refinamentos

- **Coil ImageLoader**: Configurar cache de memoria e tratamento de erro para capas
- **Type-safe navigation**: Migrar para Navigation Compose com Kotlin Serialization
- **Consistencia de `Result<T>`**: Padronizar uso em todos os UseCases
- **Cleanup de arquivos orfaos**: Implementar verificacao periodica ou rollback
- **Unificar estilo TopAppBar**: Mesma cor em todas as telas

### 6.4 Verificacoes Pos-Correcao

Apos aplicar todas as correcoes, executar:
```bash
./gradlew :app:compileDebugKotlin
./gradlew :core:data:compileDebugKotlin
./gradlew :core:domain:compileDebugKotlin
./gradlew :core:common:compileDebugKotlin
./gradlew :app:assembleDebug
```

---

## 7. Fase 1 Restricoes — Verificacao

| Restricao | Status | Observacao |
|---|---|---|
| Sem codigo TTS | OK | Nenhuma referencia a TTS/TextToSpeech |
| Sem codigo de audio | OK | Nenhuma referencia a MediaPlayer/ExoPlayer/AudioTrack |
| Sem cache de audio | OK | Nenhuma referencia a cache de midia |
| Parser EPUB basico | OK | Container + OPF + NCX/NavDoc + texto |
| Room DB com livros/caps | OK | BookEntity + ChapterEntity + DAOs |
| Compose UI basica | OK | Library + BookDetail + Reader |
| Hilt DI configurado | OK | Modulos Database, Repository, App |

**A Fase 1 esta dentro do escopo definido.** Os problemas encontrados sao tecnicos de compilacao/integracao, nao de escopo.

---

*Relatorio gerado por auditoria tecnica independente. Todos os items foram verificados por cross-reference entre arquivos.*
