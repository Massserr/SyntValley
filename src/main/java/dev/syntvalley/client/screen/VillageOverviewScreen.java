package dev.syntvalley.client.screen;

import dev.syntvalley.application.query.VillageOverviewDto;
import dev.syntvalley.client.cache.VillageOverviewCache;
import dev.syntvalley.network.CloseVillageOverviewPayload;
import dev.syntvalley.network.ProposeBuildPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Read-only Village overview. Renders the client cache; closing tells the server to drop the session. */
public final class VillageOverviewScreen extends Screen {
    private static final int MAX_LISTED = 12;
    private static final int MAX_RESOURCES_LISTED = 8;

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
    protected void init() {
        super.init();
        int left = this.width / 2 - 150;
        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.syntvalley.village_overview.build"),
                        button -> PacketDistributor.sendToServer(ProposeBuildPayload.INSTANCE))
                .bounds(left, this.height - 32, 150, 20)
                .build());
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
                    Component.literal(presence + " " + entry.name()
                            + " [" + entry.profession() + " " + entry.professionLevel() + "] — " + entry.activity()
                            + "  (h " + entry.hunger() + ", r " + entry.rest() + ")"),
                    left, top + 46 + index * 11, 0xDDDDDD);
        }
        int y = top + 46 + listed * 11;
        int remaining = overview.residentCount() - listed;
        if (remaining > 0) {
            graphics.drawString(this.font,
                    Component.translatable("screen.syntvalley.village_overview.more", remaining), left, y, 0xAAAAAA);
            y += 11;
        }

        y += 6;
        graphics.drawString(this.font,
                Component.translatable("screen.syntvalley.village_overview.storage"), left, y, 0xFFFFFF);
        y += 12;
        if (overview.resources().isEmpty()) {
            graphics.drawString(this.font,
                    Component.translatable("screen.syntvalley.village_overview.storage.empty"), left, y, 0xAAAAAA);
        } else {
            int listedResources = Math.min(overview.resources().size(), MAX_RESOURCES_LISTED);
            for (int index = 0; index < listedResources; index++) {
                VillageOverviewDto.ResourceSummaryEntry entry = overview.resources().get(index);
                graphics.drawString(this.font,
                        Component.literal("• " + entry.resource() + " × " + entry.count()), left, y, 0xDDDDDD);
                y += 11;
            }
            int moreResources = overview.resources().size() - listedResources;
            if (moreResources > 0) {
                graphics.drawString(this.font,
                        Component.translatable("screen.syntvalley.village_overview.storage.more", moreResources),
                        left, y, 0xAAAAAA);
            }
        }

        y += 8;
        String projectStatus = overview.projectStatus();
        graphics.drawString(this.font,
                projectStatus.isEmpty()
                        ? Component.translatable("screen.syntvalley.village_overview.no_project")
                        : Component.translatable("screen.syntvalley.village_overview.project", projectStatus),
                left, y, 0xFFFFFF);
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
