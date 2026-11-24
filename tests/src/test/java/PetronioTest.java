import arc.Core;
import arc.graphics.Color;
import orbital.content.*;
import orbital.world.Block;
import orbital.world.blocks.environment.Floor;
import orbital.world.modules.ItemModule;
import org.junit.jupiter.api.*;

import static orbital.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

/** Contrato do Petrônio: minério universal que substitui todos os depósitos. */
class PetronioTest{
    @BeforeAll
    static void setup(){
        ApplicationTests.launchApplication(false);
    }

    @Test
    void itemIsTheHardestGoldOre(){
        assertNotNull(Items.petronio);
        assertEquals(6, Items.petronio.hardness);
        assertEquals(Color.valueOf("ffc52e"), Items.petronio.color);
        assertTrue(Items.universalItems.contains(Items.petronio));
    }

    @Test
    void everyOreDepositNowDropsPetronio(){
        Block[] oldOres = {
            Blocks.oreCopper, Blocks.oreLead, Blocks.oreCoal, Blocks.oreTitanium,
            Blocks.oreThorium, Blocks.oreBeryllium, Blocks.oreTungsten
        };
        for(Block ore : oldOres){
            assertSame(Items.petronio, ore.itemDrop, ore.name + " must drop Petrônio");
            assertFalse(((Floor)ore).oreDefault, ore.name + " must stop generating");
        }
        assertSame(Items.petronio, Blocks.orePetronio.itemDrop);
        assertTrue(((Floor)Blocks.orePetronio).oreDefault, "the new ore generates by default");
    }

    @Test
    void petronioSatisfiesAnyMineralRequirement(){
        ItemModule items = new ItemModule();
        items.add(Items.petronio, 10);

        assertTrue(items.has(Items.copper, 5), "wildcard satisfies copper");
        assertTrue(items.has(Items.thorium, 10), "wildcard satisfies hard ore");
        items.remove(Items.copper, 4);
        assertEquals(6, items.get(Items.petronio), "the wildcard is actually spent");
    }

    @Test
    void codexKeysExistInBothLocales() throws Exception{
        String[] keys = {"petronio.button", "petronio.title", "petronio.text", "item.petronio.name"};
        for(String file : new String[]{"bundles/bundle.properties", "bundles/bundle_pt_BR.properties"}){
            String content = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(file)), java.nio.charset.StandardCharsets.UTF_8);
            for(String key : keys){
                assertTrue(content.contains(key + " ="), "missing " + key + " in " + file);
            }
        }
    }
}
