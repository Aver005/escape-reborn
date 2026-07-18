plugins {
    java
}

group = "me.aver005"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.74-stable")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveBaseName.set("EscapeReborn")
}

// Сборка + копирование в тестовый сервер: ./gradlew deploy
tasks.register<Copy>("deploy") {
    dependsOn(tasks.jar)
    from(tasks.jar.map { it.archiveFile })
    into("E:/Servers/escape/plugins")
}
