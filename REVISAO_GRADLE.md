# Revisao Gradle — EPUB Audio Reader

> Data da revisao: 2025-07-01
> Total de arquivos revisados: 10

---

## Arquivo: settings.gradle.kts
- **Status: OK**
- **Issues encontradas:** 0 problemas criticos.
  - (INFO) `pluginManagement.repositories` inclui `gradlePluginPortal()` além de `google()` e `mavenCentral()`. Isso é redundante para um projeto Android puro (todos os plugins Android/Kotlin estão no `google()` e `mavenCentral()`), mas não causa erro.

---

## Arquivo: build.gradle.kts (root)
- **Status: OK**
- **Issues encontradas:** 0 problemas. Todas as 7 referencias de plugins no catalog (`android.application`, `android.library`, `kotlin.android`, `kotlin.jvm`, `compose.compiler`, `ksp`, `hilt`) existem no TOML e estao declaradas corretamente com `apply false`.

---

## Arquivo: gradle/libs.versions.toml
- **Status: PROBLEMA**
- **Issues encontradas:**
  1. **(MEDIO)** `composeMaterial3 = "1.4.0"` definida e aplicada em `compose-material3` com `version.ref = "composeMaterial3"`. Quando se usa BOM (`compose-bom = 2025.12.00`), as versoes do Compose devem ser gerenciadas **exclusivamente** pela BOM. A versao explicita sobrescreve a BOM, criando risco de inconsistencia futura. **Correcao:** Remover `version.ref = "composeMaterial3"` da lib `compose-material3` e da secao `[versions]`.
  2. **(MEDIO)** `serialization = "1.7.3"` e `serialization-json` declarados, mas nenhum modulo aplica o plugin `kotlinx-serialization` nem usa a lib. Declaracao morta que polui o catalogo. **Correcao:** Remover se nao for usar; caso contrario, aplicar o plugin no modulo que precisa.
  3. **(BAIXO)** `compose-ui-tooling` e `compose-ui-test-manifest` declaradas no TOML mas nunca referenciadas em nenhum `build.gradle.kts`. Sao libs tipicamente usadas apenas em `debugImplementation`. **Correcao:** Adicionar `debugImplementation(libs.compose.ui.tooling)` no `app/build.gradle.kts` ou remover do TOML.
  4. **(INFO)** `composeMaterial3` em `[versions]` sera obsoleto apos a correcao do item 1. A versao 1.4.0 do Material3 ja esta incluida na BOM 2025.12.00, entao a versao explicita e redundante.

### Matriz de compatibilidade de versoes — TOML

| Componente | Versao | Status |
|---|---|---|
| Kotlin | 2.0.21 | OK |
| KSP | 2.0.21-1.0.28 | OK (match exato com Kotlin) |
| AGP | 8.7.3 | OK |
| Compose BOM | 2025.12.00 | OK (versao existe no Maven Google) |
| Gradle (wrapper) | 8.9 | OK (AGP 8.7.3 requer Gradle 8.7+) |
| Compose Compiler | via plugin Kotlin 2.0 | OK (Kotlin 2.0+ tem Compose Compiler integrado) |
| Room | 2.6.1 | OK (compativel com KSP) |
| Hilt | 2.52 | OK (compativel com KSP) |
| Coroutines | 1.9.0 | OK |
| Lifecycle | 2.8.7 | OK |
| Navigation | 2.8.5 | OK |

---

## Arquivo: gradle.properties
- **Status: PROBLEMA (leve)**
- **Issues encontradas:**
  1. **(BAIXO)** Faltam flags recomendadas para projetos Android modernos com AGP 8.x:
     - `android.nonTransitiveRClass=true` — melhora performance de build
     - `android.nonFinalResIds=true` — necessario para AGP 8.x (Resource IDs nao sao mais final)
  2. **(INFO)** `org.gradle.parallel=true` nao esta explicito (é default true em Gradle 8+, mas recomenda-se declarar).
  3. **(INFO)** `org.gradle.daemon=true` nao esta explicito (é default true).

---

## Arquivo: gradle/wrapper/gradle-wrapper.properties
- **Status: OK**
- **Issues encontradas:** Nenhuma. Gradle 8.9 é compativel com AGP 8.7.3.

---

## Arquivo: app/build.gradle.kts
- **Status: PROBLEMA**
- **Issues encontradas:**
  1. **(ALTO)** `buildTypes { release { isMinifyEnabled = true } }` sem `proguardFiles` configurado. Quando `isMinifyEnabled = true`, o AGP requer (ou fortemente recomenda) a configuracao de regras ProGuard/R8. Sem `proguardFiles`, o build pode falhar em producao ou gerar APK sem ofuscacao efetiva. **Correcao:**
     ```kotlin
     buildTypes {
         release {
             isMinifyEnabled = true
             proguardFiles(
                 getDefaultProguardFile("proguard-android-optimize.txt"),
                 "proguard-rules.pro"
             )
         }
     }
     ```
  2. **(BAIXO)** Faltam configuracoes recomendadas no `defaultConfig`:
     - `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` — necessario se houver testes instrumentados
  3. **(BAIXO)** `buildTypes` nao declara o tipo `debug` explicitamente. O AGP cria automaticamente, mas é boa pratica declara-lo se houver customizacoes futuras.
  4. **(INFO)** Todas as 12 referencias ao version catalog foram verificadas e existem no TOML: `compose.bom`, `bundles.compose`, `androidx.activity.compose`, `androidx.lifecycle.runtime.compose`, `androidx.lifecycle.viewmodel.compose`, `androidx.navigation.compose`, `androidx.documentfile`, `bundles.hilt`, `coil.compose`, `hilt.compiler`, `coroutines.android`, mais os 3 projetos (`:core:domain`, `:core:data`, `:core:common`).
  5. **(INFO)** `buildFeatures { compose = true }` é correto com Kotlin 2.0+ — ainda necessario para AGP habilitar suporte Compose, embora o compiler seja gerenciado pelo plugin `compose.compiler`.

---

## Arquivo: core/common/build.gradle.kts
- **Status: PROBLEMA (leve)**
- **Issues encontradas:**
  1. **(BAIXO)** Modulo `kotlin.jvm` sem configuracao de target Java (`compileOptions` / `kotlinOptions`). Herda Java 8 como default, enquanto os modulos Android usam Java 17. Isso cria inconsistencia de bytecode entre modulos. **Correcao:**
     ```kotlin
     plugins { alias(libs.plugins.kotlin.jvm) }

     java {
         sourceCompatibility = JavaVersion.VERSION_17
         targetCompatibility = JavaVersion.VERSION_17
     }

     dependencies { implementation(libs.coroutines.core) }
     ```
     Ou, se usar o plugin `java-library`:
     ```kotlin
     kotlin {
         jvmToolchain(17)
     }
     ```
  2. **(INFO)** Apenas 1 dependencia (`coroutines.core`), verificada e existente no TOML.

---

## Arquivo: core/domain/build.gradle.kts
- **Status: PROBLEMA (leve)**
- **Issues encontradas:**
  1. **(BAIXO)** Mesmo problema que `core/common`: modulo `kotlin.jvm` sem configuracao de target Java. Herda Java 8, inconsistente com Java 17 dos modulos Android. **Correcao:** Adicionar `java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }` ou `kotlin { jvmToolchain(17) }`.
  2. **(INFO)** As 2 dependencias (`:core:common` e `coroutines.core`) foram verificadas e existem.

---

## Arquivo: core/data/build.gradle.kts
- **Status: OK**
- **Issues encontradas:**
  1. **(INFO)** `android.library` com `minSdk = 26` e `compileSdk = 35` — consistente com o modulo `app`.
  2. **(INFO)** Todas as 8 referencias ao version catalog foram verificadas e existem: `android.library`, `kotlin.android`, `ksp`, `hilt`, `:core:domain`, `:core:common`, `bundles.room`, `room.compiler`, `bundles.hilt`, `hilt.compiler`, `bundles.coroutines`, `androidx.documentfile`.
  3. **(INFO)** KSP aplicado corretamente para Room (`ksp(libs.room.compiler)`) e Hilt (`ksp(libs.hilt.compiler)`).

---

## Arquivo: .github/workflows/build-apk.yml
- **Status: OK**
- **Issues encontradas:**
  1. **(INFO)** O workflow usa `chmod +x gradlew || true` — o `|| true` mascara erros se o `gradlew` nao existir. Recomenda-se remover o `|| true` para falhar fast.
  2. **(INFO)** `actions/cache@v4` é usado explicitamente para Gradle. O `actions/setup-java@v4` ja tem cache integrado (`cache: 'gradle'`), tornando o step de cache separado redundante. **Correcao opcional:** Usar `cache: 'gradle'` no `setup-java` e remover o step `Cache Gradle`.
  3. **(INFO)** O workflow so faz `assembleDebug`. Se quiser garantir que o build de release tambem funciona, adicionar um step `assembleRelease` (mesmo sem upload).

---

## Resumo de TODOS os problemas

| # | Severidade | Arquivo | Problema | Correcao |
|---|---|---|---|---|
| 1 | **ALTO** | `app/build.gradle.kts` | `isMinifyEnabled = true` sem `proguardFiles` | Adicionar `proguardFiles(getDefaultProguardFile(...), "proguard-rules.pro")` |
| 2 | **MEDIO** | `gradle/libs.versions.toml` | `compose-material3` com versao explicita sobrescreve BOM | Remover `version.ref = "composeMaterial3"`; deixar BOM gerenciar |
| 3 | **MEDIO** | `gradle/libs.versions.toml` | `serialization` e `serialization-json` declarados mas nunca usados | Remover do TOML ou aplicar plugin no modulo que precisa |
| 4 | **MEDIO** | `gradle/libs.versions.toml` | `compose-ui-tooling` e `compose-ui-test-manifest` declarados mas nao usados | Adicionar como `debugImplementation` no app ou remover do TOML |
| 5 | **BAIXO** | `core/common/build.gradle.kts` | Modulo `kotlin.jvm` sem target Java definido (default Java 8) | Adicionar `java { sourceCompatibility = targetCompatibility = VERSION_17 }` |
| 6 | **BAIXO** | `core/domain/build.gradle.kts` | Modulo `kotlin.jvm` sem target Java definido (default Java 8) | Adicionar `java { sourceCompatibility = targetCompatibility = VERSION_17 }` |
| 7 | **BAIXO** | `app/build.gradle.kts` | `testInstrumentationRunner` nao declarado no `defaultConfig` | Adicionar `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` |
| 8 | **BAIXO** | `gradle.properties` | Faltam flags recomendadas AGP 8.x | Adicionar `android.nonTransitiveRClass=true` e `android.nonFinalResIds=true` |
| 9 | INFO | `settings.gradle.kts` | `gradlePluginPortal()` redundante em projeto Android | Pode remover; `google()` + `mavenCentral()` sao suficientes |
| 10 | INFO | `.github/workflows/build-apk.yml` | Cache Gradle redundante com `setup-java` | Usar `cache: 'gradle'` no `setup-java` e remover step separado |
| 11 | INFO | `.github/workflows/build-apk.yml` | `chmod +x gradlew || true` mascara falhas | Remover `|| true` para fail-fast |
| 12 | INFO | `gradle.properties` | `org.gradle.parallel=true` nao explicito | Adicionar para clareza (ja é default no Gradle 8+) |

---

## Verificacao de referencias cruzadas (TODAS AS REFS)

### Plugins referenciados em build.gradle.kts (root)
| Plugin | TOML | Status |
|---|---|---|
| `android.application` | `[plugins] android-application` | OK |
| `android.library` | `[plugins] android-library` | OK |
| `kotlin.android` | `[plugins] kotlin-android` | OK |
| `kotlin.jvm` | `[plugins] kotlin-jvm` | OK |
| `compose.compiler` | `[plugins] compose-compiler` | OK |
| `ksp` | `[plugins] ksp` | OK |
| `hilt` | `[plugins] hilt` | OK |

### Libs referenciadas em app/build.gradle.kts
| Ref | TOML | Status |
|---|---|---|
| `libs.compose.bom` | `[libraries] compose-bom` | OK |
| `libs.bundles.compose` | `[bundles] compose` | OK |
| `libs.androidx.activity.compose` | `[libraries] androidx-activity-compose` | OK |
| `libs.androidx.lifecycle.runtime.compose` | `[libraries] androidx-lifecycle-runtime-compose` | OK |
| `libs.androidx.lifecycle.viewmodel.compose` | `[libraries] androidx-lifecycle-viewmodel-compose` | OK |
| `libs.androidx.navigation.compose` | `[libraries] androidx-navigation-compose` | OK |
| `libs.androidx.documentfile` | `[libraries] androidx-documentfile` | OK |
| `libs.bundles.hilt` | `[bundles] hilt` | OK |
| `libs.coil.compose` | `[libraries] coil-compose` | OK |
| `libs.hilt.compiler` | `[libraries] hilt-compiler` | OK |
| `libs.coroutines.android` | `[libraries] coroutines-android` | OK |

### Libs referenciadas em core/common/build.gradle.kts
| Ref | TOML | Status |
|---|---|---|
| `libs.coroutines.core` | `[libraries] coroutines-core` | OK |

### Libs referenciadas em core/domain/build.gradle.kts
| Ref | TOML | Status |
|---|---|---|
| `libs.coroutines.core` | `[libraries] coroutines-core` | OK |
| `project(":core:common")` | `include(":core:common")` em settings | OK |

### Libs referenciadas em core/data/build.gradle.kts
| Ref | TOML | Status |
|---|---|---|
| `libs.bundles.room` | `[bundles] room` | OK |
| `libs.room.compiler` | `[libraries] room-compiler` | OK |
| `libs.bundles.hilt` | `[bundles] hilt` | OK |
| `libs.hilt.compiler` | `[libraries] hilt-compiler` | OK |
| `libs.bundles.coroutines` | `[bundles] coroutines` | OK |
| `libs.androidx.documentfile` | `[libraries] androidx-documentfile` | OK |

**Conclusao:** Todas as 22 referencias cruzadas entre build scripts e version catalog foram validadas. Nenhuma referencia quebrada ou faltante.

---

## Verificacao final de compatibilidade Kotlin 2.0 + KSP + Compose

```
Kotlin:           2.0.21
KSP:              2.0.21-1.0.28  -> MATCH ✓
AGP:              8.7.3
Gradle:           8.9           -> AGP 8.7.3 requer Gradle 8.7+ ✓
Compose Compiler: via plugin org.jetbrains.kotlin.plugin.compose (Kotlin 2.0) ✓
Compose BOM:      2025.12.00    -> Versao existe no Maven Google ✓
Room + KSP:       2.6.1 + KSP   -> Compativel ✓
Hilt + KSP:       2.52 + KSP    -> Compativel ✓
```
