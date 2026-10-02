package com.hybridash.deathcompass;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.MathHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * After you die, an arrow at the top of the screen points back to where you died.
 * Uses the death position the server already sends to the client (the same one the Recovery Compass uses),
 * so it's always exact and works on any server, even without the mod on the server.
 */
public class DeathCompassClient implements ClientModInitializer {
	public static final String MOD_ID = "deathcompass";
	public static final Logger LOGGER = LoggerFactory.getLogger("DeathCompass");

	/** You "made it back" when you're this close (blocks). */
	private static final double ARRIVE_DISTANCE = 4.0;
	private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

	private static Cleared cleared;
	private static GlobalPos target;     // death spot we're pointing at, null = nothing to show
	private static GlobalPos lastSeen;   // last death position the game told us about
	private static boolean announced;

	@Override
	public void onInitializeClient() {
		cleared = Cleared.load();
		ClientTickEvents.END_CLIENT_TICK.register(DeathCompassClient::tick);
		HudRenderCallback.EVENT.register(DeathCompassClient::render);

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(ClientCommandManager.literal("deathcompass")
						.executes(ctx -> {
							ClientPlayerEntity p = ctx.getSource().getPlayer();
							Optional<GlobalPos> death = p.getLastDeathPos();
							if (death.isEmpty()) {
								ctx.getSource().sendFeedback(Text.literal("You haven't died in this world yet.").formatted(Formatting.GRAY));
							} else {
								ctx.getSource().sendFeedback(describe(death.get()));
								if (target == null) {
									target = death.get();
									ctx.getSource().sendFeedback(Text.literal("Arrow turned back on.").formatted(Formatting.GRAY));
								}
							}
							return 1;
						})
						.then(ClientCommandManager.literal("hide").executes(ctx -> {
							if (target != null) cleared.add(key(target));
							target = null;
							ctx.getSource().sendFeedback(Text.literal("Death arrow hidden. /deathcompass brings it back.").formatted(Formatting.GRAY));
							return 1;
						}))));
	}

	/** Server address or singleplayer world name, plus position, so cleared deaths stay cleared after relogging. */
	private static String key(GlobalPos pos) {
		MinecraftClient c = MinecraftClient.getInstance();
		String world;
		if (c.getServer() != null) world = "sp:" + c.getServer().getSaveProperties().getLevelName();
		else if (c.getCurrentServerEntry() != null) world = "mp:" + c.getCurrentServerEntry().address;
		else world = "unknown";
		BlockPos b = pos.pos();
		return world + "|" + pos.dimension().getValue() + "|" + b.getX() + "," + b.getY() + "," + b.getZ();
	}

	private static void tick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			lastSeen = null;
			target = null;
			return;
		}
		if (!player.isAlive()) return; // wait until they respawn

		Optional<GlobalPos> death = player.getLastDeathPos();
		if (death.isEmpty()) return;
		GlobalPos pos = death.get();

		if (!pos.equals(lastSeen)) {
			// New death (or we just joined a world where you died before)
			lastSeen = pos;
			boolean alreadyCleared = cleared.contains(key(pos));
			target = alreadyCleared ? null : pos;
			announced = false;
		}

		if (target == null) return;

		if (!announced) {
			announced = true;
			player.sendMessage(describe(target).copy().append(Text.literal(" Follow the arrow under your crosshair.").formatted(Formatting.GRAY)), false);
		}

		if (client.world.getRegistryKey().equals(target.dimension())) {
			BlockPos b = target.pos();
			double dist = Math.sqrt(player.squaredDistanceTo(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5));
			if (dist <= ARRIVE_DISTANCE) {
				cleared.add(key(target));
				target = null;
				SystemToast.add(client.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION,
						Text.literal("You made it back!"), Text.literal("Grab your stuff before it despawns."));
			}
		}
	}

	private static Text describe(GlobalPos pos) {
		BlockPos b = pos.pos();
		return Text.literal("☠ You died at ").formatted(Formatting.RED)
				.append(Text.literal(b.getX() + ", " + b.getY() + ", " + b.getZ()).formatted(Formatting.WHITE))
				.append(Text.literal(" in the " + dimensionName(pos) + ".").formatted(Formatting.RED));
	}

	private static String dimensionName(GlobalPos pos) {
		String path = pos.dimension().getValue().getPath();
		return switch (path) {
			case "overworld" -> "Overworld";
			case "the_nether" -> "Nether";
			case "the_end" -> "End";
			default -> path.replace('_', ' ');
		};
	}

	private static void render(DrawContext ctx, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (target == null || client.player == null || client.world == null || client.options.hudHidden) return;

		int centerX = ctx.getScaledWindowWidth() / 2;
		// Just under the crosshair, where you're already looking
		int y = ctx.getScaledWindowHeight() / 2 + 14;

		if (!client.world.getRegistryKey().equals(target.dimension())) {
			BlockPos b = target.pos();
			ctx.drawCenteredTextWithShadow(client.textRenderer,
					Text.literal("☠ Died in the " + dimensionName(target) + " at " + b.getX() + ", " + b.getY() + ", " + b.getZ()),
					centerX, y, 0xFFFF6B6B);
			return;
		}

		ClientPlayerEntity p = client.player;
		BlockPos b = target.pos();
		double dx = b.getX() + 0.5 - p.getX();
		double dz = b.getZ() + 0.5 - p.getZ();
		double dy = b.getY() - p.getY();
		double dist = Math.sqrt(dx * dx + dz * dz + dy * dy);

		// Minecraft yaw: 0 = south (+Z), 90 = west (-X), turning right increases it
		float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float rel = MathHelper.wrapDegrees(targetYaw - p.getYaw());
		int index = Math.floorMod(Math.round(rel / 45f), 8);

		String vertical = Math.abs(dy) > 6 ? (dy > 0 ? "  ▲" + (int) dy : "  ▼" + (int) -dy) : "";
		Text line = Text.literal(ARROWS[index] + " ").formatted(Formatting.RED, Formatting.BOLD)
				.append(Text.literal((int) dist + "m").formatted(Formatting.WHITE))
				.append(Text.literal(vertical).formatted(Formatting.GRAY))
				.append(Text.literal("  ☠").formatted(Formatting.DARK_RED));
		ctx.drawCenteredTextWithShadow(client.textRenderer, line, centerX, y, 0xFFFFFFFF);
	}
}
