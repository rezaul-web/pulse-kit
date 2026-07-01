package io.pulsekit.core

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EventBusTest {

    @Test
    fun emitted_events_are_observed_by_subscribers() = runTest {
        val bus = EventBus()

        bus.events.test {
            val event = CustomEvent(
                timestampMs = 1L,
                sessionId = "s1",
                name = "click",
                attributes = mapOf("target" to "button"),
            )
            bus.emit(event)

            assertEquals(event, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
