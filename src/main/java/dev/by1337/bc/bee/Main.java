package dev.by1337.bc.bee;

import dev.by1337.bc.addon.AbstractAddon;
import dev.by1337.bc.animation.AnimationRegistry;
import org.bukkit.plugin.Plugin;
import org.by1337.blib.util.SpacedNameKey;

import java.io.File;

public class Main extends AbstractAddon {
    @Override
    protected void onEnable() {
        AnimationRegistry.INSTANCE.register("bdev:bee", BeeAnimation::new);
        Plugin plugin = getPlugin();
        saveResourceToFile("beeAnimation.yml", new File(plugin.getDataFolder(), "animations/bdev/beeAnim.yml"));
    }

    @Override
    protected void onDisable() {
        AnimationRegistry.INSTANCE.unregister(new SpacedNameKey("bdev:bee"));
    }
}
