package com.capybee.engine.api;

import com.capybee.engine.pack.PackId;
import com.capybee.engine.pack.PackVersion;

public record PackRef(PackId id, PackVersion version) {}
