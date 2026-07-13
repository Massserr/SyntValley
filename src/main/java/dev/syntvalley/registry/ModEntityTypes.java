package dev.syntvalley.registry;

import dev.syntvalley.bootstrap.ProjectIdentity;
import dev.syntvalley.content.entity.SyntCitizenEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntityTypes {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, ProjectIdentity.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<SyntCitizenEntity>> SYNT_CITIZEN =
            ENTITY_TYPES.register(
                    "synt_citizen",
                    () -> EntityType.Builder.of(SyntCitizenEntity::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(10)
                            .build("synt_citizen")
            );

    private ModEntityTypes() {
    }
}
