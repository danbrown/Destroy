package com.petrolpark.destroy.config;

import java.util.EnumMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;

import org.apache.commons.lang3.tuple.Pair;

import com.simibubi.create.foundation.config.AllConfigs;
import com.simibubi.create.foundation.config.ConfigBase;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
public class DestroyAllConfigs extends AllConfigs{
    private static final Map<ModConfig.Type, ConfigBase> CONFIGS = new EnumMap<>(ModConfig.Type.class);

    //public static DestroyClientConfigs CLIENT;
    //public static DestroyCommonConfigs COMMON;
    public static DestroyServerConfigs SERVER;

    //This is all copied directly from the Create source code
    private static <T extends DestroyConfigBase> T register(Supplier<T> factory, ModConfig.Type side) {
		Pair<T, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(builder -> {
			T config = factory.get();
			config.callRegisterAll(builder);
			return config;
	    });

        T config = specPair.getLeft();
		config.specification = specPair.getRight();
		CONFIGS.put(side, config);
		return config;
    };

    public static void register(ModLoadingContext context) {
		//CLIENT = register(DestroyClientConfigs::new, ModConfig.Type.CLIENT);
		//COMMON = register(DestroyCommonConfigs::new, ModConfig.Type.COMMON);
		SERVER = register(DestroyServerConfigs::new, ModConfig.Type.SERVER);

		for (Entry<ModConfig.Type, ConfigBase> pair : CONFIGS.entrySet()) {
			context.registerConfig(pair.getKey(), pair.getValue().specification);
        };
	};

    @SubscribeEvent
	public static void onLoad(ModConfigEvent.Loading event) {
		for (ConfigBase config : CONFIGS.values()) {
			if (config.specification == event.getConfig().getSpec()) {
				config.onLoad();
            };
        };
	};

	@SubscribeEvent
	public static void onReload(ModConfigEvent.Reloading event) {
		for (ConfigBase config : CONFIGS.values()) {
			if (config.specification == event.getConfig().getSpec()) {
				config.onReload();
            };
        };
	};
};  
