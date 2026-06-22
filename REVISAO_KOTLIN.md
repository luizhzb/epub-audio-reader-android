# Revisao Kotlin -- EPUB Audio Reader

## Modulo :app

### Arquivo: App.kt
- **Status:** OK
- **Package:** `com.epubaudioreader` -- corresponde ao caminho do arquivo
- **Hilt:** `@HiltAndroidApp` presente corretamente na classe `Application`

### Arquivo: MainActivity.kt
- **Status:** OK
- **Package:** `com.epubaudioreader` -- corresponde ao caminho do arquivo
- **Hilt:** `@AndroidEntryPoint` presente corretamente
- **Compose:** `setContent` com `MaterialTheme` correto; `AppNavigation()` chamado

### Arquivo: di/AppModule.kt
- **Status:** OK
- **Package:** `com.epubaudioreader.di` -- corresponde ao caminho do arquivo
- **Hilt:** `@Module @InstallIn(SingletonComponent::class)` presente; `@Provides` para `DispatcherProvider` correto

### Arquivo: ui/navigation/Screen.kt
- **Status:** OK
- **Package:** `com.epubaudioreader.ui.navigation` -- corresponde ao caminho
- Rotas definidas corretamente com `createRoute()` helper

### Arquivo: ui/navigation/AppNavigation.kt
- **Status:** OK
- **Package:** `com.epubaudioreader.ui.navigation` -- corresponde ao caminho
- **Compose:** `@Composable` presente; `hiltViewModel()` usado para todos os 3 ViewModels (`LibraryViewModel`, `BookDetailViewModel`, `ReaderViewModel`)
- **Navigation:** `NavHost` configurado corretamente com `startDestination = Screen.Library.route`; argumentos `bookId` (Long) e `chapterId` (Long) tipados via `NavType.LongType`
- **Cross-reference:** Chama `Screen.BookDetail.createRoute(bookId)` e `Screen.Reader.createRoute(bookId, chapterId)` -- metodos existem na sealed class

### Arquivo: ui/components/BookCard.kt
- **Status:** OK
- **Package:** `com.epubaudioreader.ui.components` -- corresponde ao caminho
- **Compose:** `@Composable` presente; usa `AsyncImage` do Coil (dependencia declarada em app/build.gradle.kts)
- **Import:** `coil.compose.AsyncImage` resolvivel (libs.coil.compose no build.gradle)

### Arquivo: ui/components/EmptyState.kt
- **Status:** OK
- **Package:** `com.epubaudioreader.ui.components` -- corresponde ao caminho
- **Compose:** `@Composable` presente

### Arquivo: ui/components/ImportFab.kt
- **Status:** OK
- **Package:** `com.epubaudioreader.ui.components` -- corresponde ao caminho
- **Compose:** `@Composable` presente; referencia `R.string.import_book` (assumindo que existe em res/values)

### Arquivo: ui/screens/library/LibraryScreen.kt
- **Status:** PROBLEMA (leve)
- **Package:** `com.epubaudioreader.ui.screens.library` -- corresponde ao caminho
- **Compose:** `@Composable` presente; `collectAsStateWithLifecycle()` usado corretamente (linha 56)
- **Problema (linha 47):** Import `androidx.documentfile.DocumentFile` NAO e utilizado no arquivo. Import morto.

### Arquivo: ui/screens/library/LibraryUiState.kt
- **Status:** OK
- **Package:** `com.epubaudioreader.ui.screens.library` -- corresponde ao caminho
- Referencia `ImportProgress` do modulo domain corretamente

### Arquivo: ui/screens/library/LibraryViewModel.kt
- **Status:** OK
- **Package:** `com.epubaudioreader.ui.screens.library` -- corresponde ao caminho
- **Hilt:** `@HiltViewModel` presente; `@Inject` no construtor com `@ApplicationContext` correto
- **Cross-reference UseCases:**
  - `getBooksUseCase()` -- OK, GetBooksUseCase retorna `Flow<List<Book>>` (batendo com `_uiState.update { it.copy(books = books) }`)
  - `importBookUseCase(uri.toString(), fileSize)` -- OK, ImportBookUseCase recebe `(String, Long)`
  - `deleteBookUseCase(book.id)` -- OK, DeleteBookUseCase recebe `Long`
- **Cross-reference Result:** `Result.Success` e `Result.Error` usados corretamente; `result.data.id` acessa `Book.id` (propriedade existe); `result.message` acessa `Result.Error.message` (propriedade existe)

### Arquivo: ui/screens/bookdetail/BookDetailScreen.kt
- **Status:** OK
- **Package:** `com.epubaudioreader.ui.screens.bookdetail` -- corresponde ao caminho
- **Compose:** `@Composable` presente; `collectAsStateWithLifecycle()` usado (linha 55)

### Arquivo: ui/screens/bookdetail/BookDetailUiState.kt
- **Status:** OK
- **Package:** `com.epubaudioreader.ui.screens.bookdetail` -- corresponde ao caminho

### Arquivo: ui/screens/bookdetail/BookDetailViewModel.kt
- **Status:** OK
- **Package:** `com.epubaudioreader.ui.screens.bookdetail` -- corresponde ao caminho
- **Hilt:** `@HiltViewModel` presente; `@Inject` no construtor
- **Cross-reference:** `getBookWithChaptersUseCase(bookId)` retorna `Flow<Pair<Book?, List<Chapter>>>`; desestruturacao `(book, chapters)` na linha 27 esta correta

### Arquivo: ui/screens/reader/ReaderScreen.kt
- **Status:** OK
- **Package:** `com.epubaudioreader.ui.screens.reader` -- corresponde ao caminho
- **Compose:** `@Composable` presente; `collectAsStateWithLifecycle()` usado (linha 45)
- Usa `uiState.chapterTitle`, `uiState.paragraphs`, `uiState.isLoading`, `uiState.error` -- propriedades existem em `ReaderUiState`

### Arquivo: ui/screens/reader/ReaderUiState.kt
- **Status:** OK
- **Package:** `com.epubaudioreader.ui.screens.reader` -- corresponde ao caminho

### Arquivo: ui/screens/reader/ReaderViewModel.kt
- **Status:** PROBLEMA (CRITICO)
- **Package:** `com.epubaudioreader.ui.screens.reader` -- corresponde ao caminho
- **Hilt:** `@HiltViewModel` presente; `@Inject` no construtor
- **Problema (linhas 54-60):** O ViewModel chama `getChapterContentUseCase(chapterId)` que retorna `ChapterContent?` (do modulo domain). O objeto `ChapterContent` tem as propriedades: `paragraphs: List<String>`, `totalChars: Int`, `totalParagraphs: Int`.
  - POREM o ViewModel tenta acessar `chapter.contentHtml` (propriedade INEXISTENTE) e `chapter.title` (propriedade INEXISTENTE).
  - Correcao: usar `chapter.paragraphs` diretamente (ja e a lista de paragrafos parseada) em vez de chamar `parseParagraphs(chapter.contentHtml)`. Para o `chapterTitle`, e necessario obter o titulo do capitulo de outra fonte (ex: modificar o UseCase para retornar `Pair<Chapter, ChapterContent>` ou um wrapper com title).

---

## Modulo :core:common

### Arquivo: core/common/src/main/java/.../dispatcher/DispatcherProvider.kt
- **Status:** OK
- **Package:** `com.epubaudioreader.core.common.dispatcher` -- corresponde ao caminho
- **Kotlin idiomatico:** Interface pura Kotlin sem `@Inject` (correto para modulo common); usa `Dispatchers.IO` para I/O (linha 13)

### Arquivo: core/common/src/main/java/.../result/Result.kt
- **Status:** OK
- **Package:** `com.epubaudioreader.core.common.result` -- corresponde ao caminho
- **Kotlin idiomatico:** Sealed class pura Kotlin sem `@Inject`; extensoes `map`, `onSuccess`, `onError`, `fold` implementadas corretamente

---

## Modulo :core:domain

### Arquivo: core/domain/src/main/java/.../model/Book.kt
- **Status:** OK
- **Package:** `com.epubaudioreader.core.domain.model` -- corresponde ao caminho

### Arquivo: core/domain/src/main/java/.../model/Chapter.kt
- **Status:** OK
- **Package:** `com.epubaudioreader.core.domain.model` -- corresponde ao caminho

### Arquivo: core/domain/src/main/java/.../model/ChapterContent.kt
- **Status:** OK
- **Package:** `com.epubaudioreader.core.domain.model` -- corresponde ao caminho
- **NOTA:** Esta data class tem `paragraphs: List<String>`, `totalChars: Int`, `totalParagraphs: Int`. NAO tem `contentHtml` nem `title` (usado incorretamente em ReaderViewModel).

### Arquivo: core/domain/src/main/java/.../model/ImportProgress.kt
- **Status:** OK
- **Package:** `com.epubaudioreader.core.domain.model` -- corresponde ao caminho
- Sealed class com todas as subclasses usadas na UI (Idle, Scanning, Parsing, Saving, Importing, Success, Error)

### Arquivo: core/domain/src/main/java/.../repository/BookRepository.kt
- **Status:** OK
- **Package:** `com.epubaudioreader.core.domain.repository` -- corresponde ao caminho
- Interface coerente com implementacao `BookRepositoryImpl`:
  - `getAllBooks(): Flow<List<Book>>` -- implementado
  - `getBookById(id: Long): Flow<Book?>` -- implementado
  - `importBook(filePath: String, fileSize: Long): Result<Book>` -- implementado
  - `deleteBook(id: Long): Result<Unit>` -- implementado
  - `updateLastRead(bookId: Long, chapterId: Long?, position: Int?)` -- implementado

### Arquivo: core/domain/src/main/java/.../repository/ChapterRepository.kt
- **Status:** OK
- **Package:** `com.epubaudioreader.core.domain.repository` -- corresponde ao caminho
- Interface coerente com implementacao `ChapterRepositoryImpl`:
  - `getChaptersByBook(bookId: Long): Flow<List<Chapter>>` -- implementado
  - `getChapterById(chapterId: Long): Chapter?` -- implementado
  - `getChapterContent(chapter: Chapter): ChapterContent` -- implementado
  - `getNextChapter(bookId: Long, currentOrderIndex: Int): Chapter?` -- implementado
  - `getPreviousChapter(bookId: Long, currentOrderIndex: Int): Chapter?` -- implementado

### Arquivo: core/domain/src/main/java/.../usecase/library/DeleteBookUseCase.kt
- **Status:** PROBLEMA (CRITICO)
- **Package:** `com.epubaudioreader.core.domain.usecase.library` -- corresponde ao caminho
- **Problema (linha 5):** Usa `import javax.inject.Inject` e `@Inject constructor`, mas o modulo `:core:domain` e puro Kotlin (plugin `kotlin.jvm`) e NAO declara dependencia do `javax.inject` nem do Hilt. O import `javax.inject.Inject` NAO sera resolvido em compilacao.
- **O mesmo problema afeta TODOS os UseCases do modulo domain** (ver abaixo).
- **Correcao:** Adicionar `implementation("javax.inject:javax.inject:1")` ao `core/domain/build.gradle.kts`.

### Arquivo: core/domain/src/main/java/.../usecase/library/GetBooksUseCase.kt
- **Status:** PROBLEMA (CRITICO)
- **Package:** `com.epubaudioreader.core.domain.usecase.library` -- corresponde ao caminho
- **Problema (linha 6):** Mesmo problema de `@Inject` sem acesso a `javax.inject`.

### Arquivo: core/domain/src/main/java/.../usecase/library/ImportBookUseCase.kt
- **Status:** PROBLEMA (CRITICO)
- **Package:** `com.epubaudioreader.core.domain.usecase.library` -- corresponde ao caminho
- **Problema (linha 6):** Mesmo problema de `@Inject` sem acesso a `javax.inject`.

### Arquivo: core/domain/src/main/java/.../usecase/reader/GetBookWithChaptersUseCase.kt
- **Status:** PROBLEMA (CRITICO)
- **Package:** `com.epubaudioreader.core.domain.usecase.reader` -- corresponde ao caminho
- **Problema (linha 9):** Mesmo problema de `@Inject` sem acesso a `javax.inject`.
- **Cross-reference:** Chama `bookRepository.getBookById(bookId)` e `chapterRepository.getChaptersByBook(bookId)` -- metodos existem nas interfaces.

### Arquivo: core/domain/src/main/java/.../usecase/reader/GetChapterContentUseCase.kt
- **Status:** PROBLEMA (CRITICO)
- **Package:** `com.epubaudioreader.core.domain.usecase.reader` -- corresponde ao caminho
- **Problema (linha 5):** Mesmo problema de `@Inject` sem acesso a `javax.inject`.
- **Cross-reference:** Chama `chapterRepository.getChapterById(chapterId)` e `chapterRepository.getChapterContent(chapter)` -- metodos existem na interface.

### Arquivo: core/domain/src/main/java/.../usecase/reader/SaveProgressUseCase.kt
- **Status:** PROBLEMA (CRITICO)
- **Package:** `com.epubaudioreader.core.domain.usecase.reader` -- corresponde ao caminho
- **Problema (linha 4):** Mesmo problema de `@Inject` sem acesso a `javax.inject`.
- **Cross-reference:** Chama `bookRepository.updateLastRead(bookId, chapterId, position)` -- metodo existe na interface.

---

## Modulo :core:data

### Arquivo: core/data/src/main/java/.../di/DatabaseModule.kt
- **Status:** OK
- **Package:** `com.epubaudioreader.core.data.di` -- corresponde ao caminho
- **Hilt:** `@Module @InstallIn(SingletonComponent::class)` presente; `@Provides` para `AppDatabase`, `BookDao` e `ChapterDao` corretos
- **Room:** Configuracao da database com `.fallbackToDestructiveMigration()`

### Arquivo: core/data/src/main/java/.../di/RepositoryModule.kt
- **Status:** PROBLEMA (CRITICO)
- **Package:** `com.epubaudioreader.core.data.di` -- corresponde ao caminho
- **Hilt:** `@Module @InstallIn(SingletonComponent::class)` presente; `@Binds` para `BookRepository` e `ChapterRepository` corretos
- **Problema:** FALTA `@Binds` para a interface `EpubParser`. O `BookRepositoryImpl` injeta `EpubParser` (interface), mas so existe `@Binds` para `BookRepository` e `ChapterRepository`. A implementacao `EpubParserImpl` tem `@Inject`, mas como e uma interface, o Hilt NAO consegue prover automaticamente sem `@Binds`.
- **Correcao:** Adicionar ao `RepositoryModule.kt`:
  ```kotlin
  @Binds
  abstract fun bindEpubParser(impl: EpubParserImpl): EpubParser
  ```

### Arquivo: core/data/src/main/java/.../local/database/AppDatabase.kt
- **Status:** OK
- **Package:** `com.epubaudioreader.core.data.local.database` -- corresponde ao caminho
- **Room:** `@Database` lista `[BookEntity::class, ChapterEntity::class]` com `version = 1`, `exportSchema = true`
- **Room:** `@TypeConverters` NAO e necessario (nenhum tipo customizado usado nas entities)

### Arquivo: core/data/src/main/java/.../local/database/dao/BookDao.kt
- **Status:** OK
- **Package:** `com.epubaudioreader.core.data.local.database.dao` -- corresponde ao caminho
- **Room:** `@Dao` presente; `@Query`, `@Insert`, `@Update`, `@Delete` usados corretamente
- **Primary Key:** A tabela `books` usa `id` como PK autoincrement -- queries por id corretas

### Arquivo: core/data/src/main/java/.../local/database/dao/ChapterDao.kt
- **Status:** OK
- **Package:** `com.epubaudioreader.core.data.local.database.dao` -- corresponde ao caminho
- **Room:** `@Dao` presente; `@Insert(onConflict = OnConflictStrategy.REPLACE)` para insercao em batch
- **Foreign Key:** Tabela `chapters` tem `ForeignKey` para `BookEntity(id)` com `CASCADE` -- delecao em cascata configurada corretamente

### Arquivo: core/data/src/main/java/.../local/database/entity/BookEntity.kt
- **Status:** OK
- **Package:** `com.epubaudioreader.core.data.local.database.entity` -- corresponde ao caminho
- **Room:** `@Entity(tableName = "books")` com `@PrimaryKey(autoGenerate = true)` e indices corretos
- Mapeia 1:1 com o domain model `Book` (via BookMapper)

### Arquivo: core/data/src/main/java/.../local/database/entity/ChapterEntity.kt
- **Status:** OK
- **Package:** `com.epubaudioreader.core.data.local.database.entity` -- corresponde ao caminho
- **Room:** `@Entity(tableName = "chapters")` com `@PrimaryKey(autoGenerate = true)`, `ForeignKey` para `BookEntity` e indice unico `bookId + orderIndex`
- Mapeia 1:1 com o domain model `Chapter` (via ChapterMapper)

### Arquivo: core/data/src/main/java/.../local/storage/EpubStorageManager.kt
- **Status:** OK
- **Package:** `com.epubaudioreader.core.data.local.storage` -- corresponde ao caminho
- **Hilt:** `@Singleton @Inject` com `@ApplicationContext` correto
- **Coroutines:** Todas as operacoes de I/O usam `withContext(Dispatchers.IO)` (linhas 37, 47, 55, 61, 65, 71, 81)
- **Inconsistencia leve:** `getCoverFile(bookId)` retorna `<coversDir>/<bookId>.jpg`, mas `CoverExtractor` salva em `<coversDir>/cover_<bookId>.jpg`. Como o path real e salvo no `BookEntity.coverImagePath` (retornado pelo CoverExtractor), funciona em runtime, mas o path padrao do StorageManager nao e usado para covers.

### Arquivo: core/data/src/main/java/.../repository/BookRepositoryImpl.kt
- **Status:** OK (com dependencia do problema do RepositoryModule)
- **Package:** `com.epubaudioreader.core.data.repository` -- corresponde ao caminho
- **Hilt:** `@Inject` no construtor com multiplas dependencias (DAOs, parsers, extractors, mappers, storageManager, dispatcher)
- **Cross-reference DAO:**
  - `bookDao.getAllBooks()` -> existe em BookDao
  - `bookDao.getBookById(id)` -> existe
  - `bookDao.findBookByHash(hash)` -> existe
  - `bookDao.insertBook(entity)` -> existe
  - `bookDao.updateBook(entity)` -> existe
  - `bookDao.getBookEntityById(id)` -> existe
  - `bookDao.deleteBook(bookEntity)` -> existe (recebe BookEntity, nao Long -- corretamente faz lookup primeiro)
  - `bookDao.updateLastRead(bookId, chapterId, position, timestamp)` -> existe (4 parametros)
  - `chapterDao.insertChapters(entities)` -> existe; retorna `List<Long>` (ids gerados) -- usado corretamente na linha 133
  - `chapterDao.updateContentFilePath(chapterId, path)` -> existe
- **Cross-reference Parser:** Usa `epubParser.parse(originalFile)` -- `EpubParser.parse(File): ParsedEpub` existe (PARSER NAO INJETAVEL SEM @BINDS -- ver problema acima)
- **Cross-reference Storage:** Usa `storageManager.computeFileHash()`, `copyBookFile()`, `saveChapterText()`, `deleteBookFiles()` -- todos existem
- **Coroutines:** `withContext(dispatcher.io)` em metodos suspensos de I/O

### Arquivo: core/data/src/main/java/.../repository/ChapterRepositoryImpl.kt
- **Status:** OK
- **Package:** `com.epubaudioreader.core.data.repository` -- corresponde ao caminho
- **Hilt:** `@Inject` no construtor
- **Cross-reference DAO:**
  - `chapterDao.getChaptersByBook(bookId)` -> existe
  - `chapterDao.getChapterById(chapterId)` -> existe
  - `chapterDao.getChapterByOrder(bookId, orderIndex)` -> existe
- **Cross-reference Storage:** Usa `storageManager.readChapterText(contentFilePath)` -- existe
- **Coroutines:** `withContext(dispatcher.io)` em `getChapterContent()`

### Arquivo: core/data/src/main/java/.../repository/mapper/BookMapper.kt
- **Status:** OK
- **Package:** `com.epubaudioreader.core.data.repository.mapper` -- corresponde ao caminho
- **Hilt:** `@Inject constructor()` correto para classe concreta
- Mapeamento `BookEntity <-> Book` completo (todos os campos mapeados)

### Arquivo: core/data/src/main/java/.../repository/mapper/ChapterMapper.kt
- **Status:** OK
- **Package:** `com.epubaudioreader.core.data.repository.mapper` -- corresponde ao caminho
- **Hilt:** `@Inject constructor()` correto
- Mapeamento `ChapterEntity <-> Chapter` completo

### Arquivo: core/data/src/main/java/.../epub/parser/EpubParser.kt
- **Status:** OK
- **Package:** `com.epubaudioreader.core.data.epub.parser` -- corresponde ao caminho
- Interface com `suspend fun parse(file: File): ParsedEpub`

### Arquivo: core/data/src/main/java/.../epub/parser/EpubParserImpl.kt
- **Status:** OK
- **Package:** `com.epubaudioreader.core.data.epub.parser` -- corresponde ao caminho
- **Hilt:** `@Inject constructor` com dependencias injetaveis (todos parsers concretos com `@Inject`)
- **Coroutines:** `withContext(Dispatchers.IO)`
- Implementa `EpubParser` corretamente

### Arquivo: core/data/src/main/java/.../epub/parser/ContainerParser.kt
- **Status:** OK
- **Package:** `com.epubaudioreader.core.data.epub.parser` -- corresponde ao caminho
- **Hilt:** `@Inject constructor()` correto

### Arquivo: core/data/src/main/java/.../epub/parser/OpfParser.kt
- **Status:** OK
- **Package:** `com.epubaudioreader.core.data.epub.parser` -- corresponde ao caminho
- **Hilt:** `@Inject constructor()` correto

### Arquivo: core/data/src/main/java/.../epub/parser/NcxParser.kt
- **Status:** OK
- **Package:** `com.epubaudioreader.core.data.epub.parser` -- corresponde ao caminho
- **Hilt:** `@Inject constructor()` correto

### Arquivo: core/data/src/main/java/.../epub/parser/NavDocParser.kt
- **Status:** OK
- **Package:** `com.epubaudioreader.core.data.epub.parser` -- corresponde ao caminho
- **Hilt:** `@Inject constructor()` correto

### Arquivo: core/data/src/main/java/.../epub/extractor/ChapterExtractor.kt
- **Status:** OK
- **Package:** `com.epubaudioreader.core.data.epub.extractor` -- corresponde ao caminho
- **Hilt:** `@Inject constructor` com `TextExtractor`
- Inner class `ChapterData` usada como retorno

### Arquivo: core/data/src/main/java/.../epub/extractor/TextExtractor.kt
- **Status:** OK
- **Package:** `com.epubaudioreader.core.data.epub.extractor` -- corresponde ao caminho
- **Hilt:** `@Inject constructor()` correto

### Arquivo: core/data/src/main/java/.../epub/extractor/CoverExtractor.kt
- **Status:** OK
- **Package:** `com.epubaudioreader.core.data.epub.extractor` -- corresponde ao caminho
- **Hilt:** `@Inject constructor()` correto
- Usa `Bitmap`, `BitmapFactory`, `FileOutputStream` do Android SDK (permitido -- modulo data e Android library)

### Modelos EPUB (ParsedEpub.kt, ParsedOpf.kt, ParsedMetadata.kt, ManifestItem.kt, SpineItem.kt, TocEntry.kt, GuideReference.kt)
- **Status:** OK
- Todos em `com.epubaudioreader.core.data.epub.model` -- packages correspondem aos caminhos
- Todos sao data classes puras Kotlin sem `@Inject` (correto)

---

## Cross-References Verificados

| Origem | Referencia | Destino | Status |
|--------|-----------|---------|--------|
| LibraryViewModel | `getBooksUseCase()` | GetBooksUseCase (domain) | OK |
| LibraryViewModel | `importBookUseCase(uri.toString(), fileSize)` | ImportBookUseCase (domain) | OK |
| LibraryViewModel | `deleteBookUseCase(book.id)` | DeleteBookUseCase (domain) | OK |
| LibraryViewModel | `Result.Success(result.data.id)` | Result.Success.data = Book (tem `id`) | OK |
| LibraryViewModel | `Result.Error(result.message)` | Result.Error.message = String | OK |
| LibraryScreen | `uiState.bookToDelete` | LibraryUiState.bookToDelete: Book? | OK |
| LibraryScreen | `ImportProgress.Scanning/Parsing/Saving` | ImportProgress subclasses | OK |
| BookDetailViewModel | `getBookWithChaptersUseCase(bookId)` | GetBookWithChaptersUseCase (domain) | OK |
| BookDetailViewModel | desestruturacao `(book, chapters)` | Flow<Pair<Book?, List<Chapter>>> | OK |
| ReaderViewModel | `getChapterContentUseCase(chapterId)` | GetChapterContentUseCase (domain) | OK (chamada OK, MAS uso do retorno esta ERRADO) |
| ReaderViewModel | `saveProgressUseCase(bookId, chapterId, paragraphIndex)` | SaveProgressUseCase (domain) | OK |
| BookRepositoryImpl | `bookDao.getAllBooks()` | BookDao.getAllBooks(): Flow<List<BookEntity>> | OK |
| BookRepositoryImpl | `bookDao.findBookByHash(hash)` | BookDao.findBookByHash(): Long? | OK |
| BookRepositoryImpl | `bookDao.insertBook(entity)` | BookDao.insertBook(): Long | OK |
| BookRepositoryImpl | `chapterDao.insertChapters(entities)` | ChapterDao.insertChapters(): List<Long> | OK |
| ChapterRepositoryImpl | `chapterDao.getChaptersByBook(bookId)` | ChapterDao.getChaptersByBook(): Flow<List<ChapterEntity>> | OK |
| ChapterRepositoryImpl | `storageManager.readChapterText(path)` | EpubStorageManager.readChapterText(): String | OK |
| BookMapper | mapeia BookEntity <-> Book | BookEntity e Book campos | OK (1:1) |
| ChapterMapper | mapeia ChapterEntity <-> Chapter | ChapterEntity e Chapter campos | OK (1:1) |

---

## Resumo Consolidado

| Severidade | Arquivo | Linha | Problema | Correcao |
|------------|---------|-------|----------|----------|
| CRITICO | `core/domain/build.gradle.kts` | -- | Modulo domain NAO tem dependencia de `javax.inject`. TODOS os 6 UseCases usam `@Inject` que NAO compila. | Adicionar `implementation("javax.inject:javax.inject:1")` ao `core/domain/build.gradle.kts` |
| CRITICO | `core/data/di/RepositoryModule.kt` | 14-20 | Falta `@Binds` para interface `EpubParser`. `BookRepositoryImpl` injeta `EpubParser` mas Hilt nao consegue prover interface sem @Binds. | Adicionar: `@Binds abstract fun bindEpubParser(impl: EpubParserImpl): EpubParser` |
| CRITICO | `app/.../reader/ReaderViewModel.kt` | 54-60 | `getChapterContentUseCase` retorna `ChapterContent` (que tem `paragraphs`, `totalChars`, `totalParagraphs`), mas o codigo acessa `chapter.contentHtml` e `chapter.title` (propriedades INEXISTENTES). | Alterar para usar `chapter.paragraphs` diretamente; obter title de outra fonte ou modificar UseCase |
| LEVE | `app/.../library/LibraryScreen.kt` | 47 | Import `androidx.documentfile.DocumentFile` nao e utilizado no arquivo. | Remover import nao utilizado |
| LEVE | `core/data/.../EpubStorageManager.kt` | 32 vs CoverExtractor.kt:44 | Inconsistencia de path: `getCoverFile` espera `<bookId>.jpg` mas `CoverExtractor` salva `cover_<bookId>.jpg`. | Unificar convencao de naming (ou usar StorageManager no CoverExtractor) |

## Contagem de Problemas
- **Criticos:** 3 (compilacao/funcionamento impedido)
- **Leves:** 2 (import morto, inconsistencia de path)
- **Total de arquivos revisados:** 50 arquivos .kt
