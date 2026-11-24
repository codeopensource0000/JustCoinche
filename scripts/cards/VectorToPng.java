import com.android.ide.common.vectordrawable.VdPreview;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Renders vector drawables to PNG images, [height] pixels high, with the
 * renderer of Android Studio's previews (VdPreview, from com.android.tools:sdk-common).
 * Used for the court cards, whose drawings hold paths too long for the compiled
 * resources of an app (see split_long_paths.py).
 *
 * Usage: java -cp <same jars as SvgToVector> VectorToPng.java <height> <png folder> <vector drawable>...
 */
public class VectorToPng {
    public static void main(String[] args) throws Exception {
        int height = Integer.parseInt(args[0]);
        Path out = Files.createDirectories(Path.of(args[1]));
        for (int k = 2; k < args.length; k++) {
            Path xml = Path.of(args[k]);
            StringBuilder errors = new StringBuilder();
            BufferedImage image = VdPreview.getPreviewFromVectorXml(
                    VdPreview.TargetSize.createFromMaxDimension(height), Files.readString(xml), errors);
            if (errors.length() > 0) throw new IllegalStateException(xml + ": " + errors);
            Path png = out.resolve(xml.getFileName().toString().replace(".xml", ".png"));
            ImageIO.write(image, "png", png.toFile());
            System.out.println(png.getFileName() + ": " + image.getWidth() + " x " + image.getHeight()
                    + ", " + Files.size(png) / 1024 + " KB");
        }
    }
}
