package com.cdsap.talaiot.plugin.elasticsearch

import com.cdsap.talaiot.Talaiot
import org.gradle.api.Plugin
import org.gradle.api.Project

class TalaiotElasticSearchPlugin: Plugin<Project> {

    override fun apply(target: Project) {
        Talaiot(
            ElasticSearchExtension::class.java,
            ElasticSearchConfigurationProvider(
                target
            )
        ).setUpPlugin(target)
    }

}