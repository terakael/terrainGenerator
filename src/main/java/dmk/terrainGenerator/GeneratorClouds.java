package dmk.terrainGenerator;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;

import dmk.openSimplex.OpenSimplex2;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Component
public class GeneratorClouds {
	private final String root = "/home/dan/git/terrainGenerator/src/main/resources/images";
	private int seed;
	
	private static final int START_X = 0;
	private static final int START_Y = 0;
	private static final int WIDTH = 500;
	private static final int HEIGHT = 500;
	private static final double FEATURE_SIZE = 256;
	
	private static final double SNOW_CLIMATE = 0.2;
	private static final double DESERT_CLIMATE = 0.8;
	
	private static final Land[][] landData = new Land[WIDTH][HEIGHT];
	
	@Getter
	@RequiredArgsConstructor
	private enum Land {
		WATER(0x1f20b7, 0, 0, 0),
		BEACH(0xbdb15e, 0.2, 0.2, 0),
		FOREST(0x2d6512, 0.3, 0.2, 0.4),
		JUNGLE(0x183f05, 0.9, 0.2, 0.1),
		
		DIRT(0x9f773e, 0.15, 0.2, 0.2),
		ROCK(0xc68930, 0, 0.4, 0),
		SNOW(0xffffff, 0.1, 0.1, 0.2),
		ICE(0xccccff, 0, 0, 0);
		
		private final int color;
		private final double treeChance; // 0 being never, 1 being always
		private final double rockChance;
		private final double flowerChance;
	}
	
	public void generate(int seed) throws FileNotFoundException, IOException {
		this.seed = seed;
		
//		double[] rockMultipliers = new double[] {1, 4};
		
		BufferedImage landImage = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
//		BufferedImage treeImage = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
//		BufferedImage rockImage = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
//		BufferedImage flowerImage = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
//		BufferedImage luckImage = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
		BufferedImage depthImage = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
		final Instant start = Instant.now();
		
		forEachTile((x, y) -> {
			// while we wanna be able to position the start x/y anywhere (including negatives),
			// of course reading/writing to an array requires a 0-based local position.
			final int localX = localX(x);
			final int localY = localY(y);
			
//			double depth = getMap(x, y, 800, new double[] {1, 3, 5});

			double elevation = getElevation(x, y);
			double height = (OpenSimplex2.noise3_ImproveXY(seed, x / FEATURE_SIZE, y / FEATURE_SIZE, elevation) + 1) * 0.5; // 0-1
			
//			int rareBiome = getRareBiome(x, y);
//			if (rareBiome == 1)
//				luckImage.setRGB(localX, localY, 0xffffff);
			
			// 0 is cold af, 1 is hot af
			double climate = 1 - ((getMap(x, y, 100, new double[] {0.1, 1, 3, 5, 10}) + 1) * 0.5);
//
			Land land = getBiome(height, climate);
			
//			Integer treeColor = getTreeColor(x, y, land, height, climate);
//			if (treeColor != null) {
//				treeImage.setRGB(localX, localY, treeColor);
//			} else {
//				Integer rockColor = getRockColor(x, y, land, height, climate);
//				if (rockColor != null) {
//					rockImage.setRGB(localX, localY, rockColor);
//				} else {
//					Integer flowerColor = getFlowerColor(x, y, land, height, climate);
//					if (flowerColor != null) {
//						flowerImage.setRGB(localX, localY, flowerColor);
//					}
//				}
//			}

//			int rgb = 0x010101 * (int)((rock + 1) * 127.5);
			landImage.setRGB(localX, localY, land.getColor());
		});
		
		Duration elapsed = Duration.between(start, Instant.now());
		System.out.println("elapsed: " + elapsed.toMillis() + "ms");
		
//		randomInstance.stream().distinct().forEach(e -> {
//			System.out.println(String.format("%d: %d", e, Collections.frequency(randomInstance, e)));
//		});
		
		ImageIO.write(landImage, "png", new File(root, String.format("land_%d.png", seed)));
//		ImageIO.write(treeImage, "png", new File(root, String.format("trees_%d.png", START_X)));
//		ImageIO.write(rockImage, "png", new File(root, String.format("rocks_%d.png", START_X)));
//		ImageIO.write(flowerImage, "png", new File(root, String.format("flowers_%d.png", START_X)));
//		ImageIO.write(luckImage, "png", new File(root, String.format("luck_%d.png", START_X)));
		ImageIO.write(depthImage, "png", new File(root, String.format("depth_%d.png", seed)));
	}
	
	private int getRareBiome(int x, int y) {
		double luck = (getMap(x, y, 2000, new double[] {1, 5, 9, 12}) + 1) * 0.5; // 0-1
		
		// 3037000498.5618362 is just Math.sqrt(Math.pow(INT_MAX, 2) + Math.pow(INT_MAX, 2))
		// 14142.13562373095 is max dist for 10k/10k
		// 7071.067811865475 is for 5k/5k
		// 3535.5339059327375 is 2.5k/2.5k
//		final double maxDist = 3535.5339059327375; 
//		double distRatio = Math.min(maxDist/12, Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2))) / maxDist;
		
		if (luck > 0.915)
			return 1;
		return 0;	
	}
	
	private Integer getTreeColor(int x, int y, Land land, double height, double climate) {
		double tree = (getTreeMap(x, y) + 1) * 0.5; // 0-1
		
		if (land.getTreeChance() > tree) {
			// for a point of pseudorandomness, mod the decimal places of the tree and moisture
			// also flip the moisture because more moisture should mean more trees
			
			int TREE_NORMAL1 = 0x4c3926;
			int TREE_NORMAL2 = 0x39240f;
			int TREE_NORMAL3 = 0x3b2d1e;
			int TREE_DEAD = 0x2a1908;
			int TREE_OAK = 0x774a1c;
			int TREE_YEW = 0x52271a;
			int TREE_WILLOW = 0x6c543b;
			int TREE_PALM = 0xab4b0b;
			int TREE_CACTUS = 0x598734;
			
			// for a 5000x5000 map,
			// this randomness equation pulls out a number between 0 and 79,
			// where 0 has like 175,000 occurrences and 79 has a bit over 300
			// testing with a 500x500 map, the distribution seems roughly the same in percentage,
			// so with a big enough map I guess we'd see numbers up to 100, just very rarely.
			final int randomness = (Math.abs((int)(x * 3.8)) + Math.abs((int)(y * 0.88))) % (int)Math.ceil(tree * 100);
			
			if (climate < SNOW_CLIMATE) {
				// snow trees, dead trees
				if (randomness == 2 || randomness == 4) {
					return TREE_DEAD;
				}
			} else if (climate > DESERT_CLIMATE) {
				// cacti, dead trees
				
				double desertClimateRange = 1 - DESERT_CLIMATE;
				
				// dirt is < 0.3, sand < 0.6 height
				if (randomness == 4 && height < 0.3) {
					return TREE_DEAD;
				}
				
				if (randomness == 6 && height > 0.3 && height < 0.6 && climate > (DESERT_CLIMATE + (desertClimateRange * 0.5)))
					return TREE_CACTUS;
			} else {
				double mildClimateRangeRadius = (DESERT_CLIMATE - SNOW_CLIMATE) * 0.5;
				double mildClimateOffset = (0.1 - (Math.abs((SNOW_CLIMATE + mildClimateRangeRadius) - climate) / 3)) * 2;
				
				// on the beach
				if (randomness == 7 && height > 0.17 + mildClimateOffset && height < 0.22 + mildClimateOffset && climate > 0.6)
					return TREE_PALM;
				
				// on the grass
				if (height > 0.25 + mildClimateOffset) {
					if (randomness >= 22 && randomness < 25) {
						return TREE_NORMAL1;
					}
					
					if (randomness >= 29 && randomness < 31) {
						return TREE_NORMAL2;
					}
					
					if (randomness >= 35 && randomness < 39) {
						return TREE_NORMAL3;
					}
					
					if (randomness >= 40 && randomness < 42) {
						return TREE_OAK;
					}
					
					if (randomness == 45) {
						return TREE_DEAD;
					}

					// we use such a common number because willow has a bunch of extra special conditions
					if (randomness == 8 && height > 0.25 + mildClimateOffset && height < 0.35 + mildClimateOffset && climate > 0.45 && climate < 0.55)
						return TREE_WILLOW;

					if (randomness == 60) {
						return TREE_YEW;
					}
				}
			}
		}
		
		return null;
	}
	

	private Integer getRockColor(int x, int y, Land land, double height, double climate) {
		double rock = (getMap(x, y, 20, new double[] {1, 3}) + 1) * 0.5; // 0-1; the blacker the more likely of rocks
		if (land.getRockChance() > rock) {
			// based on the rock map and a 500x500 map, this gives a number between 0 (1.1k occurrences) and 39 (5 occurrences)
			final int randomness = (Math.abs((int)(x * 3.8)) + Math.abs((int)(y * 2.21))) % (int)Math.ceil(rock * 100);
			
			final int COPPER = 0x8c3700;
			final int IRON = 0xcccccc;
			final int COAL = 0x222222;
			final int MITH = 0x250060;
			final int ADDY = 0x28991f;
			final int RUNE = 0x00ffff;
			
			if (climate < SNOW_CLIMATE) {
				if (randomness == 7 && height > 0.6 && height < 0.65) {
					return RUNE;
				}
			} else if (climate > DESERT_CLIMATE) {
				if (randomness == 15)
					return COPPER;
				if (randomness == 19)
					return IRON;
				if (randomness == 17)
					return COAL;
				if (randomness == 34)
					return MITH;
				if (randomness == 36)
					return ADDY;
			} else {
				if (climate > 0.6) {
					// not quite desert climate, but let's add a low spread of rocks
					if (randomness == 15)
						return COPPER;
					if (randomness == 19)
						return IRON;
					if (randomness == 17)
						return COAL;
					if (randomness == 39)
						return MITH;
					if (randomness == 42)
						return ADDY;
				}
			}
		}
		
		return null;
	}

	private List<Integer> randomInstance = new ArrayList<>();
	private Integer getFlowerColor(int x, int y, Land land, double height, double climate) {
		double flower = 1 - ((getMap(x, y, 75, new double[] {2, 4, 8}) + 1) * 0.5); // 0-1, where 1 is more flowers
		if (land.getFlowerChance() > flower) {
			final int randomness = (Math.abs((int)(x * 70.1)) + Math.abs((int)(y * 90.8))) % (int)Math.ceil(flower * 100);
			
			final int RED_RUSSINE = 0xff0000;
			final int BLUE_BELL = 0x3333ff;
			final int ORANGE_HARNIA = 0xffffff;
			final int SKYFLOWER = 0xccccff;
			final int STAR_FLOWER = 0x00ffff;
			
			if (climate < SNOW_CLIMATE) {
				if (randomness == 12 && height > 0.4 && height < 0.85)
					return SKYFLOWER; // skyflower glows in icy climates
			} else if (climate > DESERT_CLIMATE) {
				if (randomness == 6 && (height > 0.3 && height < 0.6) || height > 0.8)
					return ORANGE_HARNIA; // harnia grows in the desert dirt
			} else {
				if (randomness == 32)
					return BLUE_BELL;
				
				if (randomness == 35)
					return RED_RUSSINE;
				
				if (randomness == 39)
					return STAR_FLOWER;
			}
		}
		
		return null;
	}
	
	private Land[][] getSurroundingLand(int centreX, int centreY, int rectWidth, int rectHeight) {
		final int localX = localX(centreX);
		final int localY = localY(centreY);
		
		// if we're near the bounds of the landData array, only return the appropriate rows (i.e. returning a smaller subset)
		final int left = Math.max(0, localX - (rectWidth / 2));
		final int right = Math.min(WIDTH, localX + (rectWidth / 2));
		final int top = Math.max(0, localY - (rectHeight / 2));
		final int bottom = Math.min(HEIGHT, localY + (rectHeight / 2));
		
		final int width = right - left;
		final int height = bottom - top;
		
		Land[][] surroundingLand = new Land[width][height];
		for (int y = 0; y < height; ++y) {
			for (int x = 0; x < width; ++x) {
				surroundingLand[x][y] = landData[left + x][top + y];
			}
		}
		
		return surroundingLand;
	}
	
	private void forEachTile(BiConsumer<Integer, Integer> fn) {
		for (int y = START_Y - (HEIGHT / 2); y < (HEIGHT / 2) + START_Y; y++)
		{
			for (int x = START_X - (WIDTH / 2); x < (WIDTH / 2) + START_X; x++) {
				fn.accept(x, y);
			}
		}
	}
	
	private int localX(int x) {
		return x - START_X + (WIDTH / 2);
	}
	
	private int localY(int y) {
		return y - START_Y + (HEIGHT / 2);
	}
	
	private double getElevation(int x, int y) {
		double nx = (x/(double)50);
		double ny = (y/(double)50);

		double pass1 = OpenSimplex2.noise2(seed, nx, ny) * 1;
		double pass2 = OpenSimplex2.noise2(seed, 2 * nx, 2 * ny) * 0.5;
		double pass3 = OpenSimplex2.noise2(seed, 4 * nx, 4 * ny) * 0.25;
		double pass4 = OpenSimplex2.noise2(seed, 10 * nx, 10 * ny) * 0.1;
		
		double elevation = (pass1 + pass2 + pass3 + pass4) / 1.85;
		return Math.max(-1, Math.min(1, elevation));
	}
	
	private double getMoisture(int x, int y) {
		return getMap(x, y, 200, new double[] {1, 2, 4});
	}
	
	private double getTreeMap(int x, int y) {
		double nx = (x/(double)20);
		double ny = (y/(double)20);

		double pass1 = OpenSimplex2.noise2(seed, nx, ny) * 1;
		double pass3 = OpenSimplex2.noise2(seed, 4 * nx, 4 * ny) * 0.25;
		double pass4 = OpenSimplex2.noise2(seed, 10 * nx, 10 * ny) * 0.1;
		
		double elevation = (pass1 + pass3 + pass4) / 1.35;
		return Math.max(-1, Math.min(1, elevation));
	}
	
	private double getMap(int x, int y, double frequency, double[] multipliers) {
		double nx = x/frequency;
		double ny = y/frequency;
		
		double map = 0;
		double cumulativeFraction = 0;
		for (int i = 0; i < multipliers.length; ++i) {
			map += OpenSimplex2.noise2(seed, multipliers[i] * nx, multipliers[i] * ny) * (1 / multipliers[i]);
			cumulativeFraction += 1 / multipliers[i];
		}
		
		return Math.max(-1, Math.min(1, map / cumulativeFraction));
	}
	
	private Land getBiome(double height, double climate) {
		
		if (climate < SNOW_CLIMATE) { // ice biome
			if (height < climate)
				return Land.WATER;
			if (height < 0.25 + (climate * 0.5))
				return Land.ICE;
			if (height < 0.4)
				return Land.SNOW;
			if (height < 0.85)
				return Land.SNOW;
			return Land.ICE;
		} else if (climate < DESERT_CLIMATE) {
			// if we're right between the ice and desert biomes, then there's more water and beach
			double mildClimateRangeRadius = (DESERT_CLIMATE - SNOW_CLIMATE) * 0.5;
			double mildClimateOffset = (0.1 - (Math.abs((SNOW_CLIMATE + mildClimateRangeRadius) - climate) / 3)) * 2;
			
			if (height < 0.15 + mildClimateOffset)
				return Land.WATER;
			if (height < 0.25 + mildClimateOffset)
				return Land.BEACH;
			if (height < 0.8 - (mildClimateOffset * 0.75))
				return (climate < SNOW_CLIMATE + 0.02 || climate > DESERT_CLIMATE - 0.02) ? Land.DIRT : Land.FOREST;
			return (climate > SNOW_CLIMATE + 0.1 && climate < DESERT_CLIMATE - 0.1) ? Land.JUNGLE : Land.FOREST;
		} else { // desert biome
			if (height < 0.3)
				return Land.DIRT;
			if (height < 0.6)
				return Land.BEACH;
			if (height < 0.8)
				return Land.ROCK;
			return Land.DIRT;
		}
	}
}
