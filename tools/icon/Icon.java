// Draws Devava Notes' icon, a white page with lines and a bookmark on the devava purple, into
// packaging/icon.png and icon.ico (the installer's) and the app's resources (the window's).
// Run from the project folder: java tools/icon/Icon.java
import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Icon {

    public static void main(String[] args) throws IOException {
        ImageIO.write(draw(512), "png", new File("packaging/icon.png"));
        ImageIO.write(draw(256), "png", new File("src/main/resources/com/devavaxp/notes/icon.png"));
        Files.write(Path.of("packaging/icon.ico"), ico(16, 24, 32, 48, 64, 128, 256));
    }

    /** Drawn on 64 units, like Devava Kardex's, at the size asked: sharp at every size. */
    static BufferedImage draw(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.scale(size / 64.0, size / 64.0);
        g.setColor(new Color(0x4F46E5));
        g.fill(new RoundRectangle2D.Double(0, 0, 64, 64, 28, 28));
        g.setColor(Color.WHITE);
        g.fill(new RoundRectangle2D.Double(16, 11, 32, 42, 8, 8));
        g.setColor(new Color(0xC7D2FE));
        g.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(22.5, 29, 41.5, 29));
        g.draw(new Line2D.Double(22.5, 36, 41.5, 36));
        g.draw(new Line2D.Double(22.5, 43, 34, 43));
        Path2D bookmark = new Path2D.Double();
        bookmark.moveTo(35, 11);
        bookmark.lineTo(43, 11);
        bookmark.lineTo(43, 23);
        bookmark.lineTo(39, 19.5);
        bookmark.lineTo(35, 23);
        bookmark.closePath();
        g.setColor(new Color(0x818CF8));
        g.fill(bookmark);
        g.dispose();
        return image;
    }

    /** A Windows .ico holding a PNG of each size (Windows Vista and later read them). */
    static byte[] ico(int... sizes) throws IOException {
        List<byte[]> pngs = new ArrayList<>();
        for (int size : sizes) {
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(draw(size), "png", png);
            pngs.add(png.toByteArray());
        }
        int total = 6 + 16 * sizes.length + pngs.stream().mapToInt(p -> p.length).sum();
        ByteBuffer ico = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        ico.putShort((short) 0).putShort((short) 1).putShort((short) sizes.length);
        int offset = 6 + 16 * sizes.length;
        for (int i = 0; i < sizes.length; i++) {
            ico.put((byte) (sizes[i] >= 256 ? 0 : sizes[i])).put((byte) (sizes[i] >= 256 ? 0 : sizes[i]))
                    .put((byte) 0).put((byte) 0).putShort((short) 1).putShort((short) 32)
                    .putInt(pngs.get(i).length).putInt(offset);
            offset += pngs.get(i).length;
        }
        pngs.forEach(ico::put);
        return ico.array();
    }
}
