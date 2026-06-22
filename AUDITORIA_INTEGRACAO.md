# Auditoria de Integracao - EPUB Audio Reader

> Data: 2025-06-23
> Escopo: Verificacao de cross-references entre modulos, consistencia de interfaces/implementacoes, configuracao de DI Hilt, Room schema e compilacao do projeto como um todo.

---

## Resumo Executivo

| Modulo | Status | Issues Encontradas |
|--------|--------|-------------------|
| `:core:common` | Compilavel | 0 issues |
| `:core:domain` | Quebrado | 1 issue critica (javax.inject ausente) |
| `:core:data` | Quebrado | 2 issues criticas (DAO retorno, XMLPull) |
| `:app` | Quebrado | 2 issues criticas (campo inexistente, BOM invalido) |
| Cross-Modulo | Quebrado | 2 issues criticas (assinatura URI, mapeamento) |
| CI/CD (GitHub Actions) | Quebrado | 1 issue critica (gradlew ausente) |

**Total: 8 issues criticas, 4 warnings moderados, 4 observacoes menores**

---

## Modulo: :core:common

### Status: Compilavel

| Verificacao | Resultado |
|-------------|-----------|
| Plugin `kotlin("jvm")` | OK |
| Dependencia Android | ZERO dependencias Android |
| Hilt/javax.inject | NENHUM - correto |
| kotlinx-coroutines-core | Presente |
| Tipos Android | NENHUM - puro Kotlin |

- `DispatcherProvider.kt` - Interface pura com `kotlinx.coroutines.CoroutineDispatcher` - OK
- `Result.kt` - Sealed class pura Kotlin - OK
- Sem uso de `Context`, `Uri`, `android.*`, `javax.inject` - OK

---

## Modulo: :core:domain

### Status: Quebrado - Erro de Compilacao

| Verificacao | Resultado |
|-------------|-----------|
| Plugin `kotlin("jvm")` | OK |
| Dependencia Android | ZERO - OK |
| Interface BookRepository | OK (usa Flow, suspend, tipos puros) |
| Interface ChapterRepository | OK (usa Flow, suspend, tipos puros) |
| UseCases com `@Inject` | **QUEBRADO** - `javax.inject` nao esta no classpath |

### Issues

**[CRITICO] Issue #1 - `javax.inject` nao declarado como dependencia**

Todos os 7 UseCases usam `javax.inject.Inject`:
- `ImportBookUseCase.kt:8`
- `DeleteBookUseCase.kt:7`
- `GetBooksUseCase.kt:8`
- `GetBookWithChaptersUseCase.kt:11`
- `GetChapterContentUseCase.kt:7`
- `SaveProgressUseCase.kt:6`

Mas `core/domain/build.gradle.kts` nao declara nenhuma dependencia que forneca `javax.inject`:

```kotlin
// Atual (quebrado):
dependencies {
    implementation(project(":core:common"))
    implementation(libs.coroutines.core)
}
```

**Correcao necessaria:**
```kotlin
dependencies {
    implementation(project(":core:common"))
    implementation(libs.coroutines.core)
    compileOnly(libs.hilt.android)  // ou: implementation("javax.inject:javax.inject:1")
}
```

> Nota: Idealmente o :core:domain NAO deveria depender de Hilt. A solucao correta e adicionar apenas `javax.inject:javax.inject:1` como `compileOnly`.

---

## Modulo: :core:data

### Status: Quebrado - Erro de Compilacao

| Verificacao | Resultado |
|-------------|-----------|
| Room KSP configurado | OK (`ksp(libs.room.compiler)`) |
| Hilt KSP configurado | OK (`ksp(libs.hilt.compiler)`) |
| Dependencia `:core:domain` | OK |
| Dependencia `:core:common` | OK |
| AppDatabase com todas entities | OK (BookEntity, ChapterEntity) |
| DAOs vs Repositories | **QUEBRADO** - Retorno inconsistente |

### Issues

**[CRITICO] Issue #2 - `ChapterDao.insertChapters` retorno incompatible com `BookRepositoryImpl`**

`ChapterDao.kt:18-19`:
```kotlin
@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insertChapters(chapters: List<ChapterEntity>)
```

Retorno: `Unit` (implicito)

`BookRepositoryImpl.kt:133`:
```kotlin
val chapterIds = chapterDao.insertChapters(chapterEntities)
// ...
val chapterId = chapterIds[index]  // ERRO: Unit nao e indexavel
```

**Correcao necessaria em `ChapterDao.kt`:**
```kotlin
@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insertChapters(chapters: List<ChapterEntity>): List<Long>
```

**[CRITICO] Issue #3 - `BookRepositoryImpl.importBook` usa `Result.Error` sem parametro named**

`BookRepositoryImpl.kt:59-61`:
```kotlin
return@withContext Result.Error(
    IllegalArgumentException("Arquivo nao encontrado: $filePath")
)
```

A classe `Result.Error` tem assinatura:
```kotlin
data class Error(val exception: Throwable, val message: String = exception.message ?: "Unknown error")
```

O codigo passa apenas o `Throwable` como positional argument - isso funciona em Kotlin. Nao e um erro de compilacao, mas e inconsistente com o uso de named arguments em outras partes. **Classificado como WARNING.**

**[WARNING] Issue #4 - `CoverExtractor` salva covers em path diferente de `EpubStorageManager`**

`CoverExtractor.kt:40-44` salva em `bookFile.parentFile/covers/cover_$bookId.jpg`, mas `EpubStorageManager` gerencia covers em `context.filesDir/covers/$bookId.jpg`. Paths inconsistentes nao causam erro de compilacao, mas o cover nao sera encontrado pela UI. **WARNING funcional.**

**[OK] Verificacoes que passaram:**

- `BookMapper` mapeia todos os 17 campos de `BookEntity` <-> `Book` - OK
- `ChapterMapper` mapeia todos os 9 campos de `ChapterEntity` <-> `Chapter` - OK
- `BookDao` tem todos os metodos usados por `BookRepositoryImpl`: `getAllBooks`, `getBookById`, `insertBook`, `updateLastRead`, `deleteBook`, `getBookEntityById`, `findBookByHash`, `updateBook` - OK
- `ChapterDao` tem todos os metodos exceto o retorno de `insertChapters`: `getChaptersByBook`, `getChapterById`, `getChapterByOrder`, `deleteChaptersByBook`, `updateContentFilePath` - OK (com correcao do retorno)
- `RepositoryModule` faz `@Binds` correto para ambas as interfaces - OK
- `DatabaseModule` prove AppDatabase, BookDao, ChapterDao corretamente - OK
- Todos os parsers/extractors usam `@Inject constructor()` - OK

---

## Modulo: :app

### Status: Quebrado - Erro de Compilacao

| Verificacao | Resultado |
|-------------|-----------|
| `@HiltAndroidApp` em App.kt | OK |
| `@AndroidEntryPoint` em MainActivity.kt | OK |
| `hiltViewModel()` nas screens | OK |
| Compose + Navigation | OK |
| Coil | OK |
| Dependencia dos 3 modulos core | OK |
| BOM Compose `2025.12.00` | **QUEBRADO** - versao provavelmente inexistente |

### Issues

**[CRITICO] Issue #5 - `ReaderViewModel` acessa campo `contentHtml` inexistente em `ChapterContent`**

`ReaderViewModel.kt:56`:
```kotlin
val paragraphs = parseParagraphs(chapter.contentHtml)  // ERRO: ChapterContent NAO tem contentHtml
```

`ChapterContent.kt` (domain):
```kotlin
data class ChapterContent(
    val paragraphs: List<String>,      // <-- campo correto
    val totalChars: Int,
    val totalParagraphs: Int
)
```

O `GetChapterContentUseCase` ja retorna `ChapterContent` que ja contem `paragraphs: List<String>`. Nao ha necessidade de fazer parsing novamente.

**Correcao necessaria em `ReaderViewModel.kt`:**
```kotlin
// Metodo loadChapter() - substituir o bloco try:
val chapter = getChapterContentUseCase(chapterId)
if (chapter != null) {
    _uiState.update {
        it.copy(
            chapterTitle = chapter.title,  // Mas ChapterContent nao tem title!
            paragraphs = chapter.paragraphs,  // CORRETO - usa direto
            isLoading = false,
            currentParagraphIndex = 0
        )
    }
}
```

> **Problema adicional:** `ChapterContent` nao tem campo `title`. O `ReaderViewModel` tenta setar `chapterTitle` mas nao tem essa informacao. O `GetChapterContentUseCase` retorna `ChapterContent?` sem o titulo. Precisa adicionar `title` em `ChapterContent` OU buscar o capitulo separadamente.

**[CRITICO] Issue #6 - BOM Compose versao inexistente `2025.12.00`**

`gradle/libs.versions.toml:13`:
```toml
composeBom = "2025.12.00"
```

A versao `2025.12.00` do Compose BOM provavelmente nao existe no Maven/Google. A versao mais recente conhecida e `2024.06.00` ou similar. Isso causara erro de resolucao de dependencia:

```
Could not find androidx.compose:compose-bom:2025.12.00
```

**Correcao necessaria:**
```toml
composeBom = "2024.06.00"  # ou versao mais recente valida
```

**[WARNING] Issue #7 - `LibraryViewModel.importBook` passa URI `content://` como filePath**

`LibraryViewModel.kt:54`:
```kotlin
val result = importBookUseCase(uri.toString(), fileSize)
// uri.toString() = "content://com.android.providers.../document/..."
```

`BookRepositoryImpl.kt:57`:
```kotlin
val originalFile = File(filePath)  // File("content://...") -> FALHA em runtime
```

`File` nao aceita URI `content://`. O correto e usar `DocumentFile` ou `ContentResolver` para copiar o arquivo para um path local antes de chamar `importBook`.

**WARNING funcional critico** - compila mas falha em runtime.

**[WARNING] Issue #8 - `LibraryScreen` importa `DocumentFile` mas nao usa**

`LibraryScreen.kt:47`:
```kotlin
import androidx.documentfile.DocumentFile  // Nao utilizado
```

Import nao usado. Nao quebra compilacao (warning).

**[WARNING] Issue #9 - `app/build.gradle.kts` `isMinifyEnabled = true` sem proguard rules**

```kotlin
buildTypes { release { isMinifyEnabled = true } }  // Sem proguardFiles
```

O `proguard-rules.pro` existe mas esta vazio. Isso pode causar problemas de runtime com Hilt/Room apos minificacao.

**[OK] Verificacoes que passaram:**

- `MainActivity` e `@AndroidEntryPoint` com `setContent { MaterialTheme { AppNavigation() } }` - OK
- `App.kt` e `@HiltAndroidApp` - OK
- `AppModule` prove `DispatcherProvider` - OK
- `AppNavigation` usa `hiltViewModel()` corretamente para os 3 ViewModels - OK
- Screen routes estao consistentes com argumentos: `bookDetail/{bookId}`, `reader/{bookId}/{chapterId}` - OK
- Todos os 3 ViewModels sao `@HiltViewModel` com `@Inject constructor` - OK
- UI screens usam `collectAsStateWithLifecycle()` corretamente - OK

---

## Cross-Modulo

### Mapeamento Book (domain) <-> BookEntity (data)

| Campo (domain) | Campo (entity) | Tipo | Status |
|----------------|----------------|------|--------|
| `id` | `id` | Long | OK |
| `title` | `title` | String | OK |
| `authors` | `authors` | String | OK |
| `language` | `language` | String | OK |
| `identifier` | `identifier` | String | OK |
| `description` | `description` | String? | OK |
| `coverImagePath` | `coverImagePath` | String? | OK |
| `filePath` | `filePath` | String | OK |
| `importDate` | `importDate` | Long | OK |
| `lastReadDate` | `lastReadDate` | Long? | OK |
| `totalChapters` | `totalChapters` | Int | OK |
| `totalChars` | `totalChars` | Long | OK |
| `fileSize` | `fileSize` | Long | OK |
| `hash` | `hash` | String | OK |
| `lastReadChapterId` | `lastReadChapterId` | Long? | OK |
| `lastReadPosition` | `lastReadPosition` | Int? | OK |
| **Defaults** | `language="pt-BR"` (domain) vs sem default (entity) | | OK - mapper preenche |

**Status: OK** - Todos os 16 campos mapeados corretamente por `BookMapper`.

### Mapeamento Chapter (domain) <-> ChapterEntity (data)

| Campo (domain) | Campo (entity) | Tipo | Status |
|----------------|----------------|------|--------|
| `id` | `id` | Long | OK |
| `bookId` | `bookId` | Long | OK |
| `title` | `title` | String | OK |
| `orderIndex` | `orderIndex` | Int | OK |
| `contentFilePath` | `contentFilePath` | String | OK |
| `charCount` | `charCount` | Int | OK |
| `paragraphCount` | `paragraphCount` | Int | OK |
| `spineIndex` | `spineIndex` | Int | OK |
| `href` | `href` | String | OK |

**Status: OK** - Todos os 9 campos mapeados corretamente por `ChapterMapper`.

### BookRepository (interface) vs BookRepositoryImpl

| Metodo (interface) | Metodo (impl) | Status |
|--------------------|---------------|--------|
| `getAllBooks(): Flow<List<Book>>` | Implementado com `bookDao.getAllBooks()` + mapper | OK |
| `getBookById(id: Long): Flow<Book?>` | Implementado com `bookDao.getBookById()` + mapper | OK |
| `importBook(filePath: String, fileSize: Long): Result<Book>` | Implementado | OK (logica complexa) |
| `deleteBook(id: Long): Result<Unit>` | Implementado com `bookDao.deleteBook()` + storage | OK |
| `updateLastRead(bookId, chapterId, position)` | Implementado com `bookDao.updateLastRead()` | OK |

**Status: OK** - Todos os 5 metodos implementados.

### ChapterRepository (interface) vs ChapterRepositoryImpl

| Metodo (interface) | Metodo (impl) | Status |
|--------------------|---------------|--------|
| `getChaptersByBook(bookId): Flow<List<Chapter>>` | Implementado com `chapterDao.getChaptersByBook()` + mapper | OK |
| `getChapterById(chapterId): Chapter?` | Implementado com `chapterDao.getChapterById()` + mapper | OK |
| `getChapterContent(chapter): ChapterContent` | Implementado com `storageManager.readChapterText()` | OK |
| `getNextChapter(bookId, orderIndex): Chapter?` | Implementado com `chapterDao.getChapterByOrder()` | OK |
| `getPreviousChapter(bookId, orderIndex): Chapter?` | Implementado com `chapterDao.getChapterByOrder()` + check | OK |

**Status: OK** - Todos os 5 metodos implementados.

### UseCases - Dependencias e Assinaturas

| UseCase | Dependencias via Construtor | Assinatura invoke | Chamador | Status |
|---------|---------------------------|-------------------|----------|--------|
| `GetBooksUseCase` | `BookRepository` | `(): Flow<List<Book>>` | `LibraryViewModel.loadBooks()` | OK |
| `ImportBookUseCase` | `BookRepository` | `(filePath: String, fileSize: Long): Result<Book>` | `LibraryViewModel.importBook()` | **WARNING - URI** |
| `DeleteBookUseCase` | `BookRepository` | `(bookId: Long): Result<Unit>` | `LibraryViewModel.executeDelete()` | OK |
| `GetBookWithChaptersUseCase` | `BookRepository, ChapterRepository` | `(bookId: Long): Flow<Pair<Book?, List<Chapter>>>` | `BookDetailViewModel.loadBook()` | OK |
| `GetChapterContentUseCase` | `ChapterRepository` | `(chapterId: Long): ChapterContent?` | `ReaderViewModel.loadChapter()` | **ERRO - campo inexistente** |
| `SaveProgressUseCase` | `BookRepository` | `(bookId: Long, chapterId: Long, position: Int)` | `ReaderViewModel` (via debounce) | OK |

---

## CI/CD (GitHub Actions)

### Arquivo: `.github/workflows/build-apk.yml`

### Status: Quebrado

| Verificacao | Resultado |
|-------------|-----------|
| `gradle.properties` com `android.useAndroidX=true` | OK (presente) |
| `android.enableJetifier=true` | OK (presente) |
| Aceitar licencas SDK | OK (`setup-android@v3` faz isso) |
| `chmod +x gradlew` | OK |
| `./gradlew` vs `gradle` | **QUEBRADO** - gradlew nao existe |

**[CRITICO] Issue #10 - Wrapper do Gradle incompleto**

O diretorio `gradle/wrapper/` contem apenas `gradle-wrapper.properties`. Falta:
- `gradlew` (script shell)
- `gradlew.bat` (script Windows)
- `gradle/wrapper/gradle-wrapper.jar`

O workflow executa `./gradlew :app:assembleDebug` mas o script nao existe.

**Correcao necessaria:**
```bash
# No projeto:
gradle wrapper --gradle-version 8.9
```

Isso gerara os 3 arquivos necessarios. Eles DEVEM ser commitados no Git.

**[OK] Verificacoes que passaram:**

- `setup-java@v4` com JDK 17 - OK
- `setup-android@v3` - OK
- Cache Gradle configurado - OK
- Upload artifact configurado - OK
- Trigger em `push: branches: [main]` e `workflow_dispatch` - OK

---

## Observacoes Adicionais

### [MENOR] `TextExtractor` usa `XmlPullParser` sem dependencia explicita

O `XmlPullParser` e parte do Android SDK, entao como `:core:data` e uma `android.library`, funciona. Mas se houver necessidade de testes unitarios JVM puros, pode ser necessario adicionar `org.xmlpull:xmlpull:1.1.3.1` como dependencia de teste.

### [MENOR] `BookRepositoryImpl` comenta transacao Room mas nao usa `@Transaction`

O comentario na linha 52 diz "All database operations are wrapped in a Room transaction" mas nao ha anotacao `@Transaction` nem `runInTransaction`. A importacao consiste em multiplas escritas no DB que deveriam ser atomicas.

### [MENOR] `kotlinx-serialization-json` declarado no TOML mas nunca usado

`libs.versions.toml:19,50` declara a versao e a library mas nenhum modulo a importa.

### [MENOR] `file_paths.xml` configura `files-path` para `covers/` mas `CoverExtractor` nao usa FileProvider

O `CoverExtractor` salva diretamente no filesystem sem usar `FileProvider`. Como a app le os covers diretamente via `File(coverImagePath)` no Coil, isso funciona para a app mesma. O `FileProvider` esta configurado para futuro compartilhamento.

---

## Lista Prioritaria de Correcoes

| # | Prioridade | Descricao | Arquivos Afetados | Tipo |
|---|------------|-----------|-------------------|------|
| 1 | **CRITICO** | Adicionar `javax.inject` como dependencia no :core:domain | `core/domain/build.gradle.kts` | Compilacao |
| 2 | **CRITICO** | Corrigir retorno de `ChapterDao.insertChapters` para `List<Long>` | `core/data/.../dao/ChapterDao.kt` | Compilacao |
| 3 | **CRITICO** | Corrigir `ReaderViewModel` - usar `chapter.paragraphs` e adicionar `title` em `ChapterContent` | `app/.../ReaderViewModel.kt`, `core/domain/.../ChapterContent.kt` | Compilacao |
| 4 | **CRITICO** | Corrigir versao do Compose BOM para versao existente | `gradle/libs.versions.toml` | Compilacao |
| 5 | **CRITICO** | Gerar e commitar wrapper do Gradle (`gradlew`, `gradlew.bat`, `gradle-wrapper.jar`) | `gradlew`, `gradle/wrapper/` | CI/CD |
| 6 | **ALTA** | Corrigir `LibraryViewModel.importBook` para copiar arquivo de URI antes de importar | `app/.../LibraryViewModel.kt` | Runtime |
| 7 | **ALTA** | Adicionar `@Transaction` no metodo `importBook` de `BookRepositoryImpl` | `core/data/.../BookRepositoryImpl.kt` | Runtime/Data |
| 8 | **MEDIA** | Corrigir path de salvamento do cover para usar `EpubStorageManager.getCoverFile()` | `core/data/.../CoverExtractor.kt` | Runtime |
| 9 | **MEDIA** | Adicionar `proguardFiles` no build type release | `app/build.gradle.kts` | Runtime |
| 10 | **MEDIA** | Remover import nao utilizado `DocumentFile` em `LibraryScreen` | `app/.../LibraryScreen.kt` | Warning |
| 11 | **BAIXA** | Remover dependencia `kotlinx-serialization-json` nao utilizada do TOML | `gradle/libs.versions.toml` | Cleanup |
| 12 | **BAIXA** | Adicionar `title` ao retorno de `GetChapterContentUseCase` | `core/domain/.../ChapterContent.kt`, `core/data/.../ChapterRepositoryImpl.kt` | UX |

---

## Comandos de Correcao Rapida

### Fix 1: core/domain/build.gradle.kts
```kotlin
plugins { alias(libs.plugins.kotlin.jvm) }

dependencies {
    implementation(project(":core:common"))
    implementation(libs.coroutines.core)
    compileOnly("javax.inject:javax.inject:1")
}
```

### Fix 2: core/data/.../dao/ChapterDao.kt
```kotlin
@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insertChapters(chapters: List<ChapterEntity>): List<Long>
```

### Fix 3: core/domain/.../ChapterContent.kt + ReaderViewModel.kt
```kotlin
// ChapterContent.kt
data class ChapterContent(
    val title: String,
    val paragraphs: List<String>,
    val totalChars: Int,
    val totalParagraphs: Int
)
```

```kotlin
// ReaderViewModel.kt - loadChapter()
val content = getChapterContentUseCase(chapterId)
if (content != null) {
    _uiState.update {
        it.copy(
            chapterTitle = content.title,
            paragraphs = content.paragraphs,
            isLoading = false,
            currentParagraphIndex = 0
        )
    }
}
```

### Fix 4: gradle/libs.versions.toml
```toml
composeBom = "2024.06.00"
```

### Fix 5: Gerar wrapper
```bash
cd /mnt/agents/output/project
gradle wrapper --gradle-version 8.9
```

### Fix 6: LibraryViewModel.kt - copiar arquivo antes de importar
```kotlin
fun importBook(uri: Uri) {
    viewModelScope.launch {
        _uiState.update { it.copy(importProgress = ImportProgress.Importing("Importando...", 0)) }
        try {
            val fileSize = context.contentResolver.openFileDescriptor(uri, "r")?.statSize ?: 0L
            // Copiar para cache dir
            val tempFile = File(context.cacheDir, "import_temp.epub")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            when (val result = importBookUseCase(tempFile.absolutePath, fileSize)) {
                is Result.Success -> { /* ... */ }
                is Result.Error -> { /* ... */ }
            }
            tempFile.delete() // cleanup
        } catch (e: Exception) {
            _uiState.update { it.copy(importProgress = ImportProgress.Error(e.message ?: "Erro")) }
        }
    }
}
```

---

*Relatorio gerado automaticamente pela auditoria de integracao do projeto EPUB Audio Reader.*
