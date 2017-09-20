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
import org.apache.roller.weblogger.business.Weblogger;
import org.apache.roller.weblogger.config.WebloggerConfig;
import org.apache.roller.weblogger.pojos.Theme;

import java.io.File;
import java.io.FilenameFilter;
import java.util.HashMap;
import java.util.Map;

/**
 * Base implementation of a ThemeManager.
 * 
 * This particular implementation reads theme data off the filesystem and
 * assumes that those themes are not changeable at runtime.
 */
@com.google.inject.Singleton
public class ThemeManagerImpl extends AbstractThemeManager {

	private static Log log = LogFactory.getLog(ThemeManagerImpl.class);

	// directory where themes are kept
	private String themeDir = null;

	@com.google.inject.Inject
	protected ThemeManagerImpl(Weblogger roller) {
		super(roller);
		this.themeDir = WebloggerConfig.getProperty("themes.dir");
		if (themeDir == null || themeDir.trim().length() < 1) {
			throw new RuntimeException(
					"couldn't get themes directory from config");
		} else {
			// chop off trailing slash if it exists
			if (themeDir.endsWith("/")) {
				themeDir = themeDir.substring(0, themeDir.length() - 1);
			}

			// make sure it exists and is readable
			File themeDirFile = new File(themeDir);
			if (!themeDirFile.exists() || !themeDirFile.isDirectory()
					|| !themeDirFile.canRead()) {
				throw new RuntimeException("couldn't access theme dir ["
						+ themeDir + "]");
			}
		}
	}

	protected Map<String, SharedTheme> loadAllThemesFromStorage() {

		Map<String, SharedTheme> themeMap = new HashMap<String, SharedTheme>();

		// first, get a list of the themes available
		File themesdir = new File(this.themeDir);
		FilenameFilter filter = new FilenameFilter() {

			public boolean accept(File dir, String name) {
				File file = new File(dir.getAbsolutePath() + File.separator
						+ name);
				return file.isDirectory() && !file.getName().startsWith(".");
			}
		};
		String[] themenames = themesdir.list(filter);

		if (themenames == null) {
			log.warn("No themes found!  Perhaps wrong directory for themes specified?  "
					+ "(Check themes.dir setting in roller[-custom].properties file.)");
		} else {
			log.info("Loading themes from " + themesdir.getAbsolutePath() + "...");

			// now go through each theme and load it into a Theme object
			for (String themeName : themenames) {
				try {
					SharedTheme theme = new SharedThemeFromDir(this.themeDir
							+ File.separator + themeName);
					themeMap.put(theme.getId(), theme);
					log.info("Loaded theme '" + themeName + "'");
				} catch (Exception unexpected) {
					// shouldn't happen, so let's learn why it did
					log.error("Problem processing theme '" + themeName + "':", unexpected);
				}
			}
		}

		return themeMap;
	}


	public boolean reLoadThemeFromStorage(String reloadTheme) {

		try {
			SharedTheme theme = new SharedThemeFromDir(this.themeDir + File.separator
					+ reloadTheme);

			return replaceIfNewer(theme);

		} catch (Exception unexpected) {
			// shouldn't happen, so let's learn why it did
			log.error("Problem reloading theme " + reloadTheme, unexpected);
		}

		return false;

	}
}
