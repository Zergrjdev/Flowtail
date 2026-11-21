plugins {
    application
}

group = "dev.flowtail"
version = "0.1.0"

repositories {
    mavenCentral()
    maven("https://repo.opencollab.dev/main/")
}

dependencies {
    implementation("org.geysermc.mcprotocollib:protocol:26.3-4")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.17")
}

application {
    mainClass = "dev.flowtail.bench.BotSwarm"
}
