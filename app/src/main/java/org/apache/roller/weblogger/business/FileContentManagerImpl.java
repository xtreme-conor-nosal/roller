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

package org.apache.roller.weblogger.business;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.util.RollerConstants;
import org.apache.roller.weblogger.config.WebloggerConfig;
import org.apache.roller.weblogger.config.WebloggerRuntimeConfig;
import org.apache.roller.weblogger.pojos.FileContent;
import org.apache.roller.weblogger.pojos.Weblog;
import org.apache.roller.weblogger.util.RollerMessages;

/**
 * Manages contents of the file uploaded to Roller weblogs.
 * 
 * This base implementation writes file content to a file system.
 */
public class FileContentManagerImpl extends AbstractContentManager {

    private static Log log = LogFactory.getLog(FileContentManagerImpl.class);

    private String storageDir = null;

    /**
     * Create file content manager.
     */
    public FileContentManagerImpl() {

        String inStorageDir = WebloggerConfig
                .getProperty("mediafiles.storage.dir");

        // Note: System property expansion is now handled by WebloggerConfig.

        if (inStorageDir == null || inStorageDir.trim().length() < 1) {
            inStorageDir = System.getProperty("user.home") + File.separator
                    + "roller_data" + File.separator + "mediafiles";
        }

        if (!inStorageDir.endsWith(File.separator)) {
            inStorageDir += File.separator;
        }

        this.storageDir = inStorageDir.replace('/', File.separatorChar);

    }

    public void initialize() {

    }

    /**
     * @see org.apache.roller.weblogger.business.FileContentManager#getFileContent(Weblog,
     *      String)
     */
    public FileContent getFileContent(Weblog weblog, String fileId)
            throws FileNotFoundException, FilePathException {

        // get a reference to the file, checks that file exists & is readable
        File resourceFile = this.getRealFile(weblog, fileId);

        // make sure file is not a directory
        if (resourceFile.isDirectory()) {
            throw new FilePathException("Invalid file id [" + fileId + "], "
                    + "path is a directory.");
        }

        // everything looks good, return resource
        return new FileContent(weblog, fileId, resourceFile);
    }

    /**
     * @see org.apache.roller.weblogger.business.FileContentManager#saveFileContent(Weblog,
     *      String, java.io.InputStream)
     */
    public void saveFileContent(Weblog weblog, String fileId, InputStream is)
            throws FileNotFoundException, FilePathException, FileIOException {

        // make sure uploads area exists for this weblog
        File dirPath = this.getRealFile(weblog, null);

        // create File that we are about to save
        File saveFile = new File(dirPath.getAbsolutePath() + File.separator
                + fileId);

        byte[] buffer = new byte[RollerConstants.EIGHT_KB_IN_BYTES];
        int bytesRead;
        OutputStream bos = null;
        try {
            bos = new FileOutputStream(saveFile);
            while ((bytesRead = is.read(buffer, 0,
                    RollerConstants.EIGHT_KB_IN_BYTES)) != -1) {
                bos.write(buffer, 0, bytesRead);
            }
            log.debug("The file has been written to ["
                    + saveFile.getAbsolutePath() + "]");
        } catch (Exception e) {
            throw new FileIOException("ERROR uploading file", e);
        } finally {
            try {
                if (bos != null) {
                    bos.flush();
                    bos.close();
                }
            } catch (Exception ignored) {
            }
        }

    }

    /**
     * @see org.apache.roller.weblogger.business.FileContentManager#deleteFile(Weblog,
     *      String)
     */
    public void deleteFile(Weblog weblog, String fileId)
            throws FileNotFoundException, FilePathException, FileIOException {

        // get path to delete file, checks that path exists and is readable
        File delFile = this.getRealFile(weblog, fileId);

        if (!delFile.delete()) {
            log.warn("Delete appears to have failed for [" + fileId + "]");
        }
    }

    /**
     * @inheritDoc
     */
    public void deleteAllFiles(Weblog weblog) throws FileIOException {
        // TODO: Implement
    }


    protected long getUsedBytes(Weblog weblog) throws FileNotFoundException, FilePathException {
        File storageDirectory = this.getRealFile(weblog, null);
        return this.getDirSize(storageDirectory, true);
    }

    public void release() {
    }

    /**
     * Get the size in bytes of given directory.
     *
     * Optionally works recursively counting subdirectories if they exist.
     */
    private long getDirSize(File dir, boolean recurse) {

        long size = 0;

        if (dir.exists() && dir.isDirectory() && dir.canRead()) {
            long dirSize = 0l;
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!file.isDirectory()) {
                        dirSize += file.length();
                    } else if (recurse) {
                        // count a subdirectory
                        dirSize += getDirSize(file, recurse);
                    }
                }
            }
            size += dirSize;
        }

        return size;
    }



    /**
     * Construct the full real path to a resource in a weblog's uploads area.
     */
    private File getRealFile(Weblog weblog, String fileId)
            throws FileNotFoundException, FilePathException {

        // make sure uploads area exists for this weblog
        File weblogDir = new File(this.storageDir + weblog.getHandle());
        if (!weblogDir.exists()) {
            weblogDir.mkdirs();
        }

        // now form the absolute path
        String filePath = weblogDir.getAbsolutePath();
        if (fileId != null) {
            filePath += File.separator + fileId;
        }

        // make sure path exists and is readable
        File file = new File(filePath);
        if (!file.exists()) {
            throw new FileNotFoundException("Invalid path [" + filePath + "], "
                    + "file does not exist.");
        } else if (!file.canRead()) {
            throw new FilePathException("Invalid path [" + filePath + "], "
                    + "cannot read from path.");
        }

        try {
            // make sure someone isn't trying to sneek outside the uploads dir
            if (!file.getCanonicalPath().startsWith(
                    weblogDir.getCanonicalPath())) {
                throw new FilePathException("Invalid path " + filePath + "], "
                        + "trying to get outside uploads dir.");
            }
        } catch (IOException ex) {
            // rethrow as FilePathException
            throw new FilePathException(ex);
        }

        return file;
    }

}
