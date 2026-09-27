package com.oneimage.android.ui.shared

import com.oneimage.android.api.GenerationQuote
import com.oneimage.android.api.GenerationQuoteStore
import org.junit.Assert.*
import org.junit.Test

class GenerationQuoteStoreTest {
    @Test fun invalidationPreventsSubmittingAnOldPrice() {
        val store = GenerationQuoteStore()
        store.put("video", GenerationQuote("a", 24))
        assertEquals(24, store.get("video")?.credits)
        store.clear("video")
        assertNull(store.get("video"))
    }
    @Test fun workflowQuotesStayIndependent() {
        val store = GenerationQuoteStore()
        store.put("video", GenerationQuote("a", 24))
        store.put("image", GenerationQuote("b", 0))
        store.clear("video")
        assertEquals(0, store.get("image")?.credits)
    }
}
