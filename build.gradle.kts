plugins {
	alias(libs.plugins.kotlin.jvm) apply false
	alias(libs.plugins.quarkus.extension) apply false
	alias(libs.plugins.quarkus) apply false
}

subprojects {
	group = "com.sleepkqq"
	version = findProperty("version") as String? ?: "0.0.0-SNAPSHOT"

	apply(plugin = "org.jetbrains.kotlin.jvm")

	repositories {
		mavenCentral()
		mavenLocal()
	}

	configure<JavaPluginExtension> {
		sourceCompatibility = JavaVersion.VERSION_21
		targetCompatibility = JavaVersion.VERSION_21
		withSourcesJar()
	}

	tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
		compilerOptions {
			jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
		}
	}

	tasks.withType<Test> {
		useJUnitPlatform()
		testLogging {
			events("passed", "skipped", "failed")
			showExceptions = true
			showCauses = true
			showStackTraces = true
			exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
		}
	}
}

configure(subprojects.filter { it.name != "integration-tests" }) {
	apply(plugin = "maven-publish")

	configure<PublishingExtension> {
		publications {
			create<MavenPublication>("maven") {
				from(components["java"])

				artifactId = project.name

				pom {
					name.set(project.name)
					description.set("Quarkus extension for Jimmer ORM")
					url.set("https://github.com/sleepkqq/quarkus-jimmer-extension")

					licenses {
						license {
							name.set("Apache License 2.0")
							url.set("https://www.apache.org/licenses/LICENSE-2.0")
						}
					}

					scm {
						url.set("https://github.com/sleepkqq/quarkus-jimmer-extension")
					}
				}
			}
		}
	}
}
