plugins {
    val kt = "1.5.0"

    kotlin("jvm") version kt
    kotlin("plugin.serialization") version kt
    id("net.mamoe.mirai-console") version "2.7-M1"
}

group = "net.im45.bot"
version = "1.0.0-dev-2"

repositories {
    maven("https://maven.aliyun.com/repository/public")
    mavenCentral()
}
