package dev.syntvalley.persistence.codec;

import dev.syntvalley.domain.identity.VillageId;
import dev.syntvalley.domain.village.CoreBinding;
import java.util.Objects;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Strict codec for the minimal local Synt Core binding hint. */
public final class CoreBindingNbtCodec {
    private static final String TAG_BINDING = "syntvalley_binding";
    private static final String TAG_BINDING_INVALID = "syntvalley_binding_invalid";
    private static final String TAG_VILLAGE_ID = "village_id";
    private static final String TAG_GENERATION = "binding_generation";
    private static final Set<String> BINDING_KEYS = Set.of(TAG_VILLAGE_ID, TAG_GENERATION);

    private CoreBindingNbtCodec() {
    }

    public static LocalBindingState decode(CompoundTag tag) {
        Objects.requireNonNull(tag, "tag");
        if (tag.contains(TAG_BINDING_INVALID)) {
            if (!tag.contains(TAG_BINDING_INVALID, Tag.TAG_BYTE) || tag.getBoolean(TAG_BINDING_INVALID)) {
                return new LocalBindingState.Invalid();
            }
        }
        if (!tag.contains(TAG_BINDING)) {
            return new LocalBindingState.Unbound();
        }
        if (!tag.contains(TAG_BINDING, Tag.TAG_COMPOUND)) {
            return new LocalBindingState.Invalid();
        }

        CompoundTag bindingTag = tag.getCompound(TAG_BINDING);
        if (!BINDING_KEYS.equals(bindingTag.getAllKeys())
                || !bindingTag.hasUUID(TAG_VILLAGE_ID)
                || !bindingTag.contains(TAG_GENERATION, Tag.TAG_INT)
                || bindingTag.getInt(TAG_GENERATION) < 1) {
            return new LocalBindingState.Invalid();
        }

        return new LocalBindingState.Bound(new CoreBinding(
                new VillageId(bindingTag.getUUID(TAG_VILLAGE_ID)),
                bindingTag.getInt(TAG_GENERATION)
        ));
    }

    public static void encode(CompoundTag tag, LocalBindingState state) {
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(state, "state");
        if (state instanceof LocalBindingState.Invalid) {
            tag.putBoolean(TAG_BINDING_INVALID, true);
        } else if (state instanceof LocalBindingState.Bound bound) {
            CompoundTag bindingTag = new CompoundTag();
            bindingTag.putUUID(TAG_VILLAGE_ID, bound.binding().villageId().value());
            bindingTag.putInt(TAG_GENERATION, bound.binding().generation());
            tag.put(TAG_BINDING, bindingTag);
        }
    }

    public sealed interface LocalBindingState {
        record Unbound() implements LocalBindingState {
        }

        record Bound(CoreBinding binding) implements LocalBindingState {
            public Bound {
                Objects.requireNonNull(binding, "binding");
            }
        }

        record Invalid() implements LocalBindingState {
        }
    }
}
