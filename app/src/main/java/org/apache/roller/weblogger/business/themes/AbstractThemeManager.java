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
package org.apache.roller.weblogger.business.themes;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.business.*;
import org.apache.roller.weblogger.pojos.*;
import org.apache.roller.weblogger.pojos.TemplateRendition.RenditionType;
import org.apache.roller.weblogger.pojos.ThemeTemplate.ComponentType;
import org.apache.roller.weblogger.util.RollerMessages;

import javax.activation.FileTypeMap;
import javax.activation.MimetypesFileTypeMap;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Base implementation of a ThemeManager.
 * 
 * This particular implementation reads theme data off the filesystem and
 * assumes that those themes are not changeable at runtime.
 */
public abstract class AbstractThemeManager implements ThemeManager {

	static FileTypeMap map = null;
	static {
		// TODO: figure out why PNG is missing from Java MIME types
		map = FileTypeMap.getDefaultFileTypeMap();
		if (map instanceof MimetypesFileTypeMap) {
			try {
				((MimetypesFileTypeMap) map).addMimeTypes("image/png png PNG");
			} catch (Exception ignored) {
			}
		}
	}

	private static Log log = LogFactory.getLog(AbstractThemeManager.class);
	private final Weblogger roller;
	// the Map contains ... (theme id, Theme)
	protected Map<String, SharedTheme> themes = null;

	protected AbstractThemeManager(Weblogger roller) {

		this.roller = roller;
	}

	public void initialize() throws InitializationException {

		log.debug("Initializing Theme Manager");

		// rather than be lazy we are going to load all themes from
		// the disk preemptive and cache them
		this.themes = loadAllThemesFromStorage();

		log.info("Successfully loaded " + this.themes.size() + " themes from disk.");
	}

	/**
	 * @see ThemeManager#getTheme(String)
	 */
	public SharedTheme getTheme(String id) throws WebloggerException {

		// try to lookup theme from library
		SharedTheme theme = this.themes.get(id);

		// no theme? throw exception.
		if (theme == null) {
			throw new ThemeNotFoundException("Couldn't find theme [" + id + "]");
		}

		return theme;
	}

	/**
	 * @see ThemeManager#getTheme(Weblog)
	 */
	public WeblogTheme getTheme(Weblog weblog) throws WebloggerException {

		if (weblog == null) {
			return null;
		}

		WeblogTheme weblogTheme = null;

		// if theme is custom or null then return a WeblogCustomTheme
		if (weblog.getEditorTheme() == null
				|| WeblogTheme.CUSTOM.equals(weblog.getEditorTheme())) {
			log.debug("Custom theme for weblog " + weblog.getName());
			weblogTheme = new WeblogCustomTheme(weblog);

			// otherwise we are returning a WeblogSharedTheme
		} else {
			SharedTheme staticTheme = this.themes.get(weblog
					.getEditorTheme());
			if (staticTheme != null) {

				log.debug("Using shared theme " + staticTheme.getName() + "for weblog " + weblog.getName());
				weblogTheme = new WeblogSharedTheme(weblog, staticTheme);
			} else {
				log.warn("Unable to lookup theme " + weblog.getEditorTheme());
			}
		}

		if (weblogTheme == null) {
			log.warn("No theme for weblog " + weblog.getName());
		}

		// TODO: if somehow the theme is still null should we provide some
		// kind of fallback option like a default theme?

		return weblogTheme;
	}

	/**
	 * @see ThemeManager#getEnabledThemesList()
	 */
	public List<SharedTheme> getEnabledThemesList() {
		List<SharedTheme> allThemes = new ArrayList<SharedTheme>(this.themes.values());

		// sort 'em ... default ordering for themes is by name
		Collections.sort(allThemes);

		return allThemes;
	}

	/**
	 * @see ThemeManager#importTheme(Weblog,
	 *      SharedTheme, boolean)
	 */
	public void importTheme(Weblog weblog, SharedTheme theme, boolean skipStylesheet)
			throws WebloggerException {

		log.debug("Importing theme [" + theme.getName() + "] to weblog ["
				+ weblog.getName() + "]");

		WeblogManager wmgr = roller.getWeblogManager();
		MediaFileManager fileMgr = roller.getMediaFileManager();

		MediaFileDirectory root = fileMgr.getDefaultMediaFileDirectory(weblog);
        if (root == null) {
            log.warn("Weblog " + weblog.getHandle() + " does not have a root MediaFile directory");
        }

		Set<ComponentType> importedActionTemplates = new HashSet<ComponentType>();
		ThemeTemplate stylesheetTemplate = theme.getStylesheet();
		for (ThemeTemplate themeTemplate : theme.getTemplates()) {
			WeblogTemplate template;

			// if template is an action, lookup by action
			if (themeTemplate.getAction() != null
					&& !themeTemplate.getAction().equals(ComponentType.CUSTOM)) {
				importedActionTemplates.add(themeTemplate.getAction());
				template = wmgr.getTemplateByAction(weblog,
                        themeTemplate.getAction());

				// otherwise, lookup by name
			} else {
				template = wmgr.getTemplateByName(weblog, themeTemplate.getName());
			}

			// Weblog does not have this template, so create it.
			boolean newTmpl = false;
			if (template == null) {
				template = new WeblogTemplate();
				template.setWeblog(weblog);
				newTmpl = true;
			}

			// update template attributes except leave existing custom stylesheets as-is
			if (!themeTemplate.equals(stylesheetTemplate) || !skipStylesheet) {
				template.setAction(themeTemplate.getAction());
				template.setName(themeTemplate.getName());
				template.setDescription(themeTemplate.getDescription());
				template.setLink(themeTemplate.getLink());
				template.setHidden(themeTemplate.isHidden());
				template.setNavbar(themeTemplate.isNavbar());
                template.setOutputContentType(themeTemplate.getOutputContentType());
				template.setLastModified(new Date());

				// save it
				wmgr.saveTemplate(template);

                // create weblog template code objects and save them
                for (RenditionType type : RenditionType.values()) {

                    // See if we already have some code for this template already (eg previous theme)
                    CustomTemplateRendition weblogTemplateCode = template.getTemplateRendition(type);

                    // Get the template for the new theme
                    TemplateRendition templateCode = themeTemplate.getTemplateRendition(type);
                    if (templateCode != null) {

                        // Check for existing template
                        if (weblogTemplateCode == null) {
                            // Does not exist so create a new one
                            weblogTemplateCode = new CustomTemplateRendition(template, type);
                        }
                        weblogTemplateCode.setType(type);
                        weblogTemplateCode.setTemplate(templateCode.getTemplate());
                        weblogTemplateCode.setTemplateLanguage(templateCode
                                .getTemplateLanguage());
                        WebloggerFactory.getWeblogger().getWeblogManager()
                                .saveTemplateRendition(weblogTemplateCode);
                    }

                }
            }
		}

		// now, see if the weblog has left over non-custom action templates that
		// need to be deleted because they aren't in their new theme
        for (ComponentType action : ComponentType.values()) {
            if (action == ComponentType.CUSTOM) {
                continue;
            }
			// if we didn't import this action then see if it should be deleted
			if (!importedActionTemplates.contains(action)) {
				WeblogTemplate toDelete = wmgr.getTemplateByAction(weblog, action);
				if (toDelete != null) {
					log.debug("Removing stale action template " + toDelete.getId());
					wmgr.removeTemplate(toDelete);
				}
			}
		}

		// set weblog's theme to custom, then save
		weblog.setEditorTheme(WeblogTheme.CUSTOM);
		wmgr.saveWeblog(weblog);

		// now lets import all the theme resources
        for (ThemeResource resource : theme.getResources()) {

			log.debug("Importing resource " + resource.getPath());

			if (resource.isDirectory()) {
				MediaFileDirectory mdir = fileMgr.getMediaFileDirectoryByName(
						weblog, resource.getPath());
				if (mdir == null) {
					log.debug("    Creating directory: " + resource.getPath());
					fileMgr.createMediaFileDirectory(weblog, resource.getPath());
					roller.flush();
				} else {
					log.debug("    No action: directory already exists");
				}

			} else {
				String resourcePath = resource.getPath();

				MediaFileDirectory mdir;
				String justName;
				String justPath;

				if (resourcePath.indexOf('/') == -1) {
					mdir = fileMgr.getDefaultMediaFileDirectory(weblog);
					justPath = "";
					justName = resourcePath;

				} else {
					justPath = resourcePath.substring(0,
							resourcePath.lastIndexOf('/'));
					if (!justPath.startsWith("/")) {
                        justPath = "/" + justPath;
                    }
					justName = resourcePath.substring(resourcePath
							.lastIndexOf('/') + 1);
					mdir = fileMgr.getMediaFileDirectoryByName(weblog,
							justPath);
					if (mdir == null) {
						log.debug("    Creating directory: " + justPath);
						mdir = fileMgr.createMediaFileDirectory(weblog,
								justPath);
						roller.flush();
					}
				}

				MediaFile oldmf = fileMgr.getMediaFileByOriginalPath(weblog,
						justPath + "/" + justName);
				if (oldmf != null) {
					fileMgr.removeMediaFile(weblog, oldmf);
				}

				// save file without file-type, quota checks, etc.
				InputStream is = resource.getInputStream();
				MediaFile mf = new MediaFile();
				mf.setDirectory(mdir);
				mf.setWeblog(weblog);
				mf.setName(justName);
				mf.setOriginalPath(justPath + "/" + justName);
				mf.setContentType(map.getContentType(justName));
				mf.setInputStream(is);
				mf.setLength(resource.getLength());

				log.debug("    Saving file: " + justName);
				log.debug("    Saving in directory = " + mf.getDirectory());
				RollerMessages errors = new RollerMessages();
				fileMgr.createMediaFile(weblog, mf, errors);
				try {
					resource.getInputStream().close();
				} catch (IOException ex) {
					errors.addError("error.closingStream");
					log.debug("ERROR closing inputstream");
				}
				if (errors.getErrorCount() > 0) {
					throw new WebloggerException(errors.toString());
				}
				roller.flush();
			}
		}
	}

	/**
	 * This is a convenience method which loads all the theme data from themes
	 * stored on the filesystem in the roller webapp /themes/ directory.
	 */
	protected abstract Map<String, SharedTheme> loadAllThemesFromStorage();

	/**
	 * @see ThemeManager#reLoadThemeFromStorage(String)
	 */
	public abstract boolean reLoadThemeFromStorage(String reloadTheme);

	protected boolean replaceIfNewer(SharedTheme theme) {
		Theme loadedTheme = themes.get(theme.getId());

		if (loadedTheme != null
				&& theme.getLastModified().after(
				loadedTheme.getLastModified())) {
			themes.remove(theme.getId());
			themes.put(theme.getId(), theme);
			return true;
		}
		return false;
	}
}
