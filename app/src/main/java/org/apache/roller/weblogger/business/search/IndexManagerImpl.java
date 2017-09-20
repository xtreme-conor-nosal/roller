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
@com.google.inject.Singleton
public class IndexManagerImpl extends AbstractIndexManager {
    // ~ Static fields/initializers
    // =============================================

    static Log mLogger = LogFactory.getFactory().getInstance(
            IndexManagerImpl.class);

    // ~ Instance fields
    // ========================================================

    File indexConsistencyMarker;

    private String indexDir = null;

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
    @com.google.inject.Inject
    protected IndexManagerImpl(Weblogger roller) {
        super(roller);

        // we also need to know what our index directory is
        // Note: system property expansion is now handled by WebloggerConfig
        String searchIndexDir = WebloggerConfig.getProperty("search.index.dir");
        this.indexDir = searchIndexDir.replace('/', File.separatorChar);

        // a little debugging
        mLogger.info("index dir: " + this.indexDir);

        String test = indexDir + File.separator + ".index-inconsistent";
        indexConsistencyMarker = new File(test);
    }

    protected Directory getStorageDirectory(boolean delete) {

        Directory directory = null;

        try {

            directory = FSDirectory.open(new File(indexDir));

            if (delete && directory != null) {
                // clear old files
                String[] files = directory.listAll();
                for (int i = 0; i < files.length; i++) {
                    File file = new File(indexDir, files[i]);
                    if (!file.delete()) {
                        throw new IOException("couldn't delete " + files[i]);
                    }
                }
            }

        } catch (IOException e) {
            mLogger.error("Problem accessing index directory", e);
        }

        return directory;

    }

    @Override
    protected boolean isInconsistencyMarkerSet() {
        return indexConsistencyMarker.exists();
    }

    @Override
    protected void setInconsistencyMarker() {
        try {
            indexConsistencyMarker.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to mark index inconsistent", e);
        }
    }

    @Override
    protected void clearInconsistencyMarker() {
        indexConsistencyMarker.delete();
    }

    @Override
    protected boolean indexStorageExists() {
        return new File(indexDir).exists();
    }

    @Override
    protected void createIndexStorage() {
        new File(indexDir).mkdirs();
    }
}
