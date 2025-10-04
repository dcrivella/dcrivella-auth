import org.springframework.boot.gradle.tasks.bundling.BootBuildImage
import org.apache.tools.ant.filters.ReplaceTokens

plugins {
    java
    id("org.springframework.boot") version "3.5.6"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.graalvm.buildtools.native") version "0.11.1"
    id("org.asciidoctor.jvm.convert") version "4.0.5"
}

group = "cloud.dcrivella"
version = (project.findProperty("version") as String?) ?: System.getenv("RESOURCE_SERVER_IMAGE_TAG") ?: "DEV-SNAPSHOT"
description = "resource-server"

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

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-web")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.processResources {
    filesMatching(listOf("application.yml", "application.yaml", "application-*.yml", "application-*.yaml")) {
        filter(
            mapOf("tokens" to mapOf("projectVersion" to project.version.toString())),
            ReplaceTokens::class.java
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
            "BP_NATIVE_IMAGE" to native.toString()
        )
    )

    val image = project.findProperty("spring-boot.build-image.imageName") as String?
    imageName.set(image ?: "dcrivella/auth-server:${project.version}")
}
