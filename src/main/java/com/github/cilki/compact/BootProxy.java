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

import java.io.InputStream;
import java.util.jar.Manifest;

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
		String main = null;
		try (InputStream in = BootProxy.class.getResourceAsStream("/META-INF/MANIFEST.MF")) {
			main = new Manifest(in).getMainAttributes().getValue("Boot-Class");
		}

		if (main == null)
			throw new RuntimeException("Missing Boot-Class attribute");

		CompactClassLoader loader = new CompactClassLoader(true);
		Thread.currentThread().setContextClassLoader(loader);

		loader.loadClass(main).getMethod("main", new Class[] { String[].class }).invoke(null, new Object[] { args });
	}
}
