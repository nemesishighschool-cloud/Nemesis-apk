package com.nemap.android.core

import org.junit.Assert.*
import org.junit.Test

class DomFreshnessTest {
    @Test fun rejectsPreviousOrEmptyAnswer() {
        assertFalse(DomFreshness.isFresh(true,"ancienne réponse",3,"ancienne réponse",3))
        assertFalse(DomFreshness.isFresh(true,"",4,"ancienne réponse",3))
        assertFalse(DomFreshness.isFresh(false,"nouvelle",4,"ancienne réponse",3))
    }
    @Test fun acceptsNewTurnOrChangedContinuation() {
        assertTrue(DomFreshness.isFresh(true,"nouvelle réponse",4,"ancienne réponse",3))
        assertTrue(DomFreshness.isFresh(true,"ancienne réponse et suite",3,"ancienne réponse",3))
        assertTrue(DomFreshness.isFresh(true,"répétition",4,"répétition",3))
    }
    @org.junit.Test fun continuationReturnsOnlyNewSameBlockSuffix() {
        org.junit.Assert.assertEquals(" suite", DomFreshness.freshText(true,"Réponse suite",1,"Réponse",1,true))
        org.junit.Assert.assertEquals("", DomFreshness.freshText(true,"Réponse",1,"Réponse",1,true))
        org.junit.Assert.assertEquals("", DomFreshness.freshText(true,"Texte modifié",1,"Réponse",1,true))
        org.junit.Assert.assertEquals("Nouvelle réponse", DomFreshness.freshText(true,"Nouvelle réponse",2,"Réponse",1,true))
    }

}
