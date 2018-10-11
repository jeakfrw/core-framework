package de.fearnixx.t3.teamspeak.query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * (Name shortened from `QueryRequestBuilder` for readability reasons).
 *
 * Constructs a new object implementing {@link IQueryRequest} using the builder-pattern.
 * @see IQueryRequest for the actual meanings of the methods.
 */
@SuppressWarnings({"UnusedReturnValue", "WeakerAccess"})
public class QueryBuilder {
    private String command;
    private Map<String, String> currentObj;
    private List<Map<String, String>> chain;
    private List<String> options;

    QueryBuilder() {
        reset();
    }

    public QueryBuilder reset() {
        command = "";
        currentObj =  null;
        chain = new ArrayList<>();
        options = new ArrayList<>();
        newChain();
        return this;
    }

    public QueryBuilder command(String command) {
        this.command = command;
        return this;
    }

    public QueryBuilder newChain() {
        chain.add(new HashMap<>());
        currentObj = chain.get(chain.size()-1);
        return this;
    }

    /**
     * Add a parameter to the current chain.
     * @param key The key
     * @param value String|Object - on objects {@link #toString()} is invoked
     */
    public QueryBuilder addKey(String key, Object value) {
        if (currentObj.containsKey(key))
            currentObj.replace(key, value != null ? value.toString() : null);
        else
            currentObj.put(key, value != null ? value.toString() : null);
        return this;
    }

    public QueryBuilder addOption(String option) {
        options.add(option);
        return this;
    }

    public IQueryRequest build() {
        return new IQueryRequest() {
            final String fComm = command;
            final List<Map<String, String>> fChain = chain;
            final List<String> fOptions = options;

            @Override
            public String getCommand() {
                return fComm;
            }

            @Override
            public List<Map<String, String>> getChain() {
                return fChain;
            }

            @Override
            public List<String> getOptions() {
                return fOptions;
            }
        };
    }
}
