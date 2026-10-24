package zed.rainxch.details.presentation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeferredSectionsGateTest {
    @Test
    fun the_default_setting_loads_sections_on_open_and_ignores_the_action() {
        val gate = DeferredSectionsGate(autoLoad = true)

        assertFalse(gate.isDeferred)
        assertFalse(gate.requestLoad())
    }

    @Test
    fun the_setting_off_defers_the_sections_until_the_first_request() {
        val gate = DeferredSectionsGate(autoLoad = false)

        assertTrue(gate.isDeferred)
        assertTrue(gate.requestLoad())
        assertFalse(gate.isDeferred)
    }

    @Test
    fun a_repeated_request_never_loads_the_sections_twice() {
        val gate = DeferredSectionsGate(autoLoad = false)

        assertTrue(gate.requestLoad())
        assertFalse(gate.requestLoad())
        assertFalse(gate.requestLoad())
    }
}
