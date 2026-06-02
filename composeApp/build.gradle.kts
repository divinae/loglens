import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec
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

    // JetBrains Runtime so the app has a native Wayland AWT toolkit (WLToolkit); used for compile,
    // run, and the bundled jpackage runtime. Provisioned via the foojay resolver in settings.
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
        vendor.set(JvmVendorSpec.JETBRAINS)
    }

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


// JBR launcher (native Wayland AWT) for the run task and the jpackage runtime image. The Kotlin
// jvmToolchain above already builds against JBR, but Compose's run/package tasks don't inherit it.
val jbrLauncher = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
    vendor.set(JvmVendorSpec.JETBRAINS)
}

compose.desktop {
    application {
        mainClass = "com.dv.loglens.MainKt"
        javaHome = jbrLauncher.get().metadata.installationPath.asFile.absolutePath

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "loglens"
            packageVersion = appVersion
            description = "Android logcat explorer"
            vendor = "dv"
            copyright = "© 2026 dv"

            val iconsDir = project.file("icons")
            macOS {
                bundleID = "com.dv.loglens"
                dockName = "loglens"
                iconFile.set(iconsDir.resolve("loglens.icns"))
            }
            windows {
                iconFile.set(iconsDir.resolve("loglens.ico"))
                menuGroup = "loglens"
                perUserInstall = true
                // Stable GUID so future installers upgrade in place instead of installing side-by-side.
                upgradeUuid = "8f3a6d2e-1c47-4b6a-9d8e-5a2f0c7b9e14"
            }
            linux {
                iconFile.set(iconsDir.resolve("loglens-linux.png"))
                packageName = "loglens"
                menuGroup = "Development"
                appCategory = "Development"
            }
        }
    }
}

// Run LogLens on JBR so `./gradlew run` is a native Wayland client too (not just packaged builds).
// Compose's run task is a JavaExec; point its launcher at the JBR toolchain.
tasks.withType<JavaExec>().configureEach {
    javaLauncher.set(jbrLauncher)
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
