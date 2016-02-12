package com.github.stefanbirkner.fakesftpserver.rule;

import com.github.stefanbirkner.fishbowl.Statement;
import org.junit.rules.TestRule;
import org.junit.runner.Description;

import static com.github.stefanbirkner.fishbowl.Fishbowl.*;

class Executor {
    private static final Description DUMMY_DESCRIPTION = null;

    static void executeTestWithRule(Statement test, TestRule rule) {
        wrapCheckedException(executeTestWithRuleRaw(test, rule));
    }

    static void executeTestThatThrowsExceptionWithRule(Statement test,
            TestRule rule) {
        ignoreException(
            executeTestWithRuleRaw(test, rule),
            Throwable.class);
    }

    private static Statement executeTestWithRuleRaw(Statement test,
            TestRule rule) {
        org.junit.runners.model.Statement statement
            = new org.junit.runners.model.Statement() {
                @Override
                public void evaluate() throws Throwable {
                    test.evaluate();
                }
            };
        return () -> rule.apply(statement, DUMMY_DESCRIPTION).evaluate();
    }
}
