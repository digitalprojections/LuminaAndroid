package com.oneimage.android.ui.dashboard

import com.oneimage.android.R
import com.oneimage.android.ui.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardDemoCatalogTest {
    @Test
    fun demosMirrorTheWebShowcaseWorkflows() {
        assertEquals(
            listOf("image_generation", "story_images", "ref_restyle", "game_asset_upscaler"),
            DashboardDemoCatalog.demos.map { it.id }
        )
    }

    @Test
    fun everyDemoHasADistinctBeforeAndAfterAsset() {
        DashboardDemoCatalog.demos.forEach { demo ->
            assertTrue("${demo.id} should have a title", demo.title.isNotBlank())
            assertTrue("${demo.id} should describe the transformation", demo.description.isNotBlank())
            assertNotEquals("${demo.id} should compare two assets", demo.beforeImageRes, demo.afterImageRes)
            assertNotEquals("${demo.id} should label both states", demo.beforeLabel, demo.afterLabel)
        }
    }

    @Test
    fun eachDemoOpensTheMatchingWorkflow() {
        assertEquals(Screen.ImageGen, DashboardDemoCatalog.demos[0].route)
        assertEquals(Screen.StoryImages, DashboardDemoCatalog.demos[1].route)
        assertEquals(Screen.RefRestyle, DashboardDemoCatalog.demos[2].route)
        assertEquals(Screen.GameAssetUpscaler, DashboardDemoCatalog.demos[3].route)
    }

    @Test
    fun catalogReferencesPackagedResources() {
        val resourceIds = DashboardDemoCatalog.demos.flatMap { demo ->
            listOf(demo.beforeImageRes, demo.afterImageRes)
        }

        assertTrue(resourceIds.contains(R.drawable.demo_image_generation_before))
        assertTrue(resourceIds.contains(R.drawable.demo_image_generation_after))
        assertTrue(resourceIds.contains(R.drawable.demo_story_images_before))
        assertTrue(resourceIds.contains(R.drawable.demo_story_images_after))
        assertTrue(resourceIds.contains(R.drawable.demo_ref_restyle_before))
        assertTrue(resourceIds.contains(R.drawable.demo_ref_restyle_after))
        assertTrue(resourceIds.contains(R.drawable.demo_game_asset_before))
        assertTrue(resourceIds.contains(R.drawable.demo_game_asset_after))
    }
}
