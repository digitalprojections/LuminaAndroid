package com.oneimage.android.ui.account

import org.junit.Assert.assertEquals
import org.junit.Test

class GooglePlayCreditBillingConfigTest {
    @Test
    fun configuredAndroidCreditProductsMatchGooglePlayCatalogIds() {
        val products = configuredAndroidCreditProducts("")

        assertEquals(
            listOf("small_pack_200", "medium_625", "large"),
            products.map { it.productId }
        )
        assertEquals(listOf(200L, 625L, 2250L), products.map { it.credits })
    }

    @Test
    fun configuredAndroidCreditProductsRespectAllowList() {
        val products = configuredAndroidCreditProducts(" medium_625 , large ")

        assertEquals(listOf("medium_625", "large"), products.map { it.productId })
    }
}
