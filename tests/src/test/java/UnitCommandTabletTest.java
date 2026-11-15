import orbital.ui.fragments.UnitCommandTablet;
import org.junit.jupiter.api.*;

import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The command tablet is pure hud, so its contract is the shipped text: every label, hint and
 * toast is looked up by key at runtime. A missing key would show a raw "??key??" inside the
 * tablet without breaking the build, so it is checked against the real bundles here.
 */
public class UnitCommandTabletTest{

    private static final String[] files = {
        "bundles/bundle.properties", "bundles/bundle_pt_BR.properties"
    };

    private static final String[] keys = {
        "tablet.title", "tablet.toggle", "tablet.status", "tablet.filter.all",
        "tablet.order.move", "tablet.order.attack", "tablet.order.patrol", "tablet.order.follow",
        "tablet.order.stop", "tablet.order.hold", "tablet.order.recall", "tablet.order.cancel",
        "tablet.order.build", "tablet.sent.build", "tablet.build.off", "tablet.build.none",
        "tablet.hint.move", "tablet.hint.attack", "tablet.hint.patrol",
        "tablet.follow.on", "tablet.idle", "tablet.none",
        "tablet.sent.move", "tablet.sent.attack", "tablet.sent.patrol",
        "tablet.sent.stop", "tablet.sent.hold", "tablet.sent.follow", "tablet.sent.recall",
        "tablet.noTarget", "tablet.noAnchor",
        "tablet.modules"
    };

    private static String bundle(String file) throws Exception{
        String content = Files.readString(Path.of(file));
        assertFalse(content.isEmpty(), "bundle must be readable: " + file);
        return content;
    }

    @Test
    void everyTabletTextExistsInBothBundles() throws Exception{
        for(String file : files){
            String content = bundle(file);

            for(String key : keys){
                assertTrue(content.contains("\n" + key + " =") || content.startsWith(key + " ="),
                    "missing " + key + " in " + file);
            }
        }
    }

    /** The hint key is built from the enum name, so a renamed constant would silently break it. */
    @Test
    void everyArmedOrderHasItsHint() throws Exception{
        for(String file : files){
            String content = bundle(file);

            for(UnitCommandTablet.Order order : UnitCommandTablet.Order.values()){
                String key = "tablet.hint." + order.name();
                assertTrue(content.contains("\n" + key + " =") || content.startsWith(key + " ="),
                    "missing " + key + " in " + file);
            }
        }
    }

    /** All the orders the user asked for are offered by the tablet. */
    @Test
    void tabletOffersMoveAttackAndPatrolAsMapOrders(){
        var names = new java.util.HashSet<String>();
        for(UnitCommandTablet.Order order : UnitCommandTablet.Order.values()){
            names.add(order.name());
        }

        assertTrue(names.containsAll(java.util.List.of("move", "attack", "patrol")),
            "map orders must stay available: " + names);
    }

    /** The expanded tablet may never grow into a menu below it or past the screen edge. */
    @Test
    void tabletSideRespectsWidthHeightAndMaximum(){
        assertEquals(300f, UnitCommandTablet.calculateSide(800f, 300f, 640f), 0.001f);
        assertEquals(640f, UnitCommandTablet.calculateSide(1200f, 1000f, 640f), 0.001f);
        assertEquals(100f, UnitCommandTablet.calculateSide(800f, 100f, 640f), 0.001f);
        assertEquals(0f, UnitCommandTablet.calculateSide(800f, -20f, 640f), 0.001f);
        assertEquals(0f, UnitCommandTablet.calculateSide(Float.NaN, 300f, 640f), 0.001f);
    }
}
