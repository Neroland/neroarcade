package za.co.neroland.neroarcade.item;

import java.util.Optional;

import com.mojang.serialization.Codec;

import net.minecraft.nbt.CompoundTag;

import za.co.neroland.neroarcade.compat.NbtCompat;
import za.co.neroland.neroarcade.game.CartridgeValidator;
import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.game.ValidationError;

/**
 * The value of the {@code neroarcade:game_def} item component: a cartridge's {@link GameDef} kept as its
 * canonical NBT.
 *
 * <p><b>Why the NBT and not the decoded record.</b> An item component whose codec fails takes the whole item
 * stack with it when a chunk or inventory loads. A cartridge written by a newer NeroArcade (a new slot key, a
 * newer schema) or naming a chip this server does not have must survive in a chest untouched and simply read
 * "UNREADABLE HERE" (fail closed, never lossy). So the component stores the tag as is and every read goes
 * through {@link CartridgeValidator#decode}, the one gate. Writing goes through {@link #of}, which encodes with
 * the {@link GameDef#CODEC} and enforces the 8 KiB cap.
 */
public record StoredCartridge(CompoundTag tag) {

    /** Persistent (and, by fallback, network) codec of the component. */
    public static final Codec<StoredCartridge> CODEC = CompoundTag.CODEC.xmap(StoredCartridge::new, StoredCartridge::tag);

    public StoredCartridge {
        tag = tag == null ? new CompoundTag() : tag.copy();
    }

    @Override
    public CompoundTag tag() {
        return tag.copy();
    }

    /** Encodes a cartridge for an item; refuses (never truncates) one over the size cap. */
    public static StoredCartridge of(GameDef def) throws ValidationError {
        return new StoredCartridge(CartridgeValidator.encode(def));
    }

    /** The validated, canonical cartridge; throws the first refusal (e.g. {@code unknown_module}). */
    public GameDef decode() throws ValidationError {
        return CartridgeValidator.decode(tag);
    }

    /** {@link #decode} plus resolution against this server's modules. */
    public ResolvedGame resolve() throws ValidationError {
        return CartridgeValidator.validate(decode());
    }

    /** The cartridge, or empty if it does not decode here. */
    public Optional<GameDef> tryDecode() {
        try {
            return Optional.of(decode());
        } catch (ValidationError refused) {
            return Optional.empty();
        }
    }

    /** The first refusal this server would give, or empty if the cartridge is readable here. */
    public Optional<ValidationError> error() {
        try {
            resolve();
            return Optional.empty();
        } catch (ValidationError refused) {
            return Optional.of(refused);
        }
    }

    /** The raw title (for a tooltip on an unreadable cartridge), stripped of control characters and capped. */
    public String rawTitle() {
        String title = NbtCompat.getStringOr(tag, "title", "");
        StringBuilder out = new StringBuilder();
        title.codePoints().filter(c -> !Character.isISOControl(c)).limit(32).forEach(out::appendCodePoint);
        return out.toString().strip();
    }

    /** The raw cartridge id (for {@code /neroarcade cartridge inspect} on an unreadable cartridge). */
    public String rawId() {
        return NbtCompat.getStringOr(tag, "id", "");
    }
}
