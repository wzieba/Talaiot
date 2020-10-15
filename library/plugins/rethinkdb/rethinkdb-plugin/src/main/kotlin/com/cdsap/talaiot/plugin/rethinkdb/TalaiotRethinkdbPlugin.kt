package com.cdsap.talaiot.plugin.rethinkdb

import com.cdsap.talaiot.Talaiot
import org.gradle.api.Plugin
import org.gradle.api.Project

class TalaiotRethinkdbPlugin: Plugin<Project> {

    override fun apply(target: Project) {
        Talaiot(
            RethinkdbExtension::class.java,
            RethinkdbConfigurationProvider(
                target
            )
        ).setUpPlugin(target)
    }

}