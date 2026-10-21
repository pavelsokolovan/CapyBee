package com.capybee.engine.session.wire;

import com.capybee.engine.pack.StartInputDef;

import java.util.List;

public record PackInfoDto(String id, String version, List<String> locales, String defaultLocale,
                          List<StartInputDef> startInputs, boolean generated) {
    public PackInfoDto {
        locales = List.copyOf(locales);
        startInputs = List.copyOf(startInputs);
    }
}
