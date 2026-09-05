description = "quint-connect JUnit 5 extension (@QuintRun / @QuintTest)"

dependencies {
    api(project(":quint-connect-core"))
    api(platform(libs.junit.bom))
    api(libs.junit.jupiter)
    testImplementation(libs.junit.platform.testkit)
}

sourceSets {
    test {
        // Share the ITF fixtures and the tictactoe spec with the core module.
        resources.srcDir(rootProject.file("core/src/test/resources"))
    }
}

tasks.withType<Test>().configureEach {
    // Fixture classes exercised through EngineTestKit; not to be run directly.
    (options as JUnitPlatformOptions).excludeTags("fixture")
}
