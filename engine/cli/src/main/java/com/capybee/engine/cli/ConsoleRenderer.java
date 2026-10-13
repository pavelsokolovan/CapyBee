package com.capybee.engine.cli;

import com.capybee.engine.api.EndReason;
import com.capybee.engine.api.InputMode;
import com.capybee.engine.api.ObjectiveItem;
import com.capybee.engine.api.Option;
import com.capybee.engine.api.PlaceView;
import com.capybee.engine.api.StatusItem;
import com.capybee.engine.api.View;
import com.capybee.engine.content.Block;

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

		if (!view.objectives().isEmpty()) {
			appendLine(builder);
			builder.append("Objectives: ");
			for (int index = 0; index < view.objectives().size(); index++) {
				ObjectiveItem item = view.objectives().get(index);
				if (index > 0) builder.append(", ");
				builder.append(item.label()).append(" (").append(item.state().name().toLowerCase()).append(")");
			}
		}

		if (view.outcome() != null) {
			appendLine(builder);
			builder.append("Outcome: ").append(view.outcome());
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

		if (view.ended()) {
			if (builder.length() > 0) {
				builder.append(System.lineSeparator());
			}
			builder.append("  ").append(view.endReason() == EndReason.ABANDONED ? ConsoleChrome.ABANDONED : ConsoleChrome.END);
		}

		return builder.toString();
	}

	private static void appendLine(StringBuilder builder) {
		if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '\n') {
			builder.append(System.lineSeparator());
		}
	}
}