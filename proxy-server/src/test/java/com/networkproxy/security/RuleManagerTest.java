package com.networkproxy.security;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleManagerTest {

    @Test
    void loadsOnlyEnabledRulesAndReloadsSuccessfully() throws SQLException {
        List<AccessRule> first = List.of(rule(1, true), rule(2, false));
        AtomicReference<List<AccessRule>> source = new AtomicReference<>(first);
        RuleManager manager = new RuleManager(source::get);

        assertEquals(List.of(first.get(0)), manager.getRules());

        source.set(List.of(rule(3, true)));
        manager.reload();

        assertEquals(List.of(rule(3, true)), manager.getRules());
    }

    @Test
    void failedReloadKeepsPreviousRules() throws SQLException {
        AtomicReference<Boolean> fail = new AtomicReference<>(false);
        RuleManager manager = new RuleManager(() -> {
            if (fail.get()) {
                throw new SQLException("database unavailable");
            }
            return List.of(rule(1, true));
        });

        fail.set(true);
        assertThrows(SQLException.class, manager::reload);
        assertEquals(List.of(rule(1, true)), manager.getRules());
    }

    @Test
    void reloadLoadsSshRule() throws SQLException {
        AccessRule sshRule = protocolRule(12, "SSH", 22, true);
        AtomicReference<List<AccessRule>> source =
                new AtomicReference<>(List.of());
        RuleManager manager = new RuleManager(source::get);

        source.set(List.of(sshRule));
        manager.reload();

        assertEquals(List.of(sshRule), manager.getRules());
        assertEquals("SSH", manager.getRules().get(0).protocol());
        assertEquals(22, manager.getRules().get(0).targetPort());
    }

    @Test
    void disabledSshRuleIsExcluded() throws SQLException {
        AccessRule enabledSshRule = protocolRule(13, "SSH", 22, true);
        AccessRule disabledSshRule = protocolRule(14, "SSH", 2222, false);
        RuleManager manager = new RuleManager(() ->
                List.of(enabledSshRule, disabledSshRule));

        assertEquals(List.of(enabledSshRule), manager.getRules());
        assertTrue(manager.getRules().stream()
                .noneMatch(rule -> rule.id() == disabledSshRule.id()));
    }

    @Test
    void reloadLoadsHttpHttpsAndSshRules() throws SQLException {
        AtomicReference<List<AccessRule>> source =
                new AtomicReference<>(List.of());
        RuleManager manager = new RuleManager(source::get);
        AccessRule httpRule = protocolRule(15, "HTTP", 80, true);
        AccessRule httpsRule = protocolRule(16, "HTTPS", 443, true);
        AccessRule sshRule = protocolRule(17, "SSH", 22, true);

        source.set(List.of(httpRule, httpsRule, sshRule));
        manager.reload();

        assertEquals(3, manager.getRules().size());
        assertTrue(manager.getRules().stream()
                .anyMatch(rule -> "HTTP".equals(rule.protocol())));
        assertTrue(manager.getRules().stream()
                .anyMatch(rule -> "HTTPS".equals(rule.protocol())));
        assertTrue(manager.getRules().stream()
                .anyMatch(rule -> "SSH".equals(rule.protocol())));
    }

    @Test
    void failedReloadKeepsSshSnapshot() throws SQLException {
        AccessRule sshRule = protocolRule(18, "SSH", 22, true);
        AtomicReference<Boolean> fail = new AtomicReference<>(false);
        RuleManager manager = new RuleManager(() -> {
            if (fail.get()) {
                throw new SQLException("database unavailable");
            }
            return List.of(sshRule);
        });

        fail.set(true);
        assertThrows(SQLException.class, manager::reload);
        assertEquals(List.of(sshRule), manager.getRules());
        assertEquals("SSH", manager.getRules().get(0).protocol());
    }

    @Test
    void concurrentReadsObserveCompleteSnapshots() throws Exception {
        AtomicReference<List<AccessRule>> source =
                new AtomicReference<>(List.of(rule(1, true)));
        RuleManager manager = new RuleManager(source::get);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        for (int i = 0; i < 7; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    for (int j = 0; j < 1_000; j++) {
                        int size = manager.getRules().size();
                        if (size != 1) {
                            throw new AssertionError("Incomplete rule snapshot");
                        }
                    }
                } catch (Throwable throwable) {
                    failure.compareAndSet(null, throwable);
                }
            });
        }
        executor.submit(() -> {
            try {
                start.await();
                for (int i = 0; i < 100; i++) {
                    source.set(List.of(rule(i + 2, true)));
                    manager.reload();
                }
            } catch (Throwable throwable) {
                failure.compareAndSet(null, throwable);
            }
        });

        start.countDown();
        executor.shutdown();
        assertEquals(true, executor.awaitTermination(10, TimeUnit.SECONDS));
        if (failure.get() != null) {
            throw new AssertionError("Concurrent access failed", failure.get());
        }
    }

    private static AccessRule rule(long id, boolean enabled) {
        return new AccessRule(id, null, "example.com", null, null,
                AccessRule.Action.ALLOW, 10, enabled);
    }

    private static AccessRule protocolRule(
            long id,
            String protocol,
            Integer port,
            boolean enabled
    ) {
        return new AccessRule(id, null, "example.com", port, protocol,
                AccessRule.Action.ALLOW, 10, enabled);
    }
}
