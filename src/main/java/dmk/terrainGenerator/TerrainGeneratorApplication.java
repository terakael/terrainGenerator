package dmk.terrainGenerator;

import java.awt.Color;
import java.awt.Graphics;
import java.io.IOException;
import java.util.function.BiConsumer;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.springframework.boot.Banner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationContext;

import dmk.openSimplex.OpenSimplex2;
import lombok.RequiredArgsConstructor;

@SpringBootApplication
@RequiredArgsConstructor
public class TerrainGeneratorApplication implements CommandLineRunner {
//	private final GeneratorClouds generator;
	
	double scale = 1;
	private static final int SEED = 1;
	private static final int START_X = 1000000000;
	private static final int START_Y = 20000000;
	private static final int WINDOW_WIDTH = 800;
	private static final int WINDOW_HEIGHT = 600;
	private static final double FEATURE_SIZE = 256;
	
	public static void main(String[] args) {
		ApplicationContext ctx = new SpringApplicationBuilder(TerrainGeneratorApplication.class)
				.web(WebApplicationType.NONE)
				.headless(false)
				.bannerMode(Banner.Mode.OFF)
				.run(args);
	}
	
	@Override
	public void run(String... args) throws Exception {
//		TerrainGenerator terrainGenerator = new TerrainGenerator(SEED);
		
		SwingUtilities.invokeLater(() -> {
			JFrame f = new JFrame();
			
			MyPanel mainPanel;
			try {
				mainPanel = new MyPanel(WINDOW_WIDTH, WINDOW_HEIGHT);
			} catch (IOException e) {
				e.printStackTrace();
				return;
			} 
			ConfigPanel configPanel = new ConfigPanel(mainPanel);
			
			f.add(mainPanel);
			f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			f.setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
			f.setVisible(true);
			
//			JFrame configFrame = new JFrame();
//			configFrame.add(configPanel);
//			configFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//			configFrame.setSize(100, WINDOW_HEIGHT);
//			configFrame.setVisible(true);
		});
	}
	
	private void plot(Graphics g, int gridX, int gridY, int localX, int localY, double shade) {
		int color = 0x010101 * (int)((shade + 1) * 127.5);
		g.setColor(new Color(color));
		g.drawLine(localX + (WINDOW_WIDTH * gridX), localY + (WINDOW_HEIGHT * gridY), localX + (WINDOW_WIDTH * gridX), localY + (WINDOW_HEIGHT * gridY));
	}
	
	private void forEachTile(BiConsumer<Integer, Integer> fn) {
		for (int y = START_Y - (WINDOW_HEIGHT / 2); y < (WINDOW_HEIGHT / 2) + START_Y; y++)
		{
			for (int x = START_X - (WINDOW_WIDTH / 2); x < (WINDOW_WIDTH / 2) + START_X; x++) {
				fn.accept(x, y);
			}
		}
	}
	
	private int localX(int x) {
		return x - START_X + (WINDOW_WIDTH / 2);
	}
	
	private int localY(int y) {
		return y - START_Y + (WINDOW_HEIGHT / 2);
	}
	
	private double getElevation(int seed, int x, int y, double z, int minLayer, int numLayers) {		
		double accum = 0;
		for (int i = minLayer; i < (numLayers + minLayer); ++i) {
			int sq = 1 << i;
			
//			double noise = OpenSimplex2.noise2(seed + i, (x/((double)sq * scale)), (y/((double)sq * scale)));
			double noise = OpenSimplex2.noise3_ImproveXY(seed + i, (x/((double)sq * scale)), (y/((double)sq * scale)), (z/((double)sq * scale)));
			noise *= i % 2 == 0 ? 1 : -1;
//			noise = Math.pow(Math.abs(noise), 0.5 + ((1 / ((layers + minLayer) - i)))) * (noise < 0 ? -1 : 1);
			accum += noise;
		}
		
		accum = Math.pow(Math.abs(accum), 1) * (accum < 0 ? -1 : 1);
		return Math.max(-1, Math.min(1, accum));
	}
	
	private double getElevation2(int seed, int n, int x, int y) {
		double nx = (x/(double)n);
		double ny = (y/(double)n);
		
		double cumulative = 0;
		double div = 0;
		for (int i = 0; i < 4; ++i) {
			cumulative += OpenSimplex2.noise2(seed, (1 << i) * nx * scale, (1 << i) * ny * scale) * (1 / (double)(i+1));
			div += 1 / (double)(i+1);
		}
		
		double elevation = cumulative / div;

//		double pass1 = OpenSimplex2.noise2(seed, nx, ny) * 1;
//		double pass2 = OpenSimplex2.noise2(seed, 2 * nx, 2 * ny) * 0.5;
//		double pass3 = OpenSimplex2.noise2(seed, 4 * nx, 4 * ny) * 0.25;
//		double pass4 = OpenSimplex2.noise2(seed, 10 * nx, 10 * ny) * 0.1;
//		double pass5 = OpenSimplex2.noise2(seed, 50 * nx, 50 * ny) * 0.02;
//		
//		double elevation = (pass1 + pass2 + pass3 + pass4 + pass5) / 1.87;
		return Math.max(-1, Math.min(1, elevation));
	}
	
	private double getTemperature(int x, int y, double elevation, double light) {
		// heat is weighted by elevation and light: 
		// - higher light, lower elevation = higher heat; lower light, higher elevation = lower heat
		double pass1 = OpenSimplex2.noise2(SEED + 10, (x/((double)4000 * scale)), (y/((double)4000 * scale)));
		double pass2 = OpenSimplex2.noise2(SEED + 11, (x/((double)4000 * scale)), (y/((double)4000 * scale)));
		
		double pass3 = OpenSimplex2.noise2(SEED + 12, (x/((double)500 * scale)), (y/((double)500 * scale)));
		double pass4 = OpenSimplex2.noise2(SEED + 13, (x/((double)500 * scale)), (y/((double)500 * scale)));
		
		double pass5 = OpenSimplex2.noise2(SEED + 14, (x/((double)2000 * scale)), (y/((double)2000 * scale)));
		double pass6 = OpenSimplex2.noise2(SEED + 15, (x/((double)2000 * scale)), (y/((double)2000 * scale)));
		
		double tempWeighting = 0.85; // 1 means only light counts; 0 means only elevation counts
		double avg = (pass1 + pass2 + pass3 + pass4 + pass5 + pass6) / 6 * -1;
		
		double ret = ((avg * tempWeighting) + (((light + elevation) / 2) * (1 - tempWeighting)));
		
		return Math.pow(Math.abs(ret), 2) * (ret < 0 ? -1 : 1);
	}
	
	private double getPrecipitation(int x, int y, double elevation, double light, double temperature) {
		// precipitation is weighted by elevation, light and heat:
		// - higher precipitation: elevation/heat ~0, lower light; 
		// - lower precipitation: elevation/heat closer to 1/-1, higher light
		double pass1 = OpenSimplex2.noise2(SEED + 40, (x/((double)150 * scale)), (y/((double)150 * scale)));
		double pass2 = OpenSimplex2.noise2(SEED + 41, (x/((double)250 * scale)), (y/((double)250 * scale)));
		
		double rainWeighting = 0.65; // 1 means only light counts; 0 means only elevation counts
		double avg = (pass1 + pass2) / 2;
		
		// strengths are between 0 and 1
		double elevationStrength = 1 - Math.abs(elevation);
		double lightStrength = ((light * -1) + 1) / 2;
		double temperatureStrength = 1 - Math.abs(temperature);
		
		double avgStrength = (((elevationStrength + lightStrength + temperatureStrength) / 3) - 0.5) * 2;
		
		double ret = ((avg * rainWeighting) + (avgStrength * (1-rainWeighting)));
		
		return Math.pow(Math.abs(ret), 1.5) * (ret < 0 ? -1 : 1);
	}
	
	private double getLight(int x, int y, double elevation) {
		// light is weighted by elevation: 
		// - higher elevation = stronger light; lower elevation = weaker light
		double pass1 = OpenSimplex2.noise2(SEED + 20, (x/((double)200 * scale)), (y/((double)200 * scale)));
		double pass2 = OpenSimplex2.noise2(SEED + 21, (x/((double)200 * scale)), (y/((double)200 * scale)));
		double pass3 = OpenSimplex2.noise2(SEED + 22, (x/((double)800 * scale)), (y/((double)800 * scale)));
		
		double lightWeighting = 0.85; // 1 means only light counts; 0 means only elevation counts
		double avg = ((pass1 + pass2 + pass3) / 3) * -1;
		
		double ret = ((avg * lightWeighting) + (elevation * (1-lightWeighting)));
		return Math.pow(Math.abs(ret), 0.75) * (ret < 0 ? -1 : 1);
	}
	
	private double getHumidity(int x, int y, double elevation, double light, double temperature, double precipitation) {
		// humidity is weighted by elevation, light, heat, precipitation:
		// higher humidity: elevation/temperature ~0, lower light, higher precipitation; 
		// lower humidity: elevation/temperature closer to 1/-1, higher light, lower precipitation
		
		double humidityWeighting = 0.3; // 0 means no humidity counted, 1 means only humidity counted
		
		// strengths are between 0 and 1
		double elevationStrength = 1 - Math.abs(elevation);
		double lightStrength = ((light * -1) + 1) / 2;
		double temperatureStrength = 1 - Math.abs(temperature);
		double rainStrength = (precipitation + 1) / 2;
		
		double avgStrength = (((elevationStrength + lightStrength + temperatureStrength + rainStrength) / 4) - 0.5) * 2;
		
		double pass1 = OpenSimplex2.noise2(SEED + 30, (x/(double)100), (y/(double)100)) * 0.7;
		double pass2 = OpenSimplex2.noise2(SEED + 31, (x/(double)150), (y/(double)150)) * 0.3;
		double combined = Math.pow(Math.abs(pass1 + pass2), 1.5) * (pass1 + pass2 < 0 ? -1 : 1);
		
		double ret = ((combined * humidityWeighting) + (avgStrength * (1-humidityWeighting)));
		return Math.pow(Math.abs(ret), 1) * (ret < 0 ? -1 : 1);
	}

}
