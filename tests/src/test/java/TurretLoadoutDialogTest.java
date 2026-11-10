import arc.*;
import arc.func.Boolf;
import arc.scene.style.Drawable;
import arc.scene.ui.layout.Table;
import arc.struct.*;
import arc.util.Nullable;
import arc.util.Time;
import arc.math.*;
import orbital.content.*;
import orbital.ctype.UnlockableContent;
import orbital.entities.units.*;import orbital.gen.*;
import orbital.game.*;
import orbital.type.*;
import orbital.ui.dialogs.TurretLoadoutDialog;
import orbital.world.Block;
import orbital.world.blocks.defense.turrets.*;
import org.junit.jupiter.api.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static orbital.Vars.*;

/**
 * Regression tests for the loadout dialog on hulls that ship with the native Duo.
 *
 * <p>Covers the crash the user reported: after removing the native Duo, clicking the {@code +}
 * square of the slot rail killed the game because the highlight cast {@code Tex.pane} to
 * {@code TextureRegionDrawable}, while it is a scaled nine patch. Those casts are now gone - the
 * highlight goes through {@code tinted()}, which checks the drawable type - and the removal path,
 * the catalogue and the re-armed fire are covered below.</p>
 */
public class TurretLoadoutDialogTest{

    @BeforeAll
    static void setup(){
        ApplicationTests.launchApplication(false);
    }

    @BeforeEach
    void map(){
        world.resize(64, 64);
        world.beginMapLoad();
        world.tiles.fill();
        world.endMapLoad();
    }

    /**
     * Regression for the crash report the user sent: the highlight of an armed slot square threw
     * {@code ClassCastException} (ScaledNinePatchDrawable is not a TextureRegionDrawable) and the
     * game closed. Headless runs have no scene or atlas, so the guard reads the source instead.
     */
    @Test
    void noDrawableCastCanKillTheSlotHighlight() throws Exception{
        File source = new File("../../core/src/orbital/ui/dialogs/TurretLoadoutDialog.java");
        assumeTrue(source.isFile(), "dialog source not reachable from this working directory");

        String text = new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8);
        assertFalse(text.contains("((TextureRegionDrawable)Tex.pane)"),
            "Tex.pane is a scaled nine patch: tint it through tinted(), never cast it");
        assertFalse(text.contains("((TextureRegionDrawable)Tex.whiteui)"),
            "Tex.whiteui must go through tinted() as well");
        assertTrue(text.contains("tinted(Tex.pane, Pal.accent)"),
            "the highlight must be produced by the type-checked helper");
    }

    @Test
    void hullsAcceptTheFootprintTheirContentAlreadyShips(){
        //the rule must agree with what TurretLoadouts already places on each hull: the mono carries
        //the Duo (1x1) by content, so its catalogue has to keep offering a 1x1 after it is removed
        UnitType[] hulls = {
            UnitTypes.mono, UnitTypes.flare, UnitTypes.poly, UnitTypes.alpha, UnitTypes.beta,
            UnitTypes.gamma, UnitTypes.evoke, UnitTypes.incite, UnitTypes.emanate, UnitTypes.horizon,
            UnitTypes.avert, UnitTypes.mega, UnitTypes.zenith, UnitTypes.obviate, UnitTypes.quad
        };

        for(UnitType type : hulls){
            for(TurretSlot slot : type.turretSlots){
                assertTrue(type.fitsSilhouette(slot.maxSize),
                    type.name + " declares a " + slot.maxSize + "x" + slot.maxSize
                    + " slot, so that footprint must fit its silhouette");
            }
        }
    }

    @Test
    void silhouetteRatiosAreMeasuredOnTheSameSpace(){
        //regression: coverage came from spriteArea() (the dorsal budget) while width came from the
        //raw sprite, so a 40px mono failed the width ratio (80%) although a 1x1 covers only 44% of
        //its dorsal area - the catalogue came back empty. Both ratios must use one space.
        for(UnitType type : new UnitType[]{UnitTypes.mono, UnitTypes.flare, UnitTypes.poly, UnitTypes.mega, UnitTypes.quad}){
            float area = type.spriteArea();
            for(int size = 1; size <= 2; size++){
                float footprint = size * TurretSlot.pxTile;
                boolean expected = (footprint * footprint) / area <= 0.5f && footprint / Mathf.sqrt(area) <= 0.7f;
                assertEquals(expected, type.fitsSilhouette(size), type.name + " " + size + "x" + size);
            }
        }
    }

    @Test
    void afterRemovingTheNativeDuoTheCatalogueStillOffersItsFootprint(){
        Unit unit = UnitTypes.mono.create(Team.sharded);
        unit.removeWeapon(nativeTurret(unit));

        //the filter the dialog's catalogue uses: hidden blocks are skipped, except module-only
        //vanilla turrets, which are hidden from construction menus but still offered as modules
        int offer = 0;
        for(Block block : content.blocks()){
            if(!(block instanceof Turret turret) || (block.isHidden() && !block.moduleOnly) || !TurretWeapon.canAdapt(turret)) continue;
            if(!unit.type.hasSlotFor(turret.size) || !unit.type.fitsSilhouette(turret.size)) continue;
            if(!unit.ammoCompatible(turret)) continue;
            offer++;
        }
        assertTrue(offer > 0, "removing the native Duo must leave the hull's 1x1 catalogue open");

        assertTrue(unit.canMount((Turret)Blocks.duo), "the freed slot must accept a module again");
        assertTrue(unit.canMountAt((Turret)Blocks.duo, 0));
        assertNotNull(unit.mountTurret((Turret)Blocks.duo), "the module must mount into the freed slot");
    }

    @Test
    void removingTheDuoKeepsTheHullAbleToFire(){
        Unit unit = UnitTypes.mono.create(Team.sharded);
        unit.removeWeapon(nativeTurret(unit));

        Unit enemy = UnitTypes.mono.create(Team.crux);
        unit.add();
        enemy.add();
        state.teams.updateTeamStats();
        unit.set(0f, 0f);
        enemy.set(40f, 0f);

        assertNotNull(unit.mountTurret((Turret)Blocks.duo), "the hull must be able to re-arm itself");

        int shots = 0;
        for(int i = 0; i < 400; i++){
            Time.update();
            if(unit.controller() != null) unit.controller().updateUnit();
            for(int k = 0; k < unit.mounts.length; k++){
                unit.mounts[k].weapon.update(unit, unit.mounts[k]);
                if(unit.mounts[k].weapon instanceof TurretWeapon) shots = unit.mounts[k].totalShots;
            }
            Groups.bullet.update();
        }
        assertTrue(shots > 0, "the re-armed turret must fire");
    }

    /** @return a project source file, walking upward from the test working directory. */
    private static File source(String relative){
        File dir = new File(System.getProperty("user.dir")).getAbsoluteFile();
        for(int i = 0; i < 5 && dir != null; i++, dir = dir.getParentFile()){
            File file = new File(dir, relative);
            if(file.isFile()) return file;
        }
        return null;
    }

    @Test
    void everyModuleCardAcceptsInfoContent() throws Exception{
        //the grid cards (turrets, weapon modules, support blocks) all carry the (i) affordance:
        //headless has no scene, so the signature is checked on the compiled dialog itself
        assertNotNull(TurretLoadoutDialog.class.getDeclaredMethod("moduleCard",
            Table.class, Drawable.class, String.class, String.class, int.class, float.class,
            Runnable.class, Boolf.class, String.class, UnlockableContent.class));
    }

    @Test
    void infoIconsOpenTheExistingDialogAndItHasAnXClose() throws Exception{
        File dialog = source("core/src/orbital/ui/dialogs/TurretLoadoutDialog.java");
        File info = source("core/src/orbital/ui/dialogs/ContentInfoDialog.java");
        assumeTrue(dialog != null && info != null, "dialog sources not reachable from this working directory");

        String src = new String(Files.readAllBytes(dialog.toPath()), StandardCharsets.UTF_8);
        String inf = new String(Files.readAllBytes(info.toPath()), StandardCharsets.UTF_8);

        assertTrue(src.contains("Icon.info"), "each module card needs an (i) icon");
        assertTrue(src.contains("ui.content.show(content)"), "the icon must open the existing info dialog");
        assertTrue(src.contains("event.stop()"), "the icon click must not mount the module or start a drag");
        assertTrue(src.contains("Touchable.childrenOnly"), "locked cards must keep the info icon clickable");
        assertTrue(src.contains(", turret);") && src.contains(", module.unit);") && src.contains(", block);"),
            "all three catalogues must pass the clicked module's content");

        assertTrue(inf.contains("Icon.cancel"), "the info dialog needs an X close button");
        assertTrue(inf.contains("this::hide"), "the X button must close the dialog");
    }

    /** @return the removable turret the hull carries out of the box, or null. */
    private static @Nullable orbital.type.Weapon nativeTurret(Unit unit){
        for(int i = 0; i < unit.mounts.length; i++){
            if(unit.mounts[i].weapon instanceof TurretWeapon) return unit.mounts[i].weapon;
        }
        return null;
    }
}
