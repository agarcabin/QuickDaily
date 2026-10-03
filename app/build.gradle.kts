plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.quickdaily"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.quickdaily"
        minSdk = 26
        targetSdk = 35
        versionCode = 81
        versionName = "2.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // 绂荤嚎鏋勫缓鐜璺宠繃 lint vital 浠诲姟锛堥伩鍏嶈仈缃戜笅杞?SDK 鍏冩暟鎹級
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    signingConfigs {
        // 浣跨敤 debug keystore 浣滀负 release 鍏滃簳绛惧悕锛屼究浜庢湰鍦版墦鍖呴獙璇侊紱
        // 姝ｅ紡涓婃灦鏃跺簲鏇挎崲涓轰笓鐢?release keystore锛堜笉瑕佹妸鐪熷疄 keystore 瀵嗙爜纭紪鐮佽繘浠撳簱锛?
        getByName("debug").apply {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Activity & Lifecycle
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-service:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.documentfile:documentfile:1.0.1")

    // JSON parsing (for .obsidian config)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // Coroutines
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}

private data class LocalizationEntry(
    val kind: String,
    val values: List<String>,
) {
    val formatTokens: List<String>
        get() = values.flatMap { value ->
            Regex("%(?:[0-9]+\\$)?[a-zA-Z]").findAll(value).map { it.value }.toList()
        }.sorted()

    val escapedNewlines: List<Int>
        get() = values.map { value -> Regex("\\\\n").findAll(value).count() }
}

private fun parseLocalizationDirectory(directory: java.io.File): Map<String, LocalizationEntry> {
    val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    }
    val result = linkedMapOf<String, LocalizationEntry>()
    directory.listFiles { file -> file.extension == "xml" }
        ?.sortedBy { it.name }
        ?.forEach { file ->
            val document = factory.newDocumentBuilder().parse(file)
            val root = document.documentElement
            for (index in 0 until root.childNodes.length) {
                val node = root.childNodes.item(index)
                if (node !is org.w3c.dom.Element) continue
                when (node.tagName) {
                    "string" -> {
                        val name = node.getAttribute("name")
                        if (name.isNotBlank()) {
                            result[name] = LocalizationEntry("string", listOf(node.textContent ?: ""))
                        }
                    }
                    "plurals" -> {
                        val name = node.getAttribute("name")
                        if (name.isNotBlank()) {
                            val items = (0 until node.childNodes.length)
                                .map { node.childNodes.item(it) }
                                .filterIsInstance<org.w3c.dom.Element>()
                                .filter { it.tagName == "item" }
                                .sortedBy { it.getAttribute("quantity") }
                                .map { "${it.getAttribute("quantity")}:${it.textContent ?: ""}" }
                            result[name] = LocalizationEntry("plurals", items)
                        }
                    }
                }
            }
        }
    return result
}

val verifyLocalization by tasks.registering {
    group = "verification"
    description = "Checks Android localization resource structure and reports missing optional translations."
    doLast {
        val resDir = file("src/main/res")
        val base = parseLocalizationDirectory(resDir.resolve("values"))
        check(base.isNotEmpty()) { "No base localization resources found" }
        val report = layout.buildDirectory.file("reports/localization/missing-translations.txt").get().asFile
        report.parentFile.mkdirs()
        val missing = mutableListOf<String>()
        val errors = mutableListOf<String>()
        resDir.listFiles { file ->
            file.isDirectory && Regex("values-[a-z]{2,3}(-r[A-Z]{2})?$").matches(file.name)
        }
            ?.sortedBy { it.name }
            ?.forEach { localeDir ->
                val locale = localeDir.name.removePrefix("values-")
                val translated = parseLocalizationDirectory(localeDir)
                base.forEach baseLoop@{ (key, expected) ->
                    val actual = translated[key]
                    if (actual == null) {
                        missing += "$locale:$key"
                        return@baseLoop
                    }
                    if (actual.kind != expected.kind) {
                        errors += "$locale:$key has ${actual.kind}, expected ${expected.kind}"
                    }
                    if (actual.formatTokens != expected.formatTokens) {
                        errors += "$locale:$key format tokens ${actual.formatTokens} != ${expected.formatTokens}"
                    }
                    if (actual.escapedNewlines != expected.escapedNewlines) {
                        errors += "$locale:$key escaped newline counts ${actual.escapedNewlines} != ${expected.escapedNewlines}"
                    }
                }
            }
        report.writeText(
            buildString {
                appendLine("Missing optional translations")
                missing.sorted().forEach(::appendLine)
            },
            Charsets.UTF_8,
        )
        if (errors.isNotEmpty()) {
            throw GradleException(errors.joinToString("\n"))
        }
        logger.lifecycle("Localization verification passed; missing optional translations: ${missing.size}")
    }
}

val verifyLocalizationStrict by tasks.registering {
    group = "verification"
    description = "Requires every base resource to have a structurally matching English value."
    dependsOn(verifyLocalization)
    doLast {
        val resDir = file("src/main/res")
        val base = parseLocalizationDirectory(resDir.resolve("values"))
        val english = parseLocalizationDirectory(resDir.resolve("values-en"))
        val allowSame = setOf("app_name", "qd_language_zh", "qd_language_en")
        val missing = base.keys.filterNot(english::containsKey)
        val copied = base.keys.filter { key ->
            key !in allowSame && base[key]?.values == english[key]?.values
        }
        check(missing.isEmpty()) { "Missing English resources: ${missing.joinToString()}" }
        check(copied.isEmpty()) { "English resources still equal Chinese: ${copied.joinToString()}" }
        logger.lifecycle("Strict English localization verification passed")
    }
}

val verifyLocalizationSource by tasks.registering {
    group = "verification"
    description = "Rejects new CJK user-visible literals in protected Kotlin entry points."
    dependsOn(verifyLocalization)
    doLast {
        val sourceRoot = file("src/main/java")
        val manifest = file("src/main/localization/protected_sources.txt")
        check(manifest.isFile) { "Missing localization protected source manifest: ${manifest.path}" }
        val protectedSources = manifest.readLines(Charsets.UTF_8)
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
        val literalPattern = Regex("\\\"(?:\\\\.|[^\\\"\\\\])*[\\u4e00-\\u9fff](?:\\\\.|[^\\\"\\\\])*\\\"")
        val findings = mutableListOf<String>()
        val allowedLinePatterns = listOf(
            "// localization-legacy",
            "android.util.Log.",
            "BetaLogger.",
            "IllegalStateException(",
            "DateTimeFormatter.",
            "DisplayText(",
        )
        protectedSources.forEach { relativePath ->
            val source = sourceRoot.resolve(relativePath)
            check(source.isFile) { "Protected localization source does not exist: ${source.path}" }
            var inLegacyBlock = false
            source.readLines(Charsets.UTF_8).forEachIndexed { index, line ->
                if (line.contains("localization-legacy-begin")) {
                    inLegacyBlock = true
                    return@forEachIndexed
                }
                if (inLegacyBlock) {
                    if (line.contains("localization-legacy-end")) inLegacyBlock = false
                    return@forEachIndexed
                }
                if (allowedLinePatterns.any(line::contains)) return@forEachIndexed
                literalPattern.findAll(line).forEach { match ->
                    findings += "${relativePath}:${index + 1}: ${match.value}"
                }
            }
        }
        val report = layout.buildDirectory.file("reports/localization/hardcoded-user-copy.txt").get().asFile
        report.parentFile.mkdirs()
        report.writeText(
            buildString {
                appendLine("Protected-source localization findings")
                if (findings.isEmpty()) appendLine("none") else findings.sorted().forEach(::appendLine)
            },
            Charsets.UTF_8,
        )
        check(findings.isEmpty()) {
            "Hardcoded CJK user copy found in protected sources. Use strings.xml/UiText or add an explicit localization-legacy marker for data/compatibility text:\n" +
                findings.joinToString("\n")
        }
        logger.lifecycle("Protected-source localization verification passed; findings: 0")
    }
}

tasks.named("check") {
    dependsOn(verifyLocalization, verifyLocalizationSource)
}

tasks.named("preBuild") {
    dependsOn(verifyLocalization, verifyLocalizationSource)
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    dependsOn(verifyLocalizationStrict, verifyLocalizationSource)
}


