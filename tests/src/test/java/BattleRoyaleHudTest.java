import arc.input.KeyCode;
import arc.math.geom.Vec2;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.struct.Seq;
import orbital.content.*;
import orbital.entities.units.*;
import orbital.game.*;
import orbital.gen.*;
import orbital.type.*;
import orbital.ui.AnalogStick;
import orbital.ui.fragments.HudFragment;
import orbital.world.blocks.defense.turrets.Turret;
import org.junit.jupiter.api.*;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static orbital.Vars.*;

/**
 * Contrato do 8x8: o analógico move a unidade (e publica o vetor durante o arraste), o toque
 * na tela não atira, e a barra do HUD mostra o loadout completo da unidade.
 */
public class BattleRoyaleHudTest{

    @BeforeAll
    static void setup(){
        ApplicationTests.launchApplication(false);
    }

    @BeforeEach
    void map(){
        world.resize(96, 96);
        world.beginMapLoad();
        world.tiles.fill();
        world.endMapLoad();
        state.rules = Gamemode.battleRoyale.apply(new Rules());
    }

    /** @return o listener de toque do analógico, que é quem recebe os gestos no widget. */
    private static InputListener touchListener(AnalogStick stick){
        for(var listener : stick.getListeners()){
            if(listener instanceof InputListener input) return input;
        }
        return null;
    }

    @Test
    void stickPublishesTheVectorOnDragEvenWhenGrabbedAtTheCentre(){
        Vec2 sink = new Vec2();
        boolean[] published = {false};
        AnalogStick stick = new AnalogStick(sink, () -> published[0] = sink.len2() > 0.0001f);
        InputListener listener = touchListener(stick);
        assertNotNull(listener, "the stick must have a touch listener");

        float centre = stick.size() / 2f;
        InputEvent event = new InputEvent();

        //um dedo pousa exactly no centro: o vetor é zero e o analógico ainda não está ativo
        assertTrue(listener.touchDown(event, centre, centre, 0, KeyCode.mouseLeft));
        assertEquals(0f, sink.len(), 1e-6f);
        assertFalse(published[0]);

        //arrastar para a direita tem de publicar o vetor: era exatamente aqui que o
        //handler ficava com analogActive=false durante o gesto inteiro
        listener.touchDragged(event, centre + 20f, centre, 0);
        assertTrue(published[0], "a drag must publish the live vector");
        assertTrue(sink.x > 0f, "dragging right must produce a rightward vector");

        listener.touchUp(event, centre + 20f, centre, 0, KeyCode.mouseLeft);
        assertEquals(0f, sink.len(), 1e-6f);
        assertFalse(published[0], "releasing must publish the zero vector");
    }

    @Test
    void stickKeepsScreenUpAsWorldUp(){
        Vec2 sink = new Vec2();
        AnalogStick stick = new AnalogStick(sink, () -> {});
        InputListener listener = touchListener(stick);
        assertNotNull(listener, "the stick must have a touch listener");

        float centre = stick.size() / 2f;
        InputEvent event = new InputEvent();

        //scene2d Y grows upward: dragging the knob up is positive Y in world space too.
        assertTrue(listener.touchDown(event, centre, centre, 0, KeyCode.mouseLeft));
        listener.touchDragged(event, centre, centre + 20f, 0);
        assertTrue(sink.y > 0f, "dragging up must produce an upward vector");

        listener.touchDragged(event, centre, centre - 20f, 0);
        assertTrue(sink.y < 0f, "dragging down must produce a downward vector");

        listener.touchUp(event, centre, centre - 20f, 0, KeyCode.mouseLeft);
        assertEquals(0f, sink.len(), 1e-6f);
    }

    @Test
    void aMountedModuleCanAutofireEvenWhenTheTypeCannotAttack(){
        //mono tem canAttack=false (o Duo é adicionado depois do content init), mas carrega
        //uma arma montada: o autofire do 8x8 precisa ler os mounts, não o tipo.
        Unit mono = UnitTypes.mono.spawn(Team.sharded, 100f, 100f);
        assertFalse(mono.type.canAttack);
        assertTrue(mono.hasMountedGun(), "the mounted Duo must count as a firing weapon");

        //oct não tem armas próprias; depois de montar um módulo passa a ter
        Unit oct = UnitTypes.oct.spawn(Team.sharded, 200f, 200f);
        assertFalse(oct.hasMountedGun());
        assertNotNull(oct.mountTurret((Turret)Blocks.duo));
        assertTrue(oct.hasMountedGun(), "a dorsal module mounted at runtime must be counted");
    }

    @Test
    @SuppressWarnings("unchecked")
    void hudListsTheUnitsOwnGunsInBattleRoyale(){
        //poly tem duas armas fixas e nenhum módulo dorsal: a barra antiga ficava vazia
        Unit poly = UnitTypes.poly.spawn(Team.sharded, 100f, 100f);
        assertTrue(BattleRoyaleMode.active());
        assertTrue(poly.moduleGroups().isEmpty(), "premise: poly has no dorsal module");

        try{
            Method fixed = HudFragment.class.getDeclaredMethod("fixedGuns", Unit.class);
            fixed.setAccessible(true);
            Seq guns = (Seq)fixed.invoke(null, poly);
            assertEquals(2, guns.size, "every fixed mount must be listed, even identical ones");
            for(Object gun : guns){
                assertEquals("poly-weapon", ((WeaponMount)gun).weapon.name);
            }
        }catch(ReflectiveOperationException e){
            fail(e);
        }
    }
}
