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
	private final int width, height, steps;
	private final OrderedDitherMatrix matrix;

	private float asGray(int rgb) {
		float v = ((rgb >> 8) & 0xFF) / 256.0f;
		if (v < 0.1f) {
			return 0.0f;
		} else if (v > 0.9f) {
			return 1.0f;
		} else {
			return (v - 0.1f) * (1f/0.8f);
		}
	}

	public QuantizedImage quantize(BufferedImage src) {
		if (src.getWidth() != this.width || src.getHeight() != this.height) {
			BufferedImage src2 = new BufferedImage(this.width, this.height, BufferedImage.TYPE_INT_RGB);
			Graphics2D g2d = src2.createGraphics();
//			g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			AffineTransform t = AffineTransform.getScaleInstance(
					(float) this.width / src.getWidth(),
					(float) this.height / src.getHeight()
			);
			g2d.drawRenderedImage(src, t);
			src = src2;
		}
		byte[] data = new byte[this.width * this.height];
		int i = 0;
		for (int iy = 0; iy < this.height; iy++) {
			for (int ix = 0; ix < this.width; ix++, i++) {
				float grayValue = asGray(src.getRGB(ix, iy));
				float minValue = grayValue;
				float maxValue = grayValue;
				for (int nx = -1; nx <= 1; nx++) {
					for (int ny = -1; ny <= 1; ny++) {
						int inx = ix+nx;
						int iny = iy+ny;
						if (inx < 0 || iny < 0 || inx >= this.width || iny >= this.height) {
							continue;
						}
						float nGrayValue = asGray(src.getRGB(inx, iny));
						if (nGrayValue < minValue) {
							minValue = nGrayValue;
						}
						if (nGrayValue > maxValue) {
							maxValue = nGrayValue;
						}
					}
				}
				if ((maxValue - minValue) >= 0.6f) {
					grayValue = (grayValue >= 0.5f) ? 1.0f : 0.0f;
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
