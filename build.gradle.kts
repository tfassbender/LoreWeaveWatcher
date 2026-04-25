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

// --- Manual-testing convenience tasks ---------------------------------------
// `cloneTestVault` checks out the public LoreWeaveTestVault into ./test-vault,
// or fast-forwards an existing clone. `installToTestVault` builds the shadow
// jar and drops it into <test-vault>/.loreweave/ so the watcher's
// auto-detection picks the test vault as its root.

val testVaultDir = layout.projectDirectory.dir("test-vault")
val testVaultRepo = "https://github.com/tfassbender/LoreWeaveTestVault.git"

val cloneTestVault by tasks.registering {
    group = "test vault"
    description = "Clone or fast-forward the LoreWeaveTestVault into ./test-vault for local manual testing."
    doLast {
        val dir = testVaultDir.asFile
        if (dir.resolve(".git").isDirectory) {
            exec {
                workingDir = dir
                commandLine("git", "pull", "--ff-only")
            }
        } else {
            dir.parentFile.mkdirs()
            exec {
                commandLine("git", "clone", testVaultRepo, dir.absolutePath)
            }
        }
    }
}

val installToTestVault by tasks.registering(Copy::class) {
    group = "test vault"
    description = "Build the shadow jar and copy it into ./test-vault/.loreweave/ for manual testing."
    dependsOn(cloneTestVault, tasks.shadowJar)
    from(tasks.shadowJar.flatMap { it.archiveFile })
    into(testVaultDir.dir(".loreweave"))
}
