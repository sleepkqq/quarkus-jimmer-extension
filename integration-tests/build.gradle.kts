plugins {
	alias(libs.plugins.quarkus)
}

configurations.all {
	exclude(group = "io.quarkus", module = "quarkus-devservices-h2")
	exclude(group = "io.quarkus", module = "quarkus-jdbc-h2-deployment")
	exclude(group = "com.h2database", module = "h2")
}

tasks.test {
	systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
	testLogging {
		showStandardStreams = true
	}
}

dependencies {
	implementation(enforcedPlatform(libs.quarkus.bom))

	implementation(libs.quarkus.rest)
	implementation(libs.quarkus.rest.jackson)
	implementation(libs.quarkus.config.yaml)
	implementation(libs.quarkus.vertx)
	implementation(libs.quarkus.redis.client)
	implementation(libs.quarkus.caffeine)

	implementation(project(":quarkus-jimmer"))

	runtimeOnly(libs.quarkus.jdbc.postgresql)

	annotationProcessor(libs.jimmer.apt)

	testImplementation(libs.quarkus.junit5)
	testImplementation(libs.rest.assured)
	testImplementation(libs.testcontainers)
	testImplementation(libs.testcontainers.junit)
	testImplementation(libs.testcontainers.postgresql)
}
