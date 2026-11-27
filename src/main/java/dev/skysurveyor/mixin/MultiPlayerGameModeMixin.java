package dev.skysurveyor.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.skysurveyor.Surveyor;
import dev.skysurveyor.util.Guard;

// read only, times vanillas break/use packet for ping. we send nothing
// client thread. sequence = only int local, the packets one
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	// require 0, no ping beats a crash
	@Inject(method = "startPrediction", require = 0, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V",
			shift = At.Shift.AFTER))
	private void skysurveyor$onPredict(ClientLevel level, PredictiveAction action, CallbackInfo ci, @Local int sequence) {
		Guard.run("block sent", () -> Surveyor.HUD.onBlockSent(sequence));
	}
}
