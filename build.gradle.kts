plugins {
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.graalvm.buildtools) apply false
    alias(libs.plugins.asciidoctor.jvm.convert) apply false
    alias(libs.plugins.spotless)
}

spotless {
    encoding("UTF-8")

    java {
        target("*/src/**/*.java")
        eclipse().configFile("config/spotless/eclipse-formatter.xml")
        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlinGradle {
        target("*.gradle.kts", "*/*.gradle.kts")
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }

    format("misc") {
        target(
            ".gitignore",
            "**/*.json",
            "**/*.properties",
            "**/*.sql",
            "**/*.toml",
            "**/*.yaml",
            "**/*.yml",
        )
        targetExclude(".gradle/**", ".serena/**", "**/bin/**", "**/build/**")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

allprojects {
    group = "cloud.dcrivella"

    repositories {
        mavenCentral()
    }
}

subprojects {
    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(rootProject.tasks.named("spotlessCheck"))
    }
}
