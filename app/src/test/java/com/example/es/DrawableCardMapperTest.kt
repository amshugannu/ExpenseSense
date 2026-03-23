package com.example.es

import org.junit.Test
import org.junit.Assert.*

class DrawableCardMapperTest {

    @Test
    fun testGetCards_ICICI_Credit() {
        val cards = DrawableCardMapper.getCards("ICICI Bank", "Credit")
        
        // Expected cards: Amazon Pay, Rubyx, Sapphiro, Coral, Platinum Chip + Other
        assertEquals(6, cards.size)
        
        assertEquals("Amazon Pay ICICI CC", cards[0].displayName)
        assertEquals("amazonpayicicicc", cards[0].drawableName)
        
        assertEquals("ICICI Rubyx CC", cards[1].displayName)
        assertEquals("icicirubyxcc", cards[1].drawableName)
        
        assertEquals("ICICI Sapphiro CC", cards[2].displayName)
        assertEquals("icicisapphirocc", cards[2].drawableName)
        
        assertEquals("ICICI Coral CC", cards[3].displayName)
        assertEquals("icicicoralcc", cards[3].drawableName)
        
        assertEquals("ICICI Platinum Chip CC", cards[4].displayName)
        assertEquals("iciciplatinumchipcc", cards[4].drawableName)
        
        assertEquals("Other Card", cards[5].displayName)
        assertTrue(cards[5].isCustom)
    }

    @Test
    fun testGetCards_ICICI_Debit() {
        val cards = DrawableCardMapper.getCards("ICICI Bank", "Debit")
        
        // Expected cards: Titanium, Wealth Management, Rubyx, Sapphiro, Coral + Other
        assertEquals(6, cards.size)
        
        assertEquals("ICICI Titanium DC", cards[0].displayName)
        assertEquals("icicititaniumdc", cards[0].drawableName)
        
        assertEquals("ICICI Wealth Management DC", cards[1].displayName)
        assertEquals("iciciwealthmangementdc", cards[1].drawableName)
        
        assertEquals("ICICI Rubyx DC", cards[2].displayName)
        assertEquals("icicirubyxdc", cards[2].drawableName)
        
        assertEquals("ICICI Sapphiro DC", cards[3].displayName)
        assertEquals("icicisapphirodc", cards[3].drawableName)
        
        assertEquals("ICICI Coral DC", cards[4].displayName)
        assertEquals("icicicoraldc", cards[4].drawableName)
        
        assertEquals("Other Card", cards[5].displayName)
    }

    @Test
    fun testGetCards_OtherBank_NoRegression() {
        val cards = DrawableCardMapper.getCards("HDFC Bank", "Credit")
        // Should still generate numbered cards 1-5 + Other
        assertEquals(6, cards.size)
        assertEquals("HDFC Bank Credit Card 1", cards[0].displayName)
        assertEquals("hdfccreditcard1", cards[0].drawableName)
    }
}
