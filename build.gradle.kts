plugins {
    java
}

group = "dev.sccl.pierdolchat"
version = providers.gradleProperty("releaseVersion").orElse("1.0.0-SNAPSHOT").get()

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://api.modrinth.com/maven") {
        content { includeGroup("maven.modrinth") }
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.253-stable")
    compileOnly("maven.modrinth:discordsrv:1.30.5")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveBaseName.set("pierdol-chat-paper")
    manifest.attributes["Implementation-Version"] = project.version
}
