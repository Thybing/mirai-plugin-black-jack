plugins {
    val kotlinVersion = "1.8.0"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion

    id("com.github.gmazzo.buildconfig") version "3.1.0"
    id("net.mamoe.mirai-console") version "2.16.0"
}

group = "org.example"
version = "0.1.0"


repositories {
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots")
    mavenCentral()
}


buildConfig {
    className("BuildConstants")
    packageName("org.example.mirai.plugin")
    useKotlinOutput()

    buildConfigField("String", "VERSION", "\"${project.version}\"")
}

mirai {
    noTestCore = true
    setupConsoleTestRuntime {
        // 移除 mirai-core 依赖
        classpath = classpath.filter {
            !it.nameWithoutExtension.startsWith("mirai-core-jvm")
        }
    }
}

dependencies {
    val overflowVersion = "1.0.0.519-0d68f08-SNAPSHOT"
    compileOnly("top.mrxiaom.mirai:overflow-core-api:$overflowVersion")
    testConsoleRuntime("top.mrxiaom.mirai:overflow-core:$overflowVersion")
}
