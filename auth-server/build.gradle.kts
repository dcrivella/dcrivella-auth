import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.java
import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.graalvm.buildtools)
    alias(libs.plugins.asciidoctor.jvm.convert)
}

version = (project.findProperty("version") as String?) ?: System.getenv("AUTH_SERVER_IMAGE_TAG") ?: "DEV-SNAPSHOT"
description = "auth-server"

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

extra["snippetsDir"] = file("build/generated-snippets")

dependencies {
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.oauth2.authorization.server)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    compileOnly(libs.lombok)
    developmentOnly(libs.spring.boot.devtools)
    runtimeOnly(libs.postgresql)
    annotationProcessor(libs.lombok)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.spring.restdocs.mockmvc)
    testImplementation(libs.spring.security.test)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.test {
    outputs.dir(project.extra["snippetsDir"]!!)
}

tasks.asciidoctor {
    inputs.dir(project.extra["snippetsDir"]!!)
    dependsOn(tasks.test)
}

tasks.processResources {
    filesMatching(listOf("application.yml", "application.yaml", "application-*.yml", "application-*.yaml")) {
        filter(
            mapOf("tokens" to mapOf("projectVersion" to project.version.toString())),
            ReplaceTokens::class.java,
        )
    }
}

tasks.named<BootBuildImage>("bootBuildImage") {
    // Native = default (true)
    val native = (project.findProperty("BP_NATIVE_IMAGE") as String?)?.toBoolean() ?: true

    if (native) {
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
            "BP_NATIVE_IMAGE" to native.toString(),
        ),
    )

    val image = project.findProperty("spring-boot.build-image.imageName") as String?
    imageName.set(image ?: "dcrivella/auth-server:${project.version}")
}
