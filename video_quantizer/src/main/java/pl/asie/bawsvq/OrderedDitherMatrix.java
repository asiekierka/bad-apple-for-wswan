package pl.asie.bawsvq;

public class OrderedDitherMatrix {
	private final int[] data;
	private final int span;
	private final int width, height;

	public OrderedDitherMatrix(int[] data) {
		this(data, deriveSpan(data));
	}

	public OrderedDitherMatrix(int[] data, int span) {
		this(data, span, (int) Math.sqrt(data.length));
	}

	public OrderedDitherMatrix(int[] data, int span, int width) {
		this.data = data;
		this.span = span;
		this.width = width;
		this.height = data.length / width;
	}

	public float getRatio(int x, int y) {
		return (float) this.data[(x % width) + ((y % height) * width)] / this.span;
	}

	private static int deriveSpan(int[] data) {
		int max = 0;
		for (int datum : data) {
			if (datum > max) {
				max = datum;
			}
		}
		return max + 1;
	}

	public static final OrderedDitherMatrix MATRIX_NONE = new OrderedDitherMatrix(new int[] {
			0
	});

	public static final OrderedDitherMatrix MATRIX_CHECKS = new OrderedDitherMatrix(new int[] {
			0, 1,
			1, 0
	});

	public static final OrderedDitherMatrix MATRIX_2x2 = new OrderedDitherMatrix(new int[] {
			0, 2,
			3, 1
	});

	public static final OrderedDitherMatrix MATRIX_4x4 = new OrderedDitherMatrix(new int[] {
			0, 8, 2, 10,
			12, 4, 14, 6,
			3, 11, 1, 9,
			15, 7, 13, 5
	});

	public static final OrderedDitherMatrix MATRIX_8x8 = new OrderedDitherMatrix(new int[] {
			0, 48, 12, 60, 3, 51, 15, 63,
			32, 16, 44, 28, 35, 19, 47, 31,
			8, 56, 4, 52, 11, 59, 7, 55,
			40, 24, 36, 20, 43, 27, 39, 23,
			2, 50, 14, 62, 1, 49, 13, 61,
			34, 18, 46, 30, 33, 17, 45, 29,
			10, 58, 6, 54, 9, 57, 5, 53,
			42, 26, 38, 22, 41, 25, 37, 21
	});

	public static final OrderedDitherMatrix MATRIX_4x4_VERTICAL = new OrderedDitherMatrix(new int[] {
			0, 2, 1, 3,
			0, 2, 1, 3,
			0, 2, 1, 3,
			0, 2, 1, 3
	});
}
