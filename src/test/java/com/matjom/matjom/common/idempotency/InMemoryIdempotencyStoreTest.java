package com.matjom.matjom.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.matjom.matjom.common.exception.base.IdempotencyException;
import org.junit.jupiter.api.Test;

class InMemoryIdempotencyStoreTest {

    @Test
    void returnsFreshValueThenReplays() {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();

        IdempotencyResult<String> first = store.replayOrRun(
                "key-1",
                "hash-1",
                String.class,
                new IdempotencyCallback<String>() {
                    @Override
                    public String execute() {
                        return "value-1";
                    }
                }
        );

        assertThat(first.getValue()).isEqualTo("value-1");
        assertThat(first.isReplayed()).isFalse();

        IdempotencyResult<String> second = store.replayOrRun(
                "key-1",
                "hash-1",
                String.class,
                new IdempotencyCallback<String>() {
                    @Override
                    public String execute() {
                        return "value-2";
                    }
                }
        );

        assertThat(second.getValue()).isEqualTo("value-1");
        assertThat(second.isReplayed()).isTrue();
    }

    @Test
    void throwsWhenHashesDiffer() {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();

        store.replayOrRun(
                "key-2",
                "hash-1",
                String.class,
                new IdempotencyCallback<String>() {
                    @Override
                    public String execute() {
                        return "value";
                    }
                }
        );

        boolean thrown = false;
        try {
            store.replayOrRun(
                    "key-2",
                    "hash-2",
                    String.class,
                    new IdempotencyCallback<String>() {
                        @Override
                        public String execute() {
                            return "different";
                        }
                    }
            );
        } catch (IdempotencyException expected) {
            thrown = true;
        }
        assertThat(thrown).isTrue();
    }
}
