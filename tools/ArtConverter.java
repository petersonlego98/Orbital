import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Generates the ORBITAL key-art assets from the versioned poster.
 * Run from the repository root: java tools/ArtConverter.java
 * Android launcher icons only:   java tools/ArtConverter.java android
 * No external libraries are required (JDK 17+ single-file source mode).
 */
public class ArtConverter{
    static final Path root = Paths.get(".").toAbsolutePath().normalize();
    static final Path source = root.resolve("core/assets-raw/keyart/orbital-keyart.jpg");

    public static void main(String[] args) throws Exception{
        if(args.length > 0 && args[0].equals("android")){
            writeAndroidIcons();
            System.out.println("done");
            return;
        }

        BufferedImage raw = ImageIO.read(source.toFile());
        if(raw == null) throw new IllegalStateException("cannot decode " + source);

        int w = raw.getWidth(), h = raw.getHeight();
        System.out.println("source: " + w + "x" + h);

        BufferedImage src = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = src.createGraphics();
        g.drawImage(raw, 0, 0, null);
        g.dispose();

        reportPalette(src);
        reportHistogram(src);

        //loose textures: deliberately NOT under sprites/ so the packed atlas (and Fonts.mergeFontAtlas) is untouched
        Files.createDirectories(root.resolve("core/assets/ui"));

        BufferedImage poster = resize(src, 1024, 1024);
        write(poster, root.resolve("core/assets/ui/orbital-poster.png"));

        //wordmark band, with feathered edges so the rectangle melts into the poster background
        BufferedImage wordmark = featherRect(crop(src, 0f, 0.20f, 1f, 0.52f), 0.035f, 0.15f);
        write(wordmark, root.resolve("core/assets/ui/orbital-wordmark.png"));

        //round emblem: the whole key art faded into a disc (a tight crop would slice the "R" of the wordmark)
        BufferedImage mark = featherRadial(resize(src, 512, 512));
        write(mark, root.resolve("core/assets/ui/orbital-mark.png"));

        //runtime window icon (DesktopLauncher uses icons/icon.png)
        write(drawIcon(256), root.resolve("core/assets/icons/icon.png"));

        //jpackage / installer icon: BMP-based ICO, widest Windows compatibility.
        //Every entry is drawn at its own resolution (never a downscale).
        writeIco(new int[]{16, 24, 32, 48, 64, 128, 256}, root.resolve("deploy/orbital.ico"));

        //macOS bundle icon (packr tasks), PNG-based ICNS
        writeIcns(root.resolve("core/assets/icons/icon.icns"));

        System.out.println("done");
    }

    // ---------- palette evidence ----------

    static void reportPalette(BufferedImage src){
        int w = src.getWidth(), h = src.getHeight();
        String[][] regions = {
            {"espaco_topo",      "0.02,0.00,0.30,0.09"},
            {"espaco_esquerdo",  "0.02,0.10,0.14,0.24"},
            {"nebulosa_violeta", "0.55,0.20,0.70,0.28"},
            {"galaxia_dourada",  "0.80,0.14,0.88,0.19"},
            {"anel_ciano_topo",  "0.80,0.16,0.84,0.24"},
            {"anel_ciano_meio",  "0.50,0.465,0.60,0.49"},
            {"logo_prata",       "0.40,0.31,0.50,0.40"},
            {"contorno_azul",    "0.30,0.43,0.42,0.46"},
            {"planeta_azul",     "0.00,0.64,0.14,0.74"},
            {"motor_ciano",      "0.36,0.78,0.46,0.88"},
            {"detalhe_laranja",  "0.10,0.64,0.17,0.67"},
            {"asa_cinza",        "0.44,0.60,0.54,0.68"},
            {"fundo_inferior",   "0.55,0.88,0.75,0.98"}
        };

        System.out.println("--- medias por regiao ---");
        for(String[] r : regions){
            String[] f = r[1].split(",");
            int x0 = (int)(Float.parseFloat(f[0]) * w), y0 = (int)(Float.parseFloat(f[1]) * h);
            int x1 = (int)(Float.parseFloat(f[2]) * w), y1 = (int)(Float.parseFloat(f[3]) * h);
            long sr = 0, sg = 0, sb = 0, n = 0;
            for(int y = y0; y < y1; y += 2){
                for(int x = x0; x < x1; x += 2){
                    int c = src.getRGB(x, y);
                    sr += (c >> 16) & 0xff; sg += (c >> 8) & 0xff; sb += c & 0xff; n++;
                }
            }
            System.out.printf("%-20s #%02x%02x%02x%n", r[0], sr / n, sg / n, sb / n);
        }
    }

    static void reportHistogram(BufferedImage src){
        int w = src.getWidth(), h = src.getHeight();
        Map<Integer, Integer> counts = new HashMap<>();
        for(int y = 0; y < h; y += 8){
            for(int x = 0; x < w; x += 8){
                int c = src.getRGB(x, y);
                int key = ((c >> 16) & 0xf8) << 16 | ((c >> 8) & 0xf8) << 8 | (c & 0xf8);
                counts.merge(key, 1, Integer::sum);
            }
        }
        System.out.println("--- top 14 cores quantizadas ---");
        counts.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
            .limit(14)
            .forEach(e -> System.out.printf("#%06x %d%n", e.getKey(), e.getValue()));
    }

    // ---------- image helpers ----------

    static BufferedImage crop(BufferedImage src, float fx0, float fy0, float fx1, float fy1){
        int w = src.getWidth(), h = src.getHeight();
        int x0 = Math.round(fx0 * w), y0 = Math.round(fy0 * h);
        int x1 = Math.round(fx1 * w), y1 = Math.round(fy1 * h);
        x1 = Math.min(x1, w); y1 = Math.min(y1, h);
        return src.getSubimage(x0, y0, x1 - x0, y1 - y0);
    }

    static BufferedImage resize(BufferedImage src, int tw, int th){
        BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, tw, th, null);
        g.dispose();
        return out;
    }

    static float clamp(float v){ return Math.max(0f, Math.min(1f, v)); }

    static float smooth(float t){ t = clamp(t); return t * t * (3f - 2f * t); }

    static BufferedImage featherRect(BufferedImage src, float fx, float fy){
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for(int y = 0; y < h; y++){
            float dy = Math.min(y, h - 1 - y) / (float)h;
            float ay = smooth(dy / fy);
            for(int x = 0; x < w; x++){
                float dx = Math.min(x, w - 1 - x) / (float)w;
                float a = smooth(dx / fx) * ay;
                int rgb = src.getRGB(x, y) & 0x00ffffff;
                out.setRGB(x, y, rgb | ((int)(a * 255f) << 24));
            }
        }
        return out;
    }

    static BufferedImage featherRadial(BufferedImage src){
        int w = src.getWidth(), h = src.getHeight();
        float cx = (w - 1) / 2f, cy = (h - 1) / 2f, rad = Math.min(w, h) / 2f;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for(int y = 0; y < h; y++){
            for(int x = 0; x < w; x++){
                float d = (float)Math.hypot(x - cx, y - cy) / rad;
                float a = 1f - smooth((d - 0.98f) / 0.44f);
                int rgb = src.getRGB(x, y) & 0x00ffffff;
                out.setRGB(x, y, rgb | ((int)(clamp(a) * 255f) << 24));
            }
        }
        return out;
    }

    static void write(BufferedImage img, Path out) throws Exception{
        Files.createDirectories(out.getParent());
        ImageIO.write(img, "png", out.toFile());
        System.out.printf("wrote %-52s %dx%d %8d bytes%n", root.relativize(out), img.getWidth(), img.getHeight(), Files.size(out));
    }

    // ---------- app icon ----------
    //
    // The previous icon was a plain downscale of the key art. Windows renders app
    // icons at 16-48 px (taskbar, shortcut, Start Menu, title bar, Alt+Tab), and
    // at those sizes the "ORBITAL" wordmark and the fighter became grey noise:
    // the icon looked tiny/blurry instead of a crisp emblem.
    //
    // This draws the emblem procedurally at the final resolution, so every size
    // gets its own simplified version (fewer stars, no moon, thicker strokes)
    // instead of a resampled high-res image. Geometry lives in a 512-unit master
    // space and is scaled by u; anything measured in device pixels goes through
    // `1f / u` so strokes never drop below one pixel.

    static final int ICON_MASTER = 512;
    static final int SPACE_HI = 0x141440, SPACE_MID = 0x080820, SPACE_LO = 0x03030b;
    static final int NEBULA = 0x492a89, NEBULA_DEEP = 0x241a5c, NEBULA_BLUE = 0x14305e, EMBER = 0x6a2f1e;
    static final int CYAN = 0x4fd2ff, ICE = 0x83bff6, STEEL = 0x979bac, AMBER = 0xff8a24;

    static Color argb(int rgb, float alpha){
        return new Color((rgb >> 16) & 0xff, (rgb >> 8) & 0xff, rgb & 0xff, Math.round(clamp(alpha) * 255f));
    }

    static BufferedImage drawIcon(int size){
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        prepare(g, size);
        float u = size / (float)ICON_MASTER;

        drawBackground(g, u);
        drawEmblem(g, u);

        g.dispose();
        return img;
    }

    static Graphics2D prepare(Graphics2D g, int size){
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        float u = size / (float)ICON_MASTER;
        g.scale(u, u);
        return g;
    }

    static void drawBackground(Graphics2D g, float u){
        //full-bleed space background: the shell does not pad icons for us
        g.setPaint(new LinearGradientPaint(0, 0, 0, ICON_MASTER,
            new float[]{0f, 0.55f, 1f},
            new Color[]{argb(SPACE_HI, 1f), argb(SPACE_MID, 1f), argb(SPACE_LO, 1f)}));
        g.fillRect(0, 0, ICON_MASTER, ICON_MASTER);

        nebula(g, 128, 118, 340, NEBULA, 0.55f);
        nebula(g, 404, 166, 270, NEBULA_DEEP, 0.55f);
        nebula(g, 232, 452, 330, NEBULA_BLUE, 0.55f);
        if(u >= 0.15f) nebula(g, 468, 404, 190, EMBER, 0.35f);
        stars(g, u);
    }

    static void drawEmblem(Graphics2D g, float u){
        final float pcx = 262f, pcy = 300f, pr = 148f, rx = 228f, ry = 78f;

        //halo behind the disc
        g.setPaint(new RadialGradientPaint(pcx, pcy, pr * 2.05f, new float[]{0f, 0.5f, 1f},
            new Color[]{argb(CYAN, 0.32f), argb(CYAN, 0.09f), argb(CYAN, 0f)}));
        g.fill(new Ellipse2D.Float(pcx - pr * 2.05f, pcy - pr * 2.05f, pr * 4.1f, pr * 4.1f));

        //ring, far half: everything above the planet's centre passes behind it
        Ellipse2D ring = new Ellipse2D.Float(pcx - rx, pcy - ry, rx * 2f, ry * 2f);
        float stroke = Math.min(Math.max(pr * 0.18f, 2.4f / u), pr * 0.44f);
        g.rotate(-0.30, pcx, pcy);
        g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setPaint(new LinearGradientPaint(pcx - rx, pcy, pcx + rx, pcy,
            new float[]{0f, 0.5f, 1f},
            new Color[]{argb(CYAN, 0.50f), argb(0x9d8bff, 0.50f), argb(AMBER, 0.50f)}));
        g.draw(arc(ring, 180f, 180f));
        g.rotate(0.30, pcx, pcy);

        //planet
        Shape disc = new Ellipse2D.Float(pcx - pr, pcy - pr, pr * 2f, pr * 2f);
        g.setPaint(new RadialGradientPaint(pcx - pr * 0.36f, pcy - pr * 0.42f, pr * 1.5f,
            new float[]{0f, 0.34f, 0.72f, 1f},
            new Color[]{argb(0xe9f5ff, 1f), argb(ICE, 1f), argb(0x40527a, 1f), argb(0x101731, 1f)}));
        g.fill(disc);

        Shape clip = g.getClip();
        g.setClip(disc);
        if(u >= 0.15f){
            //surface: soft belts clipped to the disc, plus a violet storm
            cloud(g, pcx - pr * 0.28f, pcy - pr * 0.42f, pr * 0.72f, pr * 0.17f, 0.16f);
            cloud(g, pcx + pr * 0.20f, pcy - pr * 0.05f, pr * 0.62f, pr * 0.13f, 0.12f);
            cloud(g, pcx - pr * 0.05f, pcy + pr * 0.48f, pr * 0.78f, pr * 0.15f, 0.10f);
            g.setPaint(argb(NEBULA, 0.30f));
            g.fill(new Ellipse2D.Float(pcx + pr * 0.18f, pcy + pr * 0.10f, pr * 0.52f, pr * 0.26f));
        }
        g.setPaint(new LinearGradientPaint(pcx - pr, pcy - pr, pcx + pr * 0.95f, pcy + pr,
            new float[]{0f, 0.45f, 1f},
            new Color[]{argb(0x02030a, 0f), argb(0x02030a, 0f), argb(0x02030a, 0.85f)}));
        g.fill(new Ellipse2D.Float(pcx - pr, pcy - pr, pr * 2f, pr * 2f));
        g.setClip(clip);

        //limb light: reads as a bright rim at every size
        g.setStroke(new BasicStroke(Math.max(1.6f / u, 3.4f)));
        g.setPaint(new RadialGradientPaint(pcx - pr * 0.45f, pcy - pr * 0.5f, pr * 1.95f,
            new float[]{0f, 0.62f, 1f},
            new Color[]{argb(CYAN, 0f), argb(CYAN, 0.10f), argb(ICE, 0.95f)}));
        g.draw(disc);

        //ring, near half + thin inner trace
        g.rotate(-0.30, pcx, pcy);
        g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setPaint(new LinearGradientPaint(pcx - rx, pcy, pcx + rx, pcy,
            new float[]{0f, 0.5f, 1f},
            new Color[]{argb(CYAN, 1f), argb(0xb9c8ff, 1f), argb(AMBER, 1f)}));
        g.draw(arc(ring, 0f, 180f));
        if(u >= 0.15f){
            float irx = rx * 0.82f, iry = ry * 0.82f;
            Ellipse2D inner = new Ellipse2D.Float(pcx - irx, pcy - iry, irx * 2f, iry * 2f);
            g.setStroke(new BasicStroke(Math.max(1.2f / u, 2.6f)));
            g.setPaint(argb(0xffffff, 0.35f));
            g.draw(arc(inner, 0f, 180f));
        }
        g.rotate(0.30, pcx, pcy);

        if(u >= 0.15f){
            float mx = 418f, my = 106f, mr = 31f;
            Shape moon = new Ellipse2D.Float(mx - mr, my - mr, mr * 2f, mr * 2f);
            g.setPaint(new RadialGradientPaint(mx - mr * 0.3f, my - mr * 0.35f, mr * 1.35f,
                new float[]{0f, 0.6f, 1f},
                new Color[]{argb(0xdde4f2, 1f), argb(STEEL, 1f), argb(0x2a3048, 1f)}));
            g.fill(moon);
            g.setStroke(new BasicStroke(Math.max(1.2f / u, 2.8f)));
            g.setPaint(argb(ICE, 0.45f));
            g.draw(moon);
            if(u >= 0.25f){
                g.setPaint(argb(0x22283c, 0.55f));
                g.fill(new Ellipse2D.Float(mx - 9f, my - 5f, 13f, 10f));
                g.fill(new Ellipse2D.Float(mx + 5f, my + 7f, 9f, 7f));
            }
            sparkle(g, 84f, 88f, 34f, 6.5f);
        }
    }

    static Arc2D arc(Ellipse2D ellipse, double start, double extent){
        return new Arc2D.Double(ellipse.getX(), ellipse.getY(), ellipse.getWidth(), ellipse.getHeight(),
                                start, extent, Arc2D.OPEN);
    }

    static void nebula(Graphics2D g, float cx, float cy, float radius, int color, float alpha){
        g.setPaint(new RadialGradientPaint(cx, cy, radius, new float[]{0f, 0.55f, 1f},
            new Color[]{argb(color, alpha), argb(color, alpha * 0.45f), argb(color, 0f)}));
        g.fill(new Ellipse2D.Float(cx - radius, cy - radius, radius * 2f, radius * 2f));
    }

    static void cloud(Graphics2D g, float cx, float cy, float rx, float ry, float alpha){
        g.setPaint(argb(0xffffff, alpha));
        g.fill(new Ellipse2D.Float(cx - rx, cy - ry, rx * 2f, ry * 2f));
    }

    static void stars(Graphics2D g, float u){
        Random rnd = new Random(20261106L);
        int count = u >= 0.25f ? 52 : u >= 0.15f ? 30 : 14;
        for(int i = 0; i < count; i++){
            float x = rnd.nextFloat() * ICON_MASTER;
            float y = rnd.nextFloat() * ICON_MASTER;
            float r = (0.7f + rnd.nextFloat() * 1.1f) / u;
            g.setPaint(argb(i % 4 == 0 ? ICE : 0xffffff, 0.25f + rnd.nextFloat() * 0.7f));
            g.fill(new Ellipse2D.Float(x - r, y - r, 2f * r, 2f * r));
        }
    }

    static void sparkle(Graphics2D g, float cx, float cy, float a, float b){
        Path2D star = new Path2D.Float();
        star.moveTo(cx, cy - a); star.lineTo(cx + b, cy - b); star.lineTo(cx + a, cy); star.lineTo(cx + b, cy + b);
        star.lineTo(cx, cy + a); star.lineTo(cx - b, cy + b); star.lineTo(cx - a, cy); star.lineTo(cx - b, cy - b);
        star.closePath();
        g.setPaint(argb(0xffffff, 0.9f));
        g.fill(star);
    }

    // ---------- Android launcher icons ----------
    //
    // The launcher icon is drawn at each density's final resolution, just like the
    // Windows ICO entries. Adaptive icons (API 26+) use two layers: the full-bleed
    // space background and a transparent foreground whose emblem is scaled into the
    // 72dp safe area of the 108dp canvas. That keeps the rings inside the mask
    // instead of having the launcher crop them.

    static final String[] ANDROID_DENSITIES = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
    static final int[] ANDROID_LEGACY = {48, 72, 96, 144, 192};
    static final int[] ANDROID_ADAPTIVE = {108, 162, 216, 324, 432};
    static final float ANDROID_ART_SCALE = 0.66f;

    static void writeAndroidIcons() throws Exception{
        Path res = root.resolve("android/res");

        for(int i = 0; i < ANDROID_DENSITIES.length; i++){
            Path dir = res.resolve("mipmap-" + ANDROID_DENSITIES[i]);
            BufferedImage legacy = drawIcon(ANDROID_LEGACY[i]);

            //pre-API 26 fallback, including the round variant some launchers request
            write(legacy, dir.resolve("ic_launcher.png"));
            write(round(legacy), dir.resolve("ic_launcher_round.png"));

            //adaptive icon layers at 108dp (the launcher masks the outer bleed)
            write(drawAdaptiveBackground(ANDROID_ADAPTIVE[i]), dir.resolve("ic_launcher_background.png"));
            write(drawAdaptiveForeground(ANDROID_ADAPTIVE[i]), dir.resolve("ic_launcher_foreground.png"));
            write(drawAdaptiveMonochrome(ANDROID_ADAPTIVE[i]), dir.resolve("ic_launcher_monochrome.png"));
        }

        Path anydpi = res.resolve("mipmap-anydpi-v26");
        String xml = adaptiveIconXml();
        writeText(xml, anydpi.resolve("ic_launcher.xml"));
        writeText(xml, anydpi.resolve("ic_launcher_round.xml"));

        //Play Store listing icon: opaque 512x512 PNG
        write(drawIcon(512), root.resolve("android/store/ic_launcher-playstore.png"));
    }

    static BufferedImage drawAdaptiveBackground(int size){
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        prepare(g, size);
        drawBackground(g, size / (float)ICON_MASTER);
        g.dispose();
        return img;
    }

    static BufferedImage drawAdaptiveForeground(int size){
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        float u = adaptiveTransform(g, size);
        drawEmblem(g, u);
        g.dispose();
        return img;
    }

    static BufferedImage drawAdaptiveMonochrome(int size){
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        float u = adaptiveTransform(g, size);
        drawMonochromeEmblem(g, u);
        g.dispose();
        return img;
    }

    /** Centers the 512-unit emblem in the adaptive canvas and scales it into the safe area. */
    static float adaptiveTransform(Graphics2D g, int size){
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        float u = size / (float)ICON_MASTER * ANDROID_ART_SCALE;
        g.translate(size / 2f, size / 2f);
        g.scale(u, u);
        g.translate(-ICON_MASTER / 2f, -ICON_MASTER / 2f);
        return u;
    }

    static void drawMonochromeEmblem(Graphics2D g, float u){
        final float pcx = 262f, pcy = 300f, pr = 148f, rx = 228f, ry = 78f;
        Ellipse2D ring = new Ellipse2D.Float(pcx - rx, pcy - ry, rx * 2f, ry * 2f);
        float stroke = Math.min(Math.max(pr * 0.18f, 2.4f / u), pr * 0.44f);
        Shape disc = new Ellipse2D.Float(pcx - pr, pcy - pr, pr * 2f, pr * 2f);

        g.setPaint(argb(0xffffff, 1f));
        g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        //ring behind the planet
        g.rotate(-0.30, pcx, pcy);
        g.draw(arc(ring, 180f, 180f));
        g.rotate(0.30, pcx, pcy);

        g.fill(disc);

        //ring in front of the planet
        g.rotate(-0.30, pcx, pcy);
        g.draw(arc(ring, 0f, 180f));
        g.rotate(0.30, pcx, pcy);

        float mx = 418f, my = 106f, mr = 31f;
        g.fill(new Ellipse2D.Float(mx - mr, my - mr, mr * 2f, mr * 2f));
    }

    /** Circular legacy icon for launchers that request android:roundIcon on API 25. */
    static BufferedImage round(BufferedImage src){
        int size = src.getWidth();
        BufferedImage mask = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D mg = mask.createGraphics();
        mg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        mg.setColor(Color.WHITE);
        mg.fill(new Ellipse2D.Float(0, 0, size, size));
        mg.dispose();

        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.setComposite(AlphaComposite.DstIn);
        g.drawImage(mask, 0, 0, null);
        g.dispose();
        return out;
    }

    static String adaptiveIconXml(){
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                <background android:drawable="@mipmap/ic_launcher_background"/>
                <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
                <monochrome android:drawable="@mipmap/ic_launcher_monochrome"/>
            </adaptive-icon>
            """;
    }

    static void writeText(String text, Path out) throws Exception{
        Files.createDirectories(out.getParent());
        Files.writeString(out, text, StandardCharsets.UTF_8);
        System.out.printf("wrote %-52s %8d bytes%n", root.relativize(out), Files.size(out));
    }

    // ---------- ICO (BMP/DIB entries) ----------

    static void writeIco(int[] sizes, Path out) throws Exception{
        ByteArrayOutputStream[] dibs = new ByteArrayOutputStream[sizes.length];
        for(int i = 0; i < sizes.length; i++) dibs[i] = dib(drawIcon(sizes[i]));

        ByteArrayOutputStream ico = new ByteArrayOutputStream();
        le16(ico, 0); le16(ico, 1); le16(ico, sizes.length);
        int offset = 6 + 16 * sizes.length;
        for(int i = 0; i < sizes.length; i++){
            ico.write(sizes[i] >= 256 ? 0 : sizes[i]); //width, 0 means 256
            ico.write(sizes[i] >= 256 ? 0 : sizes[i]); //height
            ico.write(0);                              //palette colors
            ico.write(0);                              //reserved
            le16(ico, 1);                              //planes
            le16(ico, 32);                             //bits per pixel
            le32(ico, dibs[i].size());
            le32(ico, offset);
            offset += dibs[i].size();
        }
        for(ByteArrayOutputStream d : dibs) d.writeTo(ico);

        Files.createDirectories(out.getParent());
        Files.write(out, ico.toByteArray());
        System.out.printf("wrote %-52s %d entries %8d bytes%n", root.relativize(out), sizes.length, Files.size(out));
    }

    static ByteArrayOutputStream dib(BufferedImage img){
        int w = img.getWidth(), h = img.getHeight();
        int rowBytes = ((w + 31) / 32) * 4;
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        le32(b, 40);            //BITMAPINFOHEADER size
        le32(b, w);
        le32(b, 2 * h);         //XOR + AND
        le16(b, 1);             //planes
        le16(b, 32);            //bpp
        le32(b, 0);             //BI_RGB
        le32(b, w * h * 4 + rowBytes * h);
        le32(b, 0); le32(b, 0); le32(b, 0); le32(b, 0);

        //XOR bitmap: BGRA, bottom-up
        for(int y = h - 1; y >= 0; y--){
            for(int x = 0; x < w; x++){
                int c = img.getRGB(x, y);
                b.write(c & 0xff);          //B
                b.write((c >> 8) & 0xff);   //G
                b.write((c >> 16) & 0xff);  //R
                b.write(255);               //A (opaque)
            }
        }
        //AND mask: all zero (alpha channel carries transparency)
        for(int y = 0; y < h; y++) for(int i = 0; i < rowBytes; i++) b.write(0);
        return b;
    }

    // ---------- ICNS (PNG entries) ----------

    static void writeIcns(Path out) throws Exception{
        String[] types = {"ic07", "ic08", "ic09", "ic10"};
        int[] dims = {128, 256, 512, 1024};
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for(int i = 0; i < types.length; i++){
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(drawIcon(dims[i]), "png", png);
            body.write(types[i].getBytes(StandardCharsets.US_ASCII));
            be32(body, 8 + png.size());
            png.writeTo(body);
        }
        ByteArrayOutputStream icns = new ByteArrayOutputStream();
        icns.write("icns".getBytes(StandardCharsets.US_ASCII));
        be32(icns, 8 + body.size());
        body.writeTo(icns);

        Files.createDirectories(out.getParent());
        Files.write(out, icns.toByteArray());
        System.out.printf("wrote %-52s %d entries %8d bytes%n", root.relativize(out), types.length, Files.size(out));
    }

    static void le16(ByteArrayOutputStream b, int v){ b.write(v & 0xff); b.write((v >> 8) & 0xff); }
    static void le32(ByteArrayOutputStream b, long v){
        b.write((int)(v & 0xff)); b.write((int)((v >> 8) & 0xff));
        b.write((int)((v >> 16) & 0xff)); b.write((int)((v >> 24) & 0xff));
    }
    static void be32(ByteArrayOutputStream b, int v){
        b.write((v >>> 24) & 0xff); b.write((v >>> 16) & 0xff);
        b.write((v >>> 8) & 0xff); b.write(v & 0xff);
    }
}
