plugins {
    kotlin("multiplatform") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
}

// The embedded-compose checkout whose sources are compiled here (EC_DIR,
// default: next to this directory). Nothing in it is changed.
val ecDir = file(System.getenv("EC_DIR") ?: "../embedded-compose").canonicalFile
require(ecDir.resolve("core/hal/ec_hal.h").isFile) { "no embedded-compose at $ecDir (set EC_DIR)" }
allprojects {
    extra["ecDir"] = ecDir
    // All build output under out/.
    layout.buildDirectory.set(rootDir.resolve("out/gradle" + path.replace(':', '/')))
}
