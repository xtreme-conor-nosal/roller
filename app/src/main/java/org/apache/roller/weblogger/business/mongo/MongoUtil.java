package org.apache.roller.weblogger.business.mongo;

import com.mongodb.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.weblogger.config.WebloggerConfig;

import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * Created by pivotal on 2017-09-19.
 */
public class MongoUtil {

    private static MongoClient instance;

    public static synchronized MongoClient getMongoClient() {
        if (!hasUri()) {
            throw new RuntimeException("mongo.uri not set");
        }
        if (instance == null) {
                String property = WebloggerConfig.getProperty("mongo.uri");
                MongoClientURI uri = new MongoClientURI(property);

                LogFactory.getLog(MongoUtil.class).info("Initializing mongo client: " + uri.getUsername() + " " + uri.getHosts().get(0) + " " + uri.getDatabase());
                instance = new MongoClient(uri);
        }
        return instance;
    }

    public static boolean hasUri() {
        String property = WebloggerConfig.getProperty("mongo.uri");
        return property != null && !property.isEmpty();
    }
}
