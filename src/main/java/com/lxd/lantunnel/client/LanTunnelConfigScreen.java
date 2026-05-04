package com.lxd.lantunnel.client;

import com.lxd.lantunnel.config.LanTunnelConfig;
import com.lxd.lantunnel.tunnel.ConnectionTestResult;
import com.lxd.lantunnel.tunnel.LanTunnelClient;
import com.lxd.lantunnel.tunnel.LanTunnelManager;
import com.lxd.lantunnel.tunnel.TunnelStatus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;

public final class LanTunnelConfigScreen extends Screen {
    private static final int MAX_PANEL_WIDTH = 560;
    private static final int MIN_PANEL_WIDTH = 360;
    private static final int LABEL_WIDTH = 132;
    private static final int ROW_HEIGHT = 28;
    private static final int COMPACT_ROW_HEIGHT = 18;
    private static final int FIELD_COUNT = 6;
    private static final int OPTION_COUNT = 5;
    private static final int SCROLL_STEP = 18;
    private static final int PANEL_COLOR = 0xB8000000;
    private static final int PANEL_BORDER = 0xAAFFFFFF;
    private static final int SECTION_COLOR = 0x66FFFFFF;

    private final Screen parent;
    private EditBox relayHostBox;
    private EditBox controlPortBox;
    private EditBox tokenBox;
    private EditBox publicPortBox;
    private EditBox reconnectDelayBox;
    private EditBox testTimeoutBox;
    private Checkbox enabledCheckbox;
    private Checkbox autoStartCheckbox;
    private Checkbox allowOfflinePlayersCheckbox;
    private Checkbox showLatencyOverlayCheckbox;
    private Checkbox autoSelectNodeCheckbox;
    private Button startStopButton;
    private Button testButton;
    private Button copyAddressButton;
    private Button saveButton;
    private Button backButton;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int fieldX;
    private int fieldWidth;
    private int rowHeight;
    private int fieldHeight;
    private int statusHeight;
    private int buttonY;
    private int contentTop;
    private int contentBottom;
    private int contentHeight;
    private int maxScroll;
    private int scrollOffset;
    private int checkboxStep;
    private int checkboxBaseY;
    private int sectionBaseY;
    private int formBaseY;
    private int statusBaseY;
    private int scrollbarX;
    private int footerHeight;
    private boolean twoLineButtons;
    private boolean compactLayout;
    private Component message = Component.empty();

    public LanTunnelConfigScreen(Screen parent) {
        super(Component.translatable("lan_tunnel.screen.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        LanTunnelConfig config = LanTunnelConfig.get().copy();
        computeLayout();

        int y = checkboxBaseY;

        enabledCheckbox = addRenderableWidget(new Checkbox(panelLeft + 18, y, panelWidth - 36, 20,
                Component.translatable("lan_tunnel.screen.enabled"), config.isEnabled()));
        y += checkboxStep;
        autoStartCheckbox = addRenderableWidget(new Checkbox(panelLeft + 18, y, panelWidth - 36, 20,
                Component.translatable("lan_tunnel.screen.auto_start"), config.isAutoStart()));
        y += checkboxStep;
        allowOfflinePlayersCheckbox = addRenderableWidget(new Checkbox(panelLeft + 18, y, panelWidth - 36, 20,
                Component.translatable("lan_tunnel.screen.allow_offline_players"), config.isAllowOfflinePlayers()));
        y += checkboxStep;
        showLatencyOverlayCheckbox = addRenderableWidget(new Checkbox(panelLeft + 18, y, panelWidth - 36, 20,
                Component.translatable("lan_tunnel.screen.show_latency_overlay"), config.isShowLatencyOverlay()));
        y += checkboxStep;
        autoSelectNodeCheckbox = addRenderableWidget(new Checkbox(panelLeft + 18, y, panelWidth - 36, 20,
                Component.translatable("lan_tunnel.screen.auto_select_node"), config.isAutoSelectNode()));

        y = formBaseY;
        relayHostBox = addBox(fieldX, y, config.getRelayHost(), 128);
        y += rowHeight;
        controlPortBox = addPortBox(fieldX, y, config.getRelayControlPort());
        y += rowHeight;
        tokenBox = addBox(fieldX, y, config.getToken(), 256);
        y += rowHeight;
        publicPortBox = addPortBox(fieldX, y, config.getRequestedPublicPort());
        y += rowHeight;
        reconnectDelayBox = addPortBox(fieldX, y, config.getReconnectDelaySeconds());
        y += rowHeight;
        testTimeoutBox = addPortBox(fieldX, y, config.getConnectionTestTimeoutSeconds());

        startStopButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            if (LanTunnelManager.get().isRunning()) {
                LanTunnelManager.get().stopManually();
            } else if (applyAndSave()) {
                LanTunnelManager.get().startManually();
            }
        }).bounds(0, 0, 80, 20).build());
        testButton = addRenderableWidget(Button.builder(Component.translatable("lan_tunnel.screen.test"), button -> testConnection())
                .bounds(0, 0, 80, 20).build());
        copyAddressButton = addRenderableWidget(Button.builder(Component.translatable("lan_tunnel.screen.copy_address"), button -> copyShareAddress())
                .bounds(0, 0, 80, 20).build());
        saveButton = addRenderableWidget(Button.builder(Component.translatable("lan_tunnel.screen.save"), button -> applyAndSave())
                .bounds(0, 0, 80, 20).build());
        backButton = addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(0, 0, 80, 20).build());

        layoutButtons();
        layoutWidgets();
        updateStartStopButton();
    }

    private void computeLayout() {
        compactLayout = height < 620;
        panelWidth = Math.min(MAX_PANEL_WIDTH, Math.max(260, width - 48));
        panelLeft = (width - panelWidth) / 2;
        panelTop = compactLayout ? 8 : 24;
        rowHeight = compactLayout ? COMPACT_ROW_HEIGHT : ROW_HEIGHT;
        fieldHeight = compactLayout ? 18 : 20;
        checkboxStep = compactLayout ? 18 : 24;
        statusHeight = compactLayout ? 44 : 72;
        twoLineButtons = panelWidth < 430;
        footerHeight = twoLineButtons ? 44 : 20;
        int footerBottomPadding = compactLayout ? 8 : 16;
        buttonY = height - footerBottomPadding - footerHeight;

        contentTop = panelTop + (compactLayout ? 26 : 40);
        contentBottom = buttonY - (compactLayout ? 8 : 12);

        checkboxBaseY = contentTop + (compactLayout ? 2 : 6);
        sectionBaseY = checkboxBaseY + 20 + (OPTION_COUNT - 1) * checkboxStep + (compactLayout ? 6 : 12);
        formBaseY = sectionBaseY + (compactLayout ? 12 : 18);
        statusBaseY = formBaseY + rowHeight * FIELD_COUNT + (compactLayout ? 8 : 14);

        contentHeight = statusBaseY + statusHeight + (compactLayout ? 8 : 12) - contentTop;
        int viewportHeight = Math.max(1, contentBottom - contentTop);
        maxScroll = Math.max(0, contentHeight - viewportHeight);
        scrollOffset = clamp(scrollOffset, 0, maxScroll);
        scrollbarX = panelLeft + panelWidth - 10;

        fieldX = panelLeft + LABEL_WIDTH + 20;
        fieldWidth = panelWidth - LABEL_WIDTH - 56;
        if (fieldWidth < 150) {
            fieldX = panelLeft + 98;
            fieldWidth = panelWidth - 132;
        }
    }

    private EditBox addBox(int x, int y, String value, int maxLength) {
        EditBox box = new EditBox(font, x, y, fieldWidth, fieldHeight, Component.empty());
        box.setMaxLength(maxLength);
        box.setValue(value);
        addRenderableWidget(box);
        return box;
    }

    private EditBox addPortBox(int x, int y, int value) {
        EditBox box = addBox(x, y, Integer.toString(value), 5);
        box.setFilter(text -> text.isEmpty() || text.chars().allMatch(Character::isDigit));
        return box;
    }

    @Override
    public void tick() {
        relayHostBox.tick();
        controlPortBox.tick();
        tokenBox.tick();
        publicPortBox.tick();
        reconnectDelayBox.tick();
        testTimeoutBox.tick();
        updateStartStopButton();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        drawPanel(graphics);

        int labelX = panelLeft + 18;
        graphics.drawCenteredString(font, title, width / 2, panelTop + 12, 0xFFFFFF);
        layoutWidgets();

        graphics.enableScissor(panelLeft + 2, contentTop, panelLeft + panelWidth - 12, contentBottom);
        try {
            drawScrollableContent(graphics, labelX);
            enabledCheckbox.render(graphics, mouseX, mouseY, partialTick);
            autoStartCheckbox.render(graphics, mouseX, mouseY, partialTick);
            allowOfflinePlayersCheckbox.render(graphics, mouseX, mouseY, partialTick);
            showLatencyOverlayCheckbox.render(graphics, mouseX, mouseY, partialTick);
            autoSelectNodeCheckbox.render(graphics, mouseX, mouseY, partialTick);
            relayHostBox.render(graphics, mouseX, mouseY, partialTick);
            controlPortBox.render(graphics, mouseX, mouseY, partialTick);
            tokenBox.render(graphics, mouseX, mouseY, partialTick);
            publicPortBox.render(graphics, mouseX, mouseY, partialTick);
            reconnectDelayBox.render(graphics, mouseX, mouseY, partialTick);
            testTimeoutBox.render(graphics, mouseX, mouseY, partialTick);
        } finally {
            graphics.disableScissor();
        }

        drawScrollbar(graphics);
        drawFooterSeparator(graphics);
        renderFixedButtons(graphics, mouseX, mouseY, partialTick);
    }

    private void drawPanel(GuiGraphics graphics) {
        int panelBottom = Math.min(height - 4, buttonY + footerHeight + 4);
        graphics.fill(panelLeft - 12, panelTop, panelLeft + panelWidth + 12, panelBottom, PANEL_COLOR);
        graphics.renderOutline(panelLeft - 12, panelTop, panelWidth + 24, panelBottom - panelTop, PANEL_BORDER);
    }

    private void drawScrollableContent(GuiGraphics graphics, int labelX) {
        if (!compactLayout) {
            graphics.drawString(font, Component.translatable("lan_tunnel.screen.section_connection"),
                    labelX, sectionBaseY - scrollOffset, 0xD8D8D8);
            graphics.fill(panelLeft + 12, sectionBaseY - scrollOffset - 6, panelLeft + panelWidth - 12,
                    sectionBaseY - scrollOffset - 5, SECTION_COLOR);
        }
        drawLabel(graphics, "lan_tunnel.screen.relay_host", labelX, formBaseY - scrollOffset);
        drawLabel(graphics, "lan_tunnel.screen.control_port", labelX, formBaseY - scrollOffset + rowHeight);
        drawLabel(graphics, "lan_tunnel.screen.token", labelX, formBaseY - scrollOffset + rowHeight * 2);
        drawLabel(graphics, "lan_tunnel.screen.public_port", labelX, formBaseY - scrollOffset + rowHeight * 3);
        drawLabel(graphics, "lan_tunnel.screen.reconnect_delay", labelX, formBaseY - scrollOffset + rowHeight * 4);
        drawLabel(graphics, "lan_tunnel.screen.test_timeout", labelX, formBaseY - scrollOffset + rowHeight * 5);
        drawStatus(graphics);
    }

    private void drawStatus(GuiGraphics graphics) {
        TunnelStatus status = LanTunnelManager.get().getStatus();
        int statusLeft = panelLeft + 18;
        int statusRight = panelLeft + panelWidth - 18;
        int statusWidth = statusRight - statusLeft;
        int lineHeight = compactLayout ? 11 : 14;

        int statusY = statusBaseY - scrollOffset;
        int statusTop = statusY - (compactLayout ? 2 : 4);
        graphics.fill(statusLeft - 6, statusTop, statusRight + 6, statusY + statusHeight, 0x66000000);
        graphics.drawString(font, Component.translatable("lan_tunnel.screen.section_status"), statusLeft, statusY, 0xD8D8D8);
        graphics.drawString(font, Component.translatable("lan_tunnel.screen.status", compactStatus(status)),
                statusLeft, statusY + lineHeight, status.connected() ? 0x80FF80 : 0xFFD080);
        if (status.localPort() > 0) {
            graphics.drawString(font, Component.translatable("lan_tunnel.screen.local_port", status.localPort()),
                    statusLeft + Math.min(230, statusWidth / 2), statusY + lineHeight, 0xC8C8C8);
        }
        if (!message.getString().isBlank()) {
            graphics.drawString(font, shorten(message.getString(), 72), statusLeft, statusY + lineHeight * 2, 0xFFD080);
        } else if (!status.message().isBlank() && !status.connected()) {
            graphics.drawString(font, shorten(status.message(), 72), statusLeft, statusY + lineHeight * 2, 0xAAAAAA);
        } else if (!status.publicAddress().isBlank()) {
            graphics.drawString(font, Component.translatable("lan_tunnel.screen.share_address",
                    status.publicAddress()), statusLeft, statusY + lineHeight * 2, 0x80FF80);
        } else if (status.publicPort() > 0) {
            graphics.drawString(font, Component.translatable("lan_tunnel.screen.share_address",
                    relayHostBox.getValue().trim() + ":" + status.publicPort()), statusLeft, statusY + lineHeight * 2, 0x80FF80);
        } else {
            graphics.drawString(font, Component.translatable("lan_tunnel.screen.share_address", "-"),
                    statusLeft, statusY + lineHeight * 2, 0x777777);
        }
        if (!status.diagnosticCode().isBlank() && !status.diagnosticCode().equals("CONNECTED")) {
            graphics.drawString(font, Component.translatable("lan_tunnel.screen.diagnostic",
                    status.diagnosticCode(), status.consecutiveFailures()), statusLeft + Math.min(300, statusWidth / 2),
                    statusY + lineHeight * 3, 0xAAAAAA);
        }
    }

    private void drawScrollbar(GuiGraphics graphics) {
        if (maxScroll <= 0) {
            return;
        }
        int trackTop = contentTop + 2;
        int trackBottom = contentBottom - 2;
        int trackHeight = Math.max(1, trackBottom - trackTop);
        int thumbHeight = Math.max(24, trackHeight * (contentBottom - contentTop) / Math.max(1, contentHeight));
        int thumbTop = trackTop + (trackHeight - thumbHeight) * scrollOffset / Math.max(1, maxScroll);
        graphics.fill(scrollbarX, trackTop, scrollbarX + 4, trackBottom, 0x44000000);
        graphics.fill(scrollbarX, thumbTop, scrollbarX + 4, thumbTop + thumbHeight, 0x99FFFFFF);
    }

    private void drawFooterSeparator(GuiGraphics graphics) {
        graphics.fill(panelLeft + 12, contentBottom + 3, panelLeft + panelWidth - 12, contentBottom + 4, SECTION_COLOR);
    }

    private void renderFixedButtons(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        startStopButton.render(graphics, mouseX, mouseY, partialTick);
        testButton.render(graphics, mouseX, mouseY, partialTick);
        copyAddressButton.render(graphics, mouseX, mouseY, partialTick);
        saveButton.render(graphics, mouseX, mouseY, partialTick);
        backButton.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawLabel(GuiGraphics graphics, String key, int x, int y) {
        graphics.drawString(font, Component.translatable(key), x, y + 6, 0xC0C0C0);
    }

    private Component compactStatus(TunnelStatus status) {
        if (status.connected()) {
            return Component.translatable("lan_tunnel.status.published");
        }
        if (status.running()) {
            return Component.translatable("lan_tunnel.status.connecting");
        }
        return Component.translatable("lan_tunnel.status.stopped");
    }

    private static String shorten(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private boolean applyAndSave() {
        LanTunnelConfig next = formConfig();

        String validationError = next.validate();
        if (validationError != null) {
            message = Component.literal(validationError);
            return false;
        }

        try {
            LanTunnelConfig.replace(next);
            LanTunnelConfig.get().save();
            LanTunnelManager.get().onConfigChanged();
            message = Component.translatable("lan_tunnel.screen.saved");
            return true;
        } catch (IOException exception) {
            message = Component.literal("Save failed: " + exception.getMessage());
            return false;
        }
    }

    private LanTunnelConfig formConfig() {
        LanTunnelConfig next = LanTunnelConfig.get().copy();
        next.setEnabled(enabledCheckbox.selected());
        next.setAutoStart(autoStartCheckbox.selected());
        next.setAllowOfflinePlayers(allowOfflinePlayersCheckbox.selected());
        next.setShowLatencyOverlay(showLatencyOverlayCheckbox.selected());
        next.setAutoSelectNode(autoSelectNodeCheckbox.selected());
        next.setSingleRelayNode(relayHostBox.getValue().trim(), parseInt(controlPortBox.getValue(), -1));
        next.setToken(tokenBox.getValue().trim());
        next.setRequestedPublicPort(parseInt(publicPortBox.getValue(), -1));
        next.setReconnectDelaySeconds(parseInt(reconnectDelayBox.getValue(), -1));
        next.setConnectionTestTimeoutSeconds(parseInt(testTimeoutBox.getValue(), -1));
        return next;
    }

    private void testConnection() {
        LanTunnelConfig next = formConfig();
        String validationError = next.validate();
        if (validationError != null) {
            message = Component.literal(validationError);
            return;
        }
        message = Component.translatable("lan_tunnel.screen.testing");
        Thread thread = new Thread(() -> {
            ConnectionTestResult result = LanTunnelClient.testConnection(next);
            message = Component.literal(result.message());
        }, "lan-tunnel-connection-test");
        thread.setDaemon(true);
        thread.start();
    }

    private void copyShareAddress() {
        TunnelStatus status = LanTunnelManager.get().getStatus();
        String address = status.publicAddress();
        if (address == null || address.isBlank()) {
            if (status.publicPort() > 0) {
                address = relayHostBox.getValue().trim() + ":" + status.publicPort();
            }
        }
        if (address == null || address.isBlank()) {
            message = Component.translatable("lan_tunnel.screen.no_address");
            return;
        }
        minecraft.keyboardHandler.setClipboard(address);
        message = Component.translatable("lan_tunnel.screen.address_copied");
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private void updateStartStopButton() {
        if (startStopButton != null) {
            startStopButton.setMessage(Component.translatable(LanTunnelManager.get().isRunning()
                    ? "lan_tunnel.screen.stop"
                    : "lan_tunnel.screen.start"));
        }
    }

    private void layoutButtons() {
        int gap = 8;
        if (twoLineButtons) {
            int rowWidth = Math.max(56, (panelWidth - gap * 2) / 3);
            startStopButton.setX(panelLeft);
            startStopButton.setY(buttonY);
            startStopButton.setWidth(rowWidth);
            testButton.setX(panelLeft + rowWidth + gap);
            testButton.setY(buttonY);
            testButton.setWidth(rowWidth);
            copyAddressButton.setX(panelLeft + (rowWidth + gap) * 2);
            copyAddressButton.setY(buttonY);
            copyAddressButton.setWidth(rowWidth);

            int wideButtonWidth = Math.max(72, (panelWidth - gap) / 2);
            saveButton.setX(panelLeft);
            saveButton.setY(buttonY + 24);
            saveButton.setWidth(wideButtonWidth);
            backButton.setX(panelLeft + wideButtonWidth + gap);
            backButton.setY(buttonY + 24);
            backButton.setWidth(wideButtonWidth);
            return;
        }

        int buttonWidth = Math.max(48, (panelWidth - gap * 4) / 5);
        startStopButton.setX(panelLeft);
        startStopButton.setY(buttonY);
        startStopButton.setWidth(buttonWidth);
        testButton.setX(panelLeft + buttonWidth + gap);
        testButton.setY(buttonY);
        testButton.setWidth(buttonWidth);
        copyAddressButton.setX(panelLeft + (buttonWidth + gap) * 2);
        copyAddressButton.setY(buttonY);
        copyAddressButton.setWidth(buttonWidth);
        saveButton.setX(panelLeft + (buttonWidth + gap) * 3);
        saveButton.setY(buttonY);
        saveButton.setWidth(buttonWidth);
        backButton.setX(panelLeft + (buttonWidth + gap) * 4);
        backButton.setY(buttonY);
        backButton.setWidth(buttonWidth);
    }

    private void layoutWidgets() {
        int checkboxY = checkboxBaseY - scrollOffset;
        enabledCheckbox.setY(checkboxY);
        autoStartCheckbox.setY(checkboxY + checkboxStep);
        allowOfflinePlayersCheckbox.setY(checkboxY + checkboxStep * 2);
        showLatencyOverlayCheckbox.setY(checkboxY + checkboxStep * 3);
        autoSelectNodeCheckbox.setY(checkboxY + checkboxStep * 4);

        relayHostBox.setY(formBaseY - scrollOffset);
        controlPortBox.setY(formBaseY - scrollOffset + rowHeight);
        tokenBox.setY(formBaseY - scrollOffset + rowHeight * 2);
        publicPortBox.setY(formBaseY - scrollOffset + rowHeight * 3);
        reconnectDelayBox.setY(formBaseY - scrollOffset + rowHeight * 4);
        testTimeoutBox.setY(formBaseY - scrollOffset + rowHeight * 5);
        updateContentWidgetVisibility();
    }

    private void updateContentWidgetVisibility() {
        setContentWidgetVisibility(enabledCheckbox, 20);
        setContentWidgetVisibility(autoStartCheckbox, 20);
        setContentWidgetVisibility(allowOfflinePlayersCheckbox, 20);
        setContentWidgetVisibility(showLatencyOverlayCheckbox, 20);
        setContentWidgetVisibility(autoSelectNodeCheckbox, 20);
        setContentWidgetVisibility(relayHostBox, fieldHeight);
        setContentWidgetVisibility(controlPortBox, fieldHeight);
        setContentWidgetVisibility(tokenBox, fieldHeight);
        setContentWidgetVisibility(publicPortBox, fieldHeight);
        setContentWidgetVisibility(reconnectDelayBox, fieldHeight);
        setContentWidgetVisibility(testTimeoutBox, fieldHeight);
    }

    private void setContentWidgetVisibility(AbstractWidget widget, int widgetHeight) {
        boolean visible = widget.getY() + widgetHeight > contentTop && widget.getY() < contentBottom;
        widget.visible = visible;
        widget.active = widget.getY() >= contentTop && widget.getY() + widgetHeight <= contentBottom;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (maxScroll <= 0 || mouseX < panelLeft || mouseX > panelLeft + panelWidth || mouseY < contentTop || mouseY > contentBottom) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        scrollOffset = clamp(scrollOffset - (int) Math.signum(delta) * SCROLL_STEP, 0, maxScroll);
        layoutWidgets();
        return true;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
