import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.*;
import java.io.*;

/** Temporary diagnostic helper: crops and upscales a screenshot region for UI inspection. */
public class CropImage{
    public static void main(String[] args) throws Exception{
        BufferedImage im = ImageIO.read(new File(args[0]));
        int w = im.getWidth(), h = im.getHeight();
        int x = (int)(w * Double.parseDouble(args[2]));
        int y = (int)(h * Double.parseDouble(args[3]));
        int cw = (int)(w * Double.parseDouble(args[4]));
        int ch = (int)(h * Double.parseDouble(args[5]));
        int scale = Integer.parseInt(args[6]);
        BufferedImage c = im.getSubimage(x, y, cw, ch);
        BufferedImage out = new BufferedImage(c.getWidth() * scale, c.getHeight() * scale, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(c, 0, 0, out.getWidth(), out.getHeight(), null);
        g.dispose();
        ImageIO.write(out, "png", new File(args[1]));
        System.out.println(out.getWidth() + "x" + out.getHeight());
    }
}
