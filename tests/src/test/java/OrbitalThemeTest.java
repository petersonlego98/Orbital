import arc.graphics.*;
import orbital.ui.*;
import org.junit.jupiter.api.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

/** Regression tests for the key-art theme. Rendering itself needs GL, so this covers data and fallbacks. */
public class OrbitalThemeTest{

    @BeforeAll
    static void setup(){
        ApplicationTests.launchApplication(false);
    }

    @Test
    void paletteMatchesTheMeasuredKeyArt(){
        //values measured as regional averages of core/assets-raw/keyart/orbital-keyart.jpg
        assertEquals(Color.valueOf("04040c"), OrbitalTheme.space);
        assertEquals(Color.valueOf("492a89"), OrbitalTheme.nebula);
        assertEquals(Color.valueOf("4fd2ff"), OrbitalTheme.cyan);
        assertEquals(Color.valueOf("83bff6"), OrbitalTheme.ice);
        assertEquals(Color.valueOf("979bac"), OrbitalTheme.steel);
        assertNotEquals(OrbitalTheme.cyan, OrbitalTheme.amber);
    }

    @Test
    void keyArtLookupsSurviveMissingTextures(){
        //headless runs never load the loose ui/ textures; the menu/loading screens must fall back cleanly
        assertDoesNotThrow(() -> {
            OrbitalTheme.poster();
            OrbitalTheme.wordmark();
            OrbitalTheme.mark();
            OrbitalTheme.available();
        });
    }

    @Test
    void themeStylesAreSafeBeforeTheAtlasIsReady(){
        //the loading screen is built very early; creating its styles must not require loaded textures
        assertDoesNotThrow(() -> {
            OrbitalTheme.panel();
            OrbitalTheme.menuButton();
            OrbitalTheme.loadButton();
        });
    }

    @Test
    void backdropGradientsHaveNoSeamAtQuadJoints(){
        //The menu backdrop used to be drawn as overlapping strips: every joint received double
        //alpha and appeared as a thin vertical line. Adjacent gradient quads must share the exact
        //same alpha at their common edge, with no overlap in their widths.
        int segments = OrbitalTheme.shadeSegments;
        float amount = 0.56f;

        assertEquals(amount, OrbitalTheme.shadeAlpha(0f, amount), 1e-6f);
        assertEquals(0f, OrbitalTheme.shadeAlpha(1f, amount), 1e-6f);

        for(int i = 0; i < segments - 1; i++){
            float[] left = OrbitalTheme.shadeSegment(i, segments, amount);
            float[] right = OrbitalTheme.shadeSegment(i + 1, segments, amount);
            assertEquals(left[1], right[0], 1e-7f, "alpha jump at joint " + i + " would draw a line");
            assertTrue(left[0] > left[1], "falloff must be monotonic so no bright band appears");
        }
    }

    @Test
    void appIconIsASquareOpaqueEmblem() throws Exception{
        //Windows shows the app icon at 16-48 px (taskbar, shortcut, Start Menu, title bar),
        //so the master art must stay a square, fully opaque emblem instead of a cropped or
        //transparent key-art frame.
        Path root = repoRoot();
        assumeTrue(root != null, "assets gerados ausentes: execute java tools/ArtConverter.java");

        try(InputStream in = Files.newInputStream(root.resolve("core/assets/icons/icon.png"))){
            BufferedImage img = ImageIO.read(in);
            assertNotNull(img);
            assertEquals(256, img.getWidth());
            assertEquals(256, img.getHeight());
            for(int y = 0; y < 16; y++){
                for(int x = 0; x < 16; x++){
                    assertEquals(0xff, img.getRGB(x, y) >>> 24, "canto " + x + "," + y + " deve ser opaco");
                }
            }
        }
    }

    static Path repoRoot(){
        Path dir = Path.of("").toAbsolutePath();
        for(int i = 0; i < 5 && dir != null; i++){
            if(Files.exists(dir.resolve("core/assets/icons/icon.png"))) return dir;
            dir = dir.getParent();
        }
        return null;
    }
}
