package io.github.roby222.rush.model

import io.github.roby222.rush.trip
import org.junit.Assert.assertEquals
import org.junit.Test

class CheckTest {
    @Test fun trafficLevels() {
        assertEquals(0, trafficLevel(14, 2))   // up to 2 min is normal
        assertEquals(1, trafficLevel(63, 5))   // +5 min is a slowdown even on a long trip
        assertEquals(1, trafficLevel(14, 3))   // 27% on a short trip
        assertEquals(2, trafficLevel(14, 5))   // 56%: a short trip turns intense sooner
        assertEquals(2, trafficLevel(70, 10))  // 10 min is intense on any trip
    }
}
