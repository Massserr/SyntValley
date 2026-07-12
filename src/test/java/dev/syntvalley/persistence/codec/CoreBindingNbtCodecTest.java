package dev.syntvalley.persistence.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.syntvalley.domain.identity.VillageId;
import dev.syntvalley.domain.village.CoreBinding;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class CoreBindingNbtCodecTest {
    @Test
    void bindingRoundTripsWithOnlyIdAndGeneration() {
        CoreBinding binding = new CoreBinding(
                new VillageId(UUID.fromString("b88464b6-b6e6-4d72-bf45-adf00c686a5a")),
                7
        );
        CompoundTag tag = new CompoundTag();

        CoreBindingNbtCodec.encode(tag, new CoreBindingNbtCodec.LocalBindingState.Bound(binding));
        CoreBindingNbtCodec.LocalBindingState.Bound decoded = assertInstanceOf(
                CoreBindingNbtCodec.LocalBindingState.Bound.class,
                CoreBindingNbtCodec.decode(tag)
        );

        assertEquals(binding, decoded.binding());
        assertEquals(1, tag.getAllKeys().size());
        assertEquals(2, tag.getCompound("syntvalley_binding").getAllKeys().size());
    }

    @Test
    void malformedBindingBecomesPersistentQuarantineMarker() {
        CompoundTag malformed = new CompoundTag();
        malformed.putString("syntvalley_binding", "not-a-compound");
        assertInstanceOf(CoreBindingNbtCodec.LocalBindingState.Invalid.class, CoreBindingNbtCodec.decode(malformed));

        CompoundTag quarantined = new CompoundTag();
        CoreBindingNbtCodec.encode(quarantined, new CoreBindingNbtCodec.LocalBindingState.Invalid());
        assertInstanceOf(CoreBindingNbtCodec.LocalBindingState.Invalid.class, CoreBindingNbtCodec.decode(quarantined));
        assertEquals(1, quarantined.getAllKeys().size());
    }
}
