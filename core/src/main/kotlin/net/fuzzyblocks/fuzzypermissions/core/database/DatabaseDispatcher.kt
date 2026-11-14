package net.fuzzyblocks.fuzzypermissions.core.database

import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/**
 * Runs blocking JDBC calls on virtual threads, so no platform thread is parked per query.
 * Typed as [ExecutorCoroutineDispatcher] so the plugin can call `close()` in onDisable.
 */
val DatabaseDispatcher: ExecutorCoroutineDispatcher =
    Executors.newVirtualThreadPerTaskExecutor().asCoroutineDispatcher()