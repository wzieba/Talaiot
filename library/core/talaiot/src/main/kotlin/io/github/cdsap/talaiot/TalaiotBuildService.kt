package io.github.cdsap.talaiot

import io.github.cdsap.talaiot.entities.TaskLength
import io.github.cdsap.talaiot.entities.TaskMessageState
import io.github.cdsap.talaiot.publisher.Publisher
import io.github.cdsap.talaiot.publisher.TalaiotPublisher
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import java.util.concurrent.Executors

/**
 * Tracks information of the tasks executed duting the build.
 * This service is shared by multiple tasks and holds general information of the build.
 * Once the service is finished it will publish the build/task information in configuration provided.
 * It replaces the old TalaiotTracker.
 *
 */
abstract class TalaiotBuildService :
    BuildService<TalaiotBuildService.Params>,
    AutoCloseable,
    OperationCompletionListener {

    var start = 0L
    private var configurationTime = 0L
    private var configurationIsSet = false

    interface Params : BuildServiceParameters {
        /**
         * Publishes the data stored in the [TalaiotBuildService]
         */
        val publisher: Property<TalaiotPublisher>

        /**
         * List of Gradle Tasks used in the build execution:
         *    ./gradlew clean --> clean
         *    ./gradlew clean assemble --> clean assemble
         */
        val startParameters: ListProperty<String>
        val customPublishers: ListProperty<Publisher>
        val publishOnNewThread: Property<Boolean>
        val initTime: Property<Long>
        val configurationPhaseExecuted: Property<Provider<Boolean>>
    }

    private val taskLengthList = mutableListOf<TaskLength>()

    init {
        start = System.currentTimeMillis()
    }

    override fun close() {
        val end = System.currentTimeMillis()
        if (parameters.publishOnNewThread.get()) {
            val executor = Executors.newSingleThreadExecutor()
            executor.execute {
                publish(end)
            }
        } else {
            publish(end)
        }
    }

    private fun publish(end: Long) {
        val configurationPhaseExecuted = parameters.configurationPhaseExecuted.get().get()
        val configurationTime = if (configurationPhaseExecuted) {
            start - parameters.initTime.get()
        } else {
            0
        }

        parameters.publisher.get().publish(
            taskLengthList = taskLengthList,
            start = start,
            configuration = configurationTime,
            end = end,
            duration = end - start,
            success = taskLengthList.none {
                it.state == TaskMessageState.FAILED
            },
            publishers = parameters.customPublishers.get(),
            configurationCacheHit = !configurationPhaseExecuted
        )
    }

    override fun onFinish(event: FinishEvent?) {
        if (!configurationIsSet) {
            configurationTime = System.currentTimeMillis() - start
            configurationIsSet = true
        }
        val duration = event?.result?.endTime!! - event.result?.startTime!!
        val end = event.result?.endTime!!
        val start = event.result?.startTime!!
        val taskPath = event.descriptor?.name.toString()
        val task = taskPath.split(":").last()
        val state = event.displayName.split(" ")[2]

        taskLengthList.add(
            taskLength(
                ms = duration,
                task = task,
                path = taskPath,
                state = when (state) {
                    "UP-TO-DATE" -> TaskMessageState.UP_TO_DATE
                    "FROM-CACHE" -> TaskMessageState.FROM_CACHE
                    "NO-SOURCE" -> TaskMessageState.NO_SOURCE
                    "skipped" -> TaskMessageState.SKIPPED
                    "failed" -> TaskMessageState.FAILED
                    else -> TaskMessageState.EXECUTED
                },
                rootNode = parameters.startParameters.get().contains(task.split(":").last()),
                startMs = start,
                stopMs = end
            )
        )
    }
}

private fun taskLength(
    ms: Long,
    task: String,
    path: String,
    state: TaskMessageState,
    rootNode: Boolean,
    startMs: Long,
    stopMs: Long
): TaskLength = TaskLength(
    ms = ms,
    taskName = task,
    taskPath = path,
    state = state,
    rootNode = rootNode,
    module = getModule(path),
    startMs = startMs,
    stopMs = stopMs
)

private fun getModule(path: String): String {
    val module = path.split(":")
    return if (module.size > 2) {
        module.toList().dropLast(1).joinToString(separator = ":")
    } else {
        "no_module"
    }
}
