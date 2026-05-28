import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.detekt)
}

detekt {
    buildUponDefaultConfig = true
    ignoreFailures = true
    config.setFrom(files("detekt.yml"))
    source.setFrom(files("src/jvmMain/kotlin", "src/commonMain/kotlin"))
}

val appVersion: String = (findProperty("appVersion") as? String) ?: "0.0.0"
val gitSha: String = providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim() }.getOrElse("dev")
val fullVersion = "$appVersion+$gitSha"

kotlin {
    jvm()

    jvmToolchain(21)

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.jewel.int.ui.standalone)
            implementation(libs.intellij.icons)
        }
    }
}


compose.desktop {
    application {
        mainClass = "com.dv.logexp.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.dv.logexp"
            packageVersion = appVersion
        }
    }
}

val generateBuildInfo by tasks.registering {
    val outDir = layout.buildDirectory.dir("generated/buildinfo/resources")
    outputs.dir(outDir)
    val version = fullVersion
    doLast {
        val file = outDir.get().file("build-info.properties").asFile
        file.parentFile.mkdirs()
        file.writeText("version=$version\n")
    }
}

kotlin.sourceSets.named("jvmMain") {
    resources.srcDir(generateBuildInfo)
}
