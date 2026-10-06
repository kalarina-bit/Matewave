package cc.skysparkle.matewave

import cc.skysparkle.matewave.security.SessionAuth
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionAuthTest {
    private fun auth() = SessionAuth(ByteArray(32) { it.toByte() })

    @Test
    fun acceptsInOrder() {
        val a = auth()
        assertTrue(a.acceptSeq(1))
        assertTrue(a.acceptSeq(2))
        assertTrue(a.acceptSeq(3))
    }

    @Test
    fun rejectsReplay() {
        val a = auth()
        assertTrue(a.acceptSeq(5))
        assertFalse(a.acceptSeq(5))
    }

    @Test
    fun acceptsLateButUnseen() {
        val a = auth()
        assertTrue(a.acceptSeq(1))
        assertTrue(a.acceptSeq(3))
        assertTrue(a.acceptSeq(2))
        assertFalse(a.acceptSeq(2))
    }

    @Test
    fun rejectsOlderThanWindow() {
        val a = auth()
        assertTrue(a.acceptSeq(1))
        assertTrue(a.acceptSeq(1_000))
        assertFalse(a.acceptSeq(2))
    }

    @Test
    fun rejectsNonPositive() {
        val a = auth()
        assertFalse(a.acceptSeq(0))
        assertFalse(a.acceptSeq(-1))
    }
}
