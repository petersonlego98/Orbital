import orbital.content.Blocks;
import orbital.world.Block;
import orbital.world.blocks.environment.Floor;
import orbital.type.Category;
import orbital.world.meta.BuildVisibility;
import org.junit.jupiter.api.*;

import static orbital.Vars.content;
import static org.junit.jupiter.api.Assertions.*;

/** Contrato da ocultação de todas as construções de distribuição. */
class DistributionVisibilityTest{
    @BeforeAll
    static void setup(){
        ApplicationTests.launchApplication(false);
    }

    @Test
    void everyDistributionBuildingLeavesTheBuildMenu(){
        int count = 0;
        for(Block block : content.blocks()){
            if(block == null || block.category != Category.distribution || block instanceof Floor) continue;
            count++;
            assertSame(BuildVisibility.hidden, block.buildVisibility, block.name + " must be hidden");
            assertFalse(block.inEditor, block.name + " must leave the editor menu");
        }
        assertTrue(count > 10, "the distribution category has several buildings");
    }

    @Test
    void otherCategoriesKeepTheirVisibility(){
        assertNotSame(BuildVisibility.hidden, Blocks.solarPanel.buildVisibility);
        assertNotSame(BuildVisibility.hidden, Blocks.coreShard.buildVisibility);
    }
}
