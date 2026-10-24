plugins {
    id("java")
}

group = "aegis.lumi"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    maven("https://repo.opencollab.dev/maven-releases/")
    maven("https://repo.opencollab.dev/maven-snapshots/")
}

val lumiFile = listOf(
    file("d:/AProject/survival/Lumi.jar"),
    file("../../survival/Lumi.jar"),
    file("../survival/Lumi.jar"),
    file("libs/Lumi.jar")
).firstOrNull { it.exists() }

dependencies {
    if (lumiFile != null) {
        compileOnly(files(lumiFile))
    } else {
        compileOnly(files("libs/Lumi.jar"))
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters"))
}

tasks.jar {
    archiveFileName.set("Aegis-LUMI.jar")

    val deployDir = listOf(
        file("d:/AProject/survival/plugins"),
        file("../../survival/plugins"),
        file("../survival/plugins")
    ).firstOrNull { it.exists() && it.isDirectory }

    if (deployDir != null) {
        doLast {
            copy {
                from(archiveFile)
                into(deployDir)
            }
            println("[Build] Aegis-LUMI.jar compiled and deployed to ${deployDir.path}")
        }
    }
}
