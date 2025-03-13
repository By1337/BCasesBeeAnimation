package dev.by1337.bc.bee;

import blib.com.mojang.serialization.Codec;
import blib.com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.by1337.bc.CaseBlock;
import dev.by1337.bc.animation.AbstractAnimation;
import dev.by1337.bc.animation.AnimationContext;
import dev.by1337.bc.engine.MoveEngine;
import dev.by1337.bc.particle.ParticleUtil;
import dev.by1337.bc.prize.Prize;
import dev.by1337.bc.prize.PrizeSelector;
import dev.by1337.bc.task.AsyncTask;
import dev.by1337.bc.yaml.CashedYamlContext;
import dev.by1337.virtualentity.api.entity.EquipmentSlot;
import dev.by1337.virtualentity.api.entity.InteractionHand;
import dev.by1337.virtualentity.api.task.TickTask;
import dev.by1337.virtualentity.api.virtual.VirtualEntity;
import dev.by1337.virtualentity.api.virtual.animal.VirtualBee;
import dev.by1337.virtualentity.api.virtual.monster.VirtualSkeleton;
import dev.by1337.virtualentity.api.virtual.projectile.VirtualArrow;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.by1337.blib.geom.Vec3d;

import java.util.ArrayList;

public class BeeAnimation extends AbstractAnimation {
    private final Prize winner;
    private volatile boolean waitClick = false;
    private volatile VirtualBee selectedBee = null;
    private final Config config;

    public BeeAnimation(CaseBlock caseBlock, AnimationContext context, Runnable onEndCallback, PrizeSelector prizeSelector, CashedYamlContext config, Player player) {
        super(caseBlock, context, onEndCallback, prizeSelector, config, player);
        winner = prizeSelector.getRandomPrize();
        this.config = config.get("settings", v -> v.decode(Config.CODEC).getOrThrow().getFirst());
    }

    @Override
    protected void onStart() {
        caseBlock.hideHologram();
    }

    @Override
    protected void animate() throws InterruptedException {
        final double addY = 3 / (360D / 7D);
        final double addZ = 3 / (360D / 7D);
        final Vec3d spawnPos = new Vec3d(blockPos).add(0.5, 0.5, 0.5);

        for (int i = 0; i < 8; i++) {
            final VirtualBee bee = VirtualBee.create();
            bee.setOnGround(false);
            bee.setPos(spawnPos);
            spawnParticle(Particle.END_ROD, spawnPos.add(0, 0.3, 0), 20, 0, 0, 0, 0.2);
            trackEntity(bee);
            playSound(new Vec3d(blockPos), Sound.BLOCK_BEEHIVE_EXIT, 1, 1);


            new AsyncTask() {
                int x = 0;
                Vec3d vector = new Vec3d(0, addY, addZ);

                @Override
                public void run() {
                    if (x < 360) {
                        vector = vector.add(0, addY, addZ);
                        var pos = spawnPos.add(vector.rotateAroundY(Math.toRadians(x)));
                        bee.lookAt(pos);
                        bee.setPos(pos);
                        x += 7;
                    } else {
                        cancel();
                        bee.addTickTask(new TickTask() {
                            Vec3d vec = vector;

                            @Override
                            public void run() {
                                vec = vec.rotateAroundY(Math.toRadians(5));
                                var pos = spawnPos.add(vec);
                                bee.lookAt(pos);
                                bee.setPos(pos);
                            }
                        });
                    }
                }
            }.timer().delay(1).start(this);

            sleepTicks(9);
        }
        sleepTicks(20);
        VirtualSkeleton attacker = VirtualSkeleton.create();
        attacker.setPos(new Vec3d(blockPos).add(0.5, 0.8, 0.5));
        attacker.setEquipment(EquipmentSlot.MAINHAND, new ItemStack(Material.BOW));
        attacker.setEquipment(EquipmentSlot.HEAD, new ItemStack(Material.GOLDEN_HELMET));
        attacker.addTickTask(() -> attacker.lookAt(new Vec3d(player.getLocation())));
        trackEntity(attacker);
        sleepTicks(50);

        tracker.forEach(entity -> {
            if (entity instanceof VirtualBee bee) {
                bee.removeAllTickTask();
                bee.addTickTask(new TickTask() {
                    int tick = 0;

                    @Override
                    public void run() {
                        switch (tick) {
                            case 0 -> bee.lookAt(bee.getPos().add(1, 0, 0));
                            case 2 -> bee.lookAt(bee.getPos().add(0, 0, 1));
                            case 4 -> bee.lookAt(bee.getPos().add(-1, 0, 0));
                            case 6 -> bee.lookAt(bee.getPos().add(0, 0, -1));
                            case 7 -> {
                                tick = 0;
                                return;
                            }
                        }
                        tick++;
                    }
                });
            }
        });
        sendTitle("", config.title, 5, 30, 10);
        waitClick = true;
        waitUpdate(5_000);
        waitClick = false;
        if (selectedBee == null) {
            var list = new ArrayList<>();
            tracker.forEach(list::add);
            selectedBee = (VirtualBee) list.stream().filter(e -> e instanceof VirtualBee).findFirst().get();
        }
        spawnParticle(Particle.BLOCK_CRACK, selectedBee.getPos(), 30, 0, 0, 0, 0.5, Material.HONEY_BLOCK.createBlockData());
        playSound(selectedBee.getPos(), Sound.BLOCK_HONEY_BLOCK_PLACE, 1, 1);
        tracker.forEach(entity -> {
            if (entity instanceof VirtualBee bee) {
                bee.removeAllTickTask();
                entity.lookAt(center);
            }
        });
        sleepTicks(20);
        attacker.removeAllTickTask();
        attacker.lookAt(selectedBee.getPos());
        attacker.startUsingItem(InteractionHand.MAIN_HAND);
        attacker.setAggressive(true);
        sleepTicks(20);
        attacker.stopUsingItem();
        attacker.setAggressive(false);
        attacker.addTickTask(() -> attacker.lookAt(new Vec3d(player.getLocation())));
        playSound(attacker.getPos(), Sound.ENTITY_SKELETON_SHOOT, 1, 1);

        VirtualArrow arrow = VirtualArrow.create();
        arrow.setPos(attacker.getPos().add(0, 1.8, 0));
        arrow.lookAt(selectedBee.getPos());
        arrow.setNoGravity(true);
        arrow.setNoPhysics(true);
        trackEntity(arrow);

        MoveEngine.goTo(arrow, selectedBee.getPos(), 50).startSync(this);
        removeEntity(arrow);

        playSound(selectedBee.getPos(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 0.5f);
        spawnParticle(Particle.EXPLOSION_HUGE, selectedBee.getPos(), 0);
        selectedBee.setHealth(0);

        var item = winner.createVirtualItem(selectedBee.getPos());
        item.setNoGravity(false);
        trackEntity(item);

        sleepTicks(5);
        removeEntity(selectedBee);

        new AsyncTask() {
            final Vec3d pos = new Vec3d(selectedBee.getPos().x, blockPos.y, selectedBee.getPos().z);
            @Override
            public void run() {
                ParticleUtil.spawnBlockOutlining(pos, BeeAnimation.this, Particle.FLAME, 0.1);
            }
        }.timer().delay(6).start(this);
        sleepTicks(40);

        tracker.forEach(entity -> {
            if (entity instanceof VirtualBee bee) {
                bee.setHealth(0);
                var item1 = prizeSelector.getRandomPrize().createVirtualItem(bee.getPos());
                item1.setNoGravity(false);
                trackEntity(item1);
                playSound(bee.getPos(), Sound.ENTITY_BEE_DEATH, 1, 1);
            }
        });
        sleepTicks(60);
    }

    @Override
    protected void onEnd() {
        caseBlock.showHologram();
        caseBlock.givePrize(winner, player);
    }

    @Override
    protected void onClick(VirtualEntity entity, Player clicker) {
        if (!waitClick || !clicker.equals(player)) return;
        if (entity instanceof VirtualBee bee) {
            selectedBee = bee;
            update();
        }
    }

    @Override
    public void onInteract(PlayerInteractEvent event) {
    }

    private static class Config {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("title").forGetter(v -> v.title)
        ).apply(instance, Config::new));

        private final String title;

        public Config(String title) {
            this.title = title;
        }
    }
}
