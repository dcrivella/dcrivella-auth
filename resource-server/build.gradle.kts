import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

plugins {
    java
    id("org.springframework.boot") version "3.5.6"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.graalvm.buildtools.native") version "0.11.0"
    id("org.asciidoctor.jvm.convert") version "4.0.5"
}

group = "cloud.dcrivella"
version = "0.0.1-SNAPSHOT"
description = "resource-server"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(24)
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

tasks.named<BootBuildImage>("bootBuildImage") {
    builder.set("paketobuildpacks/builder-jammy-buildpackless-tiny")
    buildpacks.set(
        listOf(
            "paketobuildpacks/java",
            "paketobuildpacks/java-native-image"
        )
    )

    // optional:
    imageName.set("dcrivella/resource-server:${project.version}")
    environment.set(
        mapOf(
            "BP_JVM_VERSION" to "24",
            "BP_NATIVE_IMAGE" to "true" // or run: SPRING_PROFILES_ACTIVE=docker ./gradlew bootBuildImage -PBP_NATIVE_IMAGE=true
        )
    )
}
