plugins {
	alias(libs.plugins.quarkus)
}

dependencies {
	implementation(enforcedPlatform(libs.quarkus.bom))

	implementation(libs.quarkus.rest)
	implementation(libs.quarkus.rest.jackson)
	implementation(libs.quarkus.config.yaml)
	implementation(libs.quarkus.vertx)
	implementation(libs.quarkus.undertow)
	implementation(libs.quarkus.redis.client)
	implementation(libs.quarkus.caffeine)

	implementation(project(":quarkus-jimmer"))

	runtimeOnly(libs.quarkus.jdbc.h2)

	implementation(libs.redisson.quarkus)

	annotationProcessor(libs.jimmer.apt)

	testImplementation(libs.quarkus.junit5)
	testImplementation(libs.rest.assured)
	testImplementation(libs.testcontainers)
	testImplementation(libs.testcontainers.junit)
}
