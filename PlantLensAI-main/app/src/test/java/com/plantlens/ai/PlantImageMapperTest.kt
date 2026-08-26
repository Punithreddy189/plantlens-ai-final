package com.plantlens.ai

import com.plantlens.ai.utils.PlantImageMapper
import org.junit.Assert.assertEquals
import org.junit.Test

class PlantImageMapperTest {

    @Test
    fun getDrawableRes_validPlantNames_returnsCorrectDrawable() {
        // Aloe Vera
        assertEquals(R.drawable.aloe_vera, PlantImageMapper.getDrawableRes("Aloe Vera"))
        assertEquals(R.drawable.aloe_vera, PlantImageMapper.getDrawableRes("Aloe vera"))
        
        // Snake Plant
        assertEquals(R.drawable.snake_plant, PlantImageMapper.getDrawableRes("Snake Plant"))
        assertEquals(R.drawable.snake_plant, PlantImageMapper.getDrawableRes("Sansevieria trifasciata"))
        
        // Peace Lily
        assertEquals(R.drawable.peace_lily, PlantImageMapper.getDrawableRes("Peace Lily"))
        assertEquals(R.drawable.peace_lily, PlantImageMapper.getDrawableRes("Spathiphyllum"))
        
        // Monstera
        assertEquals(R.drawable.monstera, PlantImageMapper.getDrawableRes("Monstera"))
        assertEquals(R.drawable.monstera, PlantImageMapper.getDrawableRes("Monstera deliciosa"))
        
        // Lavender
        assertEquals(R.drawable.lavender, PlantImageMapper.getDrawableRes("Lavender"))
        assertEquals(R.drawable.lavender, PlantImageMapper.getDrawableRes("Lavandula"))
        
        // Giant Milkweed
        assertEquals(R.drawable.giant_milkweed, PlantImageMapper.getDrawableRes("Giant Milkweed"))
        assertEquals(R.drawable.giant_milkweed, PlantImageMapper.getDrawableRes("Calotropis gigantea"))
        
        // Touch-me-not
        assertEquals(R.drawable.touch_me_not, PlantImageMapper.getDrawableRes("Touch-me-not"))
        assertEquals(R.drawable.touch_me_not, PlantImageMapper.getDrawableRes("Mimosa pudica"))
    }

    @Test
    fun getDrawableRes_unknownPlantName_returnsLogoFallback() {
        assertEquals(R.drawable.plantlens_logo, PlantImageMapper.getDrawableRes("Unknown Plant"))
        assertEquals(R.drawable.plantlens_logo, PlantImageMapper.getDrawableRes(""))
    }
}
