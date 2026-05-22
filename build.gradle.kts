plugins {
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.graalvm.buildtools) apply false
    alias(libs.plugins.asciidoctor.jvm.convert) apply false
}

allprojects {
    group = "cloud.dcrivella"

    repositories {
        mavenCentral()
    }
}
