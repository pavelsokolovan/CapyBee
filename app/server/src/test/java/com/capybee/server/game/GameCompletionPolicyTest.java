package com.capybee.server.game;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class GameCompletionPolicyTest {
    final GameCompletionPolicy policy=new GameCompletionPolicy();
    @Test void exactPolishEndingPairsHaveReviewedVersionOneBindings() {
        for(String version:List.of("1.0.0","1.1.0","1.2.0","1.3.0","1.4.0")) {
            assertThat(policy.suggest("capybee-new-hive",version,"new-path","ending-new")).contains(new GameCompletionPolicy.Suggestion("say_thank_you_helper",1));
            assertThat(policy.suggest("capybee-new-hive",version,"two-lights","ending-two")).contains(new GameCompletionPolicy.Suggestion("listen_song_reminds_home",1));
            assertThat(policy.suggest("capybee-new-hive",version,"rest-here","ending-rest")).contains(new GameCompletionPolicy.Suggestion(GameCompletionPolicy.REST,1));
        }
    }
    @Test void unknownVersionsOutcomesAndMismatchedScenesCannotInheritEligibility() {
        for(String version:List.of("0.9.0","1.0.1","1.1.1","2.0.0",""))assertThat(policy.suggest("capybee-new-hive",version,"rest-here","ending-rest")).isEmpty();
        assertThat(policy.suggest("capybee-new-hive",null,"rest-here","ending-rest")).isEmpty();
        for(String outcome:List.of("new-path","two-lights","rest-here"))assertThat(policy.suggest("capybee-new-hive","1.1.0",outcome,"arrival")).isEmpty();
        assertThat(policy.suggest("capybee-new-hive","1.1.0","generated-prose","ending-new")).isEmpty();
        assertThat(policy.suggest("capybee-new-hive","1.1.0",null,null)).isEmpty();
        assertThat(policy.suggest("unreviewed-pack","1.1.0","new-path","ending-new")).isEmpty();
    }
    @Test void englishBindingsRemainUnchanged() {
        assertThat(policy.suggest("lanterns-of-willow-marsh","1.0.0","festival-postponed","ending")).contains(new GameCompletionPolicy.Suggestion("rest",1));
        assertThat(policy.suggest("lanterns-of-willow-marsh","1.1.0","festival-postponed","ending")).isEmpty();
    }
}
