package com.u1x.damagestats.trace;

import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventListener;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class EventListenerAttributionTest {
    @Test
    void unwrapsCapturedConsumerAndFindsItsUniqueCompatibleMethod() {
        Handler handler = new Handler();
        Consumer<TestEvent> consumer = handler::onHurt;
        Wrapper wrapper = new Wrapper(consumer);

        Object captured = EventListenerAttribution.capturedConsumer(wrapper);

        assertSame(consumer, captured);
        assertEquals(Handler.class, EventListenerAttribution.consumerOwner(captured));
        assertEquals(List.of("onHurt"),
                EventListenerAttribution.compatibleMethods(Handler.class, TestEvent.class));
    }

    private static final class Wrapper implements IEventListener {
        private final Object originalConsumer;

        private Wrapper(Object originalConsumer) {
            this.originalConsumer = originalConsumer;
        }

        @Override
        public void invoke(Event event) {
        }
    }

    private static final class Handler {
        private void onHurt(TestEvent event) {
        }

        private void unrelated(String value) {
        }
    }

    private static final class TestEvent extends Event {
    }
}
