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

import static com.github.cilki.compact.CompactClassLoader.log;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.jar.JarFile;

/**
 * This class loads the application's main class with a new recursive
 * {@link CompactClassLoader} and invokes its main method. To use it, copy the
 * existing {@code Main-Class} attribute to {@code Boot-Class} and set
 * {@code Main-Class} to "com.github.cilki.compact.BootProxy".
 * 
 * @author cilki
 */
public final class BootProxy {

	public static void main(String[] args) throws Exception {
		CompactClassLoader loader = new CompactClassLoader(BootProxy.class.getClassLoader());
		Thread.currentThread().setContextClassLoader(loader);

		String main = null;
		File file = new File(BootProxy.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		try (JarFile jar = new JarFile(file)) {
			// Find boot class
			main = jar.getManifest().getMainAttributes().getValue("Boot-Class");

			// Load jar entries
			jar.stream().map(entry -> entry.getName()).filter(name -> name.endsWith(".jar")).forEach(name -> {
				try {
					System.out.println("Adding: " + new URL(String.format("file:%s!/%s", file.getAbsolutePath(), name)));
					loader.add(new URL(String.format("file:%s!/%s", file.getAbsolutePath(), name)), false);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			});
		} catch (Exception e) {
			throw new RuntimeException("Failed to read manifest", e);
		}

		if (main == null)
			throw new RuntimeException("Missing Boot-Class attribute");

		log.fine("Booting application: " + main);
		loader.loadClass(main).getMethod("main", new Class[] { String[].class }).invoke(null, new Object[] { args });
	}
}
