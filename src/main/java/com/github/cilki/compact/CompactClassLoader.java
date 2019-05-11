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

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.stream.Collectors;

/**
 * A lazy {@link ClassLoader} that can load nested jar files and bootstrap
 * applications.<br>
 * <br>
 * Note: This class is NOT thread safe.
 * 
 * @author cilki
 */
public final class CompactClassLoader extends ClassLoader {

	static {
		registerAsParallelCapable();
	}

	/**
	 * A list of classloaders responsible for loading {@link URL}s introduced by
	 * {@link #add(URL)}.
	 */
	private final List<NodeClassLoader> components;

	/**
	 * Build an empty {@link CompactClassLoader}.
	 * 
	 * @param parent The parent {@link ClassLoader} which may be {@code null}
	 */
	public CompactClassLoader(ClassLoader parent) {
		super(parent);
		components = new LinkedList<>();
	}

	/**
	 * Build a {@link CompactClassLoader} for the current jar file. At minimum,
	 * every top-level jar will be added to the class loader.
	 * 
	 * @param recursive Whether all encountered jars will also be added
	 * @throws IOException
	 */
	public CompactClassLoader(boolean recursive) throws IOException {
		this(ClassLoader.getSystemClassLoader());

		URL parent = getClass().getProtectionDomain().getCodeSource().getLocation();
		try (JarInputStream jar = new JarInputStream(parent.openStream())) {
			JarEntry entry;

			while ((entry = jar.getNextJarEntry()) != null)
				if (entry.getName().endsWith(".jar"))
					add(new URL(parent.toString() + "!/" + entry.getName()), recursive);
		}
	}

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {

		// Shortcut for standard classes
		if (name.startsWith("java"))
			return getSystemClassLoader().loadClass(name);

		// Try components
		for (NodeClassLoader component : components) {
			try {
				return component.loadClass(name);
			} catch (ClassNotFoundException e) {
				// Rethrow if not empty
				if (e.getCause() != null)
					throw e;
			}
		}

		// Delegate to parent
		return super.loadClass(name, resolve);
	}

	@Override
	public URL findResource(String name) {
		return components.stream().flatMap(component -> component.getResourcesStream(name)).findAny().orElse(null);
	}

	@Override
	public Enumeration<URL> findResources(String name) {
		return Collections.enumeration(components.stream().flatMap(component -> component.getResourcesStream(name))
				.collect(Collectors.toList()));
	}

	/**
	 * Add the given {@link URL} to the {@link ClassLoader} as a new component.
	 * 
	 * @param url The {@link URL} to add which may be a jar file or a jar file
	 *            within a jar file
	 * @throws IOException
	 */
	public void add(URL url) throws IOException {
		add(url, true);
	}

	/**
	 * Add the given {@link URL} to the {@link ClassLoader} as a new component.
	 * 
	 * @param url       The {@link URL} to add which may be a jar file or a jar file
	 *                  within a jar file
	 * @param recursive Whether all encountered jars will also be added
	 * @throws IOException
	 */
	public void add(URL url, boolean recursive) throws IOException {
		Objects.requireNonNull(url);
		if (components.stream().anyMatch(c -> c.findBaseUrl(url)))
			throw new IllegalArgumentException("The URL is already has a classloader in the hierarchy");

		components.add(new NodeClassLoader(getParent(), URLFactory.toNested(url), recursive));
	}
}
