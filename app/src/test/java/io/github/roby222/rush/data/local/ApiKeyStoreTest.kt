package io.github.roby222.rush.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiKeyStoreTest {
    @Test fun theUsersKeyWinsOverTheBuilds() {
        assertEquals("mine", effectiveKey("mine", "debug"))
        assertEquals("mine", effectiveKey("  mine \n", ""))         // pasted with spaces
        assertEquals("debug", effectiveKey(null, "debug"))           // debug build, nothing entered
        assertEquals("debug", effectiveKey(" ", "debug"))
        assertNull(effectiveKey(null, ""))                           // a fresh release install: ask for it
    }
}
