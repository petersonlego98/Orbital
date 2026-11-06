package orbital.ui;

import arc.*;
import arc.graphics.*;
import arc.scene.style.*;
import arc.util.*;
import orbital.*;
import orbital.core.*;
import orbital.gen.*;
import orbital.graphics.*;
import orbital.mod.Mods.*;

public class Links{
    /** Upstream project this fork is based on. */
    public static final String originalRepo = "https://github.com/Anuken/Mindustry";
    /**
     * Public repository for this fork. Update this constant if the source is
     * hosted somewhere else; it is used by the main menu, the About dialog and
     * the GPLv3 source-code notice.
     */
    public static final String forkRepo = "https://github.com/petersonlego98/Orbital";
    public static final String licenseUrl = "https://www.gnu.org/licenses/gpl-3.0.html";

    private static LinkEntry[] links;

    private static void createLinks(){
        links = new LinkEntry[]{
        new LinkEntry("discord", Vars.discordURL, Icon.discord, Color.valueOf("7289da")),
        new LinkEntry("changelog", forkRepo + "/commits", Icon.list, Pal.accent.cpy()),
        new LinkEntry("trello", "https://trello.com/b/aE2tcUwF", Icon.trello, Color.valueOf("026aa7")),
        new LinkEntry("wiki", "https://mindustrygame.github.io/wiki/", Icon.book, Color.valueOf("0f142f")),
        new LinkEntry("suggestions", forkRepo + "/issues/new", Icon.add, Color.valueOf("ebebeb")),
        new LinkEntry("reddit", "https://www.reddit.com/r/Mindustry/", Icon.redditAlien, Color.valueOf("ee593b")),
        new LinkEntry("itch.io", "https://anuke.itch.io/mindustry", Icon.itchio, Color.valueOf("fa5c5c")),
        new LinkEntry("google-play", "https://play.google.com/store/apps/details?id=io.anuke.mindustry", Icon.googleplay, Color.valueOf("689f38")),
        new LinkEntry("f-droid", "https://f-droid.org/packages/io.anuke.mindustry/", Icon.android, Color.valueOf("026aa7")),
        new LinkEntry("github", forkRepo, Icon.github, Color.valueOf("24292e")),
        new LinkEntry("dev-builds", "https://github.com/Anuken/MindustryBuilds", Icon.githubSquare, Color.valueOf("fafbfc")),
        new LinkEntry("bug", forkRepo + "/issues/new", Icon.wrench, Color.valueOf("cbd97f"))
        };
    }

    public static LinkEntry[] getLinks(){
        if(links == null){
            createLinks();
        }

        return links;
    }

    public static class LinkEntry{
        public final String name, title, description, link;
        public final Color color;
        public final Drawable icon;

        public LinkEntry(String name, String link, Drawable icon, Color color){
            this.name = name;
            this.color = color;
            this.description = Core.bundle.get("link." + name + ".description", "");
            this.link = link;
            this.icon = icon;
            this.title = Core.bundle.get("link." + name + ".title", Strings.capitalize(name.replace("-", " ")));
        }
    }
}
