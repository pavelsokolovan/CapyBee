package com.capybee.engine.rules.json;

import com.capybee.engine.pack.Pack;
import com.capybee.engine.pack.PackValidationContext;
import com.capybee.engine.pack.SectionValidator;

public final class RulesSectionValidator implements SectionValidator {
    @Override
    public void validate(Pack pack, PackValidationContext context) {
        RulesParser.validate(pack, context);
    }
}