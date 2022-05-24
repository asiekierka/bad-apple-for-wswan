package pl.asie.bawsvq;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Getter
@RequiredArgsConstructor
public class ImageQuantizer {
	@FunctionalInterface
	public interface GrayPixelAccess {
		float get(BufferedImage image, int x, int y);
	}

	private final int width, height, steps;
	private final OrderedDitherMatrix matrix;

	private float asGray(int rgb) {
		// float v = ((rgb >> 8) & 0xFF) / 256.0f;
		float v = ((rgb >> 8) & 0xFC) / 252.0f;
		if (v < 0.1f) {
			return 0.0f;
		} else if (v > 0.9f) {
			return 1.0f;
		} else {
			return (v - 0.1f) * (1f/0.8f);
		}
	}

	private float asGray(BufferedImage image, int x, int y) {
		if (x < 0) x = 0;
		else if (x >= image.getWidth()) x = image.getWidth() - 1;
		if (y < 0) y = 0;
		else if (y >= image.getHeight()) y = image.getHeight() - 1;
		return asGray(image.getRGB(x, y));
	}
	private float fromArray(float[] data, int x, int y, int width, int height) {
		if (x < 0) x = 0;
		else if (x >= width) x = width - 1;
		if (y < 0) y = 0;
		else if (y >= height) y = height - 1;
		return data[y * width + x];
	}

	private float laplacian(BufferedImage src, int ix, int iy, GrayPixelAccess getter) {
		return getter.get(src, ix, iy-1)
				+ getter.get(src, ix, iy+1)
				+ getter.get(src, ix-1, iy)
				+ getter.get(src, ix+1, iy)
				- (getter.get(src, ix, iy)*4);
	}

	public QuantizedImage quantize(BufferedImage src) {
		if (src.getWidth() != this.width || src.getHeight() != this.height) {
			BufferedImage src2 = new BufferedImage(this.width, this.height, BufferedImage.TYPE_INT_RGB);
			Graphics2D g2d = src2.createGraphics();
			g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
//			g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			AffineTransform t = AffineTransform.getScaleInstance(
					(float) this.width / src.getWidth(),
					(float) this.height / src.getHeight()
			);
			g2d.drawRenderedImage(src, t);
			src = src2;
		}
		byte[] data = new byte[this.width * this.height];
		int i = 0;
		float[] laplacianData = new float[this.width * this.height];
		for (int iy = 0; iy < this.height; iy++) {
			for (int ix = 0; ix < this.width; ix++, i++) {
				laplacianData[i] = laplacian(src, ix, iy, this::asGray);
			}
		}
		i = 0;
		for (int iy = 0; iy < this.height; iy++) {
			for (int ix = 0; ix < this.width; ix++, i++) {
				float grayValue = asGray(src.getRGB(ix, iy));

				float maxValue = 0.0f;
				float pMaxValue = grayValue;
				float pMinValue = grayValue;
				float pAvgValue = 0.0f;
				for (int nx = -2; nx <= 2; nx++) {
					for (int ny = -2; ny <= 2; ny++) {
						if (Math.abs(nx) == 2 && Math.abs(ny) == 2) continue;
						int inx = ix + nx;
						int iny = iy + ny;
						float pGrayValue = asGray(src, inx, iny);
						pAvgValue += (pGrayValue / 21.0f);
						if (pGrayValue > pMaxValue) {
							pMaxValue = pGrayValue;
						}
						if (pGrayValue < pMinValue) {
							pMinValue = pGrayValue;
						}
					}
				}
				for (int nx = -2; nx <= 2; nx++) {
					for (int ny = -2; ny <= 2; ny++) {
						if (Math.abs(nx) == 2 && Math.abs(ny) == 2) continue;
						int inx = ix+nx;
						int iny = iy+ny;
						// float nGrayValue = laplacian(src, inx, iny, this::asGray);
						float nGrayValue = fromArray(laplacianData, inx, iny, width, height);
						if (nGrayValue > maxValue) {
							maxValue = nGrayValue;
						}
					}
				}

				if (maxValue >= 0.5f) {
					grayValue = (grayValue >= pAvgValue) ? pMaxValue : pMinValue;
				}

				if (this.matrix != null) {
					float steppedValue = grayValue * (steps - 1);
					int svMin = (int) Math.floor(steppedValue);
					int svMax = (int) Math.ceil(steppedValue);
					float matrixPoint = svMin + this.matrix.getRatio(ix, iy);
					int sv = (steppedValue >= matrixPoint) ? svMax : svMin;
					data[i] = (byte) sv;
				} else {
					float steppedValue = grayValue * steps;
					data[i] = (byte) steppedValue;
				}
 			}
		}
		return new QuantizedImage(this.width, this.height, this.steps, data);
	}
}
