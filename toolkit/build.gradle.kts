// embedded-compose's toolkit (core/ui) for the browser, compiled from that
// checkout as it is. Only two things are this project's own:
//  - hal/: ec_hal.h as Kotlin functions that call the page (site/hal.js);
//  - ui/WebRuntime.kt: runApp(), whose device version blocks on input.
// Runtime.kt is used without its runApp(), cut out at build time.
@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
}

val ecDir: File by extra
val toolkit = ecDir.resolve("core/ui/src/linuxArm64Main/kotlin")

val runtimeWithoutRunApp by tasks.registering {
    val src = toolkit.resolve("ui/Runtime.kt")
    val out = layout.buildDirectory.dir("generated/ec")
    inputs.file(src)
    outputs.dir(out)
    doLast {
        var text = src.readText()
        // Imports only runApp() used: cinterop and the blocking input poll.
        text = text.lines().filterNot {
            it.startsWith("import kotlinx.cinterop.") || it == "import kotlin.system.exitProcess" ||
                it == "import hal.ec_event" || it == "import hal.ec_input_poll"
        }.joinToString("\n")
        val start = text.indexOf("\nfun runApp(")
        if (start < 0) throw GradleException("$src: no runApp() to replace")
        var i = text.indexOf('{', start)
        var depth = 0
        do {
            when (text[i]) { '{' -> depth++; '}' -> depth-- }
            i++
        } while (depth > 0)
        val doc = text.lastIndexOf("/**", start).takeIf { it >= 0 && text.substring(it, start).trim().endsWith("*/") } ?: start
        text = text.substring(0, doc) + "// runApp(): see WebRuntime.kt\n" + text.substring(i)
        val dst = out.get().file("ui/EcRuntime.kt").asFile
        dst.parentFile.mkdirs()
        dst.writeText("// Generated from $src without runApp().\n$text")
    }
}

kotlin {
    wasmJs {
        browser()
    }
    sourceSets {
        val wasmJsMain by getting {
            kotlin.srcDir(toolkit)
            kotlin.srcDir(runtimeWithoutRunApp)
            kotlin.exclude("ui/Runtime.kt")
            dependencies {
                api("androidx.compose.runtime:runtime:1.10.5")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            }
        }
    }
}
