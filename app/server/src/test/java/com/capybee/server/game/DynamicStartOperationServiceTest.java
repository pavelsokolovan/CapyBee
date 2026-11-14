package com.capybee.server.game;

import com.capybee.engine.session.*;
import com.capybee.engine.session.SessionTypes.OwnerRef;
import com.capybee.engine.generation.profile.*;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DynamicStartOperationServiceTest {
    @Test void reservesBeforeSchedulingAndCancellationIsTerminal() {
        var coordinator = mock(DynamicStartCoordinator.class);
        List<Runnable> tasks = new ArrayList<>();
        var service = new DynamicStartOperationService(coordinator, tasks::add, Duration.ofSeconds(5));
        var preparation = mock(ConfirmedDynamicPreparation.class);
        UUID id = UUID.randomUUID(); var owner = new OwnerRef("owner");
        assertThat(service.submit(owner,id,preparation,null,null,Duration.ZERO)).isInstanceOf(DynamicStartOperationService.Processing.class);
        service.submit(owner,id,preparation,null,null,Duration.ZERO);
        assertThat(tasks).hasSize(1);
        assertThat(service.submit(owner,id,preparation,3L,null,Duration.ZERO)).isInstanceOf(DynamicStartOperationService.Conflict.class);
        assertThat(tasks).hasSize(1);
        assertThat(service.cancel(new OwnerRef("other"),id)).isFalse();
        assertThat(service.status(new OwnerRef("other"),id)).isNull();
        assertThat(service.cancel(owner,id)).isTrue();
        assertThat(((DynamicStartOperationService.Terminal)service.status(owner,id)).result()).isInstanceOf(DynamicStartCoordinator.Cancelled.class);
        assertThat(service.exists(owner,id)).isTrue();
    }
    @Test void restartDoesNotRecreateUnpublishedPaidWork() {
        var coordinator=mock(DynamicStartCoordinator.class);
        var first=new DynamicStartOperationService(coordinator,task->{},Duration.ofSeconds(5));
        var owner=new OwnerRef("owner");var id=UUID.randomUUID();
        first.submit(owner,id,mock(ConfirmedDynamicPreparation.class),null,null,Duration.ZERO);
        var restarted=new DynamicStartOperationService(coordinator,task->{},Duration.ofSeconds(5));
        assertThat(restarted.status(owner,id)).isNull();assertThat(restarted.cancel(owner,id)).isFalse();
        verifyNoInteractions(coordinator);
    }
    @Test void audienceUsesTheWholeBirthYearInterval() {
        assertThat(DynamicGameController.audienceEligible(2014,2026,10,14)).isTrue();
        assertThat(DynamicGameController.audienceEligible(2016,2026,10,14)).isFalse();
        assertThat(DynamicGameController.audienceEligible(2011,2026,10,14)).isFalse();
        assertThat(DynamicGameController.audienceEligible(null,2026,10,14)).isFalse();
    }
}
