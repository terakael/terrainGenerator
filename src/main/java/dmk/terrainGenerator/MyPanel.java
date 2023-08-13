package dmk.terrainGenerator;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import javax.swing.JPanel;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

public class MyPanel extends JPanel {
	@RequiredArgsConstructor
	@Getter
	private class ScaledImage {
		private final double scale;
		private final BufferedImage image;
	}
	
	private static final long serialVersionUID = 1150071354388728635L;
	private final int IMAGE_WIDTH;
	private final int IMAGE_HEIGHT;
	private double currentImageWidth;
	private double currentImageHeight;
	private int WINDOW_WIDTH;
	private int WINDOW_HEIGHT;
	private int OUT_OF_BOUNDS_ROWS = 3; // how many rows of images to render outside the current window
	private final Point origin = new Point(1000029092, 999995106);
	private double scale = 1;
	private double currentScale = scale;
	private Point mousePt;
	private TerrainGenerator terrainGenerator;
	private Map<Integer, Map<Integer, ScaledImage>> images;
	private ThreadPoolExecutor es = (ThreadPoolExecutor)Executors.newFixedThreadPool(50);
	private Map<String, String> debugInfo = new LinkedHashMap<>();

	public MyPanel(int windowWidth, int windowHeight) throws IOException {
		WINDOW_WIDTH = windowWidth;
		WINDOW_HEIGHT = windowHeight;
		
		IMAGE_WIDTH = WINDOW_WIDTH / 5;
		IMAGE_HEIGHT = WINDOW_HEIGHT / 5;
		
		currentImageWidth = IMAGE_WIDTH;
		currentImageHeight = IMAGE_HEIGHT;
		
		setFocusable(true);
		requestFocusInWindow();
		setMousePos(new Point(0, 0));
		terrainGenerator = new TerrainGenerator(9);
//		terrainGenerator.toggle("drawColor");
		images = new ConcurrentHashMap<>();
		
		debugInfo.put("origin", String.format("(%d,%d)", origin.x, origin.y));
		debugInfo.put("scale", String.format("%.2f", scale));
		
		regenerate(getOrderedTiles());
		repaint();
		
		this.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		this.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				setMousePos(e.getPoint());
			}
		});
		
		this.addMouseMotionListener(new MouseMotionAdapter() {
			@Override
			public void mouseMoved(MouseEvent e) {
				setMousePos(e.getPoint());
			}
			
			@Override
			public void mouseDragged(MouseEvent e) {
				int dx = e.getX() - mousePt.x;
				int dy = e.getY() - mousePt.y;
				
				setMousePos(e.getPoint());
				
				updateOrigin(origin.x - dx, origin.y - dy);
			}
		});
		
		this.addMouseWheelListener(new MouseWheelListener() {

			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				final double multiplier = e.getWheelRotation() > 0 ? 0.8 : 1.2;
				currentScale *= multiplier;
				
				
				currentImageWidth *= multiplier;
				currentImageHeight *= multiplier;

				if (currentImageWidth >= IMAGE_WIDTH * 2) {
					setScaleRelative(0.5);
					regenerate(getOrderedTiles());
				} else if (currentImageWidth <= IMAGE_WIDTH / 2) {
					setScaleRelative(2);
					regenerate(getOrderedTiles());
				}
				debugInfo.put("scale", String.format("%.2f", scale));
				
				repaint();
			}
			
		});
		
		this.addComponentListener(new ComponentAdapter() {
			@Override
		    public void componentResized(ComponentEvent e) {
		        // do stuff
				final Dimension size = e.getComponent().getSize();
				setWindowSize(size.width, size.height);
		    }
		});
		
		this.addKeyListener(new KeyListener() {

			@Override
			public void keyTyped(KeyEvent e) {
				// TODO Auto-generated method stub
			}

			@Override
			public void keyPressed(KeyEvent e) {
				switch (e.getKeyCode()) {
				case KeyEvent.VK_SPACE:
//					terrainGenerator.toggle("drawColor");
					final String newRenderMode = terrainGenerator.cycleRenderMode();
					debugInfo.put("render mode", newRenderMode);
					images.clear();
					regenerate(getOrderedTiles());
//					clearOutOfBoundsImages();
//					repaint();
					break;
					
				case KeyEvent.VK_UP:
					int incrementedSeed = terrainGenerator.incrementSeed();
					debugInfo.put("seed", incrementedSeed + "");
					images.clear();
					regenerate(getOrderedTiles());
					break;
				case KeyEvent.VK_DOWN:
					int decrementedSeed = terrainGenerator.decrementSeed();
					debugInfo.put("seed", decrementedSeed + "");
					images.clear();
					regenerate(getOrderedTiles());
					break;
					
				case KeyEvent.VK_R: {
					try {
						terrainGenerator.loadLandProfiles();
						images.clear();
						regenerate(getOrderedTiles());
					} catch (IOException e1) {
						e1.printStackTrace();
					}
					break;
				}
				default:break;
				}
				
				if (e.getKeyCode() >= KeyEvent.VK_0 && e.getKeyCode() <= KeyEvent.VK_9) {
					debugInfo.put("render target", (e.getKeyCode() - KeyEvent.VK_0) + "");
					if (terrainGenerator.setRenderTarget(e.getKeyCode() - KeyEvent.VK_0)) {
						images.clear();
						regenerate(getOrderedTiles());
					}
				}
			}

			@Override
			public void keyReleased(KeyEvent e) {
				// TODO Auto-generated method stub
				
			}
			
		});
	}
	
	private void setScaleRelative(double multiplier) {
		currentImageWidth *= multiplier;
		currentImageHeight *= multiplier;
		scale *= multiplier;
		currentScale *= multiplier;
		origin.x /= multiplier;
		origin.y /= multiplier;
	}
	
	public void updateOrigin(int newX, int newY) {
		boolean requiresRegen = (origin.x / IMAGE_WIDTH) != (newX / IMAGE_WIDTH) ||(origin.y / IMAGE_HEIGHT) != (newY / IMAGE_HEIGHT); 
		
		origin.setLocation(newX, newY);
		debugInfo.put("origin", String.format("(%d,%d)", origin.x, origin.y));
		
		if (requiresRegen) {
			System.out.println(String.format("regen for (%d,%d)", origin.x, origin.y));
			regenerate(getOrderedTiles());
			clearOutOfBoundsImages();
		}
		
		repaint();
	}
	
	@Override
	public void paintComponent(Graphics g) {
		setBackground(Color.BLACK);
		super.paintComponent(g);
		
		images.forEach((x, map) -> {
			map.forEach((y, img) -> {
				if (img.scale == scale) {
					g.drawImage(img.image, 
							(int)Math.floor(((x - origin.x) * currentScale) + (WINDOW_WIDTH / 2)), 
							(int)Math.floor(((y - origin.y) * currentScale) + (WINDOW_HEIGHT / 2)),
							(int)Math.ceil((double)IMAGE_WIDTH * (currentImageWidth/(double)IMAGE_WIDTH)),
							(int)Math.ceil((double)IMAGE_HEIGHT * (currentImageHeight/(double)IMAGE_HEIGHT)), null);
				}
			});
		});		
		
		int debugBoxWidth = 200;
		int debugBoxHeight = (debugInfo.size() + 1) * 20;
		int debugBoxLeft = WINDOW_WIDTH - debugBoxWidth - 20;
		int debugBoxTop = WINDOW_HEIGHT - debugBoxHeight - 20;
		g.setColor(Color.WHITE);
		g.fillRect(debugBoxLeft, debugBoxTop, debugBoxWidth, debugBoxHeight);
		
		g.setColor(Color.BLACK);
		List<String> keyList = new ArrayList<String>(debugInfo.keySet());
		for(int i = 0; i < keyList.size(); i++) {
		    String key = keyList.get(i);
		    String value = debugInfo.get(key);
		    g.drawString(String.format("%s: %s", key, value), debugBoxLeft + 10, debugBoxTop + 10 + (i * 20));
		}
	}
	
	private Rectangle getRenderBounds() {
		int leftBound = origin.x - (WINDOW_WIDTH / 2) - (origin.x % IMAGE_WIDTH);
		int topBound = origin.y - (WINDOW_HEIGHT / 2) - (origin.y % IMAGE_HEIGHT);
		
		return new Rectangle(
				leftBound - (IMAGE_WIDTH * OUT_OF_BOUNDS_ROWS),
				topBound - (IMAGE_HEIGHT * OUT_OF_BOUNDS_ROWS),
				WINDOW_WIDTH + (IMAGE_WIDTH * OUT_OF_BOUNDS_ROWS * 2), // *2 because left bound starts from negative OUT_OF_BOUNDS_ROWS
				WINDOW_HEIGHT + (IMAGE_HEIGHT * OUT_OF_BOUNDS_ROWS * 2));
	}
	
	private void clearOutOfBoundsImages() {
		int widthToCache = (IMAGE_WIDTH * OUT_OF_BOUNDS_ROWS * 5);
		int heightToCache = (IMAGE_HEIGHT * OUT_OF_BOUNDS_ROWS * 5);
		
		final Rectangle renderBounds = getRenderBounds();
		
		images.entrySet().removeIf(e -> e.getKey() < renderBounds.x - widthToCache || e.getKey() >= renderBounds.getMaxX() + widthToCache);
		
		images.values().forEach(map ->
			map.entrySet().removeIf(e -> e.getKey() < renderBounds.y - heightToCache || e.getKey() >= renderBounds.getMaxY() + heightToCache));
	}
	
	private List<int[]> getOrderedTiles() {
		List<int[]> coords = new LinkedList<>();
		
		final Rectangle renderBounds = getRenderBounds();
		int centerCoordX = (int)((renderBounds.getCenterX() / IMAGE_WIDTH) * IMAGE_WIDTH);
		int centerCoordY = (int)((renderBounds.getCenterY() / IMAGE_HEIGHT) * IMAGE_HEIGHT);
		
		coords.add(new int[] {centerCoordX, centerCoordY});
		
		// do middle tile in row/col, then left, right, left, right alternating until we hit the edges
		// len is guaranteed to be an odd number so there's always a centre tile
		for (int len = 3; len <= renderBounds.width / IMAGE_WIDTH; len += 2) {
	//		int len = 3;
			for (int i = 0; i < (len / 2);) {
				// horizontal i.e. top and bottom rows
				int horizontal_x = centerCoordX + (IMAGE_WIDTH * i);
				
				// vertical i.e. left and right columns
				int vertical_y = centerCoordY + (IMAGE_HEIGHT * i);
				
				// top
				coords.add(new int[] {horizontal_x, centerCoordY - (IMAGE_HEIGHT * (len / 2))});
				
				// bottom
				coords.add(new int[] {horizontal_x, centerCoordY + (IMAGE_HEIGHT * (len / 2))});
				
				// left
				coords.add(new int[] {centerCoordX - (IMAGE_WIDTH * (len / 2)), vertical_y});
				
				// right
				coords.add(new int[] {centerCoordX + (IMAGE_WIDTH * (len / 2)), vertical_y});
				
				i *= -1;
				if (i <= 0)
					--i;
			}
			
			// bottom left corner
			coords.add(new int[] {centerCoordX + (IMAGE_WIDTH * (len / 2)), centerCoordY + (IMAGE_HEIGHT * (len / 2))});
		}
		
		coords.removeIf(o -> images.containsKey(o[0]) && images.get(o[0]).containsKey(o[1]) && images.get(o[0]).get(o[1]).scale == scale);
		
		return coords;
	}
	
	private void regenerate(List<int[]> coords) {
		es.getQueue().clear();

		for (int[] coord : coords) {
			es.execute(new Runnable() {
				@Override
				public void run() {
					if (images.containsKey(coord[0]) && images.get(coord[0]).containsKey(coord[1]) && images.get(coord[0]).get(coord[1]).scale == scale) {
						return; // image has already been processed
					}
					
					BufferedImage image = terrainGenerator.generate(coord[0], coord[1], IMAGE_WIDTH, IMAGE_HEIGHT, scale);
					
					images.putIfAbsent(coord[0], new ConcurrentHashMap<>());
					images.get(coord[0]).put(coord[1], new ScaledImage(scale, image));
					
					repaint();
					
					debugInfo.put("renderedImages", "" + images.entrySet().stream()
							.flatMap(i -> i.getValue().entrySet().stream())
							.count());
				}
		    });
		}
	}
	
	private void setMousePos(Point pt) {
		mousePt = pt;
		debugInfo.put("mousePos", String.format("(%d,%d)", pt.x, pt.y));
	}
	
	private void setWindowSize(int width, int height) {
		WINDOW_WIDTH = width;
		WINDOW_HEIGHT = height;
		regenerate(getOrderedTiles());
	}
	
	public void toggleDrawColor() {
		terrainGenerator.toggle("drawColor");
		redraw();
	}
	
	public void redraw() {
		images.clear();
		regenerate(getOrderedTiles());
	}
	
	public String getScaleAsString() {
		return Double.toString(scale);
	}
	
	public void setScaleAsString(String scaleStr) {
		try {
			double multiplier = Double.valueOf(scaleStr) / scale;
			
			scale *= multiplier;
			currentScale = scale;
			origin.x /= multiplier;
			origin.y /= multiplier;
			
			currentImageWidth *= multiplier;
			currentImageHeight *= multiplier;
			
			while (currentImageWidth >= IMAGE_WIDTH * 2) {
				currentImageWidth *= 0.5;
				currentImageHeight *= 0.5;
			} 
//
			while (currentImageWidth <= IMAGE_WIDTH / 2) {
				currentImageWidth *= 2;
				currentImageHeight *= 2;
			}
			
			redraw();
			debugInfo.put("scale", String.format("%.2f", scale));
		} catch (NumberFormatException e) {
			
		}
	}
}
