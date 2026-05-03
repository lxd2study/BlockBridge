package com.lxd.lantunnel.client;

import com.lxd.lantunnel.config.LanTunnelConfig;
import com.lxd.lantunnel.tunnel.ConnectionTestResult;
import com.lxd.lantunnel.tunnel.LanTunnelClient;
import com.lxd.lantunnel.tunnel.LanTunnelManager;
import com.lxd.lantunnel.tunnel.TunnelStatus;
import net.minecraft.client.gui.GuiGraphics;
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
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int fieldX;
    private int fieldWidth;
    private int formY;
    private int rowHeight;
    private int fieldHeight;
    private int checkboxY;
    private int sectionY;
    private int statusY;
    private int statusHeight;
    private int buttonY;
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

        int y = checkboxY;

        enabledCheckbox = addRenderableWidget(new Checkbox(panelLeft + 18, y, panelWidth - 36, 20,
                Component.translatable("lan_tunnel.screen.enabled"), config.isEnabled()));
        y += compactLayout ? 22 : 24;
        autoStartCheckbox = addRenderableWidget(new Checkbox(panelLeft + 18, y, panelWidth - 36, 20,
                Component.translatable("lan_tunnel.screen.auto_start"), config.isAutoStart()));
        y += compactLayout ? 21 : 24;
        allowOfflinePlayersCheckbox = addRenderableWidget(new Checkbox(panelLeft + 18, y, panelWidth - 36, 20,
                Component.translatable("lan_tunnel.screen.allow_offline_players"), config.isAllowOfflinePlayers()));
        y += compactLayout ? 19 : 24;
        showLatencyOverlayCheckbox = addRenderableWidget(new Checkbox(panelLeft + 18, y, panelWidth - 36, 20,
                Component.translatable("lan_tunnel.screen.show_latency_overlay"), config.isShowLatencyOverlay()));
        y += compactLayout ? 19 : 24;
        autoSelectNodeCheckbox = addRenderableWidget(new Checkbox(panelLeft + 18, y, panelWidth - 36, 20,
                Component.translatable("lan_tunnel.screen.auto_select_node"), config.isAutoSelectNode()));

        y = formY;
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

        int gap = 8;
        int buttonWidth = Math.max(48, (panelWidth - gap * 4) / 5);
        startStopButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            if (LanTunnelManager.get().isRunning()) {
                LanTunnelManager.get().stopManually();
            } else if (applyAndSave()) {
                LanTunnelManager.get().startManually();
            }
        }).bounds(panelLeft, buttonY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("lan_tunnel.screen.test"), button -> testConnection())
                .bounds(panelLeft + buttonWidth + gap, buttonY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("lan_tunnel.screen.copy_address"), button -> copyShareAddress())
                .bounds(panelLeft + (buttonWidth + gap) * 2, buttonY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("lan_tunnel.screen.save"), button -> applyAndSave())
                .bounds(panelLeft + (buttonWidth + gap) * 3, buttonY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(panelLeft + (buttonWidth + gap) * 4, buttonY, buttonWidth, 20).build());

        updateStartStopButton();
    }

    private void computeLayout() {
        compactLayout = height < 460;
        panelWidth = Math.min(MAX_PANEL_WIDTH, Math.max(MIN_PANEL_WIDTH, width - 48));
        if (width < MIN_PANEL_WIDTH + 32) {
            panelWidth = Math.max(300, width - 24);
        }
        panelLeft = (width - panelWidth) / 2;
        panelTop = compactLayout ? 8 : 24;
        rowHeight = compactLayout ? COMPACT_ROW_HEIGHT : ROW_HEIGHT;
        fieldHeight = compactLayout ? 18 : 20;
        buttonY = height - (compactLayout ? 28 : 36);
        checkboxY = panelTop + (compactLayout ? 18 : 38);
        int optionStep = compactLayout ? 19 : 24;
        int optionsBottom = checkboxY + 20 + (OPTION_COUNT - 1) * optionStep;
        sectionY = optionsBottom + (compactLayout ? 8 : 14);
        formY = sectionY + (compactLayout ? 12 : 22);
        statusHeight = compactLayout ? 34 : 72;

        fieldX = panelLeft + LABEL_WIDTH + 20;
        fieldWidth = panelWidth - LABEL_WIDTH - 40;
        if (fieldWidth < 150) {
            fieldX = panelLeft + 104;
            fieldWidth = panelWidth - 124;
        }

        int preferredStatusY = buttonY - statusHeight - (compactLayout ? 8 : 24);
        int latestStatusY = buttonY - statusHeight - (compactLayout ? 4 : 10);
        int minStatusY = formY + rowHeight * FIELD_COUNT + (compactLayout ? 6 : 10);
        if (latestStatusY >= minStatusY) {
            statusY = Math.min(Math.max(preferredStatusY, minStatusY), latestStatusY);
        } else {
            int compactFormTop = optionsBottom + 4;
            formY = Math.max(panelTop + (compactLayout ? compactFormTop - panelTop : 160),
                    latestStatusY - rowHeight * FIELD_COUNT - (compactLayout ? 6 : 10));
            minStatusY = formY + rowHeight * FIELD_COUNT + (compactLayout ? 6 : 10);
            statusY = latestStatusY;
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
        int y = formY;

        graphics.drawCenteredString(font, title, width / 2, panelTop + 12, 0xFFFFFF);
        if (!compactLayout) {
            graphics.drawString(font, Component.translatable("lan_tunnel.screen.section_connection"),
                    labelX, sectionY, 0xD8D8D8);
        }
        drawLabel(graphics, "lan_tunnel.screen.relay_host", labelX, y);
        y += rowHeight;
        drawLabel(graphics, "lan_tunnel.screen.control_port", labelX, y);
        y += rowHeight;
        drawLabel(graphics, "lan_tunnel.screen.token", labelX, y);
        y += rowHeight;
        drawLabel(graphics, "lan_tunnel.screen.public_port", labelX, y);
        y += rowHeight;
        drawLabel(graphics, "lan_tunnel.screen.reconnect_delay", labelX, y);
        y += rowHeight;
        drawLabel(graphics, "lan_tunnel.screen.test_timeout", labelX, y);

        super.render(graphics, mouseX, mouseY, partialTick);
        drawStatus(graphics);
    }

    private void drawPanel(GuiGraphics graphics) {
        int panelBottom = Math.min(buttonY - 6, statusY + statusHeight + (compactLayout ? 4 : 10));
        graphics.fill(panelLeft - 12, panelTop, panelLeft + panelWidth + 12, panelBottom, PANEL_COLOR);
        graphics.renderOutline(panelLeft - 12, panelTop, panelWidth + 24, panelBottom - panelTop, PANEL_BORDER);
        if (!compactLayout) {
            graphics.fill(panelLeft + 12, sectionY - 6, panelLeft + panelWidth - 12, sectionY - 5, SECTION_COLOR);
        }
        int statusLineY = statusY - (compactLayout ? 5 : 8);
        graphics.fill(panelLeft + 12, statusLineY, panelLeft + panelWidth - 12, statusLineY + 1, SECTION_COLOR);
    }

    private void drawStatus(GuiGraphics graphics) {
        TunnelStatus status = LanTunnelManager.get().getStatus();
        int statusLeft = panelLeft + 18;
        int statusRight = panelLeft + panelWidth - 18;
        int statusWidth = statusRight - statusLeft;
        int lineHeight = compactLayout ? 11 : 14;

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
        if (!compactLayout && !status.diagnosticCode().isBlank() && !status.diagnosticCode().equals("CONNECTED")) {
            graphics.drawString(font, Component.translatable("lan_tunnel.screen.diagnostic",
                    status.diagnosticCode(), status.consecutiveFailures()), statusLeft + Math.min(300, statusWidth / 2),
                    statusY + lineHeight * 3, 0xAAAAAA);
        }
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

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
