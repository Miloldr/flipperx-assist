package com.flipperx.assist;

import com.flipperx.assist.commands.AssistCommand;
import com.flipperx.assist.config.ModConfig;
import com.flipperx.assist.game.Compat;
import com.flipperx.assist.game.GameUtil;
import com.flipperx.assist.game.Location;
import com.flipperx.assist.game.ScreenReader;
import com.flipperx.assist.hud.AssistHud;
import com.flipperx.assist.hud.GhostText;
import com.flipperx.assist.hud.ProfitPops;
import com.flipperx.assist.mixin.SignEditScreenAccessor;
import com.flipperx.assist.net.AssistSocket;
import com.flipperx.assist.net.Reports;
import com.flipperx.assist.net.VersionCheck;
import com.flipperx.assist.screen.SettingsScreen;
import com.flipperx.assist.screen.Summary;
import com.flipperx.assist.screen.SummaryScreen;
import com.flipperx.assist.update.Updater;
import com.google.gson.JsonObject;


import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;


import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.gui.screens.ChatScreen;

public class AssistClient implements ClientModInitializer {
    private static final String ENDPOINT = System.getProperty("bzassist.dev") != null
            ? "ws://127.0.0.1:8000/ws/assist"
            : "wss://bot.flipperx.digital/ws/assist";
    private static final String DISCORD = "https://flipperx.digital/invite";

    private volatile long tickEveryMs = 2000;

    private static AssistClient instance;
    private static final AssistState STATE = new AssistState();

    private ModConfig config;
    private AssistSocket socket;

    private KeyMapping startKey;
    private KeyMapping stopKey;
    private KeyMapping commandKey;
    private ConfirmScreen pendingConfirm;
    private boolean settingsPending;
    private int filledSign;
    private String sentCommand;
    private long sentCommandAt;
    private static final long RESEND_AFTER_MS = 3_000;
    private static final int TAB_CHIP_USES = 3;

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
    private final Reports reports = new Reports();
    private int reportedContext;
    private int acceptedContext;
    private String acceptedSlotName;
    private Map<Integer, String> reportedSlotNames = new HashMap<>();
    private boolean forceReport;
    private boolean discardStep;
    private final AtomicBoolean flushQueued = new AtomicBoolean();
    private int lastShape;
    private int reportedShape;
    private long shapeSince;
    private long recheckAt;
    private String lastMenuTitle = "";
    private JsonObject clickToReport;
    private String lastClickedName = "";
    private Prefetch prefetch;
    private static final long PREFETCH_TTL_MS = 15_000;

    private record Target(AssistState.Step step, String on, String title, boolean prefix,
                          List<String> names, boolean inv, String after) {}

    private record Prefetch(Target target, int from, long until) {}
    private long authAt;
    private boolean justLinked;
    private Connection gameConnection;
    private boolean greeted;
    private long greetAt;
    private static final long GREET_AFTER_MS = 3_000;
    private static final long GREET_WAIT_MS = 10_000;
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

    public static void slotClicked(Slot slot) {
        AssistClient client = instance;
        if (client == null) return;
        String raw = GameUtil.rawName(slot.getItem());
        client.lastClickedName = GameUtil.strip(raw).trim();
        JsonObject o = new JsonObject();
        o.addProperty("slot", slot.index);
        o.addProperty("name", raw);
        o.addProperty("view_id", System.identityHashCode(GameUtil.currentScreen()));
        client.clickToReport = o;
        client.forceReport = true;
        menuChanged();
    }

    public static void menuChanged() {
        AssistClient client = instance;
        if (client == null || !client.flushQueued.compareAndSet(false, true)) return;
        Minecraft.getInstance().execute(() -> {
            client.flushQueued.set(false);
            if (Minecraft.getInstance().player != null) client.maybeReport();
        });
    }

    @Override
    public void onInitializeClient() {
        instance = this;
        config = ModConfig.get();
        socket = new AssistSocket(URI.create(ENDPOINT), STATE, Minecraft.getInstance()::execute, this::onMessage);

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("bzassist", "general"));
        startKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.bzassist.start", Compat.keyboard(), Compat.KEY_RBRACKET,
                category));
        stopKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.bzassist.stop", Compat.keyboard(), Compat.KEY_LBRACKET,
                category));
        commandKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.bzassist.command", Compat.keyboard(), Compat.KEY_B,
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
        Location.update(client);
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
        Connection connection = client.player.connection.getConnection();
        if (connection != gameConnection) {
            gameConnection = connection;
            greeted = false;
            greetAt = 0;
        }
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
            reports.reset();
            discardStep = false;
            prefetch = null;
        }
        boolean skyBlock = Location.skyBlock();
        if (skyBlock) {
            checkVersion(client);
            remindLogin(uuid);
            greet(uuid);
        }
        maybeConfirm(client);
        maybeOpenSettings(client);
        if (authBlocked) return;
        if (config.tokenFor(uuid) == null && pendingLinkUuid == null) {
            STATE.status("Run /flipperx login to link this account.");
        } else if (skyBlock || pendingLinkUuid != null) {
            socket.connect();
        }
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
        while (startKey.consumeClick()) if (skyBlock) start();
        while (stopKey.consumeClick()) if (skyBlock) stop();
        while (commandKey.consumeClick()) if (skyBlock) sendStepCommand(client);
        if (skyBlock) fillSign();

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
            sendLoginReminder(now);
        }
    }

    private void sendLoginReminder(long now) {
        nextReminderAt = now + REMINDER_EVERY_MS;
        chat(Component.literal("You are not logged in yet. ")
                .append(Chat.button("[Log in]", "/flipperx login"))
                .append(" to start flipping, or ")
                .append(Chat.button("[stop these reminders]", "/flipperx reminders off"))
                .append("."));
    }

    private void greet(String uuid) {
        if (greeted) return;
        long now = System.currentTimeMillis();
        if (greetAt == 0) greetAt = now + GREET_AFTER_MS;
        if (now < greetAt) return;
        boolean token = config.tokenFor(uuid) != null;
        if (token && !STATE.linked() && !authBlocked && now < greetAt + GREET_WAIT_MS) return;
        greeted = true;
        if (STATE.running() || STATE.resumePending() || pendingLinkUuid != null || authBlocked) return;
        if (token) {
            chat(startLine(STATE.linked() ? "Ready. " : ""));
        } else if (config.loginReminders) {
            sendLoginReminder(now);
        }
    }

    private String startLine(String before) {
        return "§a" + before + "Press §e" + startKey.getTranslatedKeyMessage().getString()
                + "§a to start, §e" + stopKey.getTranslatedKeyMessage().getString() + "§a to stop.";
    }

    private String startAgain() {
        return "Press " + startKey.getTranslatedKeyMessage().getString() + " to start again.";
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

    public String tabCommand(String typed) {
        if (!config.tabFill || !Location.skyBlock() || !STATE.running() || !STATE.current()) return null;
        AssistState.Step step = STATE.step();
        if (!step.isCommand() || !GhostText.owns(typed, step.text())) return null;
        return "/" + step.text();
    }

    public boolean tabChip() {
        return config.tabFill && config.tabFills < TAB_CHIP_USES;
    }

    public void tabFilled() {
        if (config.tabFills >= TAB_CHIP_USES) return;
        config.tabFills++;
        config.save();
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
        String key = commandKey.getTranslatedKeyMessage().getString();
        pendingConfirm = confirmTurnOn("Autocommand",
                "Pressing " + key + " will send the command on the panel for you.",
                "Autocommand is on. When the panel shows a command, press " + key + " to send it.",
                () -> config.autocommand = true);
    }

    public void autosign(Boolean on) {
        if (on == null) {
            chat(config.autosign ? "Autosign is on." : "Autosign is off.");
            return;
        }
        if (!on) {
            config.autosign = false;
            config.save();
            chat("Autosign is off.");
            return;
        }
        if (config.autosign) {
            chat("Autosign is already on.");
            return;
        }
        pendingConfirm = confirmTurnOn("Autosign",
                "When a sign asks for an amount or a search, what the panel says will be typed in for you. "
                        + "You still close the sign yourself.",
                "Autosign is on. Amounts and searches on signs get typed in for you. "
                        + "You still close the sign yourself.",
                () -> config.autosign = true);
    }

    private ConfirmScreen confirmTurnOn(String name, String what, String onLine, Runnable enable) {
        return new ConfirmScreen(yes -> {
            Compat.setScreen(null);
            if (yes) {
                enable.run();
                config.save();
                chat(onLine);
            } else {
                chat(name + " stays off.");
            }
        }, Component.literal("Turn on " + name.toLowerCase(Locale.ROOT) + "?"),
                Component.literal(what + " This is a gray area. Hypixel may count it as macroing and ban the "
                        + "account. Are you sure?"),
                Component.literal("Turn it on"), Component.literal("Cancel"));
    }

    public void settings() {
        settingsPending = true;
    }

    private void maybeOpenSettings(Minecraft client) {
        if (!settingsPending || GameUtil.currentScreen() != null) return;
        settingsPending = false;
        Compat.setScreen(new SettingsScreen());
    }

    private void maybeConfirm(Minecraft client) {
        if (pendingConfirm == null || GameUtil.currentScreen() != null) return;
        Compat.setScreen(pendingConfirm);
        pendingConfirm = null;
    }

    private void fillSign() {
        if (!config.autosign || !STATE.running() || !STATE.current()) return;
        if (!(GameUtil.currentScreen() instanceof AbstractSignEditScreen screen)) return;
        AssistState.Step step = STATE.step();
        String text = step.text();
        if (!step.isSign() || text.isEmpty()) return;
        int context = screenContext();
        if (context == filledSign || context != acceptedContext) return;
        filledSign = context;
        SignEditScreenAccessor sign = (SignEditScreenAccessor) screen;
        String[] lines = sign.bzassist$getMessages();
        if (sign.bzassist$getLine() != 0 || lines == null || lines.length == 0
                || (lines[0] != null && !lines[0].isEmpty())) return;
        var field = sign.bzassist$getSignField();
        if (field == null) return;
        field.selectAll();
        field.insertText(text);
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
        Compat.setScreen(new SummaryScreen(lastSummary));
    }

    private void maybeReport() {
        if (!STATE.linked() || !authSent || !socket.connected()) return;
        long now = System.currentTimeMillis();
        noteShape(now);
        maybePrefetch();
        int hash = screenHash();
        boolean changed = hash != lastScreenHash;
        AssistState.Step shown = STATE.step();
        if (shown.isClick()) STATE.current(targetMatches(shown, acceptedSlotName));
        boolean pipelined = reports.waiting();
        if (pipelined && now - lastTickAt > 30_000) socket.close();
        if (recheckAt > 0 && now >= recheckAt) {
            recheckAt = 0;
            forceReport = true;
        }
        if (reports.holds(changed, lastShape != reportedShape)) return;
        if (!forceReport && !changed && now - lastTickAt < tickEveryMs) return;
        int context = screenContext();
        Map<Integer, String> names = new HashMap<>();
        var menu = GameUtil.handler();
        if (menu != null) for (var slot : menu.slots) {
            names.put(slot.index, GameUtil.rawName(slot.getItem()));
        }
        JsonObject tick = ScreenReader.tick();
        tick.addProperty("quiet_ms", Math.max(0, now - shapeSince));
        if (clickToReport != null) tick.add("clicked", clickToReport);
        long seq = reports.next();
        if (reports.numbered()) {
            tick.addProperty("seq", seq);
            if (pipelined) tick.addProperty("pipelined", true);
        }
        if (!socket.send(tick)) return;
        forceReport = false;
        clickToReport = null;
        lastScreenHash = hash;
        reportedShape = lastShape;
        lastTickAt = now;
        reportedContext = context;
        reportedSlotNames = names;
        reports.sent(seq, context);
    }

    private void noteShape(long now) {
        int shape = shapeHash();
        if (shape != lastShape) {
            lastShape = shape;
            shapeSince = now;
        }
        Screen screen = GameUtil.currentScreen();
        if (GameUtil.handler() != null && !(screen instanceof AbstractSignEditScreen)) {
            lastMenuTitle = GameUtil.screenTitle().toLowerCase(Locale.ROOT);
        }
    }

    private int shapeHash() {
        var menu = GameUtil.handler();
        if (menu == null) {
            Screen screen = GameUtil.currentScreen();
            return 31 * System.identityHashCode(screen) + GameUtil.screenTitle().hashCode();
        }
        int h = 31 * GameUtil.screenTitle().hashCode() + menu.containerId;
        for (var slot : menu.slots) {
            var st = slot.getItem();
            if (st == null || st.isEmpty()) continue;
            h = h * 31 + slot.index;
            h = h * 31 + GameUtil.rawName(st).hashCode();
        }
        return h;
    }

    private void showEarly(JsonObject msg, int answered) {
        if (answered == 0 || answered != screenContext()) return;
        if (msg.has("pending") && msg.get("pending").getAsBoolean()) return;
        AssistState.Step incoming = AssistState.Step.from(msg);
        if (!incoming.isPlayers()) return;
        String name = reportedSlotNames.get(incoming.slot());
        if (!targetMatches(incoming, name)) return;
        STATE.receiveStep(incoming, false);
        acceptedContext = answered;
        acceptedSlotName = name;
    }

    private void notePrefetch(JsonObject msg, int from, boolean replace) {
        Target t = null;
        if (msg.has("ahead") && msg.get("ahead").isJsonArray()) {
            for (var e : msg.getAsJsonArray("ahead")) {
                if (e.isJsonObject()) {
                    t = target(e.getAsJsonObject(), null);
                    break;
                }
            }
        } else if (msg.has("next") && msg.get("next").isJsonObject()) {
            JsonObject n = msg.getAsJsonObject("next");
            t = target(n, n.has("sign") && n.get("sign").getAsBoolean() ? "sign" : "menu");
        }
        if (t == null) {
            if (replace) prefetch = null;
            return;
        }
        prefetch = new Prefetch(t, from, System.currentTimeMillis() + PREFETCH_TTL_MS);
    }

    private static Target target(JsonObject n, String on) {
        List<String> names = new ArrayList<>();
        if (n.has("names") && n.get("names").isJsonArray()) {
            for (var e : n.getAsJsonArray("names")) names.add(e.getAsString());
        }
        String where = on != null ? on : n.has("on") && !n.get("on").isJsonNull() ? n.get("on").getAsString() : "menu";
        String title = n.has("title") && !n.get("title").isJsonNull() ? n.get("title").getAsString() : "";
        boolean prefix = n.has("prefix") && n.get("prefix").getAsBoolean();
        boolean inv = n.has("inv") && n.get("inv").getAsBoolean();
        String after = n.has("after") && !n.get("after").isJsonNull() ? n.get("after").getAsString() : "";
        return new Target(AssistState.Step.from(n), where, title.toLowerCase(Locale.ROOT), prefix, names, inv,
                after);
    }

    private void maybePrefetch() {
        Prefetch p = prefetch;
        if (p == null) return;
        if (!STATE.running() || System.currentTimeMillis() > p.until()) {
            prefetch = null;
            return;
        }
        Screen screen = GameUtil.currentScreen();
        if (screen instanceof ChatScreen) return;
        int context = screenContext();
        if (context == p.from()) return;
        Target t = p.target();
        int slot = -1;
        String name = null;
        if ("world".equals(t.on())) {
            if (screen != null) {
                prefetch = null;
                return;
            }
        } else if (screen == null) {
            return;
        } else if ("sign".equals(t.on())) {
            if (!(screen instanceof AbstractSignEditScreen) || !titleMatches(t, lastMenuTitle)) {
                prefetch = null;
                return;
            }
        } else {
            if (screen instanceof AbstractSignEditScreen || !titleMatches(t)
                    || (!t.after().isEmpty() && !t.after().equals(lastClickedName))) {
                prefetch = null;
                return;
            }
            var menu = GameUtil.handler();
            if (menu == null) return;
            if (t.step().slot() >= 0) {
                slot = t.step().slot();
                name = "";
                for (var s : menu.slots) {
                    if (s.index == slot) name = GameUtil.rawName(s.getItem());
                }
            }
            if (slot < 0) for (String want : t.names()) {
                for (var s : menu.slots) {
                    if (s.getItem().isEmpty() || (t.inv() && !(s.container instanceof Inventory))) continue;
                    String raw = GameUtil.rawName(s.getItem());
                    if (GameUtil.strip(raw).trim().equals(want)) {
                        slot = s.index;
                        name = raw;
                        break;
                    }
                }
                if (slot >= 0) break;
            }
            if (slot < 0) return;
        }
        prefetch = null;
        STATE.receiveStep(t.step().ahead(slot, STATE.step()), false);
        acceptedContext = context;
        acceptedSlotName = name;
    }

    private static boolean titleMatches(Target t) {
        return titleMatches(t, GameUtil.screenTitle().toLowerCase(Locale.ROOT));
    }

    private static boolean titleMatches(Target t, String title) {
        if (t.title().isEmpty()) return true;
        return t.prefix() ? title.startsWith(t.title()) : title.equals(t.title());
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
                justLinked = true;
                chat("§aLinked. Authenticating...");
                authSent = false;
            }
            case "auth_ok" -> {
                authSent = true;
                STATE.linked(true);
                forceReport = true;
                authUuid = currentUuid();
                if (msg.has("deflate") && msg.get("deflate").getAsBoolean()) socket.deflate();
                reports.numbered(msg.has("seq") && msg.get("seq").getAsBoolean());
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
                    if (justLinked) {
                        chat(startLine("Ready. "));
                        chat(Component.literal("Questions? Ask in the ").append(Chat.link("Discord", DISCORD)).append("."));
                        greeted = true;
                    }
                }
                justLinked = false;
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
                long seq = msg.has("seq") && !msg.get("seq").isJsonNull() ? msg.get("seq").getAsLong() : -1;
                int answered = reports.context(seq, reportedContext);
                if (reports.stale(seq)) {
                    if (!discardStep && STATE.running()) {
                        notePrefetch(msg, answered, false);
                        showEarly(msg, answered);
                    }
                    return;
                }
                reports.answer(seq);
                if (discardStep) {
                    discardStep = false;
                    forceReport = true;
                    prefetch = null;
                    return;
                }
                if (msg.has("hud") && msg.get("hud").isJsonObject()) {
                    JsonObject hud = msg.getAsJsonObject("hud");
                    boolean wasRunning = STATE.running();
                    STATE.hud(hud);
                    if (wasRunning && !STATE.running()
                            && hud.has("stopped_reason") && !hud.get("stopped_reason").isJsonNull()) {
                        chat("§e" + STATE.status() + " " + startAgain());
                    }
                }
                AssistState.Step incoming = AssistState.Step.from(msg);
                String targetName = reportedSlotNames.get(incoming.slot());
                if (screenContext() != reportedContext || !targetMatches(incoming, targetName)) {
                    if (STATE.running()) notePrefetch(msg, answered, false);
                    forceReport = true;
                    return;
                }
                boolean pending = msg.has("pending") && msg.get("pending").getAsBoolean();
                recheckAt = msg.has("again_ms") && !msg.get("again_ms").isJsonNull()
                        ? System.currentTimeMillis() + Math.max(0, Math.min(5_000, msg.get("again_ms").getAsLong()))
                        : 0;
                STATE.receiveStep(incoming, pending);
                acceptedContext = reportedContext;
                acceptedSlotName = targetName;
                if (!pending) notePrefetch(msg, reportedContext, !"wait".equals(incoming.kind()));
                if (msg.has("notice") && !msg.get("notice").isJsonNull()) {
                    STATE.notice(msg.get("notice").getAsString(), 5000);
                }
            }
            case "order_marks" -> STATE.marks(msg.has("marks") && msg.get("marks").isJsonArray()
                    ? msg.getAsJsonArray("marks") : null);
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
            case "chat" -> {
                Component line = Chat.fromServer(msg);
                if (line != null) raw(line);
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
        o.addProperty("install", config.install());
        o.addProperty("utc_offset_min", utcOffsetMinutes());
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
        justLinked = false;
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
        o.addProperty("install", config.install());
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
        justLinked = false;
        STATE.running(false);
        chat("Forgot this account's link.");
    }

    public void start() {
        if (!Location.skyBlock()) {
            chat("Join SkyBlock first.");
            return;
        }
        start(true);
    }

    private void start(boolean announce) {
        if (STATE.running()) {
            if (announce && STATE.finishing()) finish("off", false);
            return;
        }
        if (!STATE.linked() || !socket.connected()) {
            chat("§e" + STATE.status());
            return;
        }
        JsonObject o = new JsonObject();
        o.addProperty("type", "start");
        o.addProperty("utc_offset_min", utcOffsetMinutes());
        if (!announce) o.addProperty("resume", true);
        socket.send(o);
        startedLocally();
        if (announce) chat("§aOn. Do what the panel says; it never clicks for you.");
    }

    private void startedLocally() {
        STATE.step(AssistState.Step.NONE);
        STATE.running(true);
        forceReport = true;
        discardStep = reports.waiting();
        prefetch = null;
    }

    public void finish(String text, boolean fromButton) {
        if (!Location.skyBlock()) {
            chat("Join SkyBlock first.");
            return;
        }
        if (!STATE.linked() || !socket.connected()) {
            chat("§e" + STATE.status());
            return;
        }
        String value = text == null ? "" : text.trim();
        JsonObject o = new JsonObject();
        o.addProperty("type", "finish");
        o.addProperty("text", value);
        o.addProperty("utc_offset_min", utcOffsetMinutes());
        if (fromButton) o.addProperty("source", "button");
        socket.send(o);
        forceReport = true;
    }

    private static int utcOffsetMinutes() {
        return ZoneId.systemDefault().getRules().getOffset(Instant.now()).getTotalSeconds() / 60;
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
        discardStep = reports.waiting();
        prefetch = null;
        chat("Off. " + startAgain());
    }

    public boolean handleKey(KeyEvent event) {
        if (!Location.skyBlock()) return false;
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
        chat("§e/flipperx settings §7slot highlight and Tab fill");
        chat("§e/flipperx goal 1b Hyperion §7save toward something   §e/flipperx goal clear §7drop it");
        chat("§e/flipperx finish 45min §7or §e25m §7sell everything off by then   §e/flipperx finish §7now");
        chat("§e/flipperx summary §7the last session again");
        chat("§e/flipperx update §7download the latest version");
        chat("§e/flipperx stop §7or §e[ §7pause   §e/flipperx logout §7unlink");
        chat("§e/flipperx reminders off §7no login reminders while logged out");
        chat("§e/flipperx discord §7questions and bug reports");
    }

    public void discord() {
        chat(Component.literal("Discord: ").append(Chat.link(Chat.shortUrl(DISCORD), DISCORD)));
    }

    private static void chat(String text) {
        chat(Component.literal(text));
    }

    private static void raw(Component line) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            ChatComponent chat = Compat.chat();
            if (chat != null) chat.addClientSystemMessage(line);
        });
    }

    private static void chat(Component body) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            ChatComponent chat = Compat.chat();
            if (chat != null) chat.addClientSystemMessage(Chat.line(body));
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
