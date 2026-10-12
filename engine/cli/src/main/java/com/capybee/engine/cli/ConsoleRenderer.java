package com.capybee.engine.cli;

import com.capybee.engine.api.EndReason;
import com.capybee.engine.api.InputMode;
import com.capybee.engine.api.Option;
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

		if (!view.blocks().isEmpty() && !view.status().isEmpty()) {
			builder.append(System.lineSeparator()).append(System.lineSeparator());
		}

		if (!view.status().isEmpty()) {
			for (StatusItem item : view.status()) {
				if (builder.length() > 0 && !builder.toString().endsWith(System.lineSeparator())) {
					builder.append(System.lineSeparator());
				}
				builder.append("[").append(item.label()).append(" ").append(item.value()).append("]");
			}
		}

		if (!view.options().isEmpty()) {
			if (builder.length() > 0) {
				builder.append(System.lineSeparator());
			}
			int index = 1;
			for (Option option : view.options()) {
				builder.append("  ").append(index++).append(") ").append(option.label());
				if (builder.length() > 0) {
					builder.append(System.lineSeparator());
				}
			}
			if (builder.length() > 0 && builder.charAt(builder.length() - 1) == '\n') {
				builder.setLength(builder.length() - 1);
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
}