plugins {
    id("java-library")
    kotlin("jvm") version "2.0.21"
    kotlin("kapt") version "2.0.21"
}

group = "com.salt.autotagger"
version = "0.1.2"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
    implementation("org:jaudiotagger:2.0.3")

    compileOnly("com.github.Moriafly:spw-workshop-api:0.1.0-dev14")
    kapt("com.github.Moriafly:spw-workshop-api:0.1.0-dev14")
}

val pluginClass = "com.salt.autotagger.spw.SaltAutoTaggerPlugin"
val pluginId = "com.salt.autotagger"
val pluginVersion = project.version.toString()
val pluginProvider = "yhxglsa2016"
val pluginOpenSourceUrl = "https://github.com/yhxglsa2016/Salt-auto-tagger"

tasks.processResources {
    inputs.property("pluginVersion", pluginVersion)
    filteringCharset = "UTF-8"
    filesMatching(listOf("preference_config.json", "plugin_version.txt")) {
        filter { line -> line.replace("__PLUGIN_VERSION__", pluginVersion) }
    }
}

tasks.named<Jar>("jar") {
    archiveBaseName.set(pluginId)
    archiveVersion.set(pluginVersion)
    manifest {
        attributes["Plugin-Class"] = pluginClass
        attributes["Plugin-Id"] = pluginId
        attributes["Plugin-Name"] = "Salt Auto Tagger / 歌词自动补全"
        attributes["Plugin-Version"] = pluginVersion
        attributes["Plugin-Provider"] = pluginProvider
        attributes["Plugin-Open-Source-Url"] = pluginOpenSourceUrl
        attributes["Plugin-Description"] =
            "Built-in multi-source lyrics loader for Salt Player for Windows / Salt Player for Windows 内置多源歌词补全插件。"
        attributes["Plugin-Has-Config"] = "true"
    }
}

tasks.register<Jar>("plugin") {
    archiveBaseName.set("plugin-$pluginId")
    archiveVersion.set(pluginVersion)

    into("classes") {
        with(tasks.named<Jar>("jar").get())
    }
    dependsOn(configurations.runtimeClasspath)
    into("lib") {
        from({
            configurations.runtimeClasspath.get()
                .filter { it.name.endsWith("jar") }
        })
    }
    archiveExtension.set("zip")
}
