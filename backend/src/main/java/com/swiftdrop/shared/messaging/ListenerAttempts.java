package com.swiftdrop.shared.messaging;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;

/** Bounded local retries; each invocation opens a new transaction in the inbox. */
public final class ListenerAttempts {
    private ListenerAttempts() {
    }

    public static void run(Runnable action) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                action.run();
                return;
            } catch (TransientDataAccessException | CannotCreateTransactionException
                    | org.springframework.dao.DataAccessResourceFailureException
                    | org.springframework.dao.RecoverableDataAccessException ex) {
                if (attempt == 2) {
                    break;
                }
                try {
                    Thread.sleep(1000L << attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (RuntimeException ex) {
                break;
            }
        }
        // Original body goes to the DLQ; never attach a cause that may contain payload/SQL values.
        throw new AmqpRejectAndDontRequeueException("Event processing failed; retained in dead letters");
    }
}
