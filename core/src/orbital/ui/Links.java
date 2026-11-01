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
    private static LinkEntry[] links;

    private static void createLinks(){
        links = new LinkEntry[]{
        new LinkEntry("discord", "https://discord.gg/orbital", Icon.discord, Color.valueOf("7289da")),
        new LinkEntry("changelog", "https://github.com/Anuken/Orbital/releases", Icon.list, Pal.accent.cpy()),
        new LinkEntry("trello", "https://trello.com/b/aE2tcUwF", Icon.trello, Color.valueOf("026aa7")),
        new LinkEntry("wiki", "https://orbitalgame.github.io/wiki/", Icon.book, Color.valueOf("0f142f")),
        new LinkEntry("suggestions", "https://github.com/Anuken/Orbital-Suggestions/issues/new/choose/", Icon.add, Color.valueOf("ebebeb")),
        new LinkEntry("reddit", "https://www.reddit.com/r/Orbital/", Icon.redditAlien, Color.valueOf("ee593b")),
        new LinkEntry("itch.io", "https://anuke.itch.io/orbital", Icon.itchio, Color.valueOf("fa5c5c")),
        new LinkEntry("google-play", "https://play.google.com/store/apps/details?id=io.anuke.orbital", Icon.googleplay, Color.valueOf("689f38")),
        new LinkEntry("f-droid", "https://f-droid.org/packages/io.anuke.orbital/", Icon.android, Color.valueOf("026aa7")),
        new LinkEntry("github", "https://github.com/Anuken/Orbital/", Icon.github, Color.valueOf("24292e")),
        new LinkEntry("dev-builds", "https://github.com/Anuken/OrbitalBuilds", Icon.githubSquare, Color.valueOf("fafbfc")),
        new LinkEntry("bug", "https://github.com/Anuken/Orbital/issues/new?assignees=&labels=bug&projects=&template=bug_report.yml", Icon.wrench, Color.valueOf("cbd97f"))
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
