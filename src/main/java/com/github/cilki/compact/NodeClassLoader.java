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
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * A recursive {@link ClassLoader} for some jar file that could be nested.
 * 
 * @author cilki
 */
final class NodeClassLoader extends ClassLoader {

	/**
	 * A map of all resource locations in the corresponding jar.
	 */
	private final Map<String, URL> resources;

	/**
	 * A cache of {@link Class} objects loaded by this {@link ClassLoader}.
	 */
	private final Map<String, Class<?>> classCache;

	/**
	 * A list of {@link ClassLoader}s for jars within the corresponding jar.
	 */
	private final List<NodeClassLoader> children;

	/**
	 * The {@link URL} of the jar that corresponds to this {@link ClassLoader}.
	 */
	private final URL base;

	/**
	 * Build a new root {@link NodeClassLoader}.
	 * 
	 * @param parent    The parent {@link ClassLoader}
	 * @param url       The {@link URL} to the {@link ClassLoader}'s jar
	 * @param recursive Whether all encountered jars will become children of this
	 *                  {@link ClassLoader}
	 * @throws IOException
	 */
	public NodeClassLoader(ClassLoader parent, URL url, boolean recursive) throws IOException {
		super(parent);

		this.base = Objects.requireNonNull(url);
		this.resources = new HashMap<>();
		this.classCache = new HashMap<>();
		this.children = recursive ? new LinkedList<>() : Collections.emptyList();

		try (ZipInputStream in = new ZipInputStream(url.openStream())) {
			init(url, in, recursive);
		}
	}

	/**
	 * Build a new non-root {@link NodeClassLoader}.
	 * 
	 * @param parent    The parent {@link ClassLoader}
	 * @param url       The {@link URL} to the {@link ClassLoader}'s jar
	 * @param in        An {@link InputStream} to {@code url} that is left open for
	 *                  efficiency during the initial load
	 * @param recursive Whether all encountered jars will become children of this
	 *                  {@link ClassLoader}
	 * @throws IOException
	 */
	private NodeClassLoader(NodeClassLoader parent, URL url, ZipInputStream in, boolean recursive) throws IOException {
		super(parent);

		this.base = Objects.requireNonNull(url);
		this.resources = new HashMap<>();
		this.classCache = new HashMap<>();
		this.children = recursive ? new LinkedList<>() : Collections.emptyList();

		init(url, in, recursive);
	}

	/**
	 * Initialize the {@link ClassLoader} with the contents of the corresponding
	 * jar.
	 * 
	 * @param url       The {@link URL} to the {@link ClassLoader}'s jar
	 * @param jar       An {@link InputStream} to {@code url} that is left open for
	 *                  efficiency during the initial load
	 * @param recursive Whether all encountered jars will become children of this
	 *                  {@link ClassLoader}
	 * @throws IOException
	 */
	private void init(URL url, ZipInputStream jar, boolean recursive) throws IOException {
		int position = 0;
		ZipEntry entry;

		while ((entry = jar.getNextEntry()) != null) {

			if (!entry.isDirectory()) {

				// Add to resources
				resources.put(entry.getName(), URLFactory.addEntry(url, entry.getName(), position));

				// Add everything in jar
				if (entry.getName().endsWith(".jar") && recursive) {
					children.add(new NodeClassLoader(this, resources.get(entry.getName()), new ZipInputStream(jar),
							recursive));
				}
			}

			position++;
		}
	}

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		synchronized (getClassLoadingLock(name)) {
			Class<?> found = findLoadedClass(name);
			if (found != null)
				return found;

			// Shortcut for CompactClassLoader classes
			if (name.startsWith(NodeClassLoader.class.getPackageName())) {
				try {
					return super.loadClass(name, resolve);
				} catch (ClassNotFoundException e) {
					// Next classloader
				}
			}

			// Shortcut for standard classes
			if (name.startsWith("java") || name.startsWith("sun")) {
				try {
					return getSystemClassLoader().loadClass(name);
				} catch (ClassNotFoundException e) {
					// Next classloader
				}
			}

			// Check class cache
			if (classCache.containsKey(name))
				return classCache.get(name);

			// Try children
			for (NodeClassLoader component : children) {
				try {
					return component.loadClass(name);
				} catch (ClassNotFoundException e) {
					// Next classloader
				}
			}

			String resourceName = name.replace('.', '/') + ".class";
			if (resources.containsKey(resourceName)) {
				// Define package
				if (name.contains("."))
					definePackage(name.substring(0, name.lastIndexOf('.')), null, null, null, null, null, null, null);

				// Load class from the resource
				Class<?> loaded;
				try (var in = resources.get(resourceName).openStream()) {
					loaded = defineClass(name, ByteBuffer.wrap(in.readAllBytes()), getClass().getProtectionDomain());
				} catch (IOException e) {
					throw new ClassNotFoundException(name, e);
				}

				classCache.put(name, loaded);
				if (resolve)
					resolveClass(loaded);

				return loaded;
			}

			throw new ClassNotFoundException(name);
		}
	}

	/**
	 * Like {@link #getResources(String)}, but returns a {@link Stream}.
	 * 
	 * @param name The resource path to find
	 * @return A {@link Stream} of all resources visible to the {@link ClassLoader}
	 *         that satisfies the path
	 */
	public Stream<URL> getResourcesStream(String name) {
		Stream<URL> descendents = children.stream().flatMap(c -> c.getResourcesStream(name));
		if (resources.containsKey(name))
			return Stream.concat(descendents, Stream.of(resources.get(name)));
		return descendents;
	}

	/**
	 * Find the given {@link URL} in the classloader hierarchy.
	 * 
	 * @param url A base {@link URL}
	 * @return Whether some {@link NodeClassLoader} in the hierarchy has the given
	 *         {@link URL} as a base URL
	 */
	public boolean findBaseUrl(URL url) {
		return base.equals(url) || children.stream().anyMatch(c -> c.findBaseUrl(url));
	}
}
