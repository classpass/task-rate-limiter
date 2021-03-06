import java.net.URI
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset

plugins {
    `java-library`
    `maven-publish`
    signing

    kotlin("jvm") version "1.5.31"

    id("org.jetbrains.dokka") version "1.5.30"
    id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
    id("net.researchgate.release") version "2.8.1"
    id("com.github.ben-manes.versions") version "0.39.0"
    id("org.jmailen.kotlinter") version "3.6.0"
    id("com.github.hierynomus.license") version "0.16.1"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.slf4j:slf4j-api:1.7.32")
    implementation("com.google.code.findbugs:jsr305:3.0.2")

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")

    testImplementation("org.slf4j:slf4j-simple:1.7.32")
}

java {
    withSourcesJar()
}

configure<nl.javadude.gradle.plugins.license.LicenseExtension> {
    header = rootProject.file("LICENSE-header")
}

tasks {
    // enable ${year} substitution
    OffsetDateTime.now(ZoneOffset.UTC).let { now ->
        withType<com.hierynomus.gradle.license.tasks.LicenseFormat> {
            extra["year"] = now.year.toString()
        }
        withType<com.hierynomus.gradle.license.tasks.LicenseCheck> {
            extra["year"] = now.year.toString()
        }
    }

    test {
        useJUnitPlatform()
    }

    compileKotlin {
        kotlinOptions {
            jvmTarget = "1.8"
        }
    }

    compileTestKotlin {
        kotlinOptions {
            jvmTarget = "1.8"
        }
    }

    register<Jar>("docJar") {
        from(project.tasks["dokkaHtml"])
        archiveClassifier.set("javadoc")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

configure<org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension> {
    // be picky about public, inferred function return types, etc
    explicitApi()
}

group = "com.classpass.oss.task-rate-limiter"

configure<PublishingExtension> {
    publications {
        register<MavenPublication>("sonatype") {
            from(components["java"])
            artifact(tasks["docJar"])
            // sonatype required pom elements
            pom {
                name.set("${project.group}:${project.name}")
                description.set(name)
                url.set("https://github.com/classpass/task-rate-limiter")
                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.html")
                    }
                }
                developers {
                    developer {
                        id.set("dinomite")
                        name.set("Drew Stephens")
                        email.set("drew.stephens@classpass.com")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/classpass/task-rate-limiter")
                    developerConnection.set("scm:git:https://github.com/classpass/task-rate-limiter.git")
                    url.set("https://github.com/classpass/task-rate-limiter")
                }
            }
        }
    }

    // A safe throw-away place to publish to:
    // ./gradlew publishSonatypePublicationToLocalDebugRepository -Pversion=foo
    repositories {
        maven {
            name = "localDebug"
            url = URI.create("file:///${project.buildDir}/repos/localDebug")
        }
    }
}

// don't barf for devs without signing set up
if (project.hasProperty("signing.gnupg.keyName")) {
    configure<SigningExtension> {
        useGpgCmd()
        sign(project.extensions.getByType<PublishingExtension>().publications["sonatype"])
    }
}

// releasing should publish
rootProject.tasks.afterReleaseBuild {
    dependsOn(provider { project.tasks.named("publishToSonatype") })
}

nexusPublishing {
    repositories {
        sonatype {
            // sonatypeUsername and sonatypePassword properties are used automatically
            stagingProfileId.set("1f02cf06b7d4cd") // com.classpass.oss
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
        }
    }
    // these are not strictly required. The default timeouts are set to 1 minute. But Sonatype can be really slow.
    // If you get the error "java.net.SocketTimeoutException: timeout", these lines will help.
    connectTimeout.set(Duration.ofMinutes(3))
    clientTimeout.set(Duration.ofMinutes(3))
}

release {
    // work around lack of proper kotlin DSL support
    (getProperty("git") as net.researchgate.release.GitAdapter.GitConfig).apply {
        requireBranch = "main"
    }
}
