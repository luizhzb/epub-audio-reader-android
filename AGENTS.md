# 📋 Instruções para Agentes — EPUB Audio Reader

> **ATENÇÃO:** Leia este arquivo antes de fazer qualquer alteração no projeto. Ele resume o estado atual, mudanças recentes e armadilhas comuns.

---

## ✅ Estado Atual (última atualização: 2026-06-23)

O projeto está **compilando e publicando releases automaticamente** via GitHub Actions.

- **Release mais recente:** `v0.5.1` → https://github.com/luizhzb/epub-audio-reader-android/releases/tag/v0.5.1
- **Branch principal:** `main`
- **Build local:** funciona ( APK em `app/build/outputs/apk/debug/app-debug.apk` )
- **CI/CD:** workflow `.github/workflows/build-apk.yml` builda, testa e publica o release em todo push para `main`

---

## 🧩 O que foi alterado recentemente

1. **Ajustes de UI e Gradle (commit `833d3cd`):**
   - `BookCard.kt` e `BookDetailScreen.kt`: smart cast de `book.coverImagePath`
   - `EmptyState.kt`: ícone `MenuBook` trocado para versão `AutoMirrored` (melhor para RTL)
   - `core/data/build.gradle.kts`: plugin Room e `schemaDirectory` configurados
   - `gradle/libs.versions.toml`: alias do plugin Room adicionado

2. **Correções de CI (commits seguintes):**
   - Removido `org.gradle.java.home` hardcoded do `gradle.properties` (quebrava o runner do GitHub Actions)
   - Workflow atualizado para usar **JDK 21** (antes usava 17 e causava falhas nos testes)
   - Adicionado upload de test reports no workflow para debug

3. **Refactor do `TextExtractor`:**
   - Implementação antiga usava `XmlPullParser` e falhava no teste de separação de parágrafos
   - Nova implementação usa **Jsoup**
   - Foi necessário adicionar a dependência `jsoup` em `core/data/build.gradle.kts` **E** no `gradle/libs.versions.toml` (versão + library)

---

## ⚠️ Cuidados Essenciais

### 1. Nunca commite `org.gradle.java.home` hardcoded
O arquivo `gradle.properties` NÃO deve conter caminhos locais de JDK como:

```properties
org.gradle.java.home=/usr/local/sdkman/candidates/java/21.0.10-ms
```

Isso quebra o CI porque o caminho não existe no runner do GitHub Actions. Deixe o Gradle usar o `JAVA_HOME` definido pelo workflow.

### 2. Sempre verifique o version catalog (`gradle/libs.versions.toml`)
Se você adicionar uma nova dependência em algum módulo, lembre-se de adicionar **tanto a versão quanto a definição da library** no `libs.versions.toml`.

Exemplo do Jsoup:

```toml
[versions]
jsoup = "1.17.2"

[libraries]
jsoup = { group = "org.jsoup", name = "jsoup", version.ref = "jsoup" }
```

E no módulo:

```kotlin
implementation(libs.jsoup)
```

### 3. Cuidado com arquivos de build no git status
O repositório tem muitos artefatos de build (`*/build`, `.transforms`, caches) que aparecem no `git status` porque foram versionados anteriormente, mesmo estando no `.gitignore`. **Não commite esses arquivos**. Faça commits seletivos com `git add <arquivo>`.

### 4. Rode os testes unitários antes de push
```bash
./gradlew testDebugUnitTest
```

Os testes rodam no CI; se falharem, o release não é publicado.

### 5. Cuidado ao alterar `TextExtractor.kt`
A separação de parágrafos é coberta pelo teste:

```kotlin
`extract separates paragraphs with blank lines`
```

O resultado esperado é:

```
Primeiro paragrafo.\n\nSegundo paragrafo.
```

Se alterar a lógica de extração de texto, garanta que esse teste continue passando.

### 6. O release é automático em push para `main`
Todo push para `main` dispara o workflow `Build APK + Release`. Ele:
- Builda o APK debug
- Roda os testes
- Sobrescreve o release `v0.5.1`

Não é necessário criar releases manualmente, mas **evite pushes quebrados** para `main`.

### 7. Não use `git push` sem confirmação do usuário
Git mutations (push, reset, rebase, etc.) devem ser feitas apenas quando o usuário pedir explicitamente.

---

## 🛠️ Comandos úteis

```bash
# Build local do APK
./gradlew :app:assembleDebug

# Rodar testes unitários
./gradlew testDebugUnitTest

# Limpar build
./gradlew clean

# Ver status seletivo (ignorar artefatos de build)
git status --short
```

---

## 📁 Arquivos-chave

- `gradle.properties` → configurações do Gradle (sem caminhos locais!)
- `gradle/libs.versions.toml` → catálogo de dependências
- `core/data/build.gradle.kts` → dependências do módulo data (inclui Room, Jsoup, Hilt)
- `.github/workflows/build-apk.yml` → CI/CD do release
- `core/data/src/main/java/.../TextExtractor.kt` → extração de texto dos EPUBs
