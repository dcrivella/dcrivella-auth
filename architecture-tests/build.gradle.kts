plugins {
    java
}

description = "Architecture tests"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation(project(":auth-server"))
    testImplementation(project(":client-server"))
    testImplementation(project(":resource-server"))
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.web)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.test {
    shouldRunAfter(
        ":auth-server:integrationTest",
        ":auth-server:test",
        ":client-server:integrationTest",
        ":client-server:test",
        ":resource-server:integrationTest",
        ":resource-server:archTest",
        ":resource-server:test",
    )
}
