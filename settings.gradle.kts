plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "escape-reborn"

// Локальная сборка платформы: даёт зависимость ru.kiviuly.mg:mg-api из соседнего репо
// без публикации (dependency substitution по group:name).
includeBuild("../kiviuly-mg-core")
