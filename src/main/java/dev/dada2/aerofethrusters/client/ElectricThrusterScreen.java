package dev.dada2.aerofethrusters.client;

import dev.dada2.aerofethrusters.config.AftConfigs;
import dev.dada2.aerofethrusters.content.ElectricThrusterMenu;
import dev.dada2.aerofethrusters.content.RedstoneControlMode;
import dev.dada2.aerofethrusters.network.UpdateThrusterSettingsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client-side configuration screen for the electric thruster.
 *
 * <p>The screen owns only a local copy of max thrust and redstone mode. Every
 * valid edit immediately updates that local copy and sends an
 * {@link UpdateThrusterSettingsPacket} to the server menu.</p>
 */
public class ElectricThrusterScreen extends AbstractContainerScreen<ElectricThrusterMenu> {
    private static final int PANEL_COLOR = 0xe0222428;
    private static final int PANEL_BORDER = 0xff8f9aa8;
    private static final int TRACK_COLOR = 0xff38404a;
    private static final int TEXT_COLOR = 0xffe8edf2;
    private static final int MUTED_TEXT_COLOR = 0xff9ca8b8;
    private static final int ERROR_TEXT_COLOR = 0xffff8a7a;
    private static final int SLIDER_WIDTH = 240;
    private EditBox thrustBox;
    private boolean updatingBox;
    private boolean draggingSlider;
    private double maxThrust;
    private double maxAllowedThrust;
    private RedstoneControlMode redstoneMode;

    /**
     * Creates the screen from a menu snapshot.
     *
     * @param menu backing menu
     * @param inventory player inventory
     * @param title localized screen title
     */
    public ElectricThrusterScreen(final ElectricThrusterMenu menu, final Inventory inventory,
                                  final Component title) {
        super(menu, inventory, title);
        this.imageWidth = 320;
        this.imageHeight = 148;
        this.maxThrust = menu.maxThrust();
        this.maxAllowedThrust = menu.maxAllowedThrust();
        this.redstoneMode = menu.redstoneMode();
    }

    /**
     * Builds widgets and loads current values from the menu.
     *
     * <p>The thrust input accepts digits plus one decimal point and clamps to the supported range
     * through {@link #onThrustEdited(String)}.</p>
     */
    @Override
    protected void init() {
        super.init();
        this.maxThrust = this.menu.maxThrust();
        this.maxAllowedThrust = this.menu.maxAllowedThrust();
        this.redstoneMode = this.menu.redstoneMode();

        this.thrustBox = new EditBox(this.font, this.leftPos + this.imageWidth / 2 - 55,
                this.topPos + 36, 110, 20, Component.translatable("aero_fe_thrusters.ui.max_thrust"));
        this.thrustBox.setMaxLength(13);
        this.thrustBox.setFilter(ElectricThrusterScreen::isPartialDecimalInput);
        this.thrustBox.setValue(AftConfigs.formatThrust(this.maxThrust));
        this.thrustBox.setResponder(this::onThrustEdited);
        this.addRenderableWidget(this.thrustBox);
        this.setInitialFocus(this.thrustBox);
    }

    /**
     * Handles edits in the max-thrust text box.
     *
     * @param value text currently entered by the user
     */
    private void onThrustEdited(final String value) {
        if (this.updatingBox || value.isEmpty()) {
            return;
        }

        final double parsed;
        try {
            parsed = Double.parseDouble(value);
        } catch (final NumberFormatException ignored) {
            return;
        }

        final double clamped = AftConfigs.roundThrust(Math.max(0, Math.min(this.maxAllowedThrust, parsed)));
        if (clamped != parsed) {
            this.updatingBox = true;
            this.thrustBox.setValue(AftConfigs.formatThrust(clamped));
            this.updatingBox = false;
        }

        this.updateSettings(clamped, this.redstoneMode);
    }

    /**
     * Applies UI state locally and sends it to the server.
     *
     * @param maxThrust requested max thrust in pN
     * @param mode requested redstone mode
     */
    private void updateSettings(final double maxThrust, final RedstoneControlMode mode) {
        this.maxThrust = AftConfigs.roundThrust(Math.max(0, Math.min(this.maxAllowedThrust, maxThrust)));
        this.redstoneMode = mode;
        this.menu.applyClientSettings(this.maxThrust, this.redstoneMode);
        PacketDistributor.sendToServer(new UpdateThrusterSettingsPacket(
                this.menu.blockPos(), this.maxThrust, this.redstoneMode.id()));
    }

    /**
     * Renders the screen and vanilla hover tooltips.
     *
     * @param graphics drawing context
     * @param mouseX mouse x coordinate
     * @param mouseY mouse y coordinate
     * @param partialTick partial frame time
     */
    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    /**
     * Draws the custom panel, max-thrust range text, and redstone mode slider.
     *
     * @param graphics drawing context
     * @param partialTick partial frame time
     * @param mouseX mouse x coordinate
     * @param mouseY mouse y coordinate
     */
    @Override
    protected void renderBg(final GuiGraphics graphics, final float partialTick, final int mouseX, final int mouseY) {
        final int x = this.leftPos;
        final int y = this.topPos;
        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, PANEL_COLOR);
        graphics.fill(x, y, x + this.imageWidth, y + 1, PANEL_BORDER);
        graphics.fill(x, y + this.imageHeight - 1, x + this.imageWidth, y + this.imageHeight, PANEL_BORDER);
        graphics.fill(x, y, x + 1, y + this.imageHeight, PANEL_BORDER);
        graphics.fill(x + this.imageWidth - 1, y, x + this.imageWidth, y + this.imageHeight, PANEL_BORDER);

        graphics.drawCenteredString(this.font, this.title, x + this.imageWidth / 2, y + 12, TEXT_COLOR);
        graphics.drawString(this.font, "0 <=", x + this.imageWidth / 2 - 102, y + 42, MUTED_TEXT_COLOR, false);
        graphics.drawString(this.font, "<= " + AftConfigs.formatThrust(this.maxAllowedThrust),
                x + this.imageWidth / 2 + 66, y + 42, MUTED_TEXT_COLOR, false);

        final int sliderX = this.sliderX();
        final int sliderY = this.sliderY();
        graphics.fill(sliderX - 10, sliderY - 30, sliderX + SLIDER_WIDTH + 10, sliderY + 42, 0xff1a1d22);
        graphics.drawCenteredString(this.font, this.redstoneMode.translation(),
                x + this.imageWidth / 2, sliderY - 22, TEXT_COLOR);
        // Redstone modes are discrete options, so the track has no progress-style filled segment.
        graphics.fill(sliderX, sliderY, sliderX + SLIDER_WIDTH, sliderY + 4, TRACK_COLOR);

        for (int i = 0; i < RedstoneControlMode.COUNT; i++) {
            final int tickX = sliderX + Math.round(i * (SLIDER_WIDTH / (float) (RedstoneControlMode.COUNT - 1)));
            graphics.fill(tickX - 1, sliderY - 4, tickX + 1, sliderY + 8, PANEL_BORDER);
        }

        final int knobX = this.knobX();
        graphics.fill(knobX - 4, sliderY - 6, knobX + 4, sliderY + 10, 0xffe5d17a);

        if (!this.isThrustBoxValid()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("aero_fe_thrusters.ui.invalid_thrust"),
                    x + this.imageWidth / 2, y + 62, ERROR_TEXT_COLOR);
        }
    }

    /**
     * Suppresses default inventory labels; all text is drawn by {@link #renderBg}.
     *
     * @param graphics drawing context
     * @param mouseX mouse x coordinate
     * @param mouseY mouse y coordinate
     */
    @Override
    protected void renderLabels(final GuiGraphics graphics, final int mouseX, final int mouseY) {
    }

    /**
     * Starts slider dragging when the user clicks within the slider hit area.
     *
     * @param mouseX mouse x coordinate
     * @param mouseY mouse y coordinate
     * @param button clicked mouse button
     * @return true when the click was consumed
     */
    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (this.isOverSlider(mouseX, mouseY)) {
            this.draggingSlider = true;
            this.updateModeFromMouse(mouseX);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Updates slider mode while the mouse is dragged.
     *
     * @param mouseX mouse x coordinate
     * @param mouseY mouse y coordinate
     * @param button mouse button being held
     * @param dragX x movement since previous event
     * @param dragY y movement since previous event
     * @return true when dragging was consumed by the slider
     */
    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button,
                                final double dragX, final double dragY) {
        if (this.draggingSlider) {
            this.updateModeFromMouse(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    /**
     * Stops slider dragging after mouse release.
     *
     * @param mouseX mouse x coordinate
     * @param mouseY mouse y coordinate
     * @param button released mouse button
     * @return vanilla release handling result
     */
    @Override
    public boolean mouseReleased(final double mouseX, final double mouseY, final int button) {
        this.draggingSlider = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * Converts mouse x position to one of the discrete redstone modes.
     *
     * @param mouseX mouse x coordinate
     */
    private void updateModeFromMouse(final double mouseX) {
        final int relativeX = Mth.clamp((int) Math.round(mouseX - this.sliderX()), 0, SLIDER_WIDTH);
        final int index = Math.round(relativeX * (RedstoneControlMode.COUNT - 1) / (float) SLIDER_WIDTH);
        final RedstoneControlMode mode = RedstoneControlMode.byId(index);
        if (mode != this.redstoneMode) {
            this.updateSettings(this.maxThrust, mode);
        }
    }

    /** @return left edge of the slider track */
    private int sliderX() {
        return this.leftPos + this.imageWidth / 2 - SLIDER_WIDTH / 2;
    }

    /** @return vertical position of the slider track */
    private int sliderY() {
        return this.topPos + 94;
    }

    /** @return x coordinate of the current slider knob */
    private int knobX() {
        return this.sliderX()
                + Math.round(this.redstoneMode.id() * (SLIDER_WIDTH / (float) (RedstoneControlMode.COUNT - 1)));
    }

    /**
     * Checks whether a mouse point should interact with the slider.
     *
     * @param mouseX mouse x coordinate
     * @param mouseY mouse y coordinate
     * @return true when the point is inside the slider hit area
     */
    private boolean isOverSlider(final double mouseX, final double mouseY) {
        return mouseX >= this.sliderX() - 10
                && mouseX <= this.sliderX() + SLIDER_WIDTH + 10
                && mouseY >= this.sliderY() - 14
                && mouseY <= this.sliderY() + 22;
    }

    /**
     * Validates the current text box value.
     *
     * @return true when the value is a decimal in the supported thrust range
     */
    private boolean isThrustBoxValid() {
        final String value = this.thrustBox.getValue();
        if (value.isEmpty()) {
            return false;
        }

        try {
            final double parsed = Double.parseDouble(value);
            return parsed >= 0 && parsed <= this.maxAllowedThrust && isPartialDecimalInput(value);
        } catch (final NumberFormatException ignored) {
            return false;
        }
    }

    /**
     * Allows editing a non-negative decimal with up to four digits after the decimal point.
     *
     * @param value text box candidate value
     * @return true when the text is a valid partial decimal input
     */
    private static boolean isPartialDecimalInput(final String value) {
        if (value.isEmpty()) {
            return true;
        }

        int decimalPoints = 0;
        int decimalDigits = 0;
        for (int i = 0; i < value.length(); i++) {
            final char character = value.charAt(i);
            if (character == '.') {
                if (++decimalPoints > 1) {
                    return false;
                }
                continue;
            }
            if (!Character.isDigit(character)) {
                return false;
            }
            if (decimalPoints > 0 && ++decimalDigits > 4) {
                return false;
            }
        }
        return true;
    }
}
