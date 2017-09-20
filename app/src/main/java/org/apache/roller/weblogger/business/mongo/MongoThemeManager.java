package org.apache.roller.weblogger.business.mongo;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.weblogger.business.Weblogger;
import org.apache.roller.weblogger.business.themes.AbstractThemeManager;
import org.apache.roller.weblogger.business.themes.SharedTheme;
import org.apache.roller.weblogger.config.WebloggerConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by pivotal on 2017-09-20.
 */
public class MongoThemeManager extends AbstractThemeManager {

    private DB db;

    private static final String DATABASE = WebloggerConfig.getProperty("mongo.db");

    static Log mLogger = LogFactory.getFactory().getInstance(
            MongoThemeManager.class);

    protected MongoThemeManager(Weblogger roller) {
        super(roller);
        db = MongoUtil.getMongoClient().getDB(DATABASE);
        db.createCollection("THEMES", null);
    }

    @Override
    protected Map<String, SharedTheme> loadAllThemesFromStorage() {
        Map<String, SharedTheme> themeMap = new HashMap<>();
        DBCollection themes = db.getCollection("THEMES");
        for (DBObject theme : themes.find()) {
            SharedTheme st = (SharedTheme) theme.get("THEME");
            String id = (String) theme.get("_id");
            themeMap.put(id, st);
        }
        return themeMap;
    }

    @Override
    public boolean reLoadThemeFromStorage(String reloadTheme) {
        DBCollection themes = db.getCollection("THEMES");
        DBObject query = new BasicDBObject("_id", reloadTheme);
        DBObject theme = themes.findOne(query);
        if (theme != null) {
            SharedTheme st = (SharedTheme) theme.get("THEME");
            return replaceIfNewer(st);
        }
        return false;
    }
}
