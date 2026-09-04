plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "quint-connect"

include("core", "junit", "examples")

project(":core").name = "quint-connect-core"
project(":junit").name = "quint-connect-junit"
project(":examples").name = "quint-connect-examples"
