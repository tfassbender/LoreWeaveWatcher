plugins {
    java
    application
    id("com.gradleup.shadow") version "8.3.5"
}

group = "com.tfassbender.loreweave.watch"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.commonmark:commonmark:0.24.0")
    implementation("org.snakeyaml:snakeyaml-engine:2.8")

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.26.3")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    mainClass.set("com.tfassbender.loreweave.watch.cli.Main")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveBaseName.set("lore-weave-watch")
    archiveClassifier.set("")
    archiveVersion.set("")
}
