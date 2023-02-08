package dmk.terrainGenerator;

import java.util.Random;

public class MyRandom {
	final Random r;
	
	public MyRandom(int seed) {
		r = new Random(seed);
	}
	
	public int between(int low, int high) {
		return r.nextInt(high - low) + low;
	}
	
	public boolean chance(int outOf100) {
		return between(0, 100) < outOf100;
	}
}
