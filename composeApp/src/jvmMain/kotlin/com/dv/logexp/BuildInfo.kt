package com.dv.logexp

import java.util.Properties

object BuildInfo {
    val version: String by lazy {
        val props = Properties()
        BuildInfo::class.java.getResourceAsStream("/build-info.properties")?.use { props.load(it) }
        props.getProperty("version", "dev")
    }
}
