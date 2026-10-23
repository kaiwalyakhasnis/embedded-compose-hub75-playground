pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "embedded-compose-hub75-playground"
include(":toolkit")      // embedded-compose's core/ui, compiled for the browser
include(":playground")   // the code from the editor (out/playground/src)
