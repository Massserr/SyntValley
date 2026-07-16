package dev.syntvalley.client.screen;

import dev.syntvalley.application.query.VillageLogPage;
import dev.syntvalley.network.RequestVillageLogPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Read-only village Memory / Decision Log. The server renders the lines and paginates decisions by a
 * sequence cursor; this screen only displays what it was sent and can ask for the next (older) page.
 */
public final class VillageLogScreen extends Screen {
    private final Screen parent;
    private final List<String> memoryLines = new ArrayList<>();
    private final List<String> decisionLines = new ArrayList<>();
    private long nextCursor;
    private boolean hasMore;
    private Button olderButton;

    public VillageLogScreen(Screen parent, VillageLogPage firstPage) {
        super(Component.translatable("screen.syntvalley.village_log"));
        this.parent = parent;
        applyPage(firstPage);
    }

    /** Applies a received page: the first page replaces everything, older pages append decisions. */
    public void applyPage(VillageLogPage page) {
        if (page.firstPage()) {
            memoryLines.clear();
            decisionLines.clear();
            memoryLines.addAll(page.memoryLines());
        }
        decisionLines.addAll(page.decisionLines());
        nextCursor = page.nextCursor();
        hasMore = page.hasMore();
        if (olderButton != null) {
            olderButton.active = hasMore;
        }
    }

    @Override
    protected void init() {
        super.init();
        int left = this.width / 2 - 150;
        olderButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.syntvalley.village_log.older"),
                        button -> PacketDistributor.sendToServer(new RequestVillageLogPayload(nextCursor)))
                .bounds(left, this.height - 32, 146, 20)
                .build());
        olderButton.active = hasMore;
        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.syntvalley.village_log.back"),
                        button -> Minecraft.getInstance().setScreen(parent))
                .bounds(left + 154, this.height - 32, 146, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        int left = this.width / 2 - 150;
        int y = 24;
        graphics.drawString(this.font, this.title, left, y, 0xFFFFFF);
        y += 16;

        graphics.drawString(this.font,
                Component.translatable("screen.syntvalley.village_log.memories"), left, y, 0xFFFFFF);
        y += 12;
        if (memoryLines.isEmpty()) {
            graphics.drawString(this.font,
                    Component.translatable("screen.syntvalley.village_log.empty"), left, y, 0xAAAAAA);
            y += 11;
        } else {
            for (String line : memoryLines) {
                graphics.drawString(this.font, Component.literal(line), left, y, 0xDDDDDD);
                y += 11;
            }
        }

        y += 8;
        graphics.drawString(this.font,
                Component.translatable("screen.syntvalley.village_log.decisions"), left, y, 0xFFFFFF);
        y += 12;
        if (decisionLines.isEmpty()) {
            graphics.drawString(this.font,
                    Component.translatable("screen.syntvalley.village_log.empty"), left, y, 0xAAAAAA);
        } else {
            for (String line : decisionLines) {
                if (y > this.height - 44) {
                    break; // stay above the buttons; older pages arrive on demand
                }
                graphics.drawString(this.font, Component.literal(line), left, y, 0xDDDDDD);
                y += 11;
            }
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
