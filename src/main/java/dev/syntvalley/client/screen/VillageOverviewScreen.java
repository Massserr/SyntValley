package dev.syntvalley.client.screen;

import dev.syntvalley.application.query.VillageOverviewDto;
import dev.syntvalley.client.cache.VillageOverviewCache;
import dev.syntvalley.network.CloseVillageOverviewPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Read-only Village overview. Renders the client cache; closing tells the server to drop the session. */
public final class VillageOverviewScreen extends Screen {
    private static final int MAX_LISTED = 12;

    private final VillageOverviewCache cache = new VillageOverviewCache();

    public VillageOverviewScreen(VillageOverviewDto initial) {
        super(Component.translatable("screen.syntvalley.village_overview"));
        cache.requestOpen();
        cache.accept(initial);
    }

    public void applySnapshot(VillageOverviewDto overview) {
        cache.accept(overview);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        int left = this.width / 2 - 150;
        int top = 32;
        graphics.drawString(this.font, this.title, left, top, 0xFFFFFF);

        VillageOverviewDto overview = cache.current().orElse(null);
        if (overview == null) {
            graphics.drawString(this.font,
                    Component.translatable("screen.syntvalley.village_overview.loading"), left, top + 16, 0xAAAAAA);
            return;
        }

        graphics.drawString(this.font,
                Component.literal(overview.name() + " — " + overview.lifecycle() + " (rev " + overview.revision() + ")"),
                left, top + 16, 0xFFFFFF);
        graphics.drawString(this.font,
                Component.translatable("screen.syntvalley.village_overview.residents", overview.residentCount()),
                left, top + 30, 0xFFFFFF);

        int listed = Math.min(overview.residents().size(), MAX_LISTED);
        for (int index = 0; index < listed; index++) {
            VillageOverviewDto.CitizenOverviewEntry entry = overview.residents().get(index);
            String presence = entry.present() ? "●" : "○";
            graphics.drawString(this.font,
                    Component.literal(presence + " " + entry.name() + " — " + entry.activity()
                            + "  (hunger " + entry.hunger() + ", rest " + entry.rest() + ")"),
                    left, top + 46 + index * 11, 0xDDDDDD);
        }
        int remaining = overview.residentCount() - listed;
        if (remaining > 0) {
            graphics.drawString(this.font,
                    Component.translatable("screen.syntvalley.village_overview.more", remaining),
                    left, top + 46 + listed * 11, 0xAAAAAA);
        }
    }

    @Override
    public void onClose() {
        PacketDistributor.sendToServer(CloseVillageOverviewPayload.INSTANCE);
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
