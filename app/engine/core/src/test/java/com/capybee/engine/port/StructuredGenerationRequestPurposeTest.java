package com.capybee.engine.port;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class StructuredGenerationRequestPurposeTest {
    @Test void preservesPresentationLimitAndAllowsBoundedBlueprintOutput() {
        assertThatThrownBy(() -> request(1501, StructuredGenerationRequest.Purpose.PRESENTATION)).isInstanceOf(IllegalArgumentException.class);
        assertThat(request(6000, StructuredGenerationRequest.Purpose.GAME_BLUEPRINT).maxOutputTokens()).isEqualTo(6000);
        assertThatThrownBy(() -> request(12001, StructuredGenerationRequest.Purpose.GAME_BLUEPRINT)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void oldConstructorDefaultsToPresentation() {
        var value=new StructuredGenerationRequest("r","task","policy","instructions","{}","{}",100,StructuredGenerationRequest.CostClass.LIGHT);
        assertThat(value.purpose()).isEqualTo(StructuredGenerationRequest.Purpose.PRESENTATION);
    }
    private static StructuredGenerationRequest request(int tokens, StructuredGenerationRequest.Purpose purpose){return new StructuredGenerationRequest("r","task","policy","instructions","{}","{}",tokens,StructuredGenerationRequest.CostClass.STANDARD,null,purpose);}
}
