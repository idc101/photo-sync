plugins {
    kotlin("jvm") version "1.4.32"
}

group = "net.cardnell"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.drewnoakes:metadata-extractor:2.16.0")
}
