package com.erolgizlice.routetracker.core.route

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Reads the anchor, asks [DistanceGate], and writes the point, as one indivisible step.
 *
 * Without the lock, two fixes delivered together (a batched LocationResult) could both read the same
 * anchor, both pass the gate, and both be written less than 100 m apart. The lock only works as a
 * single writer, so this class must have exactly one instance per process (a Koin `single`).
 *
 * [RouteRepository.reset] deliberately does not take the lock: reset is a single atomic delete, and every
 * interleaving with a record call ends in the same state as some serial order of the two.
 */
class RecordFixUseCase(
    private val repository: RouteRepository,
    private val gate: DistanceGate,
) {
    private val writeLock = Mutex()

    suspend operator fun invoke(fix: LocationFix): Result = writeLock.withLock {
        when (val decision = gate.evaluate(anchor = repository.lastPoint(), fix = fix)) {
            DistanceGate.Decision.Record -> Result.Recorded(repository.add(fix))
            else -> Result.Skipped(decision)
        }
    }

    sealed interface Result {
        data class Recorded(val point: RoutePoint) : Result
        data class Skipped(val decision: DistanceGate.Decision) : Result
    }
}
