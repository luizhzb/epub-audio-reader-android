# Pesquisa: GitHub Actions CI/CD para Android com Gradle Version Catalog

> **Data da pesquisa:** 2025  
> **Tecnologias:** Kotlin 2.0, Gradle Version Catalog, KSP, Hilt, Room, Jetpack Compose  
> **Foco:** Workflows GitHub Actions, sintaxe TOML, problemas comuns de build

---

## 1. GitHub Actions Workflow para Android

### 1.1 Versao das Actions Recomendadas (2025)

| Action | Versao Recomendada | Observacao |
|--------|-------------------|------------|
| `actions/checkout` | **v4** | Checkout do repositorio |
| `actions/setup-java` | **v4** (ou v5 se disponivel/estavel) | Setup do JDK; v5 usa node24 |
| `gradle/actions/setup-gradle` | **v5** ou **v6** | Cache avancado do Gradle |
| `actions/upload-artifact` | **v4** | Upload de APK como artifact |

> **Nota importante sobre `gradle/actions/setup-gradle`:**
> - A **v5** e open-source (MIT) e recomendada para maioria dos projetos
> - A **v6** (lancada em mar/2026) mudou a licenca do componente de caching para proprietary (Terms of Use). Continua gratis para repos publicos. V6 removeu o suporte experimental a Configuration Cache.
> - Para uso 100% open-source, use `cache-provider: basic` na v6.

**Recomendacao:** Use `gradle/actions/setup-gradle@v5` para estabilidade e licenca MIT pura, ou `v6` com `cache-provider: basic` se preferir a versao mais recente.

### 1.2 Android SDK Licenses

**Os runners `ubuntu-latest` do GitHub Actions ja vem com Android SDK pre-instalado**, incluindo as licencas aceitas por padrao. Normalmente **NAO e necessario** aceitar licencas manualmente.

Se voce encontrar erros de licenca:

```yaml
# Opcao 1: Action de terceiros (nao oficial)
- name: Accept Android SDK licenses
  uses: android-actions/setup-android@v4
  with:
    accept-android-sdk-licenses: 'yes'

# Opcao 2: Aceitar via sdkmanager manualmente
- name: Accept Android licenses
  run: |
    yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses || true
    yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "build-tools;35.0.0" || true
```

### 1.3 Cache do Gradle - Configuracao Correta

**Metodo recomendado (oficial Gradle):**

```yaml
- name: Setup Gradle
  uses: gradle/actions/setup-gradle@v5
  with:
    # Cache eh ativado por padrao - nao precisa configurar mais nada!
    cache-read-only: ${{ github.ref != 'refs/heads/main' }}
```

O `gradle/actions/setup-gradle` gerencia cache automaticamente:
- Baixa distribuicoes do Gradle
- Cache de dependencias (`~/.gradle/caches`)
- Cache do wrapper
- Cache de build local
- Cache de Configuration Cache (Gradle 7+, com chave de criptografia)

**Metodo alternativo (setup-java com cache basico):**

```yaml
- name: Set up JDK 17
  uses: actions/setup-java@v4
  with:
    java-version: '17'
    distribution: 'temurin'
    cache: 'gradle'  # Cache basico de dependencias
    cache-dependency-path: |
      **/*.gradle*
      **/gradle-wrapper.properties
      **/libs.versions.toml
```

**Metodo manual (se necessario controle total):**

```yaml
- name: Cache Gradle
  uses: actions/cache@v4
  with:
    path: |
      ~/.gradle/caches
      ~/.gradle/wrapper
    key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties', '**/libs.versions.toml') }}
    restore-keys: |
      ${{ runner.os }}-gradle-
```

### 1.4 Upload do APK como Artifact

```yaml
- name: Upload APK Debug
  uses: actions/upload-artifact@v4
  with:
    name: app-debug-apk
    path: app/build/outputs/apk/debug/*.apk
    if-no-files-found: error

- name: Upload APK Release
  uses: actions/upload-artifact@v4
  with:
    name: app-release-apk
    path: app/build/outputs/apk/release/*.apk
    if-no-files-found: warn

# Upload de reports (lint, testes)
- name: Upload Lint Report
  uses: actions/upload-artifact@v4
  if: always()
  with:
    name: lint-report
    path: app/build/reports/lint-results-*.html
```

### 1.5 Variaveis de Ambiente Recomendadas

```yaml
env:
  GRADLE_OPTS: "-Dorg.gradle.jvmargs=-Xmx4096M -Dorg.gradle.daemon=false -Dorg.gradle.parallel=true -Dorg.gradle.caching=true"
```

---

## 2. Gradle Version Catalog - Sintaxe Correta

### 2.1 Estrutura do Arquivo TOML

Arquivo: `gradle/libs.versions.toml`

```toml
# ============================================================
# ORDEM CORRETA DAS SECOES
# ============================================================
# 1. [versions]  - Declara versoes reutilizaveis
# 2. [libraries] - Declara dependencias (bibliotecas)
# 3. [bundles]   - Agrupa bibliotecas comumente usadas juntas
# 4. [plugins]   - Declara plugins Gradle
# ============================================================

[versions]
# --- Android & Gradle ---
agp = "8.7.3"

# --- Kotlin ---
kotlin = "2.0.21"
ksp = "2.0.21-1.0.28"

# --- Compose ---
compose-bom = "2024.12.01"

# --- Hilt ---
hilt = "2.56"
androidx-hilt = "1.2.0"

# --- Room ---
room = "2.7.0"

# --- AndroidX ---
androidx-core = "1.15.0"
androidx-lifecycle = "2.8.7"
androidx-activity = "1.9.3"
androidx-navigation = "2.8.9"

# --- Coroutines ---
kotlinx-coroutines = "1.10.1"

# --- Testing ---
junit = "4.13.2"
junit-ext = "1.2.1"
espresso = "3.6.1"

[libraries]
# --- AndroidX Core ---
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "androidx-core" }

# --- AndroidX Lifecycle ---
androidx-lifecycle-runtime = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "androidx-lifecycle" }
androidx-lifecycle-viewmodel = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "androidx-lifecycle" }

# --- AndroidX Activity ---
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "androidx-activity" }

# --- AndroidX Navigation ---
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "androidx-navigation" }

# --- Compose BOM (Bill of Materials) ---
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }

# --- Compose Libraries (SEM versao - usam a do BOM) ---
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-material-icons = { group = "androidx.compose.material", name = "material-icons-extended" }
compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }

# --- Hilt ---
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "androidx-hilt" }

# --- Room ---
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }

# --- Coroutines ---
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "kotlinx-coroutines" }

# --- Testing ---
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit-ext = { group = "androidx.test.ext", name = "junit", version.ref = "junit-ext" }
androidx-espresso = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espresso" }

[bundles]
# Bundle: todas as libs Compose que sempre vao juntas
compose = [
    "compose-ui",
    "compose-ui-graphics",
    "compose-ui-tooling-preview",
    "compose-material3",
    "compose-material-icons"
]

# Bundle: Room + coroutines
room = [
    "room-runtime",
    "room-ktx"
]

# Bundle: dependencias de teste
testing = [
    "junit",
    "androidx-junit-ext",
    "androidx-espresso"
]

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt-android = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

### 2.2 Como Usar o Version Catalog nos Build Scripts

**Projeto-level `build.gradle.kts`:**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
}
```

**App-level `build.gradle.kts`:**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

dependencies {
    // --- BOM (Bill of Materials) do Compose ---
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    
    // --- AndroidX ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    
    // --- Hilt ---
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    
    // --- Room ---
    implementation(libs.bundles.room)
    ksp(libs.room.compiler)
    
    // --- Coroutines ---
    implementation(libs.kotlinx.coroutines.android)
    
    // --- Debug / Testing ---
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit.ext)
    androidTestImplementation(libs.androidx.espresso)
}
```

### 2.3 Boas Praticas para Version Catalog

1. **Use `version.ref` para referenciar versoes** - Nunca hardcode versoes em libraries
2. **Use `-` (hifen) como separador** - O Gradle normaliza para `.` no catalog (ex: `compose-ui` vira `compose.ui`)
3. **Defina BOMs como libraries** - E depois use `platform()` no build.gradle.kts
4. **Crie bundles para grupos logicos** - Compose, Room, Testing, etc.
5. **Mantenha plugins e libraries com nomes consistentes** - Facilita autocomplete
6. **As versoes no catalogo NAO influenciam a resolucao de dependencias** - Elas sao apenas um catalogo, nao uma plataforma

### 2.4 BOM (Bill of Materials) - Como Funciona

O BOM gerencia versoes compativel de um ecossistema. Para Compose:

```toml
[libraries]
# Declare o BOM com versao
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }

# Bibliotecas SEM versao - usam a do BOM
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
```

```kotlin
// No build.gradle.kts:
implementation(platform(libs.compose.bom))  // Aplica o BOM
implementation(libs.compose.ui)              // Sem versao - vem do BOM
implementation(libs.compose.material3)       // Sem versao - vem do BOM
```

**Para sobrescrever uma versao do BOM**, declare explicitamente:
```toml
[libraries]
compose-material3 = { group = "androidx.compose.material3", name = "material3", version.ref = "compose-material3-override" }
```

---

## 3. Problemas Comuns de Build Android

### 3.1 `android.useAndroidX=true` e `android.enableJetifier=true`

No arquivo `gradle.properties`:

```properties
# Essencial para projetos modernos Android
android.useAndroidX=true
android.enableJetifier=true

# Obs: A partir do AGP 9.0, android.useAndroidX=true eh o padrao
```

| Propriedade | Funcao |
|-------------|--------|
| `android.useAndroidX=true` | Forca o uso de AndroidX em vez da Support Library antiga |
| `android.enableJetifier=true` | Converte automaticamente dependencias de terceiros que ainda usam Support Library para AndroidX em tempo de build |

**Problemas comuns:**
- **Erro:** `"This project uses AndroidX dependencies, but the 'android.useAndroidX' property is not enabled"`  
  **Solucao:** Adicione `android.useAndroidX=true` no `gradle.properties`

- **Conflitos de duplicacao** entre AndroidX e Support Library:  
  **Solucao:** Verifique se NENHUMA dependencia esta usando Support Library. Use `android.enableJetifier=true` para migrar automaticamente.

### 3.2 Conflitos kotlin-stdlib e AndroidX

**Erro tipico:**
```
Duplicate class kotlin.collections.jdk8.CollectionsJDK8Kt found
in modules kotlin-stdlib-1.8.22.jar and kotlin-stdlib-jdk8-1.6.21.jar
```

**Causa:** Mix de versoes do Kotlin stdlib (ex: kotlin-stdlib-jdk7, kotlin-stdlib-jdk8) com versoes incompativel.

**Solucoes:**

```kotlin
// 1. Na raiz do projeto, exclua stdlib antigo
dependencies {
    configurations.all {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    }
}

// 2. Ou no gradle.properties:
kotlin.stdlib.default.dependency=false  // Desabilita stdlib automatico

// 3. Declare explicitamente a versao correta:
dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin}")
}
```

### 3.3 Configuracao do Java Toolchain

**Erro tipico:**
```
Inconsistent JVM-target compatibility detected for tasks
'compileReleaseJavaWithJavac' (1.8) and 'compileReleaseKotlin' (17)
```

**Configuracao correta (Kotlin 2.0 + AGP 8.x):**

```kotlin
// app/build.gradle.kts
android {
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    // Metodo recomendado (Gradle 6.7+, Kotlin 1.5+)
    jvmToolchain(17)
}

// Alternativa (Kotlin 2.0 DSL):
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}
```

**Para Kotlin 2.0 + Compose:**
- Use **Java 17** como minimo (Java 21 tambem suportado)
- O plugin `org.jetbrains.kotlin.plugin.compose` (Kotlin 2.0+) substitui `kotlinCompilerExtensionVersion`
- AGP 8.1+ eh requerido para toolchains funcionar corretamente com Android

### 3.4 KSP vs kapt para Room e Hilt

**Regra de ouro: Use KSP, nao kapt.**

KSP (Kotlin Symbol Processing) substitui kapt com:
- Builds **2x mais rapidos**
- Suporte nativo ao Kotlin
- Melhor integracao com o compilador Kotlin

**Configuracao para Room + Hilt com KSP:**

```toml
# libs.versions.toml
[versions]
kotlin = "2.0.21"
ksp = "2.0.21-1.0.28"
hilt = "2.56"
room = "2.7.0"

[plugins]
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt-android = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }

[libraries]
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
```

```kotlin
// app/build.gradle.kts
plugins {
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

dependencies {
    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)  // KSP, nao kapt!
    
    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)  // KSP, nao kapt!
}

// Configuracao do Room via KSP
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")  // Room 2.7+: gera codigo Kotlin
}
```

**Tabela de compatibilidade (2025):**

| Componente | Versao Minima KSP | Status |
|------------|-------------------|--------|
| Hilt | 2.48+ | KSP suportado (alpha ate 2.50, stable desde 2.51+) |
| Room | 2.6.0+ | KSP fully supported |
| Moshi | 1.15.0+ | KSP suportado |
| Kotlin | 2.0.0+ | Recomendado para K2 compiler |
| KSP | 2.0.21-1.0.28 | Compativel com Kotlin 2.0.21 |

**Dica:** A versao do KSP segue o formato `KOTLIN_VERSION-KSP_VERSION`.  
Exemplo: `2.0.21-1.0.28` = Kotlin 2.0.21 + KSP 1.0.28.

---

## 4. Exemplo de Workflow Funcional Completo

### 4.1 Workflow Basico - Build Debug APK

```yaml
# .github/workflows/android-ci.yml
name: Android CI

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main, develop ]
  workflow_dispatch:  # Permite execucao manual

env:
  GRADLE_OPTS: "-Dorg.gradle.jvmargs=-Xmx4096M -Dorg.gradle.daemon=false -Dorg.gradle.parallel=true -Dorg.gradle.caching=true"

jobs:
  build:
    name: Build & Test
    runs-on: ubuntu-latest

    steps:
      # 1. Checkout do codigo
      - name: Checkout
        uses: actions/checkout@v4

      # 2. Setup do JDK 17 (necessario para AGP 8.x)
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      # 3. Setup Gradle com cache automatico
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v5
        with:
          cache-read-only: ${{ github.ref != 'refs/heads/main' }}

      # 4. Permissao de execucao para o wrapper
      - name: Grant execute permission for gradlew
        run: chmod +x ./gradlew

      # 5. (Opcional) Aceitar licencas Android se necessario
      - name: Accept Android SDK licenses
        run: |
          yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses || true
        continue-on-error: true

      # 6. Rodar lint
      - name: Run Lint
        run: ./gradlew lintDebug
        continue-on-error: true

      # 7. Rodar testes unitarios
      - name: Run Unit Tests
        run: ./gradlew testDebugUnitTest

      # 8. Compilar APK Debug
      - name: Build Debug APK
        run: ./gradlew assembleDebug --stacktrace

      # 9. Upload do APK como artifact
      - name: Upload Debug APK
        uses: actions/upload-artifact@v4
        with:
          name: app-debug
          path: app/build/outputs/apk/debug/*.apk
          if-no-files-found: error

      # 10. (Opcional) Upload de reports
      - name: Upload Test Reports
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: test-reports
          path: |
            app/build/reports/tests/
            app/build/reports/lint-results-*.html
```

### 4.2 Workflow Avancado - Multi-Job com CI/CD Completo

```yaml
# .github/workflows/android-cd.yml
name: Android CI/CD Pipeline

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main, develop ]
  workflow_dispatch:
    inputs:
      release:
        description: 'Criar release?'
        required: false
        default: 'false'

env:
  JAVA_VERSION: '17'
  JAVA_DISTRIBUTION: 'temurin'
  GRADLE_OPTS: "-Dorg.gradle.jvmargs=-Xmx6g -Dorg.gradle.daemon=false -Dorg.gradle.parallel=true -Dorg.gradle.caching=true -Dorg.gradle.configureondemand=true"

jobs:
  # ============================================================
  # JOB 1: Quality Gate (Lint + Tests)
  # ============================================================
  quality-gate:
    name: Quality Gate
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK ${{ env.JAVA_VERSION }}
        uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: ${{ env.JAVA_DISTRIBUTION }}

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v5
        with:
          cache-read-only: ${{ github.ref != 'refs/heads/main' }}

      - name: Grant execute permission
        run: chmod +x ./gradlew

      - name: Run ktlintCheck
        run: ./gradlew ktlintCheck
        continue-on-error: true

      - name: Run Lint
        run: ./gradlew lintDebug
        continue-on-error: true

      - name: Run Unit Tests
        run: ./gradlew testDebugUnitTest

      - name: Upload Lint & Test Reports
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: quality-reports
          path: |
            app/build/reports/lint-results-*.html
            app/build/reports/tests/
            */build/reports/

  # ============================================================
  # JOB 2: Build Debug APK
  # ============================================================
  build-debug:
    name: Build Debug APK
    runs-on: ubuntu-latest
    needs: quality-gate
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK ${{ env.JAVA_VERSION }}
        uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: ${{ env.JAVA_DISTRIBUTION }}

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v5
        with:
          cache-read-only: ${{ github.ref != 'refs/heads/main' }}

      - name: Grant execute permission
        run: chmod +x ./gradlew

      - name: Build Debug APK
        run: ./gradlew assembleDebug --stacktrace

      - name: Upload Debug APK
        uses: actions/upload-artifact@v4
        with:
          name: debug-apk-${{ github.run_number }}
          path: app/build/outputs/apk/debug/*.apk

  # ============================================================
  # JOB 3: Build Release (com signing)
  # ============================================================
  build-release:
    name: Build Release
    runs-on: ubuntu-latest
    needs: quality-gate
    if: github.ref == 'refs/heads/main' || github.event.inputs.release == 'true'
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK ${{ env.JAVA_VERSION }}
        uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: ${{ env.JAVA_DISTRIBUTION }}

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v5
        with:
          cache-read-only: ${{ github.ref != 'refs/heads/main' }}

      - name: Grant execute permission
        run: chmod +x ./gradlew

      # Decode keystore do GitHub Secret (base64)
      - name: Decode Keystore
        run: |
          echo "${{ secrets.SIGNING_KEY_BASE64 }}" | base64 -d > app/keystore.jks

      - name: Build Release APK
        env:
          SIGNING_KEY_ALIAS: ${{ secrets.SIGNING_KEY_ALIAS }}
          SIGNING_KEY_PASSWORD: ${{ secrets.SIGNING_KEY_PASSWORD }}
          SIGNING_STORE_PASSWORD: ${{ secrets.SIGNING_STORE_PASSWORD }}
        run: ./gradlew assembleRelease --stacktrace

      - name: Upload Release APK
        uses: actions/upload-artifact@v4
        with:
          name: release-apk-${{ github.run_number }}
          path: app/build/outputs/apk/release/*.apk
```

### 4.3 Configuracao do Signing para Release

```kotlin
// app/build.gradle.kts
android {
    signingConfigs {
        create("release") {
            storeFile = file("keystore.jks")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: ""
            keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: ""
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: ""
        }
    }
    
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

### 4.4 Como Gerar o Keystore Base64 para Secrets

```bash
# Converter keystore para base64 (para armazenar no GitHub Secret)
base64 -w 0 minha-chave.jks > keystore-base64.txt

# No GitHub: Settings > Secrets and variables > Actions > New repository secret
# Nome: SIGNING_KEY_BASE64
# Valor: conteudo do arquivo keystore-base64.txt
```

---

## 5. Resumo das Melhores Praticas

### Gradle Version Catalog
- Use `[versions]` para centralizar versoes
- Use `[libraries]` para declarar dependencias
- Use `[bundles]` para agrupar libs comumente usadas
- Use `[plugins]` para declarar plugins
- Use BOMs para gerenciar versoes de ecossistemas (Compose)

### GitHub Actions
- Use `actions/setup-java@v4` com `distribution: 'temurin'` e `java-version: '17'`
- Use `gradle/actions/setup-gradle@v5` para cache avancado (MIT license)
- Use `actions/upload-artifact@v4` para upload de APKs
- Ative cache-read-only para branches nao-main
- Sempre use `./gradlew` (Gradle Wrapper) para builds

### KSP vs kapt
- **Sempre prefira KSP** sobre kapt (builds 2x mais rapidos)
- Hilt: KSP suportado desde 2.48+, recomendado 2.51+
- Room: KSP suportado desde 2.6.0+, recomendado 2.7.0+
- KSP versao deve combinar com Kotlin: `2.0.21-1.0.28` para Kotlin 2.0.21

### Java Toolchain
- **Java 17** e o minimo recomendado para AGP 8.x + Kotlin 2.0
- Configure `jvmToolchain(17)` ou `compileOptions { sourceCompatibility/targetCompatibility }`
- Para Kotlin 2.0, use o plugin `org.jetbrains.kotlin.plugin.compose` (nao precisa mais de `kotlinCompilerExtensionVersion`)

### Build em CI
- Desabilite o Gradle daemon: `-Dorg.gradle.daemon=false`
- Ative paralelismo: `-Dorg.gradle.parallel=true`
- Ative caching: `-Dorg.gradle.caching=true`
- Os runners `ubuntu-latest` ja tem Android SDK pre-instalado

---

## 6. Links de Referencia

| Recurso | Link |
|---------|------|
| actions/setup-java | https://github.com/actions/setup-java |
| gradle/actions | https://github.com/gradle/actions |
| KSP Documentation | https://developer.android.com/build/migrate-to-ksp |
| Room Releases | https://developer.android.com/jetpack/androidx/releases/room |
| Hilt KSP Guide | https://dagger.dev/dev-guide/ksp.html |
| Compose BOM | https://developer.android.com/develop/ui/compose/bom |
| Gradle Version Catalogs | https://docs.gradle.org/current/userguide/platforms.html |
| Java versions in Android | https://developer.android.com/build/jdks |
| AGP Release Notes | https://developer.android.com/build/releases/agp |
| Kotlin JVM Toolchain | https://kotlinlang.org/docs/gradle-configure-project.html |
| GitHub Actions Caching | https://github.com/gradle/actions#setup-gradle |
| Android SDK Setup Action | https://github.com/android-actions/setup-android |
