package dmk.terrainGenerator.types;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Tiles {
	// forest biome
	grass(1, 0x55ff55),
//	dirt,
	lake(2, 0x5555ff),
	
//	// jungle biome
//	jungle_grass,
//	mud,
//	
//	// island biome
//	beach_sand,
//	shore_water,
//	
//	// ocean biome
//	ocean,
//	deep_ocean,
//	
//	// desert biome
//	desert_sand,
//	desert_rock,
	;
	
	public static Tiles getById(int id) {
		for (int i = 0; i < values().length; ++i)
			if (values()[i].id == id)
				return values()[i];
		return null;
	}
	
	private final int id;
	private final int color;
}
