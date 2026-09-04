description = "quint-connect JUnit 5 extension (@QuintRun / @QuintTest)"

dependencies {
    api(project(":quint-connect-core"))
    api(platform(libs.junit.bom))
    api(libs.junit.jupiter)
    testImplementation(libs.junit.platform.testkit)
}
