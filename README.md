# EPUB Audio Reader

Aplicativo Android nativo para leitura de arquivos EPUB com suporte a TTS (Text-to-Speech) offline via modelo Kokoro pt-BR.

## Status Atual

**Fase 1 completa:** Importacao e leitura visual de EPUBs.

| Feature | Status |
|---------|--------|
| Importacao EPUB via SAF (ACTION_OPEN_DOCUMENT) | Funcional |
| Parsing EPUB 2/3 (manual, Zip+XmlPullParser) | Funcional |
| Extracao de capa (5 niveis de fallback) | Funcional |
| Extracao de capitulos em texto limpo | Funcional |
| Biblioteca com grid de livros | Funcional |
| Tela de capitulos | Funcional |
| Leitor visual (LazyColumn de paragrafos) | Funcional |
| Persistencia (Room) + progresso de leitura | Funcional |
| TTS streaming (Sherpa-ONNX + Kokoro) | **Fase 2** |

## Tecnologias

- Kotlin 2.0.21
- Jetpack Compose (Material 3)
- Room 2.6.1
- Hilt (Dependency Injection)
- Navigation Compose
- Coil (carregamento de imagens)
- Coroutines + Flow
- Parsing manual de EPUB (ZipFile + XmlPullParser)

## Arquitetura

```
:app              — Compose UI, ViewModels, Navigation, DI
:core:common      — Kotlin puro: Result<T>, DispatcherProvider
:core:domain      — Kotlin puro: Modelos, Repository interfaces, UseCases
:core:data        — Room DB, EPUB parsing, Repositories, Storage
```

## Requisitos para Compilar

### 1. Android Studio

Baixe o **Android Studio Ladybug (2024.2.1)** ou superior:
https://developer.android.com/studio

### 2. JDK 17

O projeto requer JDK 17. O Android Studio ja inclui um embedded JDK, mas se quiser usar o sistema:

```bash
# macOS (Homebrew)
brew install openjdk@17

# Ubuntu/Debian
sudo apt install openjdk-17-jdk

# Verificar
java -version  # Deve mostrar "openjdk 17"
```

### 3. SDK do Android

No Android Studio, instale via SDK Manager:

- **Android SDK Platform 35** (compileSdk)
- **Android SDK Build-Tools 35**
- **Android SDK Command-line Tools**
- **Android Emulator** (opcional, para testar)

Ou via linha de comando:

```bash
sdkmanager "platforms;android-35" "build-tools;35.0.0"
```

## Como Compilar o APK

### Passo 1: Importar o projeto

1. Abra o Android Studio
2. **File > Open** e selecione a pasta do projeto
3. Aguarde o Gradle sync (primeira vez pode demorar varios minutos)

### Passo 2: Sync Gradle

Se o sync falhar com erro de wrapper:

```bash
# Na raiz do projeto
./gradlew wrapper --gradle-version 8.9
```

### Passo 3: Build

**Via Android Studio:**
- **Build > Make Project** (Ctrl+F9)
- **Build > Generate Signed Bundle/APK** para APK release

**Via linha de comando:**

```bash
# Build debug APK
./gradlew :app:assembleDebug

# O APK sera gerado em:
# app/build/outputs/apk/debug/app-debug.apk

# Build release APK (requer keystore)
./gradlew :app:assembleRelease
```

### Passo 4: Instalar no dispositivo

```bash
# Com dispositivo conectado via USB (com debug habilitado)
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Possiveis Erros e Solucoes

### Erro: "Gradle wrapper not found"

```bash
gradle wrapper --gradle-version 8.9
```

### Erro: "compileSdk 35 not found"

```bash
# No Android Studio: Tools > SDK Manager > SDK Platforms
# Instale "Android API 35"
```

### Erro: "KSP plugin not found"

```bash
# O projeto usa KSP (Kotlin Symbol Processing) para Room e Hilt
# Verifique se o arquivo gradle/libs.versions.toml existe
# E se as versoes de ksp e kotlin sao compativeis:
# kotlin = "2.0.21", ksp = "2.0.21-1.0.28"
```

### Erro: "Cannot resolve com.google.dagger:hilt-android"

```bash
# Verifique sua conexao com a internet (para baixar dependencias)
# Ou tente:
./gradlew --refresh-dependencies
```

## Estrutura de Diretorios

```
project/
├── gradle/
│   ├── libs.versions.toml       # Version catalog
│   └── wrapper/
│       └── gradle-wrapper.properties
├── settings.gradle.kts          # Modulos
├── build.gradle.kts             # Root build
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/epubaudioreader/
│       │   ├── App.kt
│       │   ├── MainActivity.kt
│       │   ├── di/
│       │   ├── ui/
│       │   │   ├── navigation/
│       │   │   ├── components/
│       │   │   └── screens/
│       │   │       ├── library/
│       │   │       ├── bookdetail/
│       │   │       └── reader/
│       │   └── service/          # (Fase 2: Media3)
│       └── res/
├── core/
│   ├── common/
│   │   └── src/main/java/.../common/
│   │       ├── result/Result.kt
│   │       └── dispatcher/DispatcherProvider.kt
│   ├── domain/
│   │   └── src/main/java/.../domain/
│   │       ├── model/
│   │       ├── repository/
│   │       └── usecase/
│   └── data/
│       └── src/main/java/.../data/
│           ├── local/
│           │   ├── database/
│           │   └── storage/
│           ├── epub/
│           │   ├── parser/
│           │   ├── extractor/
│           │   └── model/
│           ├── repository/
│           └── di/
```

## Proximas Fases

| Fase | Descricao |
|------|-----------|
| **Fase 2** | TTS offline: Sherpa-ONNX + Kokoro pt-BR + Media3 streaming |
| **Fase 3** | Cache de audio, otimizacoes, settings |
| **Fase 4** | Polish, testes, release |

## Licenca

MIT License - Livre para uso e modificacao.
