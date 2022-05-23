package pl.asie.bawsvq;

import com.google.common.io.LittleEndianDataOutputStream;
import it.unimi.dsi.fastutil.bytes.ByteOpenHashSet;
import it.unimi.dsi.fastutil.bytes.ByteSet;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TilePacker {
	private final static int FLIP_H = 0x01000000;
	private final static int FLIP_V = 0x02000000;
	private final static int INVERT = 0x04000000;
	private final static int ID_MASK = 0x00FFFFFF;
	private final int tileWidth, tileHeight, steps;
	private final List<QuantizedImage> globalTiles = new ArrayList<>(524288);
	private final IntSet currentlyDisplayedTiles = new IntOpenHashSet();
	private final int[] displayedTiles;
	private final int[] tileAllocation = new int[512];
	private final Int2IntMap tileGlobalIdToAllocationId = new Int2IntOpenHashMap();
	private final Object2IntMap<QuantizedImage> tileToGlobalId = new Object2IntOpenHashMap<>(524288);
	private QuantizedImage lastImage;
	private final ByteArrayOutputStream cmdOutBytes = new ByteArrayOutputStream();
	private final DataOutputStream cmdOut = new DataOutputStream(cmdOutBytes);
	private int globalTilesEstimatedSize = 0;
	private int lastTileAllocationId = 0;
	private int lastPlacedTileId = -1;
	private boolean lastBorderBlack = true;
	private final double vblsPerFrame;
	private double vblsCounter;

	public TilePacker(int tileWidth, int tileHeight, int steps, double vblsPerFrame) {
		this.tileWidth = tileWidth;
		this.tileHeight = tileHeight;
		this.steps = steps;
		this.lastImage = new QuantizedImage(this.tileWidth * 8, this.tileHeight * 8, steps, new byte[this.tileWidth * this.tileHeight * 64]);
		this.displayedTiles = new int[tileWidth * tileHeight];
		this.vblsPerFrame = vblsPerFrame;

		globalTiles.add(new QuantizedImage(8, 8, steps, new byte[64]));
		tileToGlobalId.put(globalTiles.get(0), 0);
		tileToGlobalId.put(globalTiles.get(0).invert(), INVERT);
		tileGlobalIdToAllocationId.put(0, 0);
		globalTilesEstimatedSize += getTileStorageSize(globalTiles.get(0));
	}

	public void writeTiles(OutputStream stream) throws IOException {
		for (QuantizedImage image : globalTiles) {
			for (int iy = 0; iy < 8; iy++) {
				int v0 = 0;
				int v1 = 0;
				for (int ix = 0; ix < 8; ix++) {
					int v = image.get(ix, iy) ^ 3;
					v0 |= (v & 0x1) << (7 - ix);
					v1 |= (((v >> 1) & 0x1)) << (7 - ix);
				}
				stream.write(v0);
				stream.write(v1);
			}
		}
	}

	public byte[] getCommandStream() throws IOException {
		cmdOut.flush();
		return cmdOutBytes.toByteArray();
	}

	private int getTileStorageSize(QuantizedImage img) {
		return 16;
	}

	private int allocateAllocationIdForTile(int globalId) throws IOException {
		globalId &= ID_MASK;
		int allocationId = tileGlobalIdToAllocationId.getOrDefault(globalId, -1);
		if (allocationId < 0) {
			for (int i = 0; i < tileAllocation.length; i++) {
				int ii = (lastTileAllocationId + i) % tileAllocation.length;
				if (ii == 0) continue;
				if (!currentlyDisplayedTiles.contains(ii)) {
					if (tileGlobalIdToAllocationId.get(tileAllocation[ii]) == ii) {
						tileGlobalIdToAllocationId.remove(tileAllocation[ii]);
					}
					tileAllocation[ii] = globalId;
					tileGlobalIdToAllocationId.put(globalId, ii);
					lastTileAllocationId = ii + 1;
					allocationId = ii | 0x1000;

					break;
				}
			}
			if (allocationId < 0) {
				throw new RuntimeException("!?");
			}
		}
		currentlyDisplayedTiles.add(allocationId & 0xFFF);
		return allocationId;
	}

	public void finish() throws IOException {
		cmdOut.writeByte(0xF0);
	}

	public void toggleBorder() throws IOException {
		if (lastBorderBlack) {
			lastBorderBlack = false;
			cmdOut.writeByte(0xF6);
		} else {
			lastBorderBlack = true;
			cmdOut.writeByte(0xF7);
		}
	}

	public void addImage(QuantizedImage img) throws IOException {
		currentlyDisplayedTiles.clear();
		for (int x : displayedTiles) {
			currentlyDisplayedTiles.add(x);
		}

		int scrpos = 0;
		int tpos = 0;
		int nextScrpos = 0;

		for (int ty = 0; ty < this.tileHeight; ty++, scrpos += 8) {
			for (int tx = 0; tx < this.tileWidth; tx++, scrpos++, tpos++) {
				QuantizedImage lastTile = lastImage.subview(tx * 8, ty * 8, 8, 8);
				QuantizedImage tile = img.subview(tx * 8, ty * 8, 8, 8);
				if (!Objects.equals(tile, lastTile)) {
					int id = tileToGlobalId.getOrDefault(tile, -1);
					if (id < 0) {
						id = globalTiles.size();
						globalTiles.add(tile);
						tileToGlobalId.put(tile, id);
						tileToGlobalId.putIfAbsent(tile.flipHorizontal(), id | FLIP_H);
						tileToGlobalId.putIfAbsent(tile.flipVertical(), id | FLIP_V);
						tileToGlobalId.putIfAbsent(tile.flipHorizontal().flipVertical(), id | FLIP_H | FLIP_V);
						tileToGlobalId.putIfAbsent(tile.invert(), id | INVERT);
						tileToGlobalId.putIfAbsent(tile.flipHorizontal().invert(), id | FLIP_H | INVERT);
						tileToGlobalId.putIfAbsent(tile.flipVertical().invert(), id | FLIP_V | INVERT);
						tileToGlobalId.putIfAbsent(tile.flipHorizontal().flipVertical().invert(), id | FLIP_H | FLIP_V | INVERT);
						globalTilesEstimatedSize += getTileStorageSize(tile);
					}
					int allocId = allocateAllocationIdForTile(id);
					boolean allocNew = (allocId & 0x1000) != 0;
					allocId &= 0xFFF;
					displayedTiles[tpos] = allocId;

					// bank padding
					if ((cmdOutBytes.size() & 0xFFFF) >= 0xFFFA) {
						while ((cmdOutBytes.size() & 0xFFFF) != 0) {
							cmdOut.writeByte(0xF1);
						}
					}

					int tileData = allocId;
					if ((id & FLIP_V) != 0) {
						tileData |= (1 << 11);
					}
					if ((id & FLIP_H) != 0) {
						tileData |= (1 << 10);
					}
					if ((id & INVERT) != 0) {
						tileData |= (1 << 9);
					}
					int idMasked = id & ID_MASK;

					while (scrpos > nextScrpos) {
						if ((scrpos - nextScrpos) > 127) {
							cmdOut.writeByte(127);
							nextScrpos += 127;
//							cmdOut.writeByte(0xF2 | ((tpos - nextTpos) >> 8));
//							cmdOut.writeByte((tpos - nextTpos) & 0xFF);
//							nextTpos += (tpos - nextTpos);
						} else {
							cmdOut.writeByte(scrpos - nextScrpos);
							nextScrpos += (scrpos - nextScrpos);
						}
					}

					// global_id needs 17 bits
					// tile_data needs 12 bits

					if (allocId == 0) {
						// 0xC - 0xD
						cmdOut.writeByte(((id & INVERT) != 0) ? 0xF5 : 0xF4);
					} else if (allocNew) {
						// 0x8 - 0xB
						cmdOut.writeInt(0x80000000 | (tileData << 18) | idMasked);
					} else {
						// 0xE
						cmdOut.writeShort(0xE000 | tileData);
					}
					nextScrpos = scrpos + 1;
				}
			}
		}

		// calculate new border color
		/* float borderColor = 0;
		for (int iy = 0; iy < tileHeight * 8; iy++) {
			borderColor += img.get(0, iy) >= (steps >> 1) ? 1 : 0;
			borderColor += img.get(img.getWidth() - 1, iy) >= (steps >> 1) ? 1 : 0;
		}
		borderColor /= (tileHeight * 8 * 2);
		if (lastBorderBlack && borderColor > 0.8f) {
			lastBorderBlack = false;
			cmdOut.writeByte(0xF6);
		} else if (!lastBorderBlack && borderColor < 0.2f) {
			lastBorderBlack = true;
			cmdOut.writeByte(0xF7);
		} */

		int vblsThisFrame = (int) (vblsCounter + vblsPerFrame);
		vblsCounter += (vblsPerFrame - vblsThisFrame);
		cmdOut.writeByte(0xF8 + vblsThisFrame);

		this.lastImage = img;
		System.out.println(globalTiles.size() + " tiles; " + globalTilesEstimatedSize + " + " + cmdOutBytes.size() + " bytes");
	}
}
