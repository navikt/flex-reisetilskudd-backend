val ktorVersion = "1.5.0"
val kotlinVersion = "1.4.21-2"
val coroutinesVersion = "1.4.2-native-mt"
val jacksonVersion = "2.12.1"
val kafkaEmbeddedVersion = "2.4.0"
val postgresEmbeddedVersion = "0.13.3"
val kluentVersion = "1.65"
val logbackVersion = "1.2.3"
val logstashEncoderVersion = "6.6"
val mockkVersion = "1.10.5"
val nimbusdsVersion = "7.5.1"
val prometheusVersion = "0.9.0"
val smCommonVersion = "1.c22544d"
val testContainerKafkaVersion = "1.15.1"
val kafkaVersion = "2.7.0"
val postgresVersion = "42.2.18"
val flywayVersion = "7.5.1"
val hikariVersion = "3.4.5"
val junitVersion = "5.7.0"

val githubUser: String by project
val githubPassword: String by project

plugins {
    application
    kotlin("jvm") version "1.4.21-2"
    id("com.github.johnrengelman.shadow") version "6.1.0"
}

group = "no.nav.syfo"
version = "1.0.0"

repositories {
    jcenter()
    maven { url = uri("https://packages.confluent.io/maven/") }
    maven {
        url = uri("https://maven.pkg.github.com/navikt/syfosm-common")
        credentials {
            username = githubUser
            password = githubPassword
        }
    }
}

buildscript {
    repositories {
        maven("https://plugins.gradle.org/m2/")
    }
    dependencies {
        classpath("org.jlleitschuh.gradle:ktlint-gradle:9.4.1")
    }
}

apply(plugin = "org.jlleitschuh.gradle.ktlint")

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-jackson:$ktorVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:$coroutinesVersion")
    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")

    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-auth-basic:$ktorVersion")
    implementation("io.ktor:ktor-client-jackson:$ktorVersion")
    implementation("io.ktor:ktor-jackson:$ktorVersion")
    implementation("io.ktor:ktor-auth:$ktorVersion")
    implementation("io.ktor:ktor-auth-jwt:$ktorVersion")

    implementation("no.nav.helse:syfosm-common-models:$smCommonVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.apache.kafka:kafka-clients:$kafkaVersion")

    implementation("com.fasterxml.jackson.module:jackson-module-jaxb-annotations:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    testImplementation("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("com.nimbusds:nimbus-jose-jwt:$nimbusdsVersion")
    testImplementation("org.testcontainers:kafka:$testContainerKafkaVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.eclipse.jetty")
    }

    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")

    testImplementation("no.nav:kafka-embedded-env:$kafkaEmbeddedVersion")
    testImplementation("com.opentable.components:otj-pg-embedded:$postgresEmbeddedVersion")

    testImplementation("io.ktor:ktor-server-tests:$ktorVersion")
}

application {
    mainClassName = "no.nav.helse.flex.BootstrapKt"
}

tasks {
    withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("PASSED", "FAILED", "SKIPPED")
        }
    }
    named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileTestKotlin") {
        kotlinOptions.jvmTarget = "14"
    }
}
