package com.capybee.engine.places.json;

import com.capybee.engine.pack.Pack;
import com.capybee.engine.pack.PackValidationContext;
import com.capybee.engine.pack.SectionValidator;

public final class PlacesSectionValidator implements SectionValidator {
    @Override
    public void validate(Pack pack, PackValidationContext context) {
        PlacesParser.validate(pack, context);
    }
}
