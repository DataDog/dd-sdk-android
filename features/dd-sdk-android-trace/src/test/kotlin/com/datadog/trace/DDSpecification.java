package com.datadog.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.Set;
import java.util.stream.Collectors;

public abstract class DDSpecification {

    @BeforeEach
    public void setup() {
    }

    @AfterEach
    public void cleanup() {
        if (assertThreadsEachCleanup()) {
            checkThreads();
        }
    }

    protected boolean ignoreThreadCleanup() {
        return false;
    }

    protected boolean assertThreadsEachCleanup() {
        return true;
    }

    public Set<Thread> getDDThreads() {
        // get all Java threads and filter out the ones we care about

        return Thread.getAllStackTraces()
                .keySet()
                .stream()
                .filter(it ->
                        it.getName().startsWith("dd-") &&
                                !it.getName().equals("dd-task-scheduler") &&
                                !it.getName().equals("dd-cassandra-session-executor"))
                .collect(Collectors.toSet());
    }

    public void checkThreads() {
        if (ignoreThreadCleanup()) {
            return;

        }


        // Give some time for threads to finish to prevent the race
        // between test cleanup and these assertions
        long deadline = System.currentTimeMillis() + CHECK_TIMEOUT_MS;

        Set<Thread> threads = getDDThreads();
        while (System.currentTimeMillis() < deadline && !threads.isEmpty()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            threads = getDDThreads();
        }

        if (!threads.isEmpty()) {
            System.out.println("WARNING: DD threads still active.  Forget to close() a tracer?");
        }

    }

    private static final int CHECK_TIMEOUT_MS = 3000;
}
