package features.app.warehouse

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BatchVerificationTest {

    @Test
    fun plainBatchEntryMatchesCaseInsensitively() {
        assertTrue(batchMatchesScan("batch001", "BATCH001"))
    }

    @Test
    fun gs1BatchEntryMatchesExpectedBatch() {
        assertTrue(batchMatchesScan("(10)BATCH001", "BATCH001"))
    }

    @Test
    fun batchStartingWithTenAiMatchesFullPlainEntry() {
        assertTrue(batchMatchesScan("10ABC123", "10ABC123"))
    }

    @Test
    fun mismatchedBatchIsRejected() {
        assertFalse(batchMatchesScan("WRONG", "BATCH001"))
    }
}
