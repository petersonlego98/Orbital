package orbital.ui.dialogs;

import arc.*;
import arc.graphics.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import orbital.gen.*;
import orbital.ui.*;

import java.util.regex.*;

import static orbital.Vars.*;

/**
 * Displays the privacy policy that ships with the game.
 *
 * <p>The canonical document is {@code core/assets/privacy.html}. Arc has no HTML renderer and the
 * project ships no WebView, so the document is translated here into the flat blocks the game knows
 * how to draw. Keeping the source as real HTML means the very same file can be read outside the
 * game - in a browser, or in the installation folder - without a second copy drifting apart.</p>
 */
public class PrivacyPolicyDialog extends BaseDialog{
    /** Path of the policy document inside the game assets. */
    public static final String documentPath = "privacy.html";

    /** Block kinds produced by {@link #parse(String)}. */
    public static final int typeTitle = 0, typeHeading = 1, typeText = 2, typeItem = 3;

    private static final String accent = "[accent]", reset = "[]";
    private static final String bullet = "• ";

    /**
     * Sentinels used while converting the document. They sit in the private use area so they are
     * never touched by {@link String#trim()} and can never collide with real content.
     */
    private static final String markTitle = "\uE000", markHeading = "\uE001", markText = "\uE002",
            markItem = "\uE003", markEnd = "\uE004", markAccent = "\uE005", markAccentEnd = "\uE006";

    /** One renderable piece of the policy. */
    public static class Block{
        /** One of {@link #typeTitle}, {@link #typeHeading}, {@link #typeText}, {@link #typeItem}. */
        public final int type;
        public final String text;

        public Block(int type, String text){
            this.type = type;
            this.text = text;
        }
    }

    public PrivacyPolicyDialog(){
        super("@about.privacy.title");

        shown(this::setup);
        onResize(this::setup);
    }

    void setup(){
        cont.clear();
        buttons.clear();

        float w = Core.graphics.isPortrait() ? 400f : 650f;

        Table in = new Table();
        in.margin(12f);
        ScrollPane pane = new ScrollPane(in);
        pane.setFadeScrollBars(false);

        Seq<Block> blocks = parse(readDocument());
        if(blocks.isEmpty()){
            in.add(Core.bundle.get("about.privacy.unavailable")).width(w).wrap().color(Color.lightGray).left().row();
        }

        for(Block block : blocks){
            switch(block.type){
                case typeTitle ->
                    in.add(accent + block.text + reset).style(Styles.outlineLabel).width(w).left().padBottom(4f).row();
                case typeHeading ->
                    in.add(accent + block.text + reset).width(w).wrap().left().padTop(10f).padBottom(4f).row();
                case typeItem ->
                    in.add(bullet + block.text).width(w).wrap().color(Color.lightGray).left().padBottom(2f).row();
                default ->
                    in.add(block.text).width(w).wrap().color(Color.lightGray).left().padBottom(10f).row();
            }
        }

        //Fill the content cell in both directions: with growX() alone the pane keeps the
        //height of its content and spills over the button row on short landscape screens.
        cont.add(pane).grow();

        addCloseButton();

        shown(() -> Time.run(1f, () -> Core.scene.setScrollFocus(pane)));
    }

    /** Reads the policy document from the game assets. */
    public static String readDocument(){
        try{
            return Core.files.internal(documentPath).readString("UTF-8");
        }catch(Exception e){
            return "";
        }
    }

    /**
     * Converts the policy HTML into flat blocks the game can draw.
     *
     * <p>Only the subset of HTML the document actually uses is understood: headings, paragraphs,
     * lists, emphasis and links. Anything else is dropped rather than shown as source code.</p>
     */
    public static Seq<Block> parse(String html){
        Seq<Block> blocks = new Seq<>();
        if(html == null || html.isEmpty()) return blocks;

        String text = html;

        //drop everything that is not content
        text = text.replaceAll("(?is)<!--.*?-->", "");
        text = text.replaceAll("(?is)<style.*?>.*?</style>", "");
        text = text.replaceAll("(?is)<script.*?>.*?</script>", "");
        text = text.replaceAll("(?is)<!DOCTYPE[^>]*>", "");
        text = text.replaceAll("(?is)<head.*?>.*?</head>", "");
        text = text.replaceAll("(?is)</?(html|body|title|meta|head)[^>]*>", "");

        //links keep their address, mail links already carry it as the text
        text = replaceLinks(text);

        //block structure
        text = text.replaceAll("(?is)<h1[^>]*>", markTitle);
        text = text.replaceAll("(?is)</h1>", markEnd);
        text = text.replaceAll("(?is)<h[23][^>]*>", markHeading);
        text = text.replaceAll("(?is)</h[23]>", markEnd);
        text = text.replaceAll("(?is)<p[^>]*>", markText);
        text = text.replaceAll("(?is)</p>", markEnd);
        text = text.replaceAll("(?is)<li[^>]*>", markItem);
        text = text.replaceAll("(?is)</li>", markEnd);
        text = text.replaceAll("(?is)</?(ul|ol|dl|div|section|article|header|footer|main|nav|table|tr|td|th)[^>]*>", markEnd);
        text = text.replaceAll("(?is)<br\\s*/?>", "\n");

        //inline emphasis
        text = text.replaceAll("(?is)<strong[^>]*>", markAccent);
        text = text.replaceAll("(?is)</strong>", markAccentEnd);
        text = text.replaceAll("(?is)<b[^>]*>", markAccent);
        text = text.replaceAll("(?is)</b>", markAccentEnd);
        text = text.replaceAll("(?is)</?(em|i|u|code|pre|kbd|sub|sup|small)[^>]*>", "");

        //any remaining tag is dropped
        text = text.replaceAll("(?s)<[^>]*>", "");
        text = unescape(text);

        for(String chunk : text.split(markEnd)){
            chunk = chunk.trim();
            if(chunk.isEmpty()) continue;

            int type = typeText;
            if(chunk.startsWith(markTitle)){
                type = typeTitle;
                chunk = chunk.substring(markTitle.length());
            }else if(chunk.startsWith(markHeading)){
                type = typeHeading;
                chunk = chunk.substring(markHeading.length());
            }else if(chunk.startsWith(markText)){
                type = typeText;
                chunk = chunk.substring(markText.length());
            }else if(chunk.startsWith(markItem)){
                type = typeItem;
                chunk = chunk.substring(markItem.length());
            }

            chunk = chunk.replace(markAccent, accent).replace(markAccentEnd, reset);
            chunk = normalize(chunk);
            if(chunk.isEmpty()) continue;

            blocks.add(new Block(type, chunk));
        }

        return blocks;
    }

    /** Rewrites links as plain text, keeping the address visible when it adds information. */
    private static String replaceLinks(String html){
        Matcher matcher = Pattern.compile("(?is)<a[^>]*href\\s*=\\s*\"([^\"]*)\"[^>]*>(.*?)</a>").matcher(html);
        StringBuffer out = new StringBuffer();

        while(matcher.find()){
            String href = matcher.group(1).trim();
            String label = matcher.group(2).trim();

            String replacement;
            if(href.isEmpty()){
                replacement = label;
            }else if(href.toLowerCase().startsWith("mailto:")){
                replacement = label.isEmpty() ? href.substring("mailto:".length()) : label;
            }else if(label.isEmpty() || label.equalsIgnoreCase(href)){
                replacement = href;
            }else{
                replacement = label + " (" + href + ")";
            }

            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);

        return out.toString();
    }

    /** Expands the HTML entities used by the document. */
    private static String unescape(String text){
        return text.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&ldquo;", "\u201c")
            .replace("&rdquo;", "\u201d")
            .replace("&lsquo;", "\u2018")
            .replace("&rsquo;", "\u2019")
            .replace("&ndash;", "\u2013")
            .replace("&mdash;", "\u2014")
            .replace("&laquo;", "\u00ab")
            .replace("&raquo;", "\u00bb")
            .replace("&ccedil;", "\u00e7")
            .replace("&atilde;", "\u00e3")
            .replace("&aacute;", "\u00e1")
            .replace("&eacute;", "\u00e9")
            .replace("&iacute;", "\u00ed")
            .replace("&oacute;", "\u00f3")
            .replace("&uacute;", "\u00fa")
            .replace("&ecirc;", "\u00ea")
            .replace("&agrave;", "\u00e0")
            .replace("&otilde;", "\u00f5")
            .replace("&middot;", "\u00b7")
            .replace("&bull;", "\u2022")
            .replace("&hellip;", "\u2026")
            .replace("&copy;", "\u00a9")
            .replace("&reg;", "\u00ae")
            .replace("&times;", "\u00d7");
    }

    /** Collapses whitespace runs while keeping the line breaks of the document. */
    private static String normalize(String text){
        StringBuilder sb = new StringBuilder(text.length());
        boolean pendingSpace = false;

        for(int i = 0; i < text.length(); i++){
            char c = text.charAt(i);

            if(c == '\n'){
                if(sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') sb.append('\n');
                pendingSpace = false;
                continue;
            }
            if(Character.isWhitespace(c)){
                pendingSpace = true;
                continue;
            }
            if(pendingSpace){
                if(sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') sb.append(' ');
                pendingSpace = false;
            }

            sb.append(c);
        }

        return sb.toString().trim();
    }
}
