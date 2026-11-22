package orbital.server;

import arc.*;
import arc.backend.headless.*;
import arc.util.*;
import orbital.*;
import orbital.core.*;
import orbital.ctype.*;
import orbital.game.EventType.*;
import orbital.mod.*;
import orbital.mod.Mods.*;
import orbital.net.Net;
import orbital.net.*;
import orbital.ui.*;

import java.time.*;

import static arc.util.Log.*;
import static orbital.Vars.*;
import static orbital.server.ServerControl.*;

public class ServerLauncher implements ApplicationListener{
    static String[] args;

    public static void main(String[] args){
        try{
            ServerLauncher.args = args;
            Vars.platform = new Platform(){};
            Vars.net = new Net(platform.getNet());

            logger = (level1, text) -> {
                String result = "[" + dateTime.format(LocalDateTime.now()) + "] " + format(tags[level1.ordinal()] + " " + text + "&fr");
                System.out.println(result);
            };
            new HeadlessApplication(new ServerLauncher(), throwable -> CrashHandler.handle(throwable, f -> {}));
        }catch(Throwable t){
            CrashHandler.handle(t, f -> {});
        }
    }

    @Override
    public void init(){
        Core.settings.setDataDirectory(Core.files.local("config"));
        loadLocales = false;
        headless = true;

        Vars.loadSettings();
        Vars.init();

        UI.loadColors();
        Fonts.loadContentIconsHeadless();

        content.createBaseContent();
        mods.loadScripts();
        content.createModContent();
        content.init();
        mods.loadModPatches();

        if(mods.hasContentErrors()){
            err("Error occurred loading mod content:");
            for(LoadedMod mod : mods.list()){
                if(mod.hasContentErrors()){
                    err("| &ly[@]", mod.name);
                    for(Content cont : mod.erroredContent){
                        err("| | &y@: &c@", cont.minfo.sourceFile.name(), Strings.getSimpleMessage(cont.minfo.baseError).replace("\n", " "));
                    }
                }
            }
            err("The server will now exit.");
            System.exit(1);
        }

        bases.load();

        //Persistent squad registry: each group of up to 4 players gets a unique sector seed.
        //Available even without the authoritative event log.
        orbital.net.online.OnlineHolder.installSquads();

        //Optional authoritative event log. Off by default: the legacy snapshot protocol stays the
        //default until the WebSocket transport is selected. Enable with -Dorbital.eventlog=1 and
        //optionally set the directory with -Dorbital.eventlog.dir=... .
        if(System.getProperty("orbital.eventlog") != null){
            try{
                java.nio.file.Path dir = java.nio.file.Path.of(System.getProperty(
                    "orbital.eventlog.dir", Core.files.local("events").absolutePath()));
                orbital.net.events.EventStore store = new orbital.net.events.EventStore(dir);
                store.open();
                orbital.net.events.EventRecorder recorder = new orbital.net.events.EventRecorder(store);
                recorder.install();
                orbital.net.online.OnlineHolder.install(store, recorder);
                if(orbital.net.online.AuthoritativeMode.enabled()){
                    orbital.net.online.TickRng.install(
                        orbital.logic.GlobalVars.rand.seed0, orbital.logic.GlobalVars.rand.seed1);
                }
                arc.util.Log.info("[eventstore] enabled at @ (authoritative=@)", dir,
                    orbital.net.online.AuthoritativeMode.enabled());
            }catch(Exception e){
                err("Could not start the event log: @", e.getMessage());
            }
        }

        Core.app.addListener(new ApplicationListener(){public void update(){ asyncCore.begin(); }});
        Core.app.addListener(logic = new Logic());
        Core.app.addListener(netServer = new NetServer());
        Core.app.addListener(new ServerControl(args));
        Core.app.addListener(new ApplicationListener(){public void update(){ asyncCore.end(); }});

        mods.eachClass(Mod::init);

        Events.fire(new ServerLoadEvent());
    }
}
