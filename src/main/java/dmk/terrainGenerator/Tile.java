package dmk.terrainGenerator;

import java.io.IOException;

import dmk.terrainGenerator.types.Edge;

public class Tile {
	private final int[] pixels;
	
	public Tile(int... indices) {
		pixels = indices;
	}
	
	public boolean matchesEdge(Tile otherTile, Edge thisEdge) throws IOException {
		switch (thisEdge) {
		case top:
			return pixels[0] == otherTile.pixels[6]
				&& pixels[1] == otherTile.pixels[7]
				&& pixels[2] == otherTile.pixels[8];
			
		case left:
			return pixels[0] == otherTile.pixels[2]
				&& pixels[3] == otherTile.pixels[5]
				&& pixels[6] == otherTile.pixels[8];
			
		case right:
			return pixels[2] == otherTile.pixels[0]
				&& pixels[5] == otherTile.pixels[3]
				&& pixels[8] == otherTile.pixels[6];
			
		case bottom:
			return pixels[6] == otherTile.pixels[0]
				&& pixels[7] == otherTile.pixels[1]
				&& pixels[8] == otherTile.pixels[2];
		}
		
		throw new IOException("invalid edge?");
	}

	public int pixelAt(int x, int y) {
		return pixels[y * 3 + x];
	}
}
