package dev.syntvalley.client.renderer;

import dev.syntvalley.content.entity.SyntCitizenEntity;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Minimal vanilla-villager-like renderer. The texture is a placeholder seam: a later per-citizen
 * appearance id can select a generated skin here without touching entity or domain logic.
 */
public final class SyntCitizenRenderer extends MobRenderer<SyntCitizenEntity, VillagerModel<SyntCitizenEntity>> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/villager/villager.png");

    public SyntCitizenRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerModel<>(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(SyntCitizenEntity entity) {
        return TEXTURE;
    }
}
