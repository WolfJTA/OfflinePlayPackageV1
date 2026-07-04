package com.jordan.mods.opp.client.screen;

import com.jordan.mods.opp.client.OsUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Standalone walkthrough explaining how to play together offline using
 * OfflinePlayPackage: open the world to LAN, share IP + port, and connect.
 * The step text scrolls independently of the buttons below it, so it never
 * overlaps regardless of window size or GUI scale.
 */
public class OfflineTutorialScreen extends Screen {

    private record Step(String text, boolean isNote, boolean isBold) {
    }

    private record Line(OrderedText text, boolean isNote) {
    }

    private final Screen parent;
    private List<Line> wrappedLines = List.of();

    private int lineHeight;
    private int viewportTop;
    private int viewportBottom;
    private double scrollOffset = 0;
    private double maxScroll = 0;

    public OfflineTutorialScreen(Screen parent) {
        super(Text.literal("How to Play Offline Together"));
        this.parent = parent;
    }

    private static List<Step> buildSteps() {
        List<Step> steps = new ArrayList<>();
        steps.add(new Step("Both devices need to be physically close to each other for this to work.", false, false));
        steps.add(new Step("1. Open a hotspot on a phone. It does NOT need internet access, we just need the hotspot. Reccommended to turn the phone's wifi/data off.", false, true));
        steps.add(new Step("2. Connect both computers to the hotspot. On the computer with the world, open it to LAN.", false, false));
        steps.add(new Step("3. " + opp$networkStepText(), false, false));
        steps.add(new Step("(On Android, this usually isn't necessary, but I only know this for sure with the Mojo launcher.)", true, false));
        steps.add(new Step("4. Keep the pre-filled Port number and Offline Mode ON unless you need something else.", false, false));
        steps.add(new Step("5. Type the IP into Direct Connection, or add a server using the IP as the address.", false, false));
        steps.add(new Step("6. Join the server, and you should be up and running!", false, false));
        return steps;
    }

    private static String opp$networkStepText() {
        return switch (OsUtils.CURRENT) {
            case WINDOWS ->
                    "Press \"Open Windows Network Settings\" below, then open the hotspot/Wi-Fi network you're connected to. Set it to Private, so devices can reach each other.";
            case MACOS ->
                    "Press \"Open macOS Network Settings\" below, then mark the network as Trusted and allow Minecraft through the Firewall, so devices can reach each other.";
            case LINUX ->
                    "Open your network manager (e.g. GNOME Settings > Wi-Fi > gear icon, or nm-connection-editor) and set the connection's Firewall Zone/Sharing profile to Home or Trusted, not Public.";
            case OTHER ->
                    "Find your Wi-Fi network's settings and set its profile to Private/Home/Trusted rather than Public, so devices can reach each other.";
        };
    }

    private static Text opp$networkWhyTooltip() {
        return Text.literal("Windows and macOS block incoming connections on networks marked \"Public\" by default, for security. Marking your hotspot as Private/Trusted allows other devices on it to reach your Minecraft server.");
    }

    private static Text opp$offlineSkinTooltip() {
        return Text.literal("Set a custom skin PNG to use while playing offline. Everyone with the mod will see the skin, but sadly vanilla clients wont.");
    }

    @Override
    protected void init() {
        super.init();

        int maxTextWidth = Math.max(120, this.width - 40);
        this.lineHeight = this.textRenderer.fontHeight + 2;

        List<Line> lines = new ArrayList<>();
        for (Step step : buildSteps()) {
            Text styledText = step.isBold()
                    ? Text.literal(step.text()).styled(style -> style.withBold(true))
                    : Text.literal(step.text());

            for (OrderedText wrapped : this.textRenderer.wrapLines(styledText, maxTextWidth)) {
                lines.add(new Line(wrapped, step.isNote()));
            }
        }
        this.wrappedLines = lines;

        int doneWidth = 200;
        int doneX = (this.width - doneWidth) / 2;
        int doneY = this.height - 30;

        this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Done"), button -> this.close())
                        .dimensions(doneX, doneY, doneWidth, 20)
                        .build()
        );

        // Shortcut to the offline skin screen, tucked in the far-left corner
        // so it doesn't compete with the centered tutorial flow. The tooltip
        // doubles as a mini-tutorial for what the button actually does.
        this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Offline Skin"), button -> this.client.setScreen(new OppSkinScreen(this)))
                        .dimensions(10, doneY, 100, 20)
                        .tooltip(Tooltip.of(opp$offlineSkinTooltip()))
                        .build()
        );

        int settingsY = doneY - 26;

        this.viewportTop = 26;
        this.viewportBottom = Math.max(this.viewportTop + this.lineHeight, settingsY - 6);

        int contentHeight = this.wrappedLines.size() * this.lineHeight;
        this.maxScroll = Math.max(0, contentHeight - (this.viewportBottom - this.viewportTop));
        this.scrollOffset = MathHelper.clamp(this.scrollOffset, 0, this.maxScroll);

        // Only Windows/macOS have a reliable one-click settings launch.
        if (OsUtils.CURRENT == OsUtils.Os.WINDOWS || OsUtils.CURRENT == OsUtils.Os.MACOS) {
            String label = (OsUtils.CURRENT == OsUtils.Os.WINDOWS)
                    ? "Open Windows Network Settings"
                    : "Open macOS Network Settings";

            int settingsWidth = 220;
            int settingsX = (this.width - settingsWidth) / 2;

            this.addDrawableChild(
                    ButtonWidget.builder(Text.literal(label), button -> OsUtils.tryOpenNetworkSettings())
                            .dimensions(settingsX, settingsY, settingsWidth, 20)
                            .build()
            );

            this.addDrawableChild(
                    ButtonWidget.builder(Text.literal("Why?"), button -> {
                            })
                            .dimensions(settingsX + settingsWidth + 4, settingsY, 44, 20)
                            .tooltip(Tooltip.of(opp$networkWhyTooltip()))
                            .build()
            );
        } else {
            int whyWidth = 44;
            int whyX = (this.width - whyWidth) / 2;

            this.addDrawableChild(
                    ButtonWidget.builder(Text.literal("Why?"), button -> {
                            })
                            .dimensions(whyX, settingsY, whyWidth, 20)
                            .tooltip(Tooltip.of(opp$networkWhyTooltip()))
                            .build()
            );
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.maxScroll > 0 && mouseY >= this.viewportTop && mouseY <= this.viewportBottom) {
            this.scrollOffset -= verticalAmount * this.lineHeight * 3;
            this.scrollOffset = MathHelper.clamp(this.scrollOffset, 0, this.maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFFFFFFF);

        context.enableScissor(0, this.viewportTop, this.width, this.viewportBottom);

        int startY = this.viewportTop - (int) this.scrollOffset;
        for (int i = 0; i < this.wrappedLines.size(); i++) {
            int y = startY + (i * this.lineHeight);
            if (y + this.lineHeight < this.viewportTop || y > this.viewportBottom) {
                continue;
            }

            Line line = this.wrappedLines.get(i);
            int color = line.isNote() ? 0xFF999999 : 0xFFCCCCCC;

            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    line.text(),
                    this.width / 2,
                    y,
                    color
            );
        }

        context.disableScissor();

        if (this.maxScroll > 0) {
            int trackHeight = this.viewportBottom - this.viewportTop;
            int thumbHeight = Math.max(10, (int) (trackHeight * (trackHeight / (double) (trackHeight + this.maxScroll))));
            int thumbY = this.viewportTop + (int) ((trackHeight - thumbHeight) * (this.scrollOffset / this.maxScroll));
            int barX = this.width - 6;

            context.fill(barX, this.viewportTop, barX + 2, this.viewportBottom, 0x33FFFFFF);
            context.fill(barX, thumbY, barX + 2, thumbY + thumbHeight, 0xAAFFFFFF);
        }
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}