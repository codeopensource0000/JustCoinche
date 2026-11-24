import com.android.ide.common.vectordrawable.Svg2Vector;
import com.android.ide.common.vectordrawable.VdPreview;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Converts each SVG of a folder to an Android vector drawable with the
 * converter Android Studio uses (Svg2Vector, from com.android.tools:sdk-common),
 * and renders each result to a PNG preview, to check the conversion by eye.
 *
 * Usage: java -cp <sdk-common, common, guava, kotlin-stdlib jars> SvgToVector.java <svg folder> <xml folder> <png folder>
 */
public class SvgToVector {
    private static final int PREVIEW_HEIGHT = 480;
    private static final String NOTICE = "<!-- SVG-cards 2.0.1, (C) 2005 David Bellot, GNU LGPL 2.1 or later."
            + " Cut out and converted for JustCoinche: see third_party/svg-cards/README.md. -->\n";

    public static void main(String[] args) throws Exception {
        Path in = Path.of(args[0]);
        Path xmlOut = Files.createDirectories(Path.of(args[1]));
        Path pngOut = Files.createDirectories(Path.of(args[2]));
        List<Path> svgs;
        try (var files = Files.list(in)) {
            svgs = files.filter(p -> p.toString().endsWith(".svg")).sorted().toList();
        }
        for (Path svg : svgs) {
            String name = svg.getFileName().toString().replace(".svg", "");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            String errors = Svg2Vector.parseSvgToXml(svg, bytes);
            // Lint warns about long paths, a cost of the old View drawing; Compose draws each card
            // once into an image at its size and reuses it.
            String xml = NOTICE + bytes.toString(StandardCharsets.UTF_8).replaceFirst(
                    "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"",
                    "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                            + "    tools:ignore=\"VectorPath\"");
            Files.writeString(xmlOut.resolve(name + ".xml"), xml);
            StringBuilder previewErrors = new StringBuilder();
            BufferedImage image = VdPreview.getPreviewFromVectorXml(
                    VdPreview.TargetSize.createFromMaxDimension(PREVIEW_HEIGHT), xml, previewErrors);
            ImageIO.write(image, "png", pngOut.resolve(name + ".png").toFile());
            String problems = (errors + previewErrors).trim();
            System.out.println(name + ": " + xml.length() + " chars" + (problems.isEmpty() ? "" : " | " + problems));
        }
    }
}
