package com.capybee.engine.input;

import com.capybee.engine.api.Issue;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One-start-operation boundary. Text values are safe but deliberately never rendered. */
public final class PreparedStartInputs {
    private final Map<String, String> persistentValues;
    private final Map<String, SafeTextInput> transientTextValues;
    private final List<Issue> issues;

    public PreparedStartInputs(Map<String, String> persistentValues,
                               Map<String, SafeTextInput> transientTextValues, List<Issue> issues) {
        this.persistentValues = Collections.unmodifiableMap(new LinkedHashMap<>(persistentValues));
        this.transientTextValues = Collections.unmodifiableMap(new LinkedHashMap<>(transientTextValues));
        this.issues = List.copyOf(issues);
    }

    public Map<String, String> persistentValues() { return persistentValues; }
    public Map<String, SafeTextInput> transientTextValues() { return transientTextValues; }
    public List<Issue> issues() { return issues; }
    public boolean ok() { return issues.stream().noneMatch(issue -> issue.severity() == Issue.Severity.ERROR); }

    @Override public String toString() {
        return "PreparedStartInputs[persistentIds=" + persistentValues.keySet()
                + ", transientTextCount=" + transientTextValues.size()
                + ", issueCount=" + issues.size() + "]";
    }
}
