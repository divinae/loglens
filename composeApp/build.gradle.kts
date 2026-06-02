import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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

// The JBR 25 toolchain would feed detekt --jvm-target=25, which detekt 1.23.x rejects (max 22).
// Our sources compile to Java 21 bytecode anyway, so pin detekt's analysis target to 21.
tasks.withType<Detekt>().configureEach { jvmTarget = "21" }
tasks.withType<DetektCreateBaselineTask>().configureEach { jvmTarget = "21" }

val appVersion: String = (findProperty("appVersion") as? String) ?: "0.0.0"
val gitSha: String = providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim() }.getOrElse("dev")
val fullVersion = "$appVersion+$gitSha"

kotlin {
    // Compile our own code to Java 21 bytecode (we don't need newer language-level features) even
    // though the toolchain runs on JBR 25 — Jewel 0.37 (IntelliJ 262) ships class-file v69 and only
    // loads on a Java 25 runtime, so the toolchain/bundled runtime must be 25.
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    // JetBrains Runtime 25: used for compile, run, and the bundled jpackage runtime, so end users
    // need no host JDK. Provisioned via the foojay resolver in settings.gradle.kts. (Gradle itself
    // still bootstraps on JDK 21 — the toolchain is independent of the JVM running Gradle.)
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
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


// Compose's run/package tasks don't inherit the kotlin jvmToolchain — point them at JBR explicitly.
val jbrLauncher = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(25))
    vendor.set(JvmVendorSpec.JETBRAINS)
}

compose.desktop {
    application {
        mainClass = "com.dv.loglens.MainKt"
        // jpackage builds the bundled runtime image (jlink) from this JDK — so it's JBR, not Adoptium.
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

// `./gradlew run` on JBR too, not just packaged builds. Compose's run task is a JavaExec.
tasks.withType<JavaExec>().configureEach {
    javaLauncher.set(jbrLauncher)
}

val generateBuildInfo by tasks.registering {
    val outDir = layout.buildDirectory.dir("generated/buildinfo/resources")
    outputs.dir(outDir)
    val version = fullVersion
    // Captured at configuration time so the About dialog can show what the app was built with.
    val composeVersion = libs.versions.composeMultiplatform.get()
    val kotlinVersion = libs.versions.kotlin.get()
    val jewelVersion = libs.versions.jewel.get()
    val coroutinesVersion = libs.versions.kotlinx.coroutines.get()
    inputs.property("version", version)
    inputs.property("compose", composeVersion)
    inputs.property("kotlin", kotlinVersion)
    inputs.property("jewel", jewelVersion)
    inputs.property("coroutines", coroutinesVersion)
    doLast {
        val file = outDir.get().file("build-info.properties").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            version=$version
            compose=$composeVersion
            kotlin=$kotlinVersion
            jewel=$jewelVersion
            coroutines=$coroutinesVersion
            """.trimIndent() + "\n",
        )
    }
}

kotlin.sourceSets.named("jvmMain") {
    resources.srcDir(generateBuildInfo)
}
