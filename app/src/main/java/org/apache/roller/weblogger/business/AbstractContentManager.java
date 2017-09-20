package org.apache.roller.weblogger.business;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.util.RollerConstants;
import org.apache.roller.weblogger.config.WebloggerRuntimeConfig;
import org.apache.roller.weblogger.pojos.Weblog;
import org.apache.roller.weblogger.util.RollerMessages;

import java.io.File;
import java.math.BigDecimal;

/**
 * Created by pivotal on 2017-09-19.
 */
public abstract class AbstractContentManager implements FileContentManager {


    private static Log log = LogFactory.getLog(AbstractContentManager.class);

    /**
     * @see org.apache.roller.weblogger.business.FileContentManager#overQuota(Weblog)
     */
    public boolean overQuota(Weblog weblog) {

        long maxDirBytes = getQuotaBytes();

        try {
            long weblogDirSize = getUsedBytes(weblog);

            return weblogDirSize > maxDirBytes;
        } catch (Exception ex) {
            // shouldn't ever happen, this means user's uploads dir is bad
            // rethrow as a runtime exception
            throw new RuntimeException(ex);
        }
    }

    protected abstract long getUsedBytes(Weblog weblog) throws FileNotFoundException, FilePathException;

    protected long getQuotaBytes() {
        String maxDir = WebloggerRuntimeConfig
                .getProperty("uploads.dir.maxsize");

        // maxDirSize in megabytes
        BigDecimal maxDirSize = new BigDecimal(maxDir);

        return (long) (RollerConstants.ONE_MB_IN_BYTES * maxDirSize
                .doubleValue());
    }



    /**
     * @see org.apache.roller.weblogger.business.FileContentManager#canSave(Weblog,
     *      String, String, long, RollerMessages)
     */
    public boolean canSave(Weblog weblog, String fileName, String contentType,
                           long size, RollerMessages messages) {

        // first check, is uploading enabled?
        if (!WebloggerRuntimeConfig.getBooleanProperty("uploads.enabled")) {
            messages.addError("error.upload.disabled");
            return false;
        }

        // second check, does upload exceed max size for file?
        BigDecimal maxFileMB = new BigDecimal(
                WebloggerRuntimeConfig.getProperty("uploads.file.maxsize"));
        int maxFileBytes = (int) (RollerConstants.ONE_MB_IN_BYTES * maxFileMB
                .doubleValue());
        log.debug("max allowed file size = " + maxFileBytes);
        log.debug("attempted save file size = " + size);
        if (size > maxFileBytes) {
            String[] args = { fileName, maxFileMB.toString() };
            messages.addError("error.upload.filemax", args);
            return false;
        }

        // third check, does file cause weblog to exceed quota?
        BigDecimal maxDirMB = new BigDecimal(
                WebloggerRuntimeConfig.getProperty("uploads.dir.maxsize"));
        long maxDirBytes = getQuotaBytes();
        try {
            long userDirSize = getUsedBytes(weblog);
            if (userDirSize + size > maxDirBytes) {
                messages.addError("error.upload.dirmax", maxDirMB.toString());
                return false;
            }
        } catch (Exception ex) {
            // shouldn't ever happen, means the weblogs uploads dir is bad
            // somehow
            // rethrow as a runtime exception
            throw new RuntimeException(ex);
        }

        // fourth check, is upload type allowed?
        String allows = WebloggerRuntimeConfig
                .getProperty("uploads.types.allowed");
        String forbids = WebloggerRuntimeConfig
                .getProperty("uploads.types.forbid");
        String[] allowFiles = StringUtils.split(
                StringUtils.deleteWhitespace(allows), ",");
        String[] forbidFiles = StringUtils.split(
                StringUtils.deleteWhitespace(forbids), ",");
        if (!checkFileType(allowFiles, forbidFiles, fileName, contentType)) {
            String[] args = { fileName, contentType };
            messages.addError("error.upload.forbiddenFile", args);
            return false;
        }

        return true;
    }

    /**
     * Return true if file is allowed to be uplaoded given specified allowed and
     * forbidden file types.
     */
    private boolean checkFileType(String[] allowFiles, String[] forbidFiles,
                                  String fileName, String contentType) {

        // TODO: Atom Publishing Protocol figure out how to handle file
        // allow/forbid using contentType.
        // TEMPORARY SOLUTION: In the allow/forbid lists we will continue to
        // allow user to specify file extensions (e.g. gif, png, jpeg) but will
        // now also allow them to specify content-type rules (e.g. */*, image/*,
        // text/xml, etc.).

        // if content type is invalid, reject file
        if (contentType == null || contentType.indexOf('/') == -1) {
            return false;
        }

        // default to false
        boolean allowFile = false;

        // if this person hasn't listed any allows, then assume they want
        // to allow *all* filetypes, except those listed under forbid
        if (allowFiles == null || allowFiles.length < 1) {
            allowFile = true;
        }

        // First check against what is ALLOWED

        // check file against allowed file extensions
        if (allowFiles != null && allowFiles.length > 0) {
            for (int y = 0; y < allowFiles.length; y++) {
                // oops, this allowed rule is a content-type, skip it
                if (allowFiles[y].indexOf('/') != -1) {
                    continue;
                }
                if (fileName.toLowerCase()
                        .endsWith(allowFiles[y].toLowerCase())) {
                    allowFile = true;
                    break;
                }
            }
        }

        // check file against allowed contentTypes
        if (allowFiles != null && allowFiles.length > 0) {
            for (int y = 0; y < allowFiles.length; y++) {
                // oops, this allowed rule is NOT a content-type, skip it
                if (allowFiles[y].indexOf('/') == -1) {
                    continue;
                }
                if (matchContentType(allowFiles[y], contentType)) {
                    allowFile = true;
                    break;
                }
            }
        }

        // First check against what is FORBIDDEN

        // check file against forbidden file extensions, overrides any allows
        if (forbidFiles != null && forbidFiles.length > 0) {
            for (int x = 0; x < forbidFiles.length; x++) {
                // oops, this forbid rule is a content-type, skip it
                if (forbidFiles[x].indexOf('/') != -1) {
                    continue;
                }
                if (fileName.toLowerCase().endsWith(
                        forbidFiles[x].toLowerCase())) {
                    allowFile = false;
                    break;
                }
            }
        }

        // check file against forbidden contentTypes, overrides any allows
        if (forbidFiles != null && forbidFiles.length > 0) {
            for (int x = 0; x < forbidFiles.length; x++) {
                // oops, this forbid rule is NOT a content-type, skip it
                if (forbidFiles[x].indexOf('/') == -1) {
                    continue;
                }
                if (matchContentType(forbidFiles[x], contentType)) {
                    allowFile = false;
                    break;
                }
            }
        }

        return allowFile;
    }

    /**
     * Super simple contentType range rule matching
     */
    private boolean matchContentType(String rangeRule, String contentType) {
        if (rangeRule.equals("*/*")) {
            return true;
        }
        if (rangeRule.equals(contentType)) {
            return true;
        }
        String ruleParts[] = rangeRule.split("/");
        String typeParts[] = contentType.split("/");
        if (ruleParts[0].equals(typeParts[0]) && ruleParts[1].equals("*")) {
            return true;
        }

        return false;
    }
}
