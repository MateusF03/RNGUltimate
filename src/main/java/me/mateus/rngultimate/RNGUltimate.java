package me.mateus.rngultimate;

import me.mateus.rngultimate.listener.RNGListener;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.Random;

public final class RNGUltimate extends JavaPlugin {

    public static final Random RANDOM = new Random();
    public static double walkChance;
    public static double eatChance;
    public static double mineStoneChance;
    public static double attackChance;


    @Override
    public void onEnable() {
        PluginManager pluginManager = Bukkit.getPluginManager();
        if (pluginManager.getPlugin("BlockStore") == null ){
            getLogger().severe("MISSING DEPENDENCY");
            pluginManager.disablePlugin(this);
        }
        saveDefaultConfig();
        FileConfiguration config = getConfig();
        walkChance = config.getDouble("chances.walk");
        eatChance = config.getDouble("chances.eat");
        mineStoneChance = config.getDouble("chances.mine-stone");
        attackChance = config.getDouble("chances.attack");
        RNGListener rngListener = new RNGListener(this);
        pluginManager.registerEvents(rngListener, this);
    }

    @Override
    public void onDisable() {
        saveConfig();
    }

}
