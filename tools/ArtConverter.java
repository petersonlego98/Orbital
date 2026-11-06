import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Generates the ORBITAL key-art assets from the versioned poster.
 * Run from the repository root: java tools/ArtConverter.java
 * No external libraries are required (JDK 17+ single-file source mode).
 */
public class ArtConverter{
    static final Path root = Paths.get(".").toAbsolutePath().normalize();
    static final Path source = root.resolve("core/assets-raw/keyart/orbital-keyart.jpg");

    public static void main(String[] args) throws Exception{
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
        write(resize(src, 256, 256), root.resolve("core/assets/icons/icon.png"));

        //jpackage / installer icon: BMP-based ICO, widest Windows compatibility
        writeIco(src, new int[]{16, 24, 32, 48, 64, 128, 256}, root.resolve("deploy/orbital.ico"));

        //macOS bundle icon (packr tasks), PNG-based ICNS
        writeIcns(src, root.resolve("core/assets/icons/icon.icns"));

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

    // ---------- ICO (BMP/DIB entries) ----------

    static void writeIco(BufferedImage src, int[] sizes, Path out) throws Exception{
        ByteArrayOutputStream[] dibs = new ByteArrayOutputStream[sizes.length];
        for(int i = 0; i < sizes.length; i++) dibs[i] = dib(resize(src, sizes[i], sizes[i]));

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

    static void writeIcns(BufferedImage src, Path out) throws Exception{
        String[] types = {"ic07", "ic08", "ic09", "ic10"};
        int[] dims = {128, 256, 512, 1024};
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for(int i = 0; i < types.length; i++){
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(resize(src, dims[i], dims[i]), "png", png);
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
