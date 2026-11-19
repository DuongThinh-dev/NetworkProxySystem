package com.networkproxy.security;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AccessControlServiceTest {

    private static final AccessControlService.AccessRequest REQUEST =
            new AccessControlService.AccessRequest(
                    "10.0.0.5", "Example.COM.", "93.184.216.34", 443, "https");

    @Test
    void matchesClientAllowRule() throws SQLException {
        AccessRule rule = rule(1, "10.0.0.5", null, null, null,
                AccessRule.Action.ALLOW, 10, true);

        AccessControlService.AccessDecision decision = service(rule).decide(REQUEST);

        assertEquals(AccessRule.Action.ALLOW, decision.action());
        assertEquals(rule, decision.matchedRule());
    }

    @Test
    void matchesDomainAndBlocks() throws SQLException {
        AccessRule rule = rule(2, null, "example.com", null, "HTTPS",
                AccessRule.Action.BLOCK, 10, true);

        AccessControlService.AccessDecision decision = service(rule).decide(REQUEST);

        assertEquals(AccessRule.Action.BLOCK, decision.action());
        assertEquals(AccessControlService.RULE_MATCH, decision.reason());
    }

    @Test
    void matchesDestinationIpPortAndProtocol() throws SQLException {
        AccessRule rule = rule(3, null, "93.184.216.34", 443, "HTTPS",
                AccessRule.Action.ALLOW, 10, true);

        assertEquals(AccessRule.Action.ALLOW, service(rule).decide(REQUEST).action());
    }

    @Test
    void nullConditionsMatchAnyValue() throws SQLException {
        AccessRule rule = rule(4, null, null, null, null,
                AccessRule.Action.ALLOW, 10, true);

        assertEquals(AccessRule.Action.ALLOW, service(rule).decide(REQUEST).action());
    }

    @Test
    void higherPriorityWins() throws SQLException {
        AccessRule allow = rule(5, null, "example.com", null, null,
                AccessRule.Action.ALLOW, 10, true);
        AccessRule block = rule(6, null, "example.com", null, null,
                AccessRule.Action.BLOCK, 20, true);

        assertEquals(AccessRule.Action.BLOCK, service(allow, block).decide(REQUEST).action());
    }

    @Test
    void specificityWinsWhenPriorityIsEqual() throws SQLException {
        AccessRule broad = rule(7, null, "example.com", null, null,
                AccessRule.Action.BLOCK, 10, true);
        AccessRule specific = rule(8, "10.0.0.5", "example.com", 443, "HTTPS",
                AccessRule.Action.ALLOW, 10, true);

        assertEquals(AccessRule.Action.ALLOW,
                service(broad, specific).decide(REQUEST).action());
    }

    @Test
    void blockWinsWhenPriorityAndSpecificityAreEqual() throws SQLException {
        AccessRule allow = rule(9, null, "example.com", null, null,
                AccessRule.Action.ALLOW, 10, true);
        AccessRule block = rule(10, null, "example.com", null, null,
                AccessRule.Action.BLOCK, 10, true);

        assertEquals(AccessRule.Action.BLOCK,
                service(allow, block).decide(REQUEST).action());
    }

    @Test
    void defaultsToBlockWhenNoRuleMatches() throws SQLException {
        AccessRule rule = rule(11, null, "other.example", null, null,
                AccessRule.Action.ALLOW, 10, true);

        AccessControlService.AccessDecision decision = service(rule).decide(REQUEST);

        assertEquals(AccessRule.Action.BLOCK, decision.action());
        assertEquals(AccessControlService.DEFAULT_DENY, decision.reason());
        assertNull(decision.matchedRule());
    }

    @Test
    void sshAllowRuleMatchesPort22() throws SQLException {
        AccessControlService.AccessRequest sshRequest =
                new AccessControlService.AccessRequest(
                        "10.0.0.5", "example.com", null, 22, "SSH");
        AccessRule rule = rule(12, null, "example.com", 22, "SSH",
                AccessRule.Action.ALLOW, 10, true);

        AccessControlService.AccessDecision decision =
                service(rule).decide(sshRequest);

        assertEquals(AccessRule.Action.ALLOW, decision.action());
        assertEquals(AccessControlService.RULE_MATCH, decision.reason());
        assertEquals(rule, decision.matchedRule());
    }

    @Test
    void sshBlockRuleMatches() throws SQLException {
        AccessControlService.AccessRequest sshRequest =
                new AccessControlService.AccessRequest(
                        "10.0.0.5", "example.com", null, 22, "SSH");
        AccessRule rule = rule(13, null, "example.com", 22, "SSH",
                AccessRule.Action.BLOCK, 10, true);

        AccessControlService.AccessDecision decision =
                service(rule).decide(sshRequest);

        assertEquals(AccessRule.Action.BLOCK, decision.action());
        assertEquals(AccessControlService.RULE_MATCH, decision.reason());
        assertEquals(rule, decision.matchedRule());
    }

    @Test
    void sshDefaultDeniesWhenNoRuleMatches() throws SQLException {
        AccessControlService.AccessRequest sshRequest =
                new AccessControlService.AccessRequest(
                        "10.0.0.5", "example.com", null, 22, "SSH");
        AccessRule rule = rule(14, null, "other.example", 22, "SSH",
                AccessRule.Action.ALLOW, 10, true);

        AccessControlService.AccessDecision decision =
                service(rule).decide(sshRequest);

        assertEquals(AccessRule.Action.BLOCK, decision.action());
        assertEquals(AccessControlService.DEFAULT_DENY, decision.reason());
        assertNull(decision.matchedRule());
    }

    @Test
    void sshRuleDoesNotMatchDifferentPort() throws SQLException {
        AccessControlService.AccessRequest sshRequest =
                new AccessControlService.AccessRequest(
                        "10.0.0.5", "example.com", null, 2222, "SSH");
        AccessRule rule = rule(15, null, "example.com", 22, "SSH",
                AccessRule.Action.ALLOW, 10, true);

        AccessControlService.AccessDecision decision =
                service(rule).decide(sshRequest);

        assertEquals(AccessRule.Action.BLOCK, decision.action());
        assertEquals(AccessControlService.DEFAULT_DENY, decision.reason());
        assertNull(decision.matchedRule());
    }

    @Test
    void sshHigherPriorityRuleWins() throws SQLException {
        AccessControlService.AccessRequest sshRequest =
                new AccessControlService.AccessRequest(
                        "10.0.0.5", "example.com", null, 22, "SSH");
        AccessRule lowPriorityAllow = rule(16, null, "example.com", 22, "SSH",
                AccessRule.Action.ALLOW, 10, true);
        AccessRule highPriorityBlock = rule(17, null, "example.com", 22, "SSH",
                AccessRule.Action.BLOCK, 20, true);

        AccessControlService.AccessDecision decision =
                service(lowPriorityAllow, highPriorityBlock).decide(sshRequest);

        assertEquals(AccessRule.Action.BLOCK, decision.action());
        assertEquals(highPriorityBlock, decision.matchedRule());
        assertEquals(AccessControlService.RULE_MATCH, decision.reason());
    }

    private static AccessControlService service(AccessRule... rules) throws SQLException {
        return new AccessControlService(new RuleManager(() -> List.of(rules)));
    }

    private static AccessRule rule(long id, String clientIp, String target, Integer port,
                                   String protocol, AccessRule.Action action, int priority,
                                   boolean enabled) {
        return new AccessRule(id, clientIp, target, port, protocol, action, priority, enabled);
    }
}
