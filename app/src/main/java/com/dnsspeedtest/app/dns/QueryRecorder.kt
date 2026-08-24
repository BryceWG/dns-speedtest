package com.dnsspeedtest.app.dns

class QueryRecorder {
    private val startedAtNs = System.nanoTime()
    private val events = mutableListOf<QueryEvent>()

    val startedAtMs: Long = System.currentTimeMillis()

    fun elapsedMs(): Long = (System.nanoTime() - startedAtNs) / 1_000_000L

    fun mark(stage: String, detail: String = "") {
        events += QueryEvent(elapsedMs = elapsedMs(), stage = stage, detail = detail)
    }

    fun snapshot(): List<QueryEvent> = events.toList()
}
