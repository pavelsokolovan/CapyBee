package com.capybee.engine.cli;

import com.capybee.engine.api.Command;
import com.capybee.engine.api.View;
import com.capybee.engine.session.RunSessionService;
import com.capybee.engine.session.SessionException;
import com.capybee.engine.session.SessionTypes;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.UUID;

final class SessionConsole {
    private final InputStream in;
    private final PrintStream out;
    private final PrintStream err;
    private final ConsoleTuning tuning;
    private UUID pendingCommandId;
    private BufferedReader reader;

    enum StartupChoice { CONTINUE, NEW, QUIT, ERROR }

    SessionConsole(InputStream in, PrintStream out, PrintStream err, ConsoleTuning tuning) {
        this.in = in;
        this.out = out;
        this.err = err;
        this.tuning = tuning;
    }

    StartupChoice startup(ConsoleOptions options, SessionTypes.RunSummary current) {
        if (!options.play()) return current == null ? StartupChoice.NEW : options.newRun() ? StartupChoice.NEW : StartupChoice.CONTINUE;
        if (options.script() != null) {
            if (options.runId() != null || options.continueRun()) return current == null ? StartupChoice.ERROR : StartupChoice.CONTINUE;
            if (options.newRun()) return current == null || options.replaceCurrent() ? StartupChoice.NEW : StartupChoice.ERROR;
            return current == null ? StartupChoice.NEW : StartupChoice.ERROR;
        }
        BufferedReader input = reader(options);
        if (input == null) return StartupChoice.ERROR;
        if (current == null) {
            out.println("Press Enter to set up this game, or type /quit.");
            String line = readLine(input);
            return line == null || line.trim().equalsIgnoreCase("/quit") ? StartupChoice.QUIT
                    : line.isBlank() ? StartupChoice.NEW : StartupChoice.ERROR;
        }
        out.println("Saved game found");
        out.println("Story: " + ConsoleRenderer.sanitize(current.view() != null && current.view().story() != null
                ? current.view().story().title() : current.packId().value()));
        out.println("Status: " + (current.status() == SessionTypes.RunStatus.ACTIVE ? "In progress" : current.status()));
        out.println("Last saved: " + current.updatedAt());
        out.println("\n1) Continue\n2) Start over\n3) Show details\n/quit) Leave");
        while (true) {
            String line = readLine(input);
            if (line == null || line.trim().equalsIgnoreCase("/quit")) return StartupChoice.QUIT;
            if (line.trim().equals("1")) return StartupChoice.CONTINUE;
            if (line.trim().equals("2")) {
                out.print("Start over and abandon the saved game? [y/N] ");
                String answer = readLine(input);
                if (answer != null && (answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes"))) return StartupChoice.NEW;
                out.println("Cancelled.");
            } else if (line.trim().equals("3")) {
                out.println("Run: " + current.runId() + "\nPack: " + current.packId().value() + " "
                        + current.packVersion() + "\nLocale: " + current.locale().value() + "\nRevision: " + current.revision());
            } else out.println("Choose 1, 2, 3, or /quit.");
        }
    }

    SessionTypes.RunSummary awaitActive(RunSessionService sessions, SessionTypes.OwnerRef owner,
                                        SessionTypes.RunSummary summary) {
        boolean thinking = false;
        Duration waited = Duration.ZERO;
        while (summary.status() == SessionTypes.RunStatus.PREPARING) {
            if (!thinking) {
                out.println("CapyBee is thinking…");
                thinking = true;
            } else out.print(".");
            if (waited.compareTo(tuning.maxWait()) >= 0) {
                summary = new SessionTypes.RunSummary(summary.runId(), SessionTypes.RunStatus.FAILED,
                        summary.revision(), summary.packId(), summary.packVersion(), summary.locale(),
                        summary.endReason(), "START_TIMEOUT", null, summary.updatedAt());
                break;
            }
            Duration delay = tuning.minPoll();
            Duration remaining = tuning.maxWait().minus(waited);
            if (delay.compareTo(remaining) > 0) delay = remaining;
            tuning.sleeper().sleep(delay);
            waited = waited.plus(delay);
            summary = sessions.get(owner, summary.runId());
        }
        if (thinking) out.println();
        if (summary.status() != SessionTypes.RunStatus.ACTIVE) {
            String code = summary.failureCode() == null ? "START_REJECTED" : summary.failureCode();
            err.println(code);
            err.println("  " + ConsoleErrors.sessionHint(code));
            return null;
        }
        return summary;
    }

    int run(ConsoleOptions options, RunSessionService sessions, SessionTypes.OwnerRef owner,
            SessionTypes.RunSummary initialSummary) {
        BufferedReader reader = reader(options);
        if (reader == null) {
            err.println("SCRIPT_INVALID");
            return 2;
        }
        SessionTypes.RunSummary summary = initialSummary;
        View view = summary.view();
        out.println("Run " + summary.runId());
        out.println("Running " + summary.packId().value() + " in locale " + summary.locale().value());
        String header = ConsoleRenderer.renderHeader(view);
        if (!header.isEmpty()) out.println(header);
        out.println(renderView(options, view));
        printDebug(options.debug(), sessions, owner, summary);
        while (!view.ended()) {
            if (options.script() == null) out.print("> ");
            String line = readLine(reader);
            if (line == null) return 0;
            if (options.script() != null) out.println(line.trim().startsWith("/text ")
                    ? "> [text submitted]" : "> " + line);
            String trimmed = line.trim();
            if (pendingCommandId != null) {
                try {
                    SessionTypes.CommandStatus pending = sessions.commandStatus(owner, summary.runId(), pendingCommandId);
                    if (pending.processing()) {
                        if (!isNonMutating(trimmed)) {
                            out.println("! COMMAND_STILL_RUNNING: CapyBee is still working. Wait a moment, then press Enter or type /status.");
                            continue;
                        }
                    } else {
                        pendingCommandId = null;
                        summary = sessions.get(owner, summary.runId());
                        view = summary.view();
                        if (view != null) out.println(ConsoleRenderer.render(view));
                        printDebug(options.debug(), sessions, owner, summary);
                        if (pending.outcome() != null && pending.outcome().outcome() == SessionTypes.Outcome.REJECTED) {
                            pending.outcome().issues().forEach(issue -> out.println(issue.code()));
                        }
                    }
                } catch (SessionException failure) {
                    out.println("! " + failure.code().name() + ": " + ConsoleErrors.sessionHint(failure.code().name()));
                    continue;
                }
            }
            if (trimmed.equalsIgnoreCase("/quit") || trimmed.equalsIgnoreCase("/q")) return 0;
            if (trimmed.equalsIgnoreCase("/status")) {
                out.println(ConsoleRenderer.renderStatus(view));
                printDebug(options.debug(), sessions, owner, summary);
                continue;
            }
            if (trimmed.equalsIgnoreCase("/look")) { out.println(ConsoleRenderer.renderScene(view)); continue; }
            if (trimmed.equalsIgnoreCase("/objectives") || trimmed.equalsIgnoreCase("/quests")) {
                out.println(ConsoleRenderer.renderObjectives(view)); continue;
            }
            if (trimmed.equalsIgnoreCase("/characters")) { out.println(ConsoleRenderer.renderCharacters(view)); continue; }
            if (trimmed.equalsIgnoreCase("/map")) { out.println(ConsoleRenderer.renderMap(view)); continue; }
            if (trimmed.equalsIgnoreCase("/save")) {
                if (options.storeDir() == null) out.println("This run is temporary and cannot be continued after the program closes.\nRestart with --store-dir <directory> to enable saving.");
                else out.println("Saved. Run " + summary.runId() + " is at revision " + summary.revision() + ".");
                continue;
            }
            if (trimmed.startsWith("/events") && (options.debug() || !options.play())) {
                long after = 0;
                try { if (trimmed.length() > 7) after = Long.parseLong(trimmed.substring(7).trim()); }
                catch (NumberFormatException ignored) { out.println("! Unknown command. Type ? for help."); continue; }
                while (true) {
                    var events = sessions.events(owner, summary.runId(), after, 100);
                    events.forEach(event -> out.println(event.seq() + " " + event.event().id()));
                    if (events.size() < 100) break;
                    after = events.get(events.size() - 1).seq();
                }
                continue;
            }
            if (trimmed.equalsIgnoreCase("/debug on")) {
                if (options.play() && !options.debug()) { out.println("! Debug mode can only be enabled with --debug."); continue; }
                options = withDebug(options, true);
                printDebug(true, sessions, owner, summary);
                continue;
            }
            if (trimmed.equalsIgnoreCase("/debug off")) {
                options = withDebug(options, false);
                continue;
            }
            if (trimmed.equals("?") || trimmed.equalsIgnoreCase("/help")) {
                if (options.play()) {
                    out.println("Choose with a number or trusted option ID. Press Enter when prompted.\n"
                            + "/look, /status, /objectives, /characters, /map, /move <place-id>, /text <words>, /save, /quit, /abandon");
                    if (options.debug()) out.println("Developer: /events [afterSeq], /debug off, /diagnostics");
                } else out.println("/text <words>, /move <placeId>, /status, /events [afterSeq], /debug on|off, /abandon, /quit");
                continue;
            }
            try {
                summary = sessions.get(owner, summary.runId());
                view = summary.view();
            } catch (SessionException failure) {
                out.println("! " + failure.code().name() + ": " + ConsoleErrors.sessionHint(failure.code().name()));
                continue;
            }
            Command command;
            if (trimmed.equalsIgnoreCase("/abandon") || trimmed.equalsIgnoreCase("/abandon --yes")
                    || (!options.play() && trimmed.equalsIgnoreCase("q") && sessionOption(trimmed, view) == null)) {
                if (options.script() == null) {
                    out.print(options.play() ? "Abandon this game? You will not be able to continue it. [y/N] "
                            : "Abandon this run for good? (y/N) ");
                    String answer = readLine(reader);
                    if (answer == null || !(answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes"))) {
                        out.println("Cancelled.");
                        continue;
                    }
                } else if (options.play() && !trimmed.equalsIgnoreCase("/abandon --yes")) {
                    out.println("! ABANDON_CONFIRMATION_REQUIRED: scripts must use /abandon --yes.");
                    return 4;
                }
                command = new Command.Abandon();
            } else if (trimmed.startsWith("/text ")) {
                command = new Command.SubmitText(line.substring(line.indexOf(' ') + 1));
            } else if (trimmed.startsWith("/move ")) {
                command = new Command.Move(trimmed.substring(6).trim());
            } else if (trimmed.startsWith("/")) {
                out.println("! Unknown command. Type ? for help.");
                continue;
            } else if (trimmed.isEmpty()) {
                command = new Command.Advance();
            } else {
                String optionId = sessionOption(trimmed, view);
                if (optionId != null) command = new Command.Choose(optionId);
                else if (view.input() != null && (view.input().mode() == com.capybee.engine.api.InputMode.TEXT
                        || view.input().mode() == com.capybee.engine.api.InputMode.CHOICE_OR_TEXT)) {
                    command = new Command.SubmitText(line);
                } else {
                    out.println(options.play() && trimmed.equalsIgnoreCase("q")
                            ? "! QUIT_REQUIRES_SLASH: Type /quit to leave this game."
                            : "! Choose an option, or press Enter to continue.");
                    continue;
                }
            }
            SessionTypes.CommandOutcome outcome;
            try {
                outcome = sessions.command(owner, summary.runId(),
                        new SessionTypes.CommandRequest(UUID.randomUUID(), summary.revision(), command),
                        tuning.syncWait());
                boolean printedDot = false;
                Duration waited = Duration.ZERO;
                if (outcome.outcome() == SessionTypes.Outcome.PROCESSING) out.println("CapyBee is thinking…");
                while (outcome.outcome() == SessionTypes.Outcome.PROCESSING) {
                    var status = sessions.commandStatus(owner, summary.runId(), outcome.commandId());
                    if (!status.processing() && status.outcome() != null) { outcome = status.outcome(); break; }
                    if (waited.compareTo(tuning.maxWait()) >= 0) {
                        pendingCommandId = outcome.commandId();
                        if (printedDot) out.println();
                        out.println("! COMMAND_STILL_RUNNING: CapyBee is still working. Wait a moment, then press Enter or type /status.");
                        outcome = null;
                        break;
                    }
                    Duration delay = tuning.pollDelay(status.retryAfterMs());
                    Duration remaining = tuning.maxWait().minus(waited);
                    if (delay.compareTo(remaining) > 0) delay = remaining;
                    out.print(".");
                    printedDot = true;
                    tuning.sleeper().sleep(delay);
                    waited = waited.plus(delay);
                }
                if (outcome != null && outcome.outcome() != SessionTypes.Outcome.PROCESSING && printedDot) out.println();
            } catch (SessionException failure) {
                out.println("! " + failure.code().name() + ": " + ConsoleErrors.sessionHint(failure.code().name()));
                continue;
            }
            if (outcome == null) continue;
            summary = sessions.get(owner, summary.runId());
            view = summary.view();
            if (view != null) out.println(renderView(options, view));
            printDebug(options.debug(), sessions, owner, summary);
            if (outcome.outcome() == SessionTypes.Outcome.REJECTED) {
                outcome.issues().forEach(issue -> out.println(issue.code()));
            }
        }
        return 0;
    }

    private BufferedReader reader(ConsoleOptions options) {
        if (reader != null) return reader;
        try {
            reader = options.script() == null
                    ? new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    : Files.newBufferedReader(options.script(), StandardCharsets.UTF_8);
            return reader;
        } catch (Exception failure) {
            return null;
        }
    }

    private String readLine(BufferedReader reader) {
        try {
            return reader.readLine();
        } catch (Exception exception) {
            return null;
        }
    }

    private boolean isNonMutating(String input) {
        return input.equalsIgnoreCase("/quit") || input.equalsIgnoreCase("/q")
                || input.equalsIgnoreCase("/status") || input.startsWith("/events")
                || input.equalsIgnoreCase("/debug on") || input.equalsIgnoreCase("/debug off")
                || input.equals("?") || input.equalsIgnoreCase("/help") || input.equalsIgnoreCase("/look")
                || input.equalsIgnoreCase("/objectives") || input.equalsIgnoreCase("/quests")
                || input.equalsIgnoreCase("/characters") || input.equalsIgnoreCase("/map")
                || input.equalsIgnoreCase("/save");
    }

    private String sessionOption(String input, View view) {
        if (view.options() == null) return null;
        try {
            int index = Integer.parseInt(input) - 1;
            if (index >= 0 && index < view.options().size()) return view.options().get(index).id();
        } catch (NumberFormatException ignored) {
            // Check ids below.
        }
        return view.options().stream().map(option -> option.id())
                .filter(id -> id.equalsIgnoreCase(input)).findFirst().orElse(null);
    }

    private void printDebug(boolean debug, RunSessionService sessions, SessionTypes.OwnerRef owner,
                            SessionTypes.RunSummary summary) {
        if (!debug) return;
        SessionTypes.Diagnostics diagnostics = sessions.diagnostics(owner, summary.runId());
        SessionTypes.CommandDiagnostics latest = diagnostics.recentCommands().isEmpty()
            ? null : diagnostics.recentCommands().getFirst();
        String source = latest == null || latest.sceneSource() == null ? "NONE" : latest.sceneSource().name();
        long latency = latest == null ? 0 : latest.latencyMillis();
        var prefetch = diagnostics.prefetch();
        var budget = diagnostics.budget();
        out.println("revision=" + diagnostics.revision() + " source=" + source + " latency=" + latency
            + "ms calls=" + diagnostics.generationCalls() + "/" + diagnostics.runCallCeiling()
            + " prefetch=ready:" + prefetch.ready().size() + " inflight:" + prefetch.inFlight().size()
            + " calls:" + prefetch.prefetchCalls() + " hits:" + prefetch.hits()
            + " misses:" + prefetch.misses() + " wasted:" + prefetch.wastedCalls()
            + " budget=" + budget.profileCallsLeftToday() + "/" + budget.globalCallsLeftToday());
    }

    private ConsoleOptions withDebug(ConsoleOptions options, boolean debug) {
        return new ConsoleOptions(options.packDir(), options.locale(), options.inputs(), options.seed(),
                options.script(), options.save(), options.load(), options.generated(), options.liveSmoke(),
                options.help(), options.session(), options.storeDir(), options.runId(), options.newRun(), debug,
                options.prefetch(), options.callsPerDay(), options.runCalls(), options.play(), options.memory(),
                options.classic(), options.continueRun(), options.replaceCurrent(), options.noColor(),
                options.acceptGenerationDisclosure());
    }

    private String renderView(ConsoleOptions options, View view) {
        return options.play() ? ConsoleRenderer.renderScene(view) : ConsoleRenderer.render(view);
    }
}
