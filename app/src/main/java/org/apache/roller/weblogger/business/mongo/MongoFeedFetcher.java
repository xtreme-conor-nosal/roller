package org.apache.roller.weblogger.business.mongo;

import com.mongodb.*;
import com.rometools.fetcher.impl.FeedFetcherCache;
import com.rometools.fetcher.impl.SyndFeedInfo;
import org.apache.roller.weblogger.config.WebloggerConfig;

import java.net.URL;

/**
 * Created by pivotal on 2017-09-19.
 */
public class MongoFeedFetcher implements FeedFetcherCache {

    private static MongoFeedFetcher instance;

    public synchronized static MongoFeedFetcher getInstance() {
        if (instance == null) {
            instance = new MongoFeedFetcher();
        }
        return instance;
    }

    private final MongoClient client = MongoUtil.getMongoClient();
    private static final String DATABASE = WebloggerConfig.getProperty("mongo.db");

    @Override
    public SyndFeedInfo getFeedInfo(URL url) {
        DB db = client.getDB(DATABASE);
        DBCollection collection = db.getCollection("FEED");

        DBObject query = new BasicDBObject("_id", url.toString());

        DBObject result = collection.findOne(query);
        if (result == null) {
            return null;
        } else {
            return (SyndFeedInfo) (result).get("feedInfo");
        }
    }

    @Override
    public void setFeedInfo(URL url, SyndFeedInfo syndFeedInfo) {
        DB db = client.getDB(DATABASE);
        DBCollection collection = db.getCollection("FEED");

        DBObject feedInfoObject = new BasicDBObject("_id", url.toString()).append("feedInfo", syndFeedInfo);
        collection.insert(feedInfoObject);
    }

    @Override
    public void clear() {
        DB db = client.getDB(DATABASE);
        DBCollection collection = db.getCollection("FEED");
        collection.remove(new BasicDBObject());
    }

    @Override
    public SyndFeedInfo remove(URL url) {
        SyndFeedInfo info = getFeedInfo(url);
        if (info != null) {
            DB db = client.getDB(DATABASE);
            DBCollection collection = db.getCollection("FEED");
            collection.remove(new BasicDBObject("_id", url.toString()));
        }
        return info;
    }
}
