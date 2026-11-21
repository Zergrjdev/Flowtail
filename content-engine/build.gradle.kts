plugins {
    java
}

group = "dev.flowtail"
version = "0.1.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

repositories {
    mavenCentral()
}

val paperApi = fileTree("../upstream/Paper/paper-api/build/libs") {
    include("paper-api-*.jar")
    exclude("*-sources.jar", "*-javadoc.jar")
}
val paperRuntimeLibraries = fileTree("../run/upstream/libraries") {
    include("**/*.jar")
}

dependencies {
    compileOnly(paperApi)
    compileOnly(paperRuntimeLibraries)
    testImplementation(paperRuntimeLibraries)
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveBaseName.set("FlowTailContent")
}
