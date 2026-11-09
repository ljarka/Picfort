package com.github.ljarka.picfort.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuotaExceededTest {
    @Test
    fun `recognises Immich quota error`() {
        assertTrue(isQuotaExceeded("""{"message":"Quota has been exceeded!"}"""))
    }

    @Test
    fun `other errors are not quota errors`() {
        assertFalse(isQuotaExceeded("""{"message":"Validation failed","errors":[]}"""))
        assertFalse(isQuotaExceeded(""))
        assertFalse(isQuotaExceeded(null))
    }
}
