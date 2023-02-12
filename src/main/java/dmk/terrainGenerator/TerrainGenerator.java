package dmk.terrainGenerator;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dmk.openSimplex.OpenSimplex2;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

public class TerrainGenerator {
	private int seed;
	public TerrainGenerator(int seed) throws IOException {
		this.seed = seed;
		loadLandProfiles();
	}
	
	private final double featureSize = 256;
	private JsonNode profileRoot = null;
	
	private final Map<String, Boolean> toggles = new HashMap<>();
	
	@Getter
	@RequiredArgsConstructor
	private enum Land {
		OCEAN(0x000066),
	    WATER(0x1f20b7),
	    SWAMP_WATER(0x00523d),
	    LAVA(0x990000),
	    
	    SAND(0xbdb15e),
	    DIRT(0x9f773e),
	    MUD(0x524113),
	    ROCK(0xc68930),
	    ICE(0xccccff),
	    VOLCANIC_ROCK(0x222222),
	    
	    GRASS(0x2d6512),
	    SWAMP_GRASS(0x117733),
	    SNOWY_GRASS(0xaaffaa),
	    FOREST(0x183f05),
	    SNOWY_FOREST(0x559955),
	    
	    SNOW(0xffffff),
	    
	    TEST_MAGENTA(0xff00ff),
	    TEST_YELLOW(0xffff00),
	    TEST_CYAN(0x00ffff)
	    ;
		private final int color;
	}
	
	private enum RenderMode {
		COLOR_ONLY, NOISE_ONLY;//, OVERLAY;
		
		private static RenderMode[] vals = values();
	    public RenderMode next()
	    {
	        return vals[(this.ordinal()+1) % vals.length];
	    }
	}
	
	private int renderTarget = 0;
	
	private RenderMode renderMode = RenderMode.COLOR_ONLY;
	
	public BufferedImage generate(int left, int top, int width, int height, double scale) {
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		
		for (int y = top; y < top + height; ++y) {
			for (int x = left; x < left + width; ++x) {
				// while we wanna be able to position the start x/y anywhere (including negatives),
				// of course reading/writing to an array requires a 0-based local position.
				final int localX = x - left;
				final int localY = y - top;
				
//				double tide = -curve(
//						avg(getNoise(seed + 4, scale, 1000, 3, x, y),
//							getNoise(seed + 5, scale, 3000, 3, x, y),
//							getNoise(seed + 11, scale, 500, 3, x, y)), 1.5);
				
				double elevation = getElevation(seed, scale, x, y);
//				double humidity = curve(getElevation(seed + 1, scale, x, y), 1.5);
				
				double humidity = avg(
						getNoise(seed + 7, scale, 1200, 1, x, y),
						getNoise(seed + 7, scale, 600, 1, x, y),
						getNoise(seed + 8, scale, 1200, 1, x, y));
				humidity = curve(humidity, 2);

				double volcanicActivity = curve(
						avg(getNoise(seed + 9, scale, 1000, 1, x, y),
							getNoise(seed + 10, scale, 1000, 2, x, y)), 0.5);
				volcanicActivity = volcanicActivity < -0.8 ? -curve((volcanicActivity + 1) * 5, 1) : 1;
				
				//int color = getColor(h, tide, humidity, volcanicActivity, renderMode);
				int color = getColorSimple(elevation, humidity, volcanicActivity, renderMode);
//				int color = chooseLand(h, tide, humidity, volcanicActivity, renderMode);
				img.setRGB(localX, localY, color);
			}
		}
		
		return img;
	}
	
	private double getElevation(int seed, double scale, int x, int y) {
		double elevation = curve(getNoise(seed, scale, 50, 4, x, y), 1);
		double s1 = curve(OpenSimplex2.noise3_ImproveXY(seed+1, (x * scale) / 256, (y * scale) / 256, elevation), 1);
		double s2 = curve(OpenSimplex2.noise3_ImproveXY(seed+2, (x * scale) / 256, (y * scale) / 256, elevation), 1);
		double s = avg(s1, s2);
		
		double elevation2 = curve(getNoise(seed+3, scale, 200, 6, x, y), 0.5);
		double l1 = curve(OpenSimplex2.noise3_ImproveXY(seed+4, (x * scale) / 1024, (y * scale) / 1024, elevation2), 0.5);
		double l2 = curve(OpenSimplex2.noise3_ImproveXY(seed+5, (x * scale) / 1024, (y * scale) / 1024, elevation2), 0.5);
		double l = avg(l1, l2);
		
		double e = (avg(elevation, elevation2) + 1) * 0.5; // 0-1
		
		return curve(avg(s, l), 0.75 + (e/2));
	}
	
	private int chooseLand(double height, double tide, double humidity, double volcanicActivity, RenderMode renderMode) {
		if (renderMode == RenderMode.NOISE_ONLY)
			return 0x010101 * (int)((getRenderTarget(height, tide, humidity, volcanicActivity) + 1) * 127.5);
		
		Map<String, Double> scores = new HashMap<>();
		
		profileRoot.fieldNames().forEachRemaining(landName -> {
			scores.put(landName, 
					getScore(height, profileRoot.get(landName).get("height"))
					+ getScore(tide, profileRoot.get(landName).get("tide"))
					+ getScore(humidity, profileRoot.get(landName).get("humidity"))
					+ getScore(volcanicActivity, profileRoot.get(landName).get("volcanicActivity")));
		});
		
		final String highestScoreLand = Collections.max(scores.entrySet(), Comparator.comparingDouble(Map.Entry::getValue)).getKey();
		int color;
		if (scores.get(highestScoreLand) == 0) {
			color = Land.TEST_MAGENTA.color;
		} else
			color = Integer.decode(profileRoot.get(highestScoreLand).get("color").asText());
		
		return renderMode == RenderMode.COLOR_ONLY 
				? color 
				: manipulateColor(color, getRenderTarget(height, tide, humidity, volcanicActivity));
	}
	
	private double getScore(double input, JsonNode profile) {
		if (profile == null)
			return 0;
		
		final double idealPosition = profile.get("idealPosition").asDouble();
		final double anchor = profile.get("anchor").asDouble();
		final double variance = profile.get("variance").asDouble();
		final double decayCurve = profile.get("decayCurve").asDouble();
		
		final double minBound = idealPosition - (variance * anchor);
		final double maxBound = idealPosition + (variance * (1 - anchor));
		
		if (input < minBound || input > maxBound)
			return 0;
		
		return 1 - Math.pow(Math.abs(idealPosition - input) / variance, decayCurve);
	}
	
	private int getColorSimple(double height, double humidity, double volcanicActivity, RenderMode renderMode) {
		if (renderMode == RenderMode.NOISE_ONLY)
			return 0x010101 * (int)((humidity + 1) * 127.5);
		
		double ocean = -1.0;
//		double water = -0.7 + (humidity * 0.2); // y=0.4(x+0.8)^{0.45}-1
//		double sand = -0.2 + (humidity * 0.4); // y=0.95(x+0.95)^{0.27}-1
//		double grass = -0.1 - (humidity * 0.4); // y=-(x+0.95)^{0.15}+1
//		double forest = 0.4 - (humidity * 0.4); // y=-0.8(x+0.8)^{0.43}+1
//		double rock = 0.7 + (humidity * 0.3); // y=(x+1.1)^{0.1}
//		double snow = 0.7 - (humidity * 0.4); // y=-0.6(x+0.3)^{0.1}+1.5
		
		double water = 0.2 * Math.pow((humidity+1), 0.3) - 1;
		double sand = 0.9 * Math.pow((humidity+1), 0.5) - 1;
		double grass = -1.2 * Math.pow((humidity+1), 0.15)+1.1;
		double forest = -Math.pow(humidity, 3)+0.35;
		double rock = 0.8 * Math.pow((humidity+1.1), 0.3);
		double snow = -0.6 * Math.pow((humidity+0.6), 0.3)+1.4;
		
		if (height >= snow)
			return height < rock 
					? Land.SNOW.getColor() 
					: Land.ICE.getColor();
		
		if (height >= rock)
			return Land.ROCK.getColor();
		
		if (height >= forest)
			return height < sand
					? Land.SWAMP_GRASS.getColor()
					: Land.FOREST.getColor();
		
		if (height >= grass)
			return height < sand
					? Land.MUD.getColor()
					: Land.GRASS.getColor();
		
		if (height >= sand)
			return Land.SAND.getColor();
		
		if (height >= water)
			return Land.WATER.getColor();
		
		return Land.OCEAN.getColor();
	}

	private int getColor(double height, double tide, double humidity, double volcanicActivity, RenderMode renderMode) {
		if (renderMode == RenderMode.NOISE_ONLY)
			return 0x010101 * (int)((getRenderTarget(height, tide, humidity, volcanicActivity) + 1) * 127.5);
		
		// tide: +/-0.5
		double scaledTide = tide / 2.0;
		double scaledHumidity = humidity / 2.0;
		
		Land land = Land.GRASS;
		
		if (height < -0.8 - (scaledTide * 0.5) + (scaledHumidity * 0.25))
			land = Land.OCEAN;
		
		// -0.8 -> -0.5
		else if (height < -0.5 + scaledTide + (scaledHumidity * 0.5)) {
			land = humidity > 0.035
					? height < -0.55
						? height < -0.65
							? Land.SWAMP_WATER
							: Land.MUD
						: Land.SWAMP_GRASS
					: Land.WATER;
		}
		
		// -0.5 -> -0.4
		else if (height < -0.4 + (scaledTide * 0.5) - scaledHumidity) {
			land = humidity > 0.01
					? height < -0.55
						? Land.MUD
						: Land.WATER
					: Land.SAND;
		}
		
		// -0.4 - +0.4
		else if (height < 0.4 - (scaledHumidity * 0.5)) {
			land = humidity > 0
					? height < -0.45
						? Land.MUD
						: height < 0 && humidity > 0.1
							? Land.SWAMP_GRASS
							: Land.GRASS
					: Land.GRASS;
		}
		
		// 0.4 - 0.7
		else if (height < 0.7 - (humidity * 0.25)) {
			land = volcanicActivity > 0
					? height > 0.65
						? Land.DIRT
						: Land.FOREST
					: humidity > 0 && height > 0.65
						? Land.SNOWY_FOREST
						: Land.FOREST;
		}
		
		// 0.7 -> 0.72
		else if (height < 0.72) {
			land = volcanicActivity > 0.1 
					? Land.VOLCANIC_ROCK 
					: humidity > 0.1
						? Land.SNOWY_FOREST
						: Land.SNOW;
		}
		
		// 0.72 -> 0.78
		else if (height < 0.78) {
			land = volcanicActivity > 0.05 
					? Land.VOLCANIC_ROCK 
					: volcanicActivity > 0 
						? Land.ROCK 
						: humidity > 0.1
							? Land.SNOWY_FOREST
							: humidity < 0
								? Land.SNOWY_GRASS
								: Land.SNOW;
		}
		
		// 0.78 -> 0.82
		else if (height < 0.82) {
			land = volcanicActivity > 0 
					? Land.VOLCANIC_ROCK 
					: volcanicActivity < -0.1 
						? humidity < 0
							? Land.SNOWY_GRASS
							: Land.SNOW 
						: Land.ROCK;
		}
		
		else {
			land = volcanicActivity > 0 
					? Land.LAVA 
					: volcanicActivity < -0.1
						? Land.ICE 
						: Land.ROCK;
		}
		
		return renderMode == RenderMode.COLOR_ONLY 
				? land.color 
				: manipulateColor(land.color, getRenderTarget(height, tide, humidity, volcanicActivity));
	}
	
    public static int manipulateColor(int color, double factor) {
    	Color oldColor = new Color(color);
    	
    	int r = (int)Math.max(Math.min(oldColor.getRed() - (255 * curve(factor, 0.5)), 255), 0);
    	int g = (int)Math.max(Math.min(oldColor.getGreen() - (255 * curve(factor, 0.5)), 255), 0);
    	int b = (int)Math.max(Math.min(oldColor.getBlue() - (255 * curve(factor, 0.5)), 255), 0);
    	
        return new Color(r, g, b).getRGB();
    }
    
    private double getRenderTarget(double... targets) {
    	if (renderTarget > targets.length || renderTarget == 0)
    		return targets[0];
    	return targets[renderTarget - 1];
    }
    
    public boolean setRenderTarget(int target) {
    	if (target == renderTarget)
    		return false;
    	renderTarget = target;
    	return true;
    }
	
	private static double avg(double... noises) {
		return Arrays.stream(noises).average().orElse(0);
	}
	
	private static double curve(double inp, double pow) {
		return Math.pow(Math.abs(inp), pow) * (inp < 0 ? 1 : -1);
	}
	
	private double getNoise(int seed, double scale, double n, int iterations, int x, int y) {
		double nx = (x * scale) / n;
		double ny = (y * scale) / n;
		
		double cumulative = 0;
		double div = 0;
		for (int i = 0; i < iterations; ++i) {
			cumulative += OpenSimplex2.noise2(seed, (1 << i) * nx, (1 << i) * ny) * (1 / (double)(i+1));
			div += 1 / (double)(i+1);
		}
		
		return Math.max(-1, Math.min(1, cumulative / div));
	}
	
	public void setToggle(String toggleName, boolean toggle) {
		toggles.put(toggleName, toggle);
	}
	
	public void toggle(String toggleName) {
		setToggle(toggleName, !getToggle(toggleName));
		System.out.println(String.format("%s set to %b.", toggleName, getToggle(toggleName)));
	}
	
	public boolean getToggle(String toggleName) {
		return toggles.containsKey(toggleName) && toggles.get(toggleName);
	}
	
	public String cycleRenderMode() {
		renderMode = renderMode.next();
		return renderMode.toString();
	}
	
	public int incrementSeed() {
		return ++seed;
	}
	
	public int decrementSeed() {
		return --seed;
	}
	
	public void loadLandProfiles() throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		File from = new File("/home/dan/git/terrainGenerator/landProfiles.json");
		profileRoot = mapper.readTree(from);
		System.out.println(profileRoot.toPrettyString());
	}
	
	
//	public BufferedImage generate(int left, int top, int width, int height, double scale) {
//	BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
//	
//	for (int y = top; y < top + height; ++y) {
//		for (int x = left; x < left + width; ++x) {
//			// while we wanna be able to position the start x/y anywhere (including negatives),
//			// of course reading/writing to an array requires a 0-based local position.
//			final int localX = x - left;
//			final int localY = y - top;
//			
////			double elevation = getNoise(seed, scale, 50, 4, x, y);
////			double elevation2 = getNoise(seed, scale, 100, 4, x, y);
////			double elevation3 = getNoise(seed, scale, 500, 4, x, y);
//			double elevation = getNoise(seed, scale, 53, 4, x, y);
//			double elevation2 = getNoise(seed, scale, 522, 4, x, y);
//			double elevation3 = getNoise(seed, scale, 5014, 4, x, y);
//			elevation = (elevation + elevation2 + elevation3);
////			double xyscale = Math.pow((((elevation/3) + 1) / 2), 2);
//			
//			// because we're dividing by x and y in the noise3, as we go out further from the centre
//			// the map starts warping beacuse x/y get so big (or so negative).
//			// we give it a nice curve using sine so it stays relatively constant forever.
//			double cappedx = Math.sin(Math.abs((double)x * scale) / 2200) * 500;
//			double cappedy = Math.cos(Math.abs((double)y * scale) / 2200) * 500;
//			
//			double cap = getNoise(seed, scale, 2200*360, 16, x, y) * 500;
//			cappedx = cap;
//			cappedy = cap;
//			
//			
//			double noise = OpenSimplex2.noise3_ImproveXY(seed, cappedx / featureSize, cappedy / featureSize, elevation/2);
//			
//			double coldness = getNoise(seed, scale, 2458, 2, x, y);
//			double coldness2 = getNoise(seed+1, scale, 500, 1, x, y);
//			double coldness3 = getNoise(seed+2, scale, 12518, 6, x, y);
//			coldness3 = (coldness3 + (1 - Math.abs(noise))) / 2;
//			coldness = (((coldness + coldness2 + coldness3) / 3) + Math.abs(noise)) / 2;
//			coldness = Math.pow(Math.abs(coldness), 5);
//			
////			double hotness = getNoise(seed+1, scale, 1500, 4, x, y);
////			hotness = (hotness * (1 - Math.pow(Math.abs(noise), 3))); // if there's cold, then there's defintiely no hot
////			hotness = Math.pow(Math.abs(hotness), 4); // black/white extremes both mean desert, so 0-1 abs
//			
////			double temperature = (coldness - hotness) * 2;
////			temperature = Math.max(-1, Math.min(1, temperature));
////			temperature = Math.pow(Math.abs(temperature), 2) * (temperature < 0 ? -1 : 1);
//			
//			double tide = getNoise(seed, scale, 600, 2, x, y);
//			tide = Math.pow(Math.abs(tide), 3) * (tide < 0 ? 1 : -1);
//			
////			img.setRGB(localX, localY, getColor(noise, false));
//			img.setRGB(localX, localY, getColor(noise, coldness, tide));
//		}
//	}
//	
//	return img;
//}

//public BufferedImage cool1(int left, int top, int width, int height, double scale) {
//	BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
//	
//	for (int y = top; y < top + height; ++y) {
//		for (int x = left; x < left + width; ++x) {
//			// while we wanna be able to position the start x/y anywhere (including negatives),
//			// of course reading/writing to an array requires a 0-based local position.
//			double capx = curve(getNoise(seed, scale, 100, 3, x, y), 0.5);
//			double capy = curve(getNoise(seed+1, scale, 1000, 7, x, y), 0.5);
//			double capz = curve(getNoise(seed+2, scale, 10000, 15, x, y), 0.5);
//			double capw = curve(getNoise(seed+3, scale, 100000, 30, x, y), 0.5);
//			double noise = OpenSimplex2.noise3_ImproveXY(seed, capx + capy, capy * capz, capz + capw);
//			img.setRGB(x - left, y - top, getColor(noise, getToggle("drawColor")));
//		}
//	}
//	
//	return img;
//}

//public BufferedImage _gen_png(int left, int top, int width, int height, double scale) {
//	BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
//	
//	for (int y = top; y < top + height; ++y) {
//		for (int x = left; x < left + width; ++x) {
//			// while we wanna be able to position the start x/y anywhere (including negatives),
//			// of course reading/writing to an array requires a 0-based local position.
//			final int localX = x - left;
//			final int localY = y - top;
//			
//			double elevation = getElevation(x, y, scale);
//			double h = OpenSimplex2.noise3_ImproveXY(seed, (x * scale) / 256, (y * scale) / 256, elevation * 5); // 0-1
//
//			img.setRGB(localX, localY, getColor(h, getToggle("drawColor")));
//		}
//	}
//	
//	return img;
//}

//public BufferedImage original(int left, int top, int width, int height, double scale) {
//	BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
//	
//	for (int y = top; y < top + height; ++y) {
//		for (int x = left; x < left + width; ++x) {
//			// while we wanna be able to position the start x/y anywhere (including negatives),
//			// of course reading/writing to an array requires a 0-based local position.
//			final int localX = x - left;
//			final int localY = y - top;
//			
//			double elevation = getNoise(seed, scale, 50, 4, x, y);
//			double h = OpenSimplex2.noise3_ImproveXY(seed, (x * scale) / 256, (y * scale) / 256, elevation);
//
//			img.setRGB(localX, localY, getColor(h, getToggle("drawColor")));
//		}
//	}
//	
//	return img;
//}
}
