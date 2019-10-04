/******************************************************************************
 *                                                                            *
 *  Copyright 2019 Tyler Cook (https://github.com/cilki)                      *
 *                                                                            *
 *  Licensed under the Apache License, Version 2.0 (the "License");           *
 *  you may not use this file except in compliance with the License.          *
 *  You may obtain a copy of the License at                                   *
 *                                                                            *
 *      http://www.apache.org/licenses/LICENSE-2.0                            *
 *                                                                            *
 *  Unless required by applicable law or agreed to in writing, software       *
 *  distributed under the License is distributed on an "AS IS" BASIS,         *
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  *
 *  See the License for the specific language governing permissions and       *
 *  limitations under the License.                                            *
 *                                                                            *
 *****************************************************************************/
package com.github.cilki.compact;

import java.io.File;
import java.net.URL;
import java.util.jar.JarFile;

/**
 * This class loads the application's main class with a new
 * {@link CompactClassLoader} and invokes its main method. The default thread
 * context loader will be replaced with the new classloader.
 * 
 * <p>
 * To use this class, copy the existing {@code Main-Class} manifest attribute to
 * {@code Boot-Class} and set {@code Main-Class} to
 * "com.github.cilki.compact.BootProxy". Set {@code Compact-Class-Path} to the
 * nested class path archives.
 * 
 * @author cilki
 */
public final class BootProxy {

	public static void main(String[] args) throws Exception {
		CompactClassLoader loader;

		if (ClassLoader.getSystemClassLoader() instanceof CompactClassLoader) {
			loader = (CompactClassLoader) ClassLoader.getSystemClassLoader();
		} else {
			// Set the parent classloader to platform rather than system to avoid duplicate
			// classpath entries from the main jar
			loader = new CompactClassLoader(ClassLoader.getPlatformClassLoader());
		}

		String main = null;
		File file = new File(BootProxy.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		try (JarFile jar = new JarFile(file)) {
			var manifest = jar.getManifest().getMainAttributes();

			// Find boot class
			main = manifest.getValue("Boot-Class");
			if (main == null)
				throw new RuntimeException("Missing Boot-Class attribute");

			// Add root jar
			loader.add(file.toURI().toURL(), false);

			// Add classpath entries
			var classPath = manifest.getValue("Compact-Class-Path");
			if (classPath != null) {
				for (var entry : classPath.split(" ")) {
					loader.add(new URL(String.format("%s!/%s", file.toURI().toURL(), entry)), false);
				}
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to read manifest", e);
		}

		// Set the context loader so all future threads receive the new classloader
		Thread.currentThread().setContextClassLoader(loader);

		// Invoke the application's main method
		loader.loadDown(main, null).getMethod("main", new Class[] { String[].class }).invoke(null,
				new Object[] { args });
	}
}
