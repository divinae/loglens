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
// Prefer the CI-provided SHA (the runner image may not even have a `git` binary); fall back to
// `git` locally, and to "dev" if neither is available. runCatching guards against the process
// failing to start, which `getOrElse` on the provider does not catch.
val gitSha: String = providers.environmentVariable("CI_COMMIT_SHORT_SHA").orNull?.takeIf { it.isNotBlank() }
    ?: runCatching {
        providers.exec {
            commandLine("git", "rev-parse", "--short", "HEAD")
            isIgnoreExitValue = true
        }.standardOutput.asText.get().trim()
    }.getOrNull()?.takeIf { it.isNotBlank() }
    ?: "dev"
val fullVersion = "$appVersion+$gitSha"

kotlin {
    // Compile to Java 25 bytecode to match the JBR 25 toolchain and Jewel 0.37 (IntelliJ 262,
    // class-file v69): Jewel ships `inline` helpers (e.g. Modifier.thenIf) as v69 bytecode, which
    // can't be inlined into an older jvmTarget, so we must target 25 to consume them.
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_25)
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
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
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


// IntelliJ's icons-api (via Jewel 0.41) depends on IntelliJ's own coroutines fork, published under a
// different group, so Gradle keeps it next to kotlinx-coroutines instead of picking one. Whichever
// comes first on the classpath wins: in the packaged app that was the older fork, and code compiled
// against 1.11 died with NoSuchMethodError (Job.cancel$default). Use the mainline artifact only.
configurations.configureEach {
    resolutionStrategy.dependencySubstitution {
        substitute(module("org.jetbrains.intellij.deps.kotlinx:kotlinx-coroutines-core-jvm"))
            .using(module("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:${libs.versions.kotlinx.coroutines.get()}"))
            .because("one coroutines on the classpath; the IntelliJ fork shadowed 1.11")
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

val generateBuildInfo = tasks.register("generateBuildInfo") {
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

// Test classes are Java 25 bytecode, which Gradle's class scanner treats loosely: helper classes
// such as LogIngestorTest's nested Harness got run as tests and failed. Run *Test classes only.
tasks.withType<Test>().configureEach {
    isScanForTestClasses = false
    include("**/*Test.class")
}
