// The playground: the editor's files (written to out/playground/src by
// server.py) and a main() that runs their App().
@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    wasmJs {
        outputModuleName = "playground"
        browser()
        binaries.executable()
    }
    sourceSets {
        val wasmJsMain by getting {
            kotlin.srcDir(rootDir.resolve("out/playground/src"))
            dependencies {
                implementation(project(":toolkit"))
            }
        }
    }
}
