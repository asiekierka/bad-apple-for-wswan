package pl.asie.bawsvq;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class Main {
	private static List<QuantizedImage> loadImages(ImageQuantizer q) {
		AtomicInteger steps = new AtomicInteger();
		return IntStream.rangeClosed(1, 6572)
				.sorted()
				.parallel()
				.mapToObj(i -> {
					try {
						int k;
						if (((k = steps.incrementAndGet()) % 100) == 0) {
							System.out.println("read progress: " + k);
						}
						BufferedImage img = ImageIO.read(new File(String.format("../frames/%05d.png", i)));
						return q.quantize(img);
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				}).toList();
	}

	private static void saveImages(List<QuantizedImage> images, Set<Integer> borderFlipFrames) throws IOException {
		boolean borderIsBlack = true;
		for (int i = 0; i < images.size(); i++) {
			if (i > 0 && (i % 100) == 0) {
				System.out.println("save progress: " + i);
			}
			if (borderFlipFrames.contains(i + 1)) {
				borderIsBlack = !borderIsBlack;
			}
			QuantizedImage img = images.get(i);
			BufferedImage img2 = img.toBufferedImage();
			BufferedImage imgFull = new BufferedImage(224, 144, BufferedImage.TYPE_INT_RGB);
			Graphics2D g2d = imgFull.createGraphics();
			g2d.setColor(borderIsBlack ? Color.BLACK : new Color(0xc0c0c0));
			g2d.fillRect(0, 0, 224, 144);
			g2d.drawImage(img2, 16, 0, null);
			ImageIO.write(imgFull, "PNG", new File(String.format("temp/%05d.png", i)));
		}
	}

	public static void main(String[] args) throws Exception {
		Set<Integer> borderFlipFrames = Set.of(
				57, // w
				443, // b
				820, // w
				1262, // b
				1685, // w
				1742, // b
				2741, // w
				2783, // b
				3319, // w
				3623, // b
				3649, // w
				3675, // b
				3770, // w
				4260, // b
				4627, // w
				5405, // b?
				6139, // w
				6513 // b
		);
		boolean is2bpp = true;

		ImageQuantizer q = new ImageQuantizer(192, 144, is2bpp ? 4 : 2, OrderedDitherMatrix.MATRIX_4x4);
		List<QuantizedImage> images;
		File imageCache = new File("imageCache.bin");
		if (imageCache.exists()) {
			try (FileInputStream fis = new FileInputStream(imageCache); ObjectInputStream ois = new ObjectInputStream(fis)) {
				images = (List<QuantizedImage>) ois.readObject();
			}
		} else {
			images = loadImages(q);
			try (FileOutputStream fos = new FileOutputStream(imageCache); ObjectOutputStream oos = new ObjectOutputStream(fos)) {
				oos.writeObject(images);
			}
			saveImages(images, borderFlipFrames);
		}

		TilePacker packer = new TilePacker(24, 18, q.getSteps(),
				(12000.0 / 159.0) / 30.0);
		int frameCtr = 1;
		for (QuantizedImage img : images) {
			if (borderFlipFrames.contains(frameCtr++)) {
				packer.toggleBorder();
			}
			packer.addImage(img);
		}
		packer.finish();
		try (FileOutputStream fos = new FileOutputStream("../res/tiles.bin")) {
			packer.writeTiles(fos);
		}
		try (FileOutputStream fos = new FileOutputStream("../res/commands.bin")) {
			fos.write(packer.getCommandStream());
		}
	}
}
