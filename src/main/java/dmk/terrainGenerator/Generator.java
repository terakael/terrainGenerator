package dmk.terrainGenerator;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;

import dmk.terrainGenerator.types.Edge;

@Component
public class Generator {
	private final String root = "/home/dan/git/terrainGenerator/src/main/resources/images";
	private final int seed = 0;
	private List<Tile> tiles = null;
	private final int outputWidth = 500;
	private final int outputHeight = 500;
	
	public void generate() throws FileNotFoundException, IOException {
		loadTiles("simple_map.png");
		
		Map<Integer, Map<Edge, Set<Integer>>> matchingTiles = findMatchingTiles();

		int[] outputArray = generateInternal(outputWidth, outputHeight, matchingTiles);
		
		save(outputArray, String.format("output_%d.png", seed));
	}
	
	private void loadTiles(String... filenames) throws IOException {
		tiles = new ArrayList<>();
		for (String filename : filenames) {
			final BufferedImage image = loadImage(filename);
			
			if (image.getHeight() % 3 != 0 || image.getWidth() % 3 != 0)
				throw new IOException("width and height need to be divisible by 3.");
			
			for (int y = 0; y < image.getHeight(); y += 3) {
				for (int x = 0; x < image.getWidth(); x += 3) {
					Tile tile = new Tile(image.getRGB(x, y, 3, 3, null, 0, 3));
					tiles.add(tile);
				}
			}
		}
	}
	
	private void save(int[] outputArray, String filename) throws IOException {
		BufferedImage outImage = new BufferedImage(outputWidth * 3, outputHeight * 3, BufferedImage.TYPE_INT_RGB);
		for (int i = 0; i < outputArray.length; ++i) {
			if (outputArray[i] == -1)
				continue; // if a tile couldn't be placed then the square will remain black
			
			int top = (i / outputWidth) * 3;
			int left = (i % outputWidth) * 3;
			
			for (int yOffset = 0; yOffset < 3; ++yOffset) {
				for (int xOffset = 0; xOffset < 3; ++xOffset) {
					outImage.setRGB(left + xOffset, top + yOffset, tiles.get(outputArray[i]).pixelAt(xOffset, yOffset));
				}
			}
		}
		
		ImageIO.write(outImage, "png", new File(root, filename));
	}
	
	private int[] generateInternal(int width, int height, Map<Integer, Map<Edge, Set<Integer>>> matchingTiles) {
		MyRandom generator = new MyRandom(seed);
		
		int[] outputArray = new int[width * height];
		Arrays.fill(outputArray, -1);
		
		final int startElement = generator.between(0, outputArray.length);
		outputArray[startElement] = new ArrayList<>(matchingTiles.keySet()).get(generator.between(0, matchingTiles.keySet().size()));
		
		Set<Integer> processed = new HashSet<>();
		processed.add(startElement);
		
		Set<Integer> toProcess = new HashSet<>();
		toProcess.addAll(getSiblingElements(startElement, width, height));
		while (!toProcess.isEmpty()) {
			// we copy because we modify the toProcess during this loop so we can't be looping through it at the same time
			final Set<Integer> copy = new HashSet<>(toProcess);
			copy.forEach(element -> {
				System.out.println(String.format("processing element %d", element));
				final List<Integer> siblingElements = getSiblingElementsInOrder(element, width, height);
				
				Set<Integer> potentialFits = new HashSet<>();
				
				for (int i = 0; i < Edge.values().length; ++i) {
					final Integer sibling = siblingElements.get(i);
					if (sibling != null) {
						if (outputArray[sibling] != -1) {
							// list of potential fits for the top sibling (the top sibling's bottom matches)
							final Set<Integer> matches = matchingTiles.get(outputArray[sibling]).get(getOppositeEdge(Edge.values()[i]));
							if (potentialFits.isEmpty())
								potentialFits.addAll(matches);
							else {
								potentialFits.retainAll(matches);
							}
						} else if (outputArray[sibling] == -1 && !processed.contains(sibling)) {
							toProcess.add(sibling);
						}
					} 
				}
				
				if (!potentialFits.isEmpty()) {
					int fit = chooseFitWithRules(generator, new ArrayList<>(potentialFits));
//					outputArray[element] = new ArrayList<>(potentialFits).get(generator.between(0, potentialFits.size()));
					outputArray[element] = fit;
					
//					try {
//						save(outputArray, "output.png");
//						Thread.sleep(1000);
//					} catch (IOException | InterruptedException e) {
//						// TODO Auto-generated catch block
//						e.printStackTrace();
//					}
				} else {
					System.out.println("no potential fits for this element");
				}
				
				toProcess.remove(element);
				processed.add(element);
			});
		}
		
		return outputArray;
	}
	
	private int chooseFitWithRules(MyRandom generator, List<Integer> potentialFits) {
		// if it's full sand then give a higher chance of returning another full sand
		if (potentialFits.contains(27)) {
			if (generator.chance(80))
				return 27;
		}
		
		// if it's full water then give a higher chance of returning another full water
		if (potentialFits.contains(6)) {
			if (generator.chance(95))
				return 6;
		}
		
		// edge tiles have a higher chance to make nicer boundaries
//		List<Integer> edgeTiles = Arrays.asList(0, 1, 2, 5, 7, 10, 11, 12);
//		edgeTiles.retainAll(potentialFits);
//		if (!edgeTiles.isEmpty()) {
//			if (generator.chance(50))
//				return edgeTiles.get(generator.between(0, edgeTiles.size()));
//		} 
		
		// everything else is equal chance
		return potentialFits.get(generator.between(0, potentialFits.size()));
	}
	
	private Edge getOppositeEdge(Edge edge) {
		switch (edge) {
		case top: return Edge.bottom;
		case bottom: return Edge.top;
		case left: return Edge.right;
		case right: return Edge.left;
		}
		return null;
	}
	
	private List<Integer> getSiblingElementsInOrder(int element, int width, int height) {
		final List<Integer> siblings = new LinkedList<>();
		
		// top
		siblings.add(element - width > 0 ? element - width : null);
		
		// left
		siblings.add(element % width != 0 ? element - 1 : null);
		
		// right
		siblings.add(element % width < width - 1 ? element + 1 : null);
		
		// bottom
		siblings.add(element + width < width * height ? element + width : null);
		
		return siblings;
	}
	
	private Set<Integer> getSiblingElements(int element, int width, int height) {
		return getSiblingElementsInOrder(element, width, height).stream()
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());
	}
	
	private Map<Integer, Map<Edge, Set<Integer>>> findMatchingTiles() throws IOException {
		Map<Integer, Map<Edge, Set<Integer>>> matchingTiles = new HashMap<>(); // tileId, <top, left, right, bottom matches>
		
		for (int i = 0; i < tiles.size(); ++i) {
			for (int j = 0; j < tiles.size(); ++j) {
				for (Edge edge : Edge.values()) {
					if (tiles.get(i).matchesEdge(tiles.get(j), edge)) {
						matchingTiles.putIfAbsent(i, new HashMap<>());
						matchingTiles.get(i).putIfAbsent(edge, new HashSet<>());
						matchingTiles.get(i).get(edge).add(j);
					}
				}
			}
		}
		
//		matchingTiles.forEach((tile, edges) -> {
//			System.out.println(tile + ":");
//			edges.forEach((edge, matches) -> {
//				System.out.println(String.format("%s: %s", edge.toString(), String.join(",", matches.stream().map(Object::toString).collect(Collectors.toSet()))));
//			});
//		});
		
		return matchingTiles;
	}
	
	private BufferedImage loadImage(String filename) throws FileNotFoundException, IOException {
		File imageFile = new File(root, filename);
		return ImageIO.read(new FileInputStream(imageFile));
	}
}
