import net.ltgt.gradle.errorprone.errorprone

plugins {
    alias(libs.plugins.spotless)
    alias(libs.plugins.errorprone) apply false
}

allprojects {
    group = "io.github.nsyee"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

spotless {
    kotlinGradle {
        target("*.gradle.kts", "*/*.gradle.kts")
        ktlint()
    }
    format("misc") {
        target("*.md", "docs/**/*.md", ".gitignore", ".editorconfig", ".github/**/*.yml")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// Typesafe catalog accessors do not resolve inside `subprojects {}`; capture them here.
val javaVersion = libs.versions.jdk.get()
val googleJavaFormatVersion = libs.versions.gjf.get()
val errorproneCore = libs.errorprone.core
val junitBom = libs.junit.bom
val junitJupiter = libs.junit.jupiter
val junitPlatformLauncher = libs.junit.platform.launcher

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "net.ltgt.errorprone")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(javaVersion)
        }
        withSourcesJar()
        withJavadocJar()
    }

    dependencies {
        "errorprone"(errorproneCore)
        "testImplementation"(platform(junitBom))
        "testImplementation"(junitJupiter)
        "testRuntimeOnly"(junitPlatformLauncher)
    }

    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            googleJavaFormat(googleJavaFormatVersion)
            formatAnnotations()
            removeUnusedImports()
            licenseHeaderFile(rootProject.file("gradle/license-header.txt"))
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
        options.errorprone {
            disableWarningsInGeneratedCode = true
            // Tests routinely trade strictness for brevity.
            if (name.contains("Test")) {
                disable("MissingSummary")
            }
        }
    }

    tasks.withType<Javadoc>().configureEach {
        (options as StandardJavadocDocletOptions).apply {
            encoding = "UTF-8"
            addBooleanOption("Xdoclint:all,-missing", true)
        }
    }

    // Cross-runtime parity tests (tag "parity") spawn the Rust reference
    // implementation via cargo; they only run in the dedicated `parityTest` task.
    tasks.named<Test>("test") {
        useJUnitPlatform {
            excludeTags("parity")
            // Integration tests that spawn the real Quint / Apalache CLI are tagged
            // "quint" / "apalache"; -PskipQuint / -PskipApalache exclude them for
            // environments without the CLI.
            if (project.hasProperty("skipQuint")) {
                excludeTags("quint")
            }
            if (project.hasProperty("skipApalache")) {
                excludeTags("apalache")
            }
        }
    }

    val testSourceSet = extensions.getByType<JavaPluginExtension>().sourceSets["test"]
    tasks.register<Test>("parityTest") {
        description = "Runs the @Tag(\"parity\") tests against the Rust quint-connect reference (needs quint + cargo)."
        group = "verification"
        testClassesDirs = testSourceSet.output.classesDirs
        classpath = testSourceSet.runtimeClasspath
        useJUnitPlatform { includeTags("parity") }
        systemProperty("quintconnect.parity.dir", rootProject.file("parity").absolutePath)
        outputs.upToDateWhen { false }
    }

    tasks.withType<Test>().configureEach {
        testLogging {
            events("failed", "skipped")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}
