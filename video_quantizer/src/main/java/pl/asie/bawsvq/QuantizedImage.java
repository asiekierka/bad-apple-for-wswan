package pl.asie.bawsvq;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.awt.image.BufferedImage;
import java.io.Serial;
import java.io.Serializable;

@EqualsAndHashCode
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class QuantizedImage implements Serializable {
	@Serial
	private static final long serialVersionUID = -7371552472243480321L;
	@Getter
	private final int width, height, steps;
	@Getter
	private final byte[] data;

	public int get(int x, int y) {
		return data[(y * this.width) + x];
	}

	public QuantizedImage flipHorizontal() {
		byte[] newData = new byte[this.width * this.height];
		for (int iy = 0; iy < this.height; iy++) {
			for (int ix = 0; ix < this.width; ix++) {
				newData[(iy * this.width) + (this.width - 1 - ix)] = data[(iy * this.width) + ix];
			}
		}
		return new QuantizedImage(this.width, this.height, this.steps, newData);
	}

	public QuantizedImage flipVertical() {
		byte[] newData = new byte[this.width * this.height];
		for (int iy = 0; iy < this.height; iy++) {
			for (int ix = 0; ix < this.width; ix++) {
				newData[((this.height - 1 - iy) * this.width) + ix] = data[(iy * this.width) + ix];
			}
		}
		return new QuantizedImage(this.width, this.height, this.steps, newData);
	}

	public QuantizedImage invert() {
		byte[] newData = new byte[this.width * this.height];
		for (int iy = 0; iy < this.height; iy++) {
			for (int ix = 0; ix < this.width; ix++) {
				newData[(iy * this.width) + ix] = (byte) (steps - 1 - data[(iy * this.width) + ix]);
			}
		}
		return new QuantizedImage(this.width, this.height, this.steps, newData);
	}

	public QuantizedImage plusOne() {
		byte[] newData = new byte[this.width * this.height];
		for (int iy = 0; iy < this.height; iy++) {
			for (int ix = 0; ix < this.width; ix++) {
				int v = data[(iy * this.width) + ix] + 1;
				while (v >= steps) v -= 1;
				newData[(iy * this.width) + ix] = (byte) v;
			}
		}
		return new QuantizedImage(this.width, this.height, this.steps, newData);
	}

	public QuantizedImage minusOne() {
		byte[] newData = new byte[this.width * this.height];
		for (int iy = 0; iy < this.height; iy++) {
			for (int ix = 0; ix < this.width; ix++) {
				int v = data[(iy * this.width) + ix] - 1;
				while (v < 0) v += 1;
				newData[(iy * this.width) + ix] = (byte) v;
			}
		}
		return new QuantizedImage(this.width, this.height, this.steps, newData);
	}

	public QuantizedImage subview(int x, int y, int width, int height) {
		byte[] newData = new byte[width * height];
		for (int iy = 0; iy < height; iy++) {
			System.arraycopy(data, ((y + iy) * this.width) + x, newData, iy * width, width);
		}
		return new QuantizedImage(width, height, this.steps, newData);
	}

	public BufferedImage toBufferedImage() {
		BufferedImage image = new BufferedImage(this.width, this.height, BufferedImage.TYPE_INT_RGB);
		int i = 0;
		for (int iy = 0; iy < this.height; iy++) {
			for (int ix = 0; ix < this.width; ix++, i++) {
				int v = data[i];
				image.setRGB(ix, iy, (0x10101 * (v * 255 / steps)) | 0xFF000000);
			}
		}
		return image;
	}
}
