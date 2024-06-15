plugins {
    kotlin("jvm") version "1.9.23"
}

group = "ink.awning.easy.evaluate"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    val ktorVersion = "2.3.11"
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("com.fleeksoft.ksoup:ksoup:0.1.2")
    implementation("net.sourceforge.tess4j:tess4j:5.11.0")
    implementation("org.slf4j:slf4j-log4j12:2.0.13")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}