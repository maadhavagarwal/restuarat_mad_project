package com.restaurant.mobile.delivery

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * MultilingualMenuTranslationTest
 * Verifies Gemini/Lexicon menu translations, caching behavior, fallback resiliency,
 * and RestroMultilingualSearch cross-language query matching for Hindi and Marathi.
 */
class MultilingualMenuTranslationTest {

    private val sampleButterChicken = Dish(
        id = "dish-bc-1",
        restaurantId = "rest-1",
        category = "Main Course",
        name = "Butter Chicken",
        description = "Tender clay oven roasted chicken in rich makhani gravy",
        price = 45000L,
        image = "butter_chicken.jpg",
        vegetarian = false
    )

    private val sampleDalMakhani = Dish(
        id = "dish-dm-1",
        restaurantId = "rest-1",
        category = "Main Course",
        name = "Dal Makhani",
        description = "Slow simmered black lentils with cream and butter",
        price = 32000L,
        image = "dal_makhani.jpg",
        vegetarian = true
    )

    private val sampleMisalPav = Dish(
        id = "dish-mp-1",
        restaurantId = "rest-2",
        category = "Snacks",
        name = "Misal Pav",
        description = "Spicy sprouted moth bean curry with crunchy farsan and pav",
        price = 15000L,
        image = "misal_pav.jpg",
        vegetarian = true
    )

    private val sampleDosa = Dish(
        id = "dish-dosa-1",
        restaurantId = "rest-3",
        category = "South Indian",
        name = "Ghee Roast Dosa",
        description = "Crispy golden crepe roasted in pure desi ghee",
        price = 22000L,
        image = "ghee_dosa.jpg",
        vegetarian = true
    )

    @Before
    fun setUp() {
        RestroI18n.setLanguage("en")
        RestroMenuTranslationManager.clearCache()
    }

    @After
    fun tearDown() {
        RestroI18n.setLanguage("en")
        RestroMenuTranslationManager.clearCache()
    }

    // --- Test Group 1: English Original Presentation ---

    @Test
    fun testEnglishReturnsOriginalText() {
        assertEquals("Butter Chicken", sampleButterChicken.localizedName("en"))
        assertEquals("Tender clay oven roasted chicken in rich makhani gravy", sampleButterChicken.localizedDescription("en"))

        assertEquals("Dal Makhani", sampleDalMakhani.localizedName("en"))
        assertEquals("Misal Pav", sampleMisalPav.localizedName("en"))
    }

    // --- Test Group 2: Hindi Menu Translation ---

    @Test
    fun testHindiMenuTranslationFromLexicon() {
        assertEquals("बटर चिकन", sampleButterChicken.localizedName("hi"))
        assertTrue(sampleButterChicken.localizedDescription("hi").contains("मक्खन"))

        assertEquals("दाल मखनी", sampleDalMakhani.localizedName("hi"))
        assertTrue(sampleDalMakhani.localizedDescription("hi").contains("काली दाल"))

        assertEquals("मिसल पाव", sampleMisalPav.localizedName("hi"))
    }

    // --- Test Group 3: Marathi Menu Translation ---

    @Test
    fun testMarathiMenuTranslationFromLexicon() {
        assertEquals("झणझणीत मिसळ पाव", sampleMisalPav.localizedName("mr"))
        assertTrue(sampleMisalPav.localizedDescription("mr").contains("झणझणीत कट"))

        assertEquals("तूप रोस्ट डोसा", sampleDosa.localizedName("mr"))
        assertTrue(sampleDosa.localizedDescription("mr").contains("तुपात भाजलेला"))

        assertEquals("बटर चिकन", sampleButterChicken.localizedName("mr"))
    }

    // --- Test Group 4: Direct Custom Translations ---

    @Test
    fun testCustomTranslationsOverrideLexicon() {
        val customDish = sampleButterChicken.copy(
            translations = mapOf(
                "hi" to DishTranslation("शाही मखमली चिकन", "विशेष शाही ग्रेवी"),
                "mr" to DishTranslation("खास मलाई चिकन", "विशेष मलाईदार रस्सा")
            )
        )

        assertEquals("शाही मखमली चिकन", customDish.localizedName("hi"))
        assertEquals("विशेष शाही ग्रेवी", customDish.localizedDescription("hi"))

        assertEquals("खास मलाई चिकन", customDish.localizedName("mr"))
        assertEquals("विशेष मलाईदार रस्सा", customDish.localizedDescription("mr"))
    }

    // --- Test Group 5: Translation Caching Behavior ---

    @Test
    fun testTranslationCachingLifecycle() {
        assertFalse(RestroMenuTranslationManager.isCached("hi", "dish-new-1", "Kashmiri Pulao"))

        RestroMenuTranslationManager.cacheTranslation(
            lang = "hi",
            dishId = "dish-new-1",
            dishName = "Kashmiri Pulao",
            translation = DishTranslation("कश्मीरी पुलाव", "केसर और मेवे से बना सुगंधित पुलाव")
        )

        assertTrue(RestroMenuTranslationManager.isCached("hi", "dish-new-1", "Kashmiri Pulao"))

        val trans = RestroMenuTranslationManager.getTranslation(
            dishId = "dish-new-1",
            dishName = "Kashmiri Pulao",
            dishDesc = "Fragrant rice",
            lang = "hi"
        )
        assertEquals("कश्मीरी पुलाव", trans.name)
        assertEquals("केसर और मेवे से बना सुगंधित पुलाव", trans.description)
    }

    // --- Test Group 6: Graceful Fallback on Unknown Dishes ---

    @Test
    fun testGracefulFallbackForUnknownDish() {
        val unknownDish = Dish(
            id = "dish-exotic-99",
            restaurantId = "rest-99",
            category = "Fusion",
            name = "Nordic Smoked Salmon Tartare",
            description = "Smoked salmon with capers and dill emulsion",
            price = 68000L,
            image = "salmon.jpg",
            vegetarian = false
        )

        // For untranslated dish without lexicon match, gracefully falls back to original text
        val hiName = unknownDish.localizedName("hi")
        assertEquals("Nordic Smoked Salmon Tartare", hiName)

        val mrDesc = unknownDish.localizedDescription("mr")
        assertEquals("Smoked salmon with capers and dill emulsion", mrDesc)
    }

    // --- Test Group 7: Multilingual Menu Search Integration ---

    @Test
    fun testRestroMultilingualSearchWithDevanagariQueries() {
        // Hindi query matching
        assertTrue(RestroMultilingualSearch.matchesDish(sampleButterChicken, "बटर चिकन"))
        assertTrue(RestroMultilingualSearch.matchesDish(sampleDalMakhani, "दाल मखनी"))

        // Marathi query matching
        assertTrue(RestroMultilingualSearch.matchesDish(sampleMisalPav, "मिसळ पाव"))
        assertTrue(RestroMultilingualSearch.matchesDish(sampleMisalPav, "झणझणीत"))
        assertTrue(RestroMultilingualSearch.matchesDish(sampleDosa, "तूप रोस्ट"))

        // English query matching still works 100%
        assertTrue(RestroMultilingualSearch.matchesDish(sampleButterChicken, "Butter Chicken"))
        assertTrue(RestroMultilingualSearch.matchesDish(sampleDalMakhani, "dal makhani"))
        assertTrue(RestroMultilingualSearch.matchesDish(sampleMisalPav, "misal"))

        // Transliterated Latin matching
        assertTrue(RestroMultilingualSearch.matchesDish(sampleButterChicken, "chicken"))
        assertTrue(RestroMultilingualSearch.matchesDish(sampleDalMakhani, "dal"))

        // Dietary filters in multilingual queries
        assertTrue(RestroMultilingualSearch.matchesDish(sampleDalMakhani, "शाकाहारी दाल"))
        assertFalse(RestroMultilingualSearch.matchesDish(sampleButterChicken, "शाकाहारी चिकन")) // non-veg rejected
    }

    // --- Test Group 8: RestroI18n State Reactivity ---

    @Test
    fun testRestroI18nGlobalLanguageSwitching() {
        RestroI18n.setLanguage("hi")
        assertEquals("hi", RestroI18n.currentLanguage)
        assertEquals("बटर चिकन", sampleButterChicken.localizedName())

        RestroI18n.setLanguage("mr")
        assertEquals("mr", RestroI18n.currentLanguage)
        assertEquals("झणझणीत मिसळ पाव", sampleMisalPav.localizedName())

        RestroI18n.setLanguage("en")
        assertEquals("en", RestroI18n.currentLanguage)
        assertEquals("Butter Chicken", sampleButterChicken.localizedName())
    }
}
