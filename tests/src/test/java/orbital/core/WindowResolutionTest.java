package orbital.core;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/** Tests the display-resolution value stored by Settings > Advanced Graphics. */
class WindowResolutionTest{
    @Test
    void storesTheDimensionsInAStableValue(){
        WindowResolution resolution = new WindowResolution(2560, 1440, 144);

        assertEquals(2560, resolution.width);
        assertEquals(1440, resolution.height);
        assertEquals(144, resolution.refreshRate);
        assertEquals("2560x1440", resolution.id());
        assertEquals("2560x1440", resolution.toString());
    }

    @Test
    void rejectsInvalidDimensions(){
        assertThrows(IllegalArgumentException.class, () -> new WindowResolution(0, 720));
        assertThrows(IllegalArgumentException.class, () -> new WindowResolution(1280, 0));
        assertThrows(IllegalArgumentException.class, () -> new WindowResolution(-1, 720));
    }

    @Test
    void unknownRefreshRatesAreStoredAsZero(){
        assertEquals(0, new WindowResolution(1280, 720, -1).refreshRate);
        assertEquals(0, new WindowResolution(1280, 720).refreshRate);
    }

    @Test
    void parsesStoredValues(){
        assertEquals(new WindowResolution(1920, 1080), WindowResolution.parse("1920x1080"));
        assertEquals(new WindowResolution(1280, 720), WindowResolution.parse(" 1280X720 "));
    }

    @Test
    void invalidOrAutomaticValuesHaveNoResolution(){
        for(String value : new String[]{null, "", "  ", "auto", "automatic", "x720", "1280x", "1280", "0x720", "1280x0", "-1x720", "1280x720x60", "manyxpixels"}){
            assertNull(WindowResolution.parse(value), "'" + value + "' must not be a resolution");
        }
    }

    @Test
    void dimensionsIdentifyTheModeRegardlessOfRefreshRate(){
        WindowResolution sixty = new WindowResolution(1920, 1080, 60);
        WindowResolution highRefresh = new WindowResolution(1920, 1080, 144);

        assertEquals(sixty, highRefresh);
        assertEquals(sixty.hashCode(), highRefresh.hashCode());
        assertNotEquals(sixty, new WindowResolution(1920, 1200, 60));
    }
}
