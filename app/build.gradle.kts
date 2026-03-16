plugins {
    alias(libs.plugins.kotlin.jvm)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20"
    id("io.ktor.plugin") version "3.3.0"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:3.3.0")
    implementation("io.ktor:ktor-server-netty-jvm:3.3.0")
    implementation("io.ktor:ktor-server-call-logging-jvm:3.3.0")
    implementation("io.ktor:ktor-server-status-pages-jvm:3.3.0")
    implementation("io.ktor:ktor-server-config-yaml-jvm:3.3.0")
    implementation("io.ktor:ktor-server-sessions-jvm:3.3.0")
    implementation("io.ktor:ktor-server-auth-jvm:3.3.0")
    implementation("io.ktor:ktor-server-pebble-jvm:3.3.0")

    implementation("io.pebbletemplates:pebble:4.1.1")

    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)

    implementation(libs.postgres)

    implementation("ch.qos.logback:logback-classic:1.5.22")

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.ktor:ktor-server-test-host-jvm:3.3.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:2.2.20")
    testImplementation("com.h2database:h2:2.2.224")
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("io.ktor:ktor-client-content-negotiation")

    implementation("org.mindrot:jbcrypt:0.4")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass.set("io.ktor.server.netty.EngineMain")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}