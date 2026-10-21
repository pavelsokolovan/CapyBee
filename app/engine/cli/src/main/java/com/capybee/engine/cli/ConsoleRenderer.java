package com.capybee.engine.cli;

import com.capybee.engine.api.EndReason;
import com.capybee.engine.api.InputMode;
import com.capybee.engine.api.ObjectiveItem;
import com.capybee.engine.api.Option;
import com.capybee.engine.api.PlaceView;
import com.capybee.engine.api.StatusItem;
import com.capybee.engine.api.View;
import com.capybee.engine.content.Block;

import java.util.List;

public final class ConsoleRenderer {
	private ConsoleRenderer() {}

	public static String render(View view) {
		if (view == null) {
			return "";
		}

		StringBuilder builder = new StringBuilder();
		for (Block block : view.blocks()) {
			if (builder.length() > 0) {
				builder.append(System.lineSeparator());
			}
			if (block.kind() == com.capybee.engine.content.BlockKind.SPEECH) {
				builder.append(block.speaker()).append(": ").append(block.text());
			} else if (block.kind() == com.capybee.engine.content.BlockKind.SYSTEM) {
				builder.append("[").append(block.text()).append("]");
			} else {
				builder.append(block.text());
			}
		}

		if (!view.blocks().isEmpty() && (!view.status().isEmpty() || !view.objectives().isEmpty() || view.outcome() != null)) {
			builder.append(System.lineSeparator()).append(System.lineSeparator());
		}

		if (!view.status().isEmpty()) {
			for (int index = 0; index < view.status().size(); index++) {
				StatusItem item = view.status().get(index);
				if (index > 0) builder.append("  ");
				builder.append("[").append(item.label()).append(" ").append(item.value()).append("]");
			}
		}

		if (view.result() == null && !view.objectives().isEmpty()) {
			appendLine(builder);
			builder.append("Objectives:");
			for (int index = 0; index < view.objectives().size(); index++) {
				ObjectiveItem item = view.objectives().get(index);
				if (item.description() == null) {
					if (index == 0) builder.append(" "); else builder.append(", ");
					builder.append(item.label()).append(" (").append(item.state().name().toLowerCase()).append(")");
				} else {
					builder.append(System.lineSeparator()).append("  [")
							.append(item.state().name().toLowerCase()).append("] ").append(item.label())
							.append(" — ").append(item.description());
				}
			}
		}

		if (view.result() == null && view.outcome() != null) {
			appendLine(builder);
			builder.append("Outcome: ").append(view.outcome());
		}

		if (view.result() != null) {
			var result = view.result();
			appendLine(builder);
			builder.append("Result: ").append(result.label()).append(" — ").append(result.headline());
			builder.append(System.lineSeparator()).append(System.lineSeparator()).append(result.summary());
			if (!result.objectives().isEmpty()) {
				builder.append(System.lineSeparator()).append(System.lineSeparator()).append("Objectives:");
				for (var item : result.objectives()) builder.append(System.lineSeparator()).append("  [")
						.append(item.state().name().toLowerCase()).append("] ").append(item.label())
						.append(" — ").append(item.note());
			}
			if (!result.facts().isEmpty()) {
				builder.append(System.lineSeparator()).append(System.lineSeparator()).append("What changed:");
				for (var fact : result.facts()) builder.append(System.lineSeparator()).append("  - ").append(fact.label());
			}
			if (result.reflection() != null && !result.reflection().isBlank()) {
				builder.append(System.lineSeparator()).append(System.lineSeparator()).append(result.reflection());
			}
		}

		if (view.places() != null) {
			PlaceView current = view.places().places().stream()
					.filter(place -> place.id().equals(view.places().currentPlaceId())).findFirst().orElse(null);
			appendLine(builder);
			builder.append("Place: ").append(current == null ? view.places().currentPlaceId() : current.label());
			if (!view.ended()) {
				appendLine(builder);
				builder.append("Moves:");
				if (view.places().availableMoves().isEmpty()) {
					builder.append(" none");
				} else {
					for (String placeId : view.places().availableMoves()) {
						PlaceView destination = view.places().places().stream()
								.filter(place -> place.id().equals(placeId)).findFirst().orElse(null);
						builder.append(System.lineSeparator()).append("  move ").append(placeId)
								.append(" — ").append(destination == null ? placeId : destination.label());
					}
				}
			}
		}

		if (!view.options().isEmpty()) {
			appendLine(builder);
			int index = 1;
			for (Option option : view.options()) {
				builder.append("  ").append(index++).append(") ").append(option.label());
				if (!option.enabled()) builder.append(" (unavailable)");
				if (option.hint() != null) builder.append(" — ").append(option.hint());
				if (builder.length() > 0) {
					builder.append(System.lineSeparator());
				}
			}
			String lineSeparator = System.lineSeparator();
			if (builder.toString().endsWith(lineSeparator)) {
				builder.setLength(builder.length() - lineSeparator.length());
			}
		}

		if (view.input().mode() == InputMode.NONE && !view.ended()) {
			if (builder.length() > 0) {
				builder.append(System.lineSeparator());
			}
			builder.append("  ").append(ConsoleChrome.ENTER);
		}

		if ((view.input().mode() == InputMode.TEXT || view.input().mode() == InputMode.CHOICE_OR_TEXT)
				&& !view.ended()) {
			if (builder.length() > 0) builder.append(System.lineSeparator());
			builder.append("  Text input: up to ").append(view.input().maxChars()).append(" characters.");
		}

		if (view.ended()) {
			if (builder.length() > 0) {
				builder.append(System.lineSeparator());
			}
			builder.append("  ").append(view.endReason() == EndReason.ABANDONED ? ConsoleChrome.ABANDONED : ConsoleChrome.END);
		}

		return builder.toString();
	}

	public static String renderHeader(View view) {
		return view == null || view.story() == null ? "" : view.story().title();
	}

	public static String renderScene(View view) {
		if (view == null) return "";
		View scene = new View(view.blocks(), view.options(), view.input(), List.of(), view.ended(),
				view.endReason(), List.of(), null, null, view.story(), List.of(), view.result());
		return sanitize(render(scene));
	}

	public static String renderStatus(View view) {
		if (view == null) return "No current game.";
		StringBuilder text = new StringBuilder("Status");
		if (view.story() != null) text.append(System.lineSeparator()).append("Story: ").append(sanitize(view.story().title()));
		for (StatusItem item : view.status()) text.append(System.lineSeparator()).append("  ")
				.append(sanitize(item.label())).append(": ").append(sanitize(item.value()));
		if (view.outcome() != null) text.append(System.lineSeparator()).append("Outcome: ").append(sanitize(view.outcome()));
		return text.toString();
	}

	public static String renderObjectives(View view) {
		if (view == null || view.objectives().isEmpty()) return "Objectives\n  None discovered.";
		StringBuilder text = new StringBuilder("Objectives");
		for (ObjectiveItem item : view.objectives()) {
			text.append(System.lineSeparator()).append("  [").append(item.state().name().toLowerCase()).append("] ")
					.append(sanitize(item.label()));
			if (item.description() != null) text.append(" — ").append(sanitize(item.description()));
		}
		return text.toString();
	}

	public static String renderCharacters(View view) {
		if (view == null || view.characters().isEmpty()) return "Characters\n  None discovered.";
		StringBuilder text = new StringBuilder("Characters");
		for (var character : view.characters()) {
			text.append(System.lineSeparator()).append("  ").append(sanitize(character.name()));
			if (character.inCurrentScene()) text.append(" (here)");
			if (character.description() != null) text.append(" — ").append(sanitize(character.description()));
		}
		return text.toString();
	}

	public static String renderMap(View view) {
		if (view == null || view.places() == null) return "Map\n  No map is available.";
		StringBuilder text = new StringBuilder("Map");
		for (PlaceView place : view.places().places()) {
			text.append(System.lineSeparator()).append(place.id().equals(view.places().currentPlaceId()) ? "  * " : "    ")
					.append(sanitize(place.label()));
			if (view.places().availableMoves().contains(place.id())) text.append(" — /move ").append(place.id());
		}
		if (!view.places().links().isEmpty()) {
			text.append(System.lineSeparator()).append("Links:");
			for (var link : view.places().links()) text.append(System.lineSeparator()).append("  ")
					.append(link.from()).append(link.bidirectional() ? " <-> " : " -> ").append(link.to());
		}
		return text.toString();
	}

	public static String sanitize(String value) {
		if (value == null) return "";
		StringBuilder safe = new StringBuilder(value.length());
		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);
			if (character == '\n' || character == '\r' || character == '\t' || character >= 0x20 && character != 0x7f) safe.append(character);
		}
		return safe.toString();
	}

	private static void appendLine(StringBuilder builder) {
		if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '\n') {
			builder.append(System.lineSeparator());
		}
	}
}
