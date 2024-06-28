plugins {
    java
    `java-library`
    `maven-publish`
    signing
}

group = "io.github.gaming32"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.dmlloyd:jdk-classfile-preview:22.4")

    testImplementation("com.nitorcreations:javaxdelta:1.5")
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    repositories {
        fun maven(name: String, releases: String, snapshots: String) {
            maven {
                this.name = name
                url = uri(if (version.toString().endsWith("-SNAPSHOT")) snapshots else releases)
                credentials(PasswordCredentials::class)
                authentication {
                    create<BasicAuthentication>("basic")
                }
            }
        }

        maven(
            "gaming32",
            "https://maven.jemnetworks.com/releases",
            "https://maven.jemnetworks.com/snapshots"
        )
        maven(
            "central",
            "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2",
            "https://s01.oss.sonatype.org/content/repositories/snapshots"
        )
    }

    publications {
        create<MavenPublication>("maven") {
            artifactId = project.name
            groupId = project.group.toString()
            version = project.version.toString()

            from(components["java"])

            pom {
                name = project.name
                description = "A library allowing you to create Python-style generators in Java"
                url = "https://github.com/Gaming32/j21-generators"
                licenses {
                    license {
                        name = "MIT License"
                        url = "https://github.com/Gaming32/j21-generators/blob/main/LICENSE"
                    }
                }
                developers {
                    developer {
                        id = "Gaming32"
                    }
                }
                scm {
                    connection = "scm:git:git://github.com/Gaming32/j21-generators.git"
                    developerConnection = connection
                    url = "https://github.com/Gaming32/j21-generators"
                }
            }
        }
    }
}

signing {
    isRequired = false
    useGpgCmd()
    sign(configurations.archives.get())
    sign(publishing.publications["maven"])
}

tasks.withType<PublishToMavenRepository>().forEach { it.dependsOn(tasks.withType<Sign>()) }
