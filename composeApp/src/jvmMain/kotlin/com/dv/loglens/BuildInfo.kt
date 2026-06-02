package com.dv.loglens

import java.util.Properties

object BuildInfo {
    private val props: Properties by lazy {
        Properties().apply {
            BuildInfo::class.java.getResourceAsStream("/build-info.properties")?.use { load(it) }
        }
    }

    val version: String get() = props.getProperty("version", "dev")
    val composeVersion: String get() = props.getProperty("compose", "?")
    val kotlinVersion: String get() = props.getProperty("kotlin", "?")
    val jewelVersion: String get() = props.getProperty("jewel", "?")
    val coroutinesVersion: String get() = props.getProperty("coroutines", "?")

    /** The actual JVM the app is running on — the bundled JetBrains Runtime in packaged builds. */
    val runtime: String get() = "JDK ${System.getProperty("java.version")} (${System.getProperty("java.vendor")})"
}
