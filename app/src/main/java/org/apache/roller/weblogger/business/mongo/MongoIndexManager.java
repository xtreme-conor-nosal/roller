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

package org.apache.roller.weblogger.business.mongo;

import com.mongodb.DB;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.store.Directory;
import org.apache.roller.weblogger.business.Weblogger;
import org.apache.roller.weblogger.business.search.AbstractIndexManager;
import org.apache.roller.weblogger.config.WebloggerConfig;
import org.lumongo.storage.lucene.DistributedDirectory;
import org.lumongo.storage.lucene.MongoDirectory;

import java.io.IOException;

/**
 * Lucene implementation of IndexManager. This is the central entry point into
 * the Lucene searching API.
 * 
 * @author Mindaugas Idzelis (min@idzelis.com)
 * @author mraible (formatting and making indexDir configurable)
 */
@com.google.inject.Singleton
public class MongoIndexManager extends AbstractIndexManager {

    private DB db;

    private static final String DATABASE = WebloggerConfig.getProperty("mongo.db");

    static Log mLogger = LogFactory.getFactory().getInstance(
            MongoIndexManager.class);

    /**
     * Creates a new lucene index manager. This should only be created once.
     * Creating the index manager more than once will definately result in
     * errors. The preferred way of getting an index is through the
     * RollerContext.
     *
     * @param roller - the weblogger instance
     */
    @com.google.inject.Inject
    protected MongoIndexManager(Weblogger roller) {
        super(roller);

        db = MongoUtil.getMongoClient().getDB(DATABASE);

        mLogger.info("Created MongoIndexManager");

    }

    @Override
    protected boolean isInconsistencyMarkerSet() {
        return db.collectionExists("INCONSISTENT");
    }

    @Override
    protected void setInconsistencyMarker() {
        db.createCollection("INCONSISTENT", null);
    }

    @Override
    protected void clearInconsistencyMarker() {
        db.getCollection("INCONSISTENT").drop();
    }

    @Override
    protected boolean indexStorageExists() {
        return db.collectionExists("INDEX");
    }

    @Override
    protected void createIndexStorage() {
        db.createCollection("INDEX", null);
    }

    @Override
    protected Directory getStorageDirectory(boolean delete) {
        try {
            return new DistributedDirectory(new MongoDirectory(MongoUtil.getMongoClient(), DATABASE, "INDEX"));
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Error creating Mongo-backed MapDirectory for Lucene", e);
        }
    }
}
