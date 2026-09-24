package org.routingplatform.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeterministicDestinationFallbackTest {
    @Test fun extractsGermanNavigationAddress() {
        assertEquals(
            "am steinfeld 35 in 84174 Eching",
            DeterministicDestinationFallback.extractSearchQuery(
                "Fahr mich nach am steinfeld 35 in 84174 Eching"
            )
        )
    }

    @Test fun acceptsPlainAddressWithoutInventingCoordinates() {
        assertEquals(
            "Am Steinfeld 35, 84174 Eching",
            DeterministicDestinationFallback.extractSearchQuery(
                "Am Steinfeld 35, 84174 Eching"
            )
        )
    }

    @Test fun symbolicFavoritesStayWithIntentPath() {
        assertNull(DeterministicDestinationFallback.extractSearchQuery("Zuhause"))
        assertNull(DeterministicDestinationFallback.extractSearchQuery("work"))
    }

    @Test fun rejectsControlCharactersAndOversizedSearchQuery() {
        assertNull(DeterministicDestinationFallback.extractSearchQuery("Eching\n84174"))
        assertNull(DeterministicDestinationFallback.extractSearchQuery("x".repeat(161)))
    }
}
