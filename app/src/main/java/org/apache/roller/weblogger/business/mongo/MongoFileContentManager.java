package org.apache.roller.weblogger.business.mongo;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.MongoClient;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSDBFile;
import org.apache.roller.weblogger.business.*;
import org.apache.roller.weblogger.config.WebloggerConfig;
import org.apache.roller.weblogger.pojos.FileContent;
import org.apache.roller.weblogger.pojos.Weblog;
import org.apache.roller.weblogger.util.RollerMessages;

import java.io.InputStream;

/**
 * Created by pivotal on 2017-09-19.
 */
public class MongoFileContentManager extends AbstractContentManager {

    private final MongoClient client = MongoUtil.getMongoClient();
    private static MongoFileContentManager instance;
    private static final String DATABASE = WebloggerConfig.getProperty("mongo.db");

    public static synchronized MongoFileContentManager getInstance() {
        if (instance == null) {
            instance = new MongoFileContentManager();
        }
        return instance;
    }

    @Override
    protected long getUsedBytes(Weblog weblog) throws FileNotFoundException, FilePathException {
        DB db = client.getDB(DATABASE);
        DBCollection collection = db.getCollection(weblog.getId());
        return (long) collection.getStats().get("storageSize");
    }

    @Override
    public FileContent getFileContent(Weblog weblog, String fileId) throws FileNotFoundException, FilePathException {
        DB db = client.getDB(DATABASE);
        GridFS gridFS = new GridFS(db, weblog.getId());
        GridFSDBFile file = gridFS.findOne(fileId);
        return new FileContent(weblog, fileId, file.getUploadDate().getTime(), file.getLength(), file.getInputStream());
    }

    @Override
    public void saveFileContent(Weblog weblog, String fileId, InputStream is) throws FileNotFoundException, FilePathException, FileIOException {
        DB db = client.getDB(DATABASE);
        GridFS gridFS = new GridFS(db, weblog.getId());
        gridFS.createFile(is, fileId);
    }

    @Override
    public void deleteFile(Weblog weblog, String fileId) throws FileNotFoundException, FilePathException, FileIOException {
        DB db = client.getDB(DATABASE);
        GridFS gridFS = new GridFS(db, weblog.getId());
        gridFS.remove(fileId);
    }

    @Override
    public void deleteAllFiles(Weblog weblog) throws FileIOException {
        DB db = client.getDB(DATABASE);
        GridFS gridFS = new GridFS(db, weblog.getId());
        gridFS.remove(new BasicDBObject());
    }

    @Override
    public void release() {

    }
}
