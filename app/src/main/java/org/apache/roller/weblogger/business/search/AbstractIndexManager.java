/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  The ASF licenses this file to You
 * under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.  For additional information regarding
 * copyright in this work, please see the NOTICE file in the top level
 * directory of this distribution.
 */

package org.apache.roller.weblogger.business.search;

import java.io.File;
import java.io.IOException;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.miscellaneous.LimitTokenCountAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.RAMDirectory;
import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.business.InitializationException;
import org.apache.roller.weblogger.business.Weblogger;
import org.apache.roller.weblogger.business.search.operations.AddEntryOperation;
import org.apache.roller.weblogger.business.search.operations.IndexOperation;
import org.apache.roller.weblogger.business.search.operations.ReIndexEntryOperation;
import org.apache.roller.weblogger.business.search.operations.RebuildWebsiteIndexOperation;
import org.apache.roller.weblogger.business.search.operations.RemoveEntryOperation;
import org.apache.roller.weblogger.business.search.operations.RemoveWebsiteIndexOperation;
import org.apache.roller.weblogger.business.search.operations.WriteToIndexOperation;
import org.apache.roller.weblogger.pojos.WeblogEntry;
import org.apache.roller.weblogger.pojos.Weblog;
import org.apache.roller.weblogger.config.WebloggerConfig;

/**
 * Lucene implementation of IndexManager. This is the central entry point into
 * the Lucene searching API.
 *
 * @author Mindaugas Idzelis (min@idzelis.com)
 * @author mraible (formatting and making indexDir configurable)
 */
public abstract class AbstractIndexManager implements IndexManager {
    // ~ Static fields/initializers
    // =============================================

    private IndexReader reader;
    private final Weblogger roller;

    static Log mLogger = LogFactory.getFactory().getInstance(
            AbstractIndexManager.class);

    // ~ Instance fields
    // ========================================================

    private boolean searchEnabled = true;

    private boolean useRAMIndex = false;

    private RAMDirectory fRAMindex;

    private boolean inconsistentAtStartup = false;

    private ReadWriteLock rwl = new ReentrantReadWriteLock();

    // ~ Constructors
    // ===========================================================

    /**
     * Creates a new lucene index manager. This should only be created once.
     * Creating the index manager more than once will definately result in
     * errors. The preferred way of getting an index is through the
     * RollerContext.
     *
     * @param roller - the weblogger instance
     */
    protected AbstractIndexManager(Weblogger roller) {
        this.roller = roller;

        // check config to see if the internal search is enabled
        String enabled = WebloggerConfig.getProperty("search.enabled");
        if ("false".equalsIgnoreCase(enabled)) {
            this.searchEnabled = false;
        }

        // a little debugging
        mLogger.info("search enabled: " + this.searchEnabled);

    }

    protected abstract boolean isInconsistencyMarkerSet();
    protected abstract void setInconsistencyMarker();
    protected abstract void clearInconsistencyMarker();
    protected abstract boolean indexStorageExists();
    protected abstract void createIndexStorage();

    /**
     * @inheritDoc
     */
    public void initialize() throws InitializationException {

        // only initialize the index if search is enabled
        if (this.searchEnabled) {

            // 1. If inconsistency marker exists.
            // Delete index
            // 2. if we're using RAM index
            // load ram index wrapper around index
            //
            if (isInconsistencyMarkerSet()) {
                getStorageDirectory(true);
                inconsistentAtStartup = true;
                mLogger.debug("Index inconsistent: marker exists");
            } else {
                if (!indexStorageExists()) {
                    createIndexStorage();;
                    inconsistentAtStartup = true;
                    mLogger.debug("Index inconsistent: new");
                }
                setInconsistencyMarker();
            }

            if (indexExists()) {
                if (useRAMIndex) {
                    Directory filesystem = getStorageDirectory(false);
                    try {
                        fRAMindex = new RAMDirectory(filesystem, IOContext.DEFAULT);
                    } catch (IOException e) {
                        mLogger.error("Error creating in-memory index", e);
                    }
                }
            } else {
                mLogger.debug("Creating index");
                inconsistentAtStartup = true;
                if (useRAMIndex) {
                    fRAMindex = new RAMDirectory();
                    createIndex(fRAMindex);
                } else {
                    createIndex(getStorageDirectory(true));
                }
            }

            if (isInconsistentAtStartup()) {
                mLogger.info("Index was inconsistent. Rebuilding index in the background...");
                try {
                    rebuildWebsiteIndex();
                } catch (WebloggerException e) {
                    mLogger.error("ERROR: scheduling re-index operation");
                }
            } else {
                mLogger.info("Index initialized and ready for use.");
            }
        }

    }

    // ~ Methods
    // ================================================================

    public void rebuildWebsiteIndex() throws WebloggerException {
        scheduleIndexOperation(new RebuildWebsiteIndexOperation(roller, this,
                null));
    }

    public void rebuildWebsiteIndex(Weblog website) throws WebloggerException {
        scheduleIndexOperation(new RebuildWebsiteIndexOperation(roller, this,
                website));
    }

    public void removeWebsiteIndex(Weblog website) throws WebloggerException {
        scheduleIndexOperation(new RemoveWebsiteIndexOperation(roller, this,
                website));
    }

    public void addEntryIndexOperation(WeblogEntry entry)
            throws WebloggerException {
        AddEntryOperation addEntry = new AddEntryOperation(roller, this, entry);
        scheduleIndexOperation(addEntry);
    }

    public void addEntryReIndexOperation(WeblogEntry entry)
            throws WebloggerException {
        ReIndexEntryOperation reindex = new ReIndexEntryOperation(roller, this,
                entry);
        scheduleIndexOperation(reindex);
    }

    public void removeEntryIndexOperation(WeblogEntry entry)
            throws WebloggerException {
        RemoveEntryOperation removeOp = new RemoveEntryOperation(roller, this,
                entry);
        executeIndexOperationNow(removeOp);
    }

    public ReadWriteLock getReadWriteLock() {
        return rwl;
    }

    public boolean isInconsistentAtStartup() {
        return inconsistentAtStartup;
    }

    /**
     * This is the analyzer that will be used to tokenize comment text.
     *
     * @return Analyzer to be used in manipulating the database.
     */
    public final Analyzer getAnalyzer() {
        return new StandardAnalyzer(FieldConstants.LUCENE_VERSION);
    }

    private void scheduleIndexOperation(final IndexOperation op) {
        try {
            // only if search is enabled
            if (this.searchEnabled) {
                mLogger.debug("Starting scheduled index operation: "
                        + op.getClass().getName());
                roller.getThreadManager().executeInBackground(op);
            }
        } catch (InterruptedException e) {
            mLogger.error("Error executing operation", e);
        }
    }

    /**
     * @param op
     */
    public void executeIndexOperationNow(final IndexOperation op) {
        try {
            // only if search is enabled
            if (this.searchEnabled) {
                mLogger.debug("Executing index operation now: "
                        + op.getClass().getName());
                roller.getThreadManager().executeInForeground(op);
            }
        } catch (InterruptedException e) {
            mLogger.error("Error executing operation", e);
        }
    }

    public synchronized void resetSharedReader() {
        reader = null;
    }

    public synchronized IndexReader getSharedIndexReader() {
        if (reader == null) {
            try {
                reader = DirectoryReader.open(getIndexDirectory());
            } catch (IOException e) {
            }
        }
        return reader;
    }

    protected abstract Directory getStorageDirectory(boolean delete);

    /**
     * Get the directory that is used by the lucene index. This method will
     * return null if there is no index at the directory location. If we are
     * using a RAM index, the directory will be a ram directory.
     *
     * @return Directory The directory containing the index, or null if error.
     */
    public Directory getIndexDirectory() {
        if (useRAMIndex) {
            return fRAMindex;
        } else {
            return getStorageDirectory(false);
        }
    }

    private boolean indexExists() {
        try {
            return DirectoryReader.indexExists(getIndexDirectory());
        } catch (IOException e) {
            mLogger.error("Problem accessing index directory", e);
        }
        return false;
    }



    private void createIndex(Directory dir) {
        IndexWriter writer = null;

        try {

            IndexWriterConfig config = new IndexWriterConfig(
                    FieldConstants.LUCENE_VERSION, new LimitTokenCountAnalyzer(
                    getAnalyzer(),
                    IndexWriterConfig.DEFAULT_TERM_INDEX_INTERVAL));

            writer = new IndexWriter(dir, config);

        } catch (IOException e) {
            mLogger.error("Error creating index", e);
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException e) {
            }
        }
    }

    private IndexOperation getSaveIndexOperation() {
        return new WriteToIndexOperation(this) {
            public void doRun() {
                Directory dir = getIndexDirectory();
                Directory fsdir = getStorageDirectory(true);
                IndexWriter writer = null;
                try {
                    IndexWriterConfig config = new IndexWriterConfig(FieldConstants.LUCENE_VERSION,
                            new LimitTokenCountAnalyzer(getAnalyzer(),
                                    IndexWriterConfig.DEFAULT_TERM_INDEX_INTERVAL));
                    writer = new IndexWriter(fsdir, config);
                    writer.addIndexes(new Directory[] { dir });
                    writer.commit();
                    clearInconsistencyMarker();
                } catch (IOException e) {
                    mLogger.error("Problem saving index to disk", e);
                    // Delete the directory, since there was a problem saving the RAM contents
                    getStorageDirectory(true);
                } finally {
                    try {
                        if (writer != null) {
                            writer.close();
                        }
                    } catch (IOException e1) {
                        mLogger.warn("Unable to close IndexWriter.");
                    }
                }
            }
        };
    }

    public void release() {
        // no-op
    }

    public void shutdown() {
        if (useRAMIndex) {
            scheduleIndexOperation(getSaveIndexOperation());
        } else {
            clearInconsistencyMarker();
        }

        try {
            if (reader != null) {
                reader.close();
            }
        } catch (IOException e) {
            // won't happen, since it was
        }
    }

}
