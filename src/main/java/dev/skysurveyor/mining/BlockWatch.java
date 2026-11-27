package dev.skysurveyor.mining;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

// read only, sends nothing
// hypixel breaks server side, no prediction mess
public final class BlockWatch {
	// gone = spread i think (unconfirmed)
	public interface Listener {
		void onBreak(BlockPos pos, String block, int ticks, boolean clean, List<String> gone);
	}

	// server may still finish it. first guess, tune later
	private static final int LATE_TICKS = 10;
	// else they count as the next blocks spread
	private static final int OWN_TICKS = 20;
	// game min (research/11 s8)
	// blocks can go faster, hypixel keeps old progress (rec 7)
	private static final int MIN_TICKS = 4;

	private static final class Target {
		final BlockPos pos;
		final Block block;
		final int since;
		// refreshed while held, else early neighbours = spread
		Block[] around;
		int lastHit;
		// false = looked away, time useless
		boolean clean = true;

		Target(BlockPos pos, Block block, int since, Block[] around) {
			this.pos = pos;
			this.block = block;
			this.since = since;
			this.around = around;
			this.lastHit = since;
		}
	}

	private record Own(BlockPos pos, int tick) {
	}

	private final Listener listener;
	private final List<Target> left = new ArrayList<>();
	private final Deque<Own> own = new ArrayDeque<>();
	private final List<Target> went = new ArrayList<>();
	private Target target;
	private int tick;

	public BlockWatch(Listener listener) {
		this.listener = listener;
	}

	// before tick()
	public void reset() {
		target = null;
		left.clear();
		own.clear();
	}

	// once per block in survival, every tick in creative
	public void onAttack(Level level, BlockPos pos) {
		if (target != null && target.pos.equals(pos)) {
			target.lastHit = tick;
			return;
		}
		if (target != null) left.add(target);
		Block block = level.getBlockState(pos).getBlock();
		// same target again, else counts twice
		for (Iterator<Target> it = left.iterator(); it.hasNext();) {
			Target old = it.next();
			if (!old.pos.equals(pos)) continue;
			it.remove();
			if (old.block != block) break;
			old.clean = false;
			old.lastHit = tick;
			target = old;
			return;
		}
		target = new Target(pos.immutable(), block, tick, around(level, pos));
	}

	// after packets + attack
	public void onTick(Minecraft client) {
		ClientLevel level = client.level;
		if (level == null) {
			tick++;
			return;
		}
		// callback wont fire again, use vanillas flag
		if (target != null && client.gameMode != null && client.gameMode.isDestroying()) target.lastHit = tick;
		if (target != null && target.lastHit < tick) {
			left.add(target);
			target = null;
		}
		went.clear();
		if (target != null && gone(level, target)) {
			went.add(target);
			target = null;
		} else if (target != null) {
			target.around = around(level, target.pos);
		}
		for (Iterator<Target> it = left.iterator(); it.hasNext();) {
			Target old = it.next();
			if (gone(level, old)) {
				went.add(old);
				it.remove();
			} else if (tick - old.lastHit > LATE_TICKS) {
				it.remove();
			}
		}
		// longest first, else spread counts as ur block
		went.sort((a, b) -> Integer.compare(ticks(b), ticks(a)));
		List<BlockPos> counted = new ArrayList<>();
		for (Target old : went) {
			int ticks = ticks(old);
			if (ticks < MIN_TICKS && touches(old.pos, counted)) continue;
			// moved on same tick, still clean
			report(level, old, ticks, old.clean && ticks >= MIN_TICKS && tick - old.lastHit <= 1);
			counted.add(old.pos);
		}
		went.clear();
		while (!own.isEmpty() && tick - own.peekFirst().tick() > OWN_TICKS) own.removeFirst();
		tick++;
	}

	public boolean holding() {
		return target != null;
	}

	private static boolean held(Target target) {
		return ticks(target) >= MIN_TICKS;
	}

	// incl the tick it went
	private static int ticks(Target target) {
		return target.lastHit - target.since + 1;
	}

	private static boolean touches(BlockPos pos, List<BlockPos> others) {
		for (BlockPos other : others) {
			if (Math.abs(pos.getX() - other.getX()) <= 1 && Math.abs(pos.getY() - other.getY()) <= 1
					&& Math.abs(pos.getZ() - other.getZ()) <= 1) return true;
		}
		return false;
	}

	private static boolean gone(ClientLevel level, Target target) {
		return level.getBlockState(target.pos).getBlock() != target.block;
	}

	private void report(ClientLevel level, Target target, int ticks, boolean clean) {
		own.addLast(new Own(target.pos, tick));
		List<String> gone = new ArrayList<>();
		int i = 0;
		for (BlockPos pos : BlockPos.betweenClosed(target.pos.offset(-1, -1, -1), target.pos.offset(1, 1, 1))) {
			if (pos.equals(target.pos)) continue;
			Block before = target.around[i++];
			if (before == null || level.getBlockState(pos).getBlock() == before || isOwn(pos)) continue;
			gone.add(id(before));
		}
		listener.onBreak(target.pos, id(target.block), ticks, clean, gone);
	}

	private boolean isOwn(BlockPos pos) {
		for (Own o : own) {
			if (o.pos().equals(pos)) return true;
		}
		for (Target old : left) {
			if (old.pos.equals(pos) && held(old)) return true;
		}
		for (Target old : went) {
			if (old.pos.equals(pos) && held(old)) return true;
		}
		return target != null && target.pos.equals(pos);
	}

	// 26 neighbours, null = air
	private static Block[] around(Level level, BlockPos center) {
		Block[] blocks = new Block[26];
		int i = 0;
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
			if (pos.equals(center)) continue;
			var state = level.getBlockState(pos);
			blocks[i++] = state.isAir() ? null : state.getBlock();
		}
		return blocks;
	}

	private static String id(Block block) {
		return BuiltInRegistries.BLOCK.getKey(block).getPath();
	}
}
