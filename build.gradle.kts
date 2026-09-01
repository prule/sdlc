import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    java
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "8.10.1"
    id("org.openapi.generator") version "7.14.0"
}

group = "com.acme"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

val openApiOutputDir = layout.buildDirectory.dir("generated").get().asFile

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    runtimeOnly("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    compileOnly("org.openapitools:jackson-databind-nullable:0.2.6")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:1.20.4")
    }
}

sourceSets {
    main {
        java {
            srcDir("$openApiOutputDir/src/main/java")
        }
    }
}

openApiGenerate {
    generatorName.set("spring")
    inputSpec.set("$rootDir/src/main/resources/openapi/openapi.yaml")
    outputDir.set(openApiOutputDir.path)
    apiPackage.set("com.acme.generated.api")
    modelPackage.set("com.acme.generated.model")
    invokerPackage.set("com.acme.generated.invoker")
    configOptions.set(
        mapOf(
            "interfaceOnly" to "true",
            "useSpringBoot3" to "true",
            "useTags" to "true",
            "skipDefaultInterface" to "true",
            "documentationProvider" to "none",
        )
    )
    globalProperties.set(
        mapOf(
            "models" to "",
            "apis" to "",
        )
    )
}

tasks.withType<GenerateTask> {
    outputs.cacheIf { true }
}

tasks.named("compileJava") {
    dependsOn("openApiGenerate")
}

spotless {
    java {
        googleJavaFormat("1.36.1")
        target("src/**/*.java")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.named("check") {
    dependsOn("spotlessCheck")
}

tasks.register<Copy>("installGitHooks") {
    description = "Installs the repo-managed git hooks into .git/hooks"
    group = "build setup"
    from("$rootDir/git-hooks/pre-commit")
    into("$rootDir/.git/hooks")
    doLast {
        val hook = file("$rootDir/.git/hooks/pre-commit")
        if (hook.exists()) {
            hook.setExecutable(true)
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
