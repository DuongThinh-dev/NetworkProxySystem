package com.networkproxy.security;

import com.networkproxy.repository.RuleRepository;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class RuleManager {

    @FunctionalInterface
    public interface RuleLoader {
        List<AccessRule> load() throws SQLException;
    }

    private static final Comparator<AccessRule> RULE_ORDER =
            Comparator.comparingInt(AccessRule::priority).reversed()
                    .thenComparing(Comparator.comparingInt(RuleManager::specificity)
                            .reversed())
                    .thenComparing(rule -> rule.action() == AccessRule.Action.BLOCK ? 0 : 1)
                    .thenComparingLong(AccessRule::id);

    private final RuleLoader ruleLoader;
    private volatile List<AccessRule> rules = List.of();

    public RuleManager(RuleRepository repository) throws SQLException {
        this(Objects.requireNonNull(repository, "Rule repository is required")::findEnabled);
    }

    public RuleManager(RuleLoader ruleLoader) throws SQLException {
        this.ruleLoader = Objects.requireNonNull(ruleLoader, "Rule loader is required");
        reload();
    }

    public List<AccessRule> getRules() {
        return rules;
    }

    public void reload() throws SQLException {
        List<AccessRule> loadedRules = ruleLoader.load();
        if (loadedRules == null) {
            throw new IllegalStateException("Rule loader returned null");
        }

        List<AccessRule> activeRules = loadedRules.stream()
                .filter(Objects::nonNull)
                .filter(AccessRule::enabled)
                .sorted(RULE_ORDER)
                .toList();
        rules = activeRules;
    }

    static int specificity(AccessRule rule) {
        int count = 0;
        if (rule.clientIp() != null) {
            count++;
        }
        if (rule.target() != null) {
            count++;
        }
        if (rule.targetPort() != null) {
            count++;
        }
        if (rule.protocol() != null) {
            count++;
        }
        return count;
    }
}
