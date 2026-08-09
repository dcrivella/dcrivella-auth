plugins {
    java
    idea
}

description = "Black-box system tests"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

val systemTestSourceSet =
    sourceSets.create("systemTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }

configurations[systemTestSourceSet.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[systemTestSourceSet.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

idea {
    module {
        testSources.from(systemTestSourceSet.java.srcDirs)
        testResources.from(systemTestSourceSet.resources.srcDirs)
    }
}

dependencies {
    add(systemTestSourceSet.implementationConfigurationName, platform(libs.cucumber.bom))
    add(systemTestSourceSet.implementationConfigurationName, platform(libs.spring.boot.dependencies))
    add(systemTestSourceSet.implementationConfigurationName, libs.cucumber.java)
    add(systemTestSourceSet.implementationConfigurationName, libs.cucumber.junit.platform.engine)
    add(systemTestSourceSet.implementationConfigurationName, libs.junit.platform.suite)
    add(systemTestSourceSet.implementationConfigurationName, libs.spring.boot.starter.test)
    add(systemTestSourceSet.runtimeOnlyConfigurationName, libs.junit.platform.launcher)
}

fun Test.configureEndpoints() {
    outputs.upToDateWhen { false }
    systemProperty(
        "systemTest.authServerUrl",
        providers.gradleProperty("systemTest.authServerUrl").getOrElse("http://localhost:9000"),
    )
    systemProperty(
        "systemTest.clientServerUrl",
        providers.gradleProperty("systemTest.clientServerUrl").getOrElse("http://localhost:8080"),
    )
    systemProperty(
        "systemTest.resourceServerUrl",
        providers.gradleProperty("systemTest.resourceServerUrl").getOrElse("http://localhost:8081"),
    )
    systemProperty(
        "systemTest.issuer",
        providers.gradleProperty("systemTest.issuer").getOrElse("http://host.docker.internal:9000"),
    )
}

val systemTestPreflight =
    tasks.register<Test>("systemTestPreflight") {
        description = "Checks system-test configuration and service reachability without changing the runtime."
        group = "verification"
        testClassesDirs = systemTestSourceSet.output.classesDirs
        classpath = systemTestSourceSet.runtimeClasspath
        filter {
            includeTestsMatching("cloud.dcrivella.systemtests.SystemTestPreflightTest")
        }
        useJUnitPlatform {
            includeTags("preflight")
        }
        configureEndpoints()
    }

val smokeTest =
    tasks.register<Test>("smokeTest") {
        description = "Verifies essential runtime capabilities against an already-running stack."
        group = "verification"
        testClassesDirs = systemTestSourceSet.output.classesDirs
        classpath = systemTestSourceSet.runtimeClasspath
        filter {
            includeTestsMatching("cloud.dcrivella.systemtests.SmokeSystemTest")
        }
        useJUnitPlatform {
            includeTags("smoke")
        }
        configureEndpoints()
        dependsOn(systemTestPreflight)
    }

tasks.register<Test>("e2eTest") {
    description = "Exercises real token issuance and protected API access across an already-running stack."
    group = "verification"
    testClassesDirs = systemTestSourceSet.output.classesDirs
    classpath = systemTestSourceSet.runtimeClasspath
    filter {
        includeTestsMatching("cloud.dcrivella.systemtests.CucumberE2eSystemTest")
    }
    useJUnitPlatform {
        includeTags("e2e")
    }
    configureEndpoints()
    dependsOn(smokeTest)
}
