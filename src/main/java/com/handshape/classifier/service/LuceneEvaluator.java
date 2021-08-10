package com.handshape.classifier.service;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MultiPhraseQuery;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

/**
 * Classifier that uses a set of Lucene queries to assign categories to
 * passed-in maps of key-value pairs. This class watches the file that is passed
 * in for changes, and will reload the categories at runtime.
 *
 * @author jturner
 */
public class LuceneEvaluator implements Closeable {

    /**
     * @return the lastLoadTime
     */
    public long getLastLoadTime() {
        return lastLoadTime;
    }

    /**
     * Field name that will be used for query terms that have no specified
     * field.
     */
    public static final String DEFAULT_FIELD_NAME = "text";

    private Analyzer analyzer = new StandardAnalyzer();
    private final File myFile;
    private final FileWatcher watcher;
    private Map<String, Query> queries = new TreeMap<>();
    private long lastLoadTime = 0L;

    /**
     * Constructor for a new Lucene evaluator. Malformed queries get logged to
     * stderr. The passed-in file is watched for changes at runtime.
     *
     * @param f A properties file from which the set of queries and categories
     * will be loaded.
     * @throws IOException if the properties file can't be read for any reason.
     */
    public LuceneEvaluator(File f) throws IOException {
        myFile = f;
        loadCategories();
        watcher = new FileWatcher(myFile);
        watcher.start();
    }

    /**
     * Manually stop the thread that watches for changes to the category file.
     */
    public void stop() {
        if (!watcher.isStopped()) {
            watcher.stopThread();
        }
    }

    /**
     * Loads the categories from the file associated with this classifer.
     * @throws IOException if the given file can't be loaded for any reason.
     */
    
    public void loadCategories() throws IOException {
        Properties p = new Properties();
        try ( FileInputStream fis = new FileInputStream(myFile)) {
            p.load(fis);
            lastLoadTime = System.currentTimeMillis();
        }
        TreeMap<String, Query> newQueries = new TreeMap<>();
        QueryParser parser = new QueryParser("text", analyzer);
        parser.setAllowLeadingWildcard(true);
        for (Object o : p.keySet()) {
            String key = String.valueOf(o);
            if (!key.contains(".")) {
                try {
                    Query query = parser.parse(p.getProperty(key));
                    newQueries.put(key, query);
                } catch (ParseException parseException) {
                    System.err.println("Error parsing category '" + key + "':");
                    System.err.println(parseException.getLocalizedMessage());
                }
            }
        }
        queries = newQueries;
    }

    /**
     * Evaluates the passed-in map against the set of categories and queries
     * that have been loaded, and returns the set of matching categories. Each
     * key in the map is converted to a Lucene field for the purposes of query
     * evaluation.
     *
     * @param evaluationData the map to be evaluated
     * @return the set of matching category keys
     */
    public Set<String> evaluate(Map<String, String> evaluationData) {
        MemoryIndex mi = new MemoryIndex();
        for (Entry<String, String> entry : evaluationData.entrySet()) {
            mi.addField(entry.getKey(), entry.getValue(), analyzer);
        }
        mi.freeze();
        TreeSet<String> returnable = new TreeSet<>();
        for (Entry<String, Query> entry : queries.entrySet()) {
            if (mi.search(entry.getValue()) > 0.0) {
                returnable.add(entry.getKey());
            }
        }
        return returnable;
    }

    /**
     * Fetches the set of fields used in the union of all queries.
     *
     * @return the set of all field keys used
     */
    public Set<String> getFieldList() {
        Set<String> returnable = new TreeSet<>();
        for (Query q : queries.values()) {
            returnable.addAll(collectFields(q));
        }
        return returnable;
    }

    private Set<String> collectFields(Query query) {
        Set<String> fields = new TreeSet<>();
        if (query instanceof BooleanQuery) {
            for (BooleanClause child : ((BooleanQuery) query).clauses()) {
                fields.addAll(collectFields(child.getQuery()));
            }
        } else if (query instanceof TermQuery) {
            fields.add(((TermQuery) query).getTerm().field());
        } else if (query instanceof MultiTermQuery) {
            fields.add(((MultiTermQuery) query).getField());
        } else if (query instanceof PhraseQuery) {
            for (Term t : ((PhraseQuery) query).getTerms()) {
                fields.add(t.field());
            }
            // Is this covered by the automaton query? There's a spearate 'field' field.
            //        } else if (query instanceof PrefixQuery) {
            //            fields.add(((PrefixQuery) query).getPrefix().field());
        } else if (query instanceof MultiPhraseQuery) {
            for (Term[] t1 : ((MultiPhraseQuery) query).getTermArrays()) {
                for (Term t : t1) {
                    fields.add(t.field());
                }
            }
        } else if (query instanceof MultiTermQuery) { // Does Fuzzy and numeric range query terms
            fields.add(((MultiTermQuery) query).getField());
        }
        return fields;
    }

    /**
     * Implementation of Closeable that stops the watcher thread
     *
     * @throws IOException
     */
    @Override
    public void close() throws IOException {
        stop();
    }

    private class FileWatcher extends Thread {

        private final File file;
        private final AtomicBoolean stop = new AtomicBoolean(false);

        public FileWatcher(File file) {
            super(file.getName() + " watcher");
            this.file = file;
        }

        public boolean isStopped() {
            return stop.get();
        }

        public void stopThread() {
            stop.set(true);
        }

        public void doOnChange() {
            Logger.getLogger(LuceneEvaluator.class.getName()).log(Level.INFO, "Detected change in {0} - reloading.", myFile.getName());
            try {
                loadCategories();
            } catch (IOException ex) {
                Logger.getLogger(LuceneEvaluator.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        @Override
        public void run() {
            try ( WatchService watcher = FileSystems.getDefault().newWatchService()) {
                Path path = file.toPath().toAbsolutePath().getParent();
                if (path != null) {
                    path.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);
                    while (!isStopped()) {
                        WatchKey key;
                        try {
                            key = watcher.poll(25, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException e) {
                            return;
                        }
                        if (key == null) {
                            Thread.yield();
                            continue;
                        }
                        for (WatchEvent<?> event : key.pollEvents()) {
                            WatchEvent.Kind<?> kind = event.kind();

                            @SuppressWarnings("unchecked")
                            WatchEvent<Path> ev = (WatchEvent<Path>) event;
                            Path filename = ev.context();

                            if (kind == StandardWatchEventKinds.OVERFLOW) {
                                Thread.yield();
                                continue;
                            } else if (kind == java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
                                    && filename.toString().equals(file.getName())) {
                                doOnChange();
                            }
                            boolean valid = key.reset();
                            if (!valid) {
                                break;
                            }
                        }
                        Thread.yield();
                    }
                }
            } catch (Throwable e) {
                // Log or rethrow the error
            }
        }
    }
}
