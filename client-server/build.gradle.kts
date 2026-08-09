import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.java
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

plugins {
    java
    idea
    jacoco
    alias(libs.plugins.pitest)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

val nativeImageEnabled = providers.gradleProperty("BP_NATIVE_IMAGE").map { it.toBooleanStrict() }.getOrElse(true)
// Keep JVM image builds on the regular bootJar path; applying GraalVM would add AOT tasks to both modes.
if (nativeImageEnabled) {
    pluginManager.apply("org.graalvm.buildtools.native")
}

version =
    providers
        .gradleProperty("version")
        .orElse(providers.environmentVariable("CLIENT_SERVER_IMAGE_TAG"))
        .getOrElse("DEV-SNAPSHOT")
description = "client-server"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

val integrationTestSourceSet =
    sourceSets.create("integrationTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }

configurations[integrationTestSourceSet.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[integrationTestSourceSet.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

idea {
    module {
        testSources.from(integrationTestSourceSet.java.srcDirs)
        testResources.from(integrationTestSourceSet.resources.srcDirs)
    }
}

dependencies {
    implementation(libs.spring.boot.starter.oauth2.client)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.webflux)
    implementation(libs.spring.boot.starter.thymeleaf)
    developmentOnly(libs.spring.boot.devtools)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.security.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

val integrationTestTask =
    tasks.register<Test>("integrationTest") {
        description = "Runs the client server integration tests."
        group = "verification"
        testClassesDirs = integrationTestSourceSet.output.classesDirs
        classpath = integrationTestSourceSet.runtimeClasspath
        shouldRunAfter(tasks.test)
    }

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

pitest {
    pitestVersion =
        libs.versions.pitest.core
            .get()
    junit5PluginVersion =
        libs.versions.pitest.junit5
            .get()
    targetClasses =
        setOf(
            "cloud.dcrivella.clientserver.HomeController",
            "cloud.dcrivella.clientserver.WebClientResourceServerClient",
        )
    targetTests = setOf("cloud.dcrivella.clientserver.*Test")
    testSourceSets = listOf(sourceSets.test.get(), integrationTestSourceSet)
    threads = 4
    mutators = setOf("DEFAULTS")
    outputFormats = setOf("HTML", "XML")
    timestampedReports = false
    exportLineCoverage = true
}

tasks.named<JacocoReport>("jacocoTestReport") {
    description = "Generates combined unit and integration test coverage."
    dependsOn(tasks.test, integrationTestTask)
    executionData.setFrom(
        fileTree(layout.buildDirectory.dir("jacoco")) {
            include("test.exec", "integrationTest.exec")
        },
    )
    reports {
        html.required = true
        xml.required = true
        csv.required = false
    }
}

tasks.processResources {
    val applicationVersion = project.version.toString()
    inputs.property("applicationVersion", applicationVersion)
    filesMatching(listOf("application.yml", "application.yaml", "application-*.yml", "application-*.yaml")) {
        filter(
            mapOf("tokens" to mapOf("projectVersion" to applicationVersion)),
            ReplaceTokens::class.java,
        )
    }
}

tasks.named<BootBuildImage>("bootBuildImage") {
    if (nativeImageEnabled) {
        // Native image build
        builder.set("paketobuildpacks/builder-jammy-buildpackless-tiny")
        buildpacks.set(listOf("paketobuildpacks/java", "paketobuildpacks/java-native-image"))
    } else {
        // JVM image build
        builder.set("paketobuildpacks/builder-jammy-tiny")
        buildpacks.set(listOf("paketobuildpacks/java")) // no native-image buildpack needed
    }

    environment.set(
        mapOf(
            "BP_JVM_VERSION" to "25",
            "BP_NATIVE_IMAGE" to nativeImageEnabled.toString(),
        ),
    )

    val image = project.findProperty("spring-boot.build-image.imageName") as String?
    imageName.set(image ?: "dcrivella/client-server:${project.version}")
}
