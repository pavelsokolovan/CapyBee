package com.capybee.engine.generation.profile;

import com.capybee.engine.pack.PackId;
import com.capybee.engine.pack.PackVersion;

public record ProfileIdentity(PackId id, PackVersion version, String fingerprint) {}
