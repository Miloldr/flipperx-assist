package com.flipperx.assist;

import com.flipperx.assist.commands.AssistCommand;
import com.flipperx.assist.config.ModConfig;
import com.flipperx.assist.game.GameUtil;
import com.flipperx.assist.game.ScreenReader;
import com.flipperx.assist.hud.AssistHud;
import com.flipperx.assist.hud.ProfitPops;
import com.flipperx.assist.net.AssistSocket;
import com.flipperx.assist.net.VersionCheck;
import com.flipperx.assist.screen.Summary;
import com.flipperx.assist.screen.SummaryScreen;
import com.flipperx.assist.update.Updater;
import com.google.gson.JsonObject;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.gui.screens.ChatScreen;

public class AssistClient implements ClientModInitializer {
    private static final String ENDPOINT = System.getProperty("bzassist.dev") != null
            ? "ws://127.0.0.1:8000/ws/assist"
            : "wss://bot.flipperx.digital/ws/assist";

    private volatile long tickEveryMs = 2000;

    private static AssistClient instance;
    private static final AssistState STATE = new AssistState();

    private ModConfig config;
    private AssistSocket socket;

    private KeyMapping startKey;
    private KeyMapping stopKey;
    private KeyMapping commandKey;
    private boolean confirmAutocommand;
    private String sentCommand;
    private long sentCommandAt;
    private static final long RESEND_AFTER_MS = 3_000;

    private volatile String authUuid = "";
    private volatile boolean authSent = false;
    private long lastTickAt = 0;
    private int lastScreenHash = 0;
    private long authConnection = -1;
    private String pendingLinkUuid;
    private boolean linkSent;
    private boolean wasInWorld;
    private long leftWorldAt;
    private static final long WORLD_GONE_MS = 15_000;
    private boolean authBlocked;
    private boolean awaitingStep;
    private int reportedContext;
    private int acceptedContext;
    private String acceptedSlotName;
    private Map<Integer, String> reportedSlotNames = new HashMap<>();
    private boolean forceReport;
    private boolean discardStep;
    private long authAt;
    private boolean announcedReady;
    private Summary lastSummary;
    private long summaryOpenUntil;
    private static final long SUMMARY_WAIT_MS = 60_000;
    private static final long FIRST_REMINDER_MS = 2 * 60_000;
    private static final long REMINDER_EVERY_MS = 20 * 60_000;
    private long nextReminderAt;
    private static final long VERSION_CHECK_DELAY_MS = 10_000;
    private static final long VERSION_CHECK_EVERY_MS = 6 * 60 * 60_000;
    private long versionCheckAt;
    private String announcedVersion;
    private boolean notedVersion;
    private final AtomicBoolean updating = new AtomicBoolean();

    public static AssistClient get() {
        return instance;
    }

    public static AssistState state() {
        return STATE;
    }

    @Override
    public void onInitializeClient() {
        instance = this;
        config = ModConfig.get();
        socket = new AssistSocket(URI.create(ENDPOINT), STATE, Minecraft.getInstance()::execute, this::onMessage);

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("bzassist", "general"));
        startKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.bzassist.start", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_BRACKET,
                category));
        stopKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.bzassist.stop", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_BRACKET,
                category));
        commandKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.bzassist.command", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B,
                category));

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bzassist", "panel"),
                (graphics, tickCounter) -> {
                    Screen open = GameUtil.currentScreen();
                    if (open instanceof AbstractContainerScreen || open instanceof AbstractSignEditScreen) {
                        return;
                    }
                    AssistHud.render(graphics, STATE);
                    ProfitPops.render(graphics);
                });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
                AssistCommand.register(dispatcher));

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay || !STATE.linked()) return;
            JsonObject o = new JsonObject();
            o.addProperty("type", "chat");
            o.addProperty("line", message.getString());
            socket.send(o);
        });

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);

        Updater.init();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> installOnExit());
    }

    private void onTick(Minecraft client) {
        if (client.player == null) {
            long now = System.currentTimeMillis();
            if (wasInWorld) leftWorldAt = now;
            wasInWorld = false;
            if (leftWorldAt > 0 && now - leftWorldAt > WORLD_GONE_MS) {
                socket.close();
                STATE.reset();
                pendingLinkUuid = null;
                linkSent = false;
                authSent = false;
                leftWorldAt = 0;
                nextReminderAt = 0;
            }
            return;
        }
        wasInWorld = true;
        leftWorldAt = 0;
        String uuid = currentUuid();
        if (!authUuid.isEmpty() && !uuid.equals(authUuid)) {
            socket.close();
            STATE.reset();
            authSent = false;
            authBlocked = false;
            pendingLinkUuid = null;
            linkSent = false;
            authUuid = uuid;
        }
        if (authConnection != socket.generation()) {
            authSent = false;
            linkSent = false;
            awaitingStep = false;
            discardStep = false;
        }
        checkVersion(client);
        remindLogin(uuid);
        maybeConfirmAutocommand(client);
        if (authBlocked) return;
        if (config.tokenFor(uuid) != null || pendingLinkUuid != null) socket.connect();
        else STATE.status("Run /flipperx login to link this account.");
        if (socket.connected()) {
            authConnection = socket.generation();
            if (pendingLinkUuid != null && !linkSent) sendLink();
            if (pendingLinkUuid == null && !authSent) {
                String token = config.tokenFor(uuid);
                if (token != null) sendAuth(uuid, token);
            }
        }

        if (authSent && !STATE.linked() && System.currentTimeMillis() - authAt > 15_000) {
            socket.close();
        }
        while (startKey.consumeClick()) start();
        while (stopKey.consumeClick()) stop();
        while (commandKey.consumeClick()) sendStepCommand(client);

        maybeOpenSummary();
        maybeReport();
    }

    private void checkVersion(Minecraft client) {
        long now = System.currentTimeMillis();
        if (versionCheckAt == 0) {
            versionCheckAt = now + VERSION_CHECK_DELAY_MS;
        } else if (now >= versionCheckAt) {
            versionCheckAt = now + VERSION_CHECK_EVERY_MS;
            noteVersion();
            VersionCheck.fetch(client::execute, this::announceUpdate);
        }
    }

    private void noteVersion() {
        if (notedVersion) return;
        notedVersion = true;
        String now = VersionCheck.current();
        String waiting = Updater.pendingVersion();
        if (waiting != null && waiting.equals(config.updateTried)) {
            chat(Component.literal("Version " + waiting + " is downloaded but did not install when you last "
                            + "closed the game. It tries again when you close it, or download it from ")
                    .append(Chat.link(Chat.shortUrl(VersionCheck.PAGE), VersionCheck.PAGE))
                    .append(" and replace the old jar in your mods folder."));
        } else if (config.lastVersion != null && VersionCheck.compare(now, config.lastVersion) > 0) {
            chat("Updated to " + now + ".");
        }
        if (!now.equals(config.lastVersion)) {
            config.lastVersion = now;
            config.save();
        }
    }

    private void announceUpdate(VersionCheck.Release release) {
        if (release.version().equals(announcedVersion)) return;
        announcedVersion = release.version();
        if (release.version().equals(Updater.pendingVersion())) return;
        MutableComponent line = Component.literal("Version " + release.version() + " is out, you have "
                + VersionCheck.current() + ". ");
        if (Updater.available() && release.installable()) {
            chat(line.append(Chat.button("[Update]", "/flipperx update"))
                    .append(" downloads it now, and it installs when you close Minecraft."));
        } else {
            chat(line.append("Download it from ")
                    .append(Chat.link(Chat.shortUrl(release.page()), release.page()))
                    .append(" and replace the old jar in your mods folder."));
        }
    }

    public void update() {
        if (!updating.compareAndSet(false, true)) {
            chat("Already downloading the update.");
            return;
        }
        VersionCheck.latest().whenComplete((release, error) -> {
            if (error != null || release == null) {
                updating.set(false);
                chat("Could not reach the server to check for a new version.");
                return;
            }
            String current = VersionCheck.current();
            if (VersionCheck.compare(release.version(), current) <= 0) {
                updating.set(false);
                chat("You have the latest version, " + current + ".");
                return;
            }
            if (release.version().equals(Updater.pendingVersion())) {
                updating.set(false);
                chat(release.version() + " is already downloaded. It installs when you close Minecraft.");
                return;
            }
            chat("Downloading " + release.version() + "...");
            Updater.download(release).whenComplete((done, failure) -> {
                updating.set(false);
                if (failure == null) {
                    chat("Downloaded " + release.version() + ". It installs when you close Minecraft "
                            + "and runs the next time you start it.");
                } else {
                    chat(Component.literal("Could not update (" + Updater.reason(failure) + "). Download it from ")
                            .append(Chat.link(Chat.shortUrl(release.page()), release.page()))
                            .append(" and replace the old jar in your mods folder."));
                }
            });
        });
    }

    private void installOnExit() {
        String version = Updater.pendingVersion();
        if (version == null) return;
        config.updateTried = version;
        config.save();
        Updater.install();
    }

    private void remindLogin(String uuid) {
        if (!config.loginReminders || config.tokenFor(uuid) != null || pendingLinkUuid != null) {
            nextReminderAt = 0;
            return;
        }
        long now = System.currentTimeMillis();
        if (nextReminderAt == 0) {
            nextReminderAt = now + FIRST_REMINDER_MS;
        } else if (now >= nextReminderAt) {
            nextReminderAt = now + REMINDER_EVERY_MS;
            chat(Component.literal("You are not logged in yet. ")
                    .append(Chat.button("[Log in]", "/flipperx login"))
                    .append(" to start flipping, or ")
                    .append(Chat.button("[stop these reminders]", "/flipperx reminders off"))
                    .append("."));
        }
    }

    private void sendStepCommand(Minecraft client) {
        if (!config.autocommand || client.player == null || GameUtil.currentScreen() != null) return;
        AssistState.Step step = STATE.step();
        if (!STATE.running() || !STATE.current() || !step.isCommand() || step.text().isEmpty()) return;
        long now = System.currentTimeMillis();
        if (step.text().equals(sentCommand) && now - sentCommandAt < RESEND_AFTER_MS) return;
        sentCommand = step.text();
        sentCommandAt = now;
        client.player.connection.sendCommand(step.text());
    }

    public String autocommandKey() {
        if (!config.autocommand || commandKey.isUnbound()) return null;
        return commandKey.getTranslatedKeyMessage().getString();
    }

    public void autocommand(Boolean on) {
        if (on == null) {
            chat(config.autocommand ? "Autocommand is on." : "Autocommand is off.");
            return;
        }
        if (!on) {
            config.autocommand = false;
            config.save();
            chat("Autocommand is off.");
            return;
        }
        if (config.autocommand) {
            chat("Autocommand is already on.");
            return;
        }
        confirmAutocommand = true;
    }

    private void maybeConfirmAutocommand(Minecraft client) {
        if (!confirmAutocommand || GameUtil.currentScreen() != null) return;
        confirmAutocommand = false;
        String key = commandKey.getTranslatedKeyMessage().getString();
        client.gui.setScreen(new ConfirmScreen(yes -> {
            client.gui.setScreen(null);
            if (yes) {
                config.autocommand = true;
                config.save();
                chat("Autocommand is on. When the panel shows a command, press " + key + " to send it.");
            } else {
                chat("Autocommand stays off.");
            }
        }, Component.literal("Turn on autocommand?"),
                Component.literal("Pressing " + key + " will send the command on the panel for you. "
                        + "This is a gray area. Hypixel may count it as macroing and ban the account. "
                        + "Are you sure?"),
                Component.literal("Turn it on"), Component.literal("Cancel")));
    }

    public void loginReminders(boolean on) {
        config.loginReminders = on;
        config.save();
        nextReminderAt = 0;
        chat(on ? "Login reminders are back on."
                : "No more login reminders. /flipperx reminders on turns them back on.");
    }

    private void maybeOpenSummary() {
        if (lastSummary == null || summaryOpenUntil == 0) return;
        if (System.currentTimeMillis() > summaryOpenUntil) {
            summaryOpenUntil = 0;
            return;
        }
        if (GameUtil.currentScreen() != null) return;
        summaryOpenUntil = 0;
        Minecraft.getInstance().gui.setScreen(new SummaryScreen(lastSummary));
    }

    private void maybeReport() {
        if (!STATE.linked() || !authSent || !socket.connected()) return;
        long now = System.currentTimeMillis();
        int hash = screenHash();
        boolean changed = hash != lastScreenHash;
        AssistState.Step shown = STATE.step();
        if (shown.isClick()) STATE.current(targetMatches(shown, acceptedSlotName));
        if (awaitingStep) {
            if (now - lastTickAt > 30_000) socket.close();
            return;
        }
        if (!forceReport && !changed && now - lastTickAt < tickEveryMs) return;
        forceReport = false;
        lastScreenHash = hash;
        lastTickAt = now;
        reportedContext = screenContext();
        reportedSlotNames.clear();
        var menu = GameUtil.handler();
        if (menu != null) for (var slot : menu.slots) {
            reportedSlotNames.put(slot.index, GameUtil.rawName(slot.getItem()));
        }
        awaitingStep = socket.send(ScreenReader.tick());
    }

    private int screenHash() {
        var menu = GameUtil.handler();
        if (menu == null) {
            Screen screen = GameUtil.currentScreen();
            return screen instanceof AbstractSignEditScreen ? 0x5349474E : GameUtil.screenTitle().hashCode();
        }
        int h = 31 * GameUtil.screenTitle().hashCode() + menu.containerId;
        for (var slot : menu.slots) {
            var st = slot.getItem();
            if (st == null || st.isEmpty()) continue;
            h = h * 31 + slot.index;
            h = h * 31 + GameUtil.rawName(st).hashCode();
            h = h * 31 + st.getCount();
            h = h * 31 + GameUtil.lore(st).hashCode();
        }
        return h;
    }

    private int screenContext() {
        Screen screen = GameUtil.currentScreen();
        if (screen == null || screen instanceof ChatScreen) return 0;
        return 31 * System.identityHashCode(screen) + GameUtil.screenTitle().hashCode();
    }

    private boolean targetMatches(AssistState.Step step, String name) {
        if (!step.isClick()) return true;
        var menu = GameUtil.handler();
        if (menu == null || name == null || name.isEmpty()) return false;
        for (var slot : menu.slots) {
            if (slot.index == step.slot()) return name.equals(GameUtil.rawName(slot.getItem()));
        }
        return false;
    }

    private void onMessage(JsonObject msg) {
        String type = msg.has("type") ? msg.get("type").getAsString() : "";
        switch (type) {
            case "link_url" -> {
                String url = msg.get("url").getAsString();
                chat(Component.literal("Click to finish logging in: ").append(Chat.link(url, url)));
            }
            case "link_complete" -> {
                if (pendingLinkUuid == null || !pendingLinkUuid.equals(currentUuid())) return;
                config.setToken(pendingLinkUuid, msg.get("session_token").getAsString());
                pendingLinkUuid = null;
                linkSent = false;
                chat("§aLinked. Authenticating...");
                authSent = false;
            }
            case "auth_ok" -> {
                authSent = true;
                STATE.linked(true);
                forceReport = true;
                authUuid = currentUuid();
                if (msg.has("idle_tick_ms")) tickEveryMs = Math.max(250, Math.min(10_000, msg.get("idle_tick_ms").getAsLong()));
                boolean running = msg.has("running") && msg.get("running").getAsBoolean();
                boolean resume = STATE.takeResume();
                STATE.running(running);
                if (running) {
                    STATE.status("Reconnected.");
                    chat("§aReconnected, carrying on.");
                } else if (resume) {
                    start(false);
                    chat("§aReconnected. Assist re-reads your orders first, then carries on.");
                } else {
                    STATE.status("Ready. Press " + startKey.getTranslatedKeyMessage().getString() + " to start.");
                    if (!announcedReady) {
                        chat("§aReady. Press §e" + startKey.getTranslatedKeyMessage().getString()
                                + "§a to start, §e" + stopKey.getTranslatedKeyMessage().getString() + "§a to stop.");
                    }
                    announcedReady = true;
                }
            }
            case "auth_err" -> {
                authSent = false;
                STATE.disconnected();
                STATE.forgetResume();
                String reason = msg.has("reason") ? msg.get("reason").getAsString() : "unknown";
                if (reason.contains("token")) config.clearToken(currentUuid());
                authBlocked = true;
                socket.close();
                STATE.status(reason + ". Run /flipperx login to reconnect.");
                chat("§c" + STATE.status());
            }
            case "step" -> {
                awaitingStep = false;
                if (discardStep) {
                    discardStep = false;
                    forceReport = true;
                    return;
                }
                if (msg.has("hud") && msg.get("hud").isJsonObject()) STATE.hud(msg.getAsJsonObject("hud"));
                AssistState.Step incoming = AssistState.Step.from(msg);
                String targetName = reportedSlotNames.get(incoming.slot());
                if (screenContext() != reportedContext || !targetMatches(incoming, targetName)) {
                    forceReport = true;
                    return;
                }
                boolean pending = msg.has("pending") && msg.get("pending").getAsBoolean();
                STATE.receiveStep(incoming, pending);
                acceptedContext = reportedContext;
                acceptedSlotName = targetName;
                if (msg.has("notice") && !msg.get("notice").isJsonNull()) {
                    STATE.notice(msg.get("notice").getAsString(), 5000);
                }
            }
            case "gain" -> {
                if (msg.has("profit")) ProfitPops.spawn(msg.get("profit").getAsDouble());
                if (msg.has("goal_reached") && msg.get("goal_reached").getAsBoolean()) {
                    ProfitPops.banner("Goal reached");
                }
            }
            case "summary" -> {
                lastSummary = Summary.from(msg);
                summaryOpenUntil = System.currentTimeMillis() + SUMMARY_WAIT_MS;
            }
            case "message" -> {
                if (msg.has("text")) chat(msg.get("text").getAsString());
            }
            default -> { }
        }
    }

    private void sendAuth(String uuid, String token) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "auth");
        o.addProperty("token", token);
        o.addProperty("mc_uuid", uuid);
        Minecraft mc = Minecraft.getInstance();
        o.addProperty("mc_username", mc.getUser() == null ? "" : mc.getUser().getName());
        o.addProperty("version", VersionCheck.current());
        socket.send(o);
        STATE.status("Authenticating...");
        authAt = System.currentTimeMillis();
        authSent = true;
        authUuid = uuid;
    }

    public void requestLink() {
        socket.close();
        STATE.reset();
        authSent = false;
        authBlocked = false;
        announcedReady = false;
        pendingLinkUuid = currentUuid();
        authUuid = pendingLinkUuid;
        linkSent = false;
        STATE.status("Connecting to request a login link...");
        socket.connect();
        chat("Asking the website for a link...");
    }

    private void sendLink() {
        JsonObject o = new JsonObject();
        o.addProperty("type", "link_request");
        o.addProperty("mc_uuid", pendingLinkUuid);
        o.addProperty("mc_username", Minecraft.getInstance().getUser().getName());
        o.addProperty("version", VersionCheck.current());
        linkSent = socket.send(o);
        STATE.status("Finish linking using the link in chat.");
    }

    public void logout() {
        socket.close();
        config.clearToken(currentUuid());
        pendingLinkUuid = null;
        linkSent = false;
        authBlocked = false;
        STATE.reset();
        STATE.status("Run /flipperx login to link this account.");
        authSent = false;
        announcedReady = false;
        STATE.running(false);
        chat("Forgot this account's link.");
    }

    public void start() {
        start(true);
    }

    private void start(boolean announce) {
        if (STATE.running()) return;
        if (!STATE.linked() || !socket.connected()) {
            chat("§e" + STATE.status());
            return;
        }
        JsonObject o = new JsonObject();
        o.addProperty("type", "start");
        socket.send(o);
        STATE.step(AssistState.Step.NONE);
        STATE.running(true);
        forceReport = true;
        discardStep = awaitingStep;
        if (announce) chat("§aOn. Do what the panel says; it never clicks for you.");
    }

    public void stop() {
        JsonObject o = new JsonObject();
        o.addProperty("type", "stop");
        socket.send(o);
        STATE.forgetResume();
        STATE.running(false);
        STATE.step(AssistState.Step.NONE);
        STATE.status("Paused. Press " + startKey.getTranslatedKeyMessage().getString() + " to start.");
        forceReport = true;
        discardStep = awaitingStep;
        chat("Off.");
    }

    public boolean handleKey(KeyEvent event) {
        if (GameUtil.currentScreen() != null
                && GameUtil.currentScreen().getFocused() instanceof EditBox) return false;
        if (startKey != null && startKey.matches(event)) { start(); return true; }
        if (stopKey != null && stopKey.matches(event)) { stop(); return true; }
        if (commandKey != null && commandKey.matches(event)) return commandKeyInMenu();
        return false;
    }

    private boolean commandKeyInMenu() {
        AssistState.Step step = STATE.step();
        if (!config.autocommand || !STATE.running()
                || !(step.isCommand() || "close".equals(step.kind()))) return false;
        STATE.notice("Close the menu first.", 3000);
        return true;
    }

    public void showSummary() {
        if (lastSummary == null) {
            chat("Nothing to show yet. The summary appears when you stop assist after a run.");
            return;
        }
        summaryOpenUntil = System.currentTimeMillis() + SUMMARY_WAIT_MS;
    }

    public void shareSummary(String url, String id) {
        Minecraft.getInstance().keyboardHandler.setClipboard(url);
        chat(Component.literal("Link copied: ").append(Chat.link(Chat.shortUrl(url), url)));
        if (id == null || !STATE.linked() || !socket.connected()) return;
        JsonObject o = new JsonObject();
        o.addProperty("type", "share");
        o.addProperty("id", id);
        socket.send(o);
    }

    public void goal(String text) {
        if (!STATE.linked() || !socket.connected()) {
            chat("§e" + STATE.status());
            return;
        }
        JsonObject o = new JsonObject();
        o.addProperty("type", "goal");
        o.addProperty("text", text == null ? "" : text);
        socket.send(o);
    }

    public void resetHud() {
        config.hudX = -1;
        config.hudY = 8;
        config.save();
        chat("Panel position reset. It sits beside the open menu; drag it to move it.");
    }

    public void printHelp() {
        chat("§e/flipperx login §7link this account   §e/flipperx start §7or §e] §7begin");
        chat("§e/flipperx hud reset §7restore the panel position");
        chat("§e/flipperx goal 500m Hyperion §7save toward something   §e/flipperx goal clear §7drop it");
        chat("§e/flipperx summary §7the last session again");
        chat("§e/flipperx update §7download the latest version");
        chat("§e/flipperx stop §7or §e[ §7pause   §e/flipperx logout §7unlink");
        chat("§e/flipperx reminders off §7no login reminders while logged out");
    }

    private static void chat(String text) {
        chat(Component.literal(text));
    }

    private static void chat(Component body) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.gui != null && mc.gui.hud != null) {
                mc.gui.hud.getChat().addClientSystemMessage(Chat.line(body));
            }
        });
    }

    private static String currentUuid() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getUser() != null && mc.getUser().getProfileId() != null) {
            return mc.getUser().getProfileId().toString().replace("-", "").toLowerCase();
        }
        return mc.player == null ? "" : mc.player.getStringUUID().replace("-", "").toLowerCase();
    }
}
