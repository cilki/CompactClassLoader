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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * A recursive {@link ClassLoader} for some jar file that could be nested.
 * 
 * @author cilki
 */
final class NodeClassLoader extends ClassLoader {

	static {
		registerAsParallelCapable();
	}

	/**
	 * A map of all resource locations in the corresponding jar.
	 */
	private final Map<String, URL> resources;

	/**
	 * A list of {@link ClassLoader}s for jars within the corresponding jar.
	 */
	private final List<NodeClassLoader> children;

	/**
	 * The {@link URL} of the jar that corresponds to this {@link ClassLoader}.
	 */
	private final URL base;

	/**
	 * A {@link ProtectionDomain} for all classes loaded by this node.
	 */
	private final ProtectionDomain protectionDomain;

	/**
	 * Build a new root {@link NodeClassLoader}.
	 * 
	 * @param parent    The parent {@link ClassLoader}
	 * @param url       The {@link URL} to the {@link ClassLoader}'s jar
	 * @param recursive Whether all encountered jars will become children of this
	 *                  {@link ClassLoader}
	 * @throws IOException If an I/O exception occurs while reading the given URL
	 */
	public NodeClassLoader(ClassLoader parent, URL url, boolean recursive) throws IOException {
		super(parent);

		if (parent instanceof CompactClassLoader)
			throw new IllegalArgumentException("Invalid parent classloader");

		this.base = Objects.requireNonNull(url);
		this.protectionDomain = new ProtectionDomain(new CodeSource(base, (Certificate[]) null), null, this, null);
		this.resources = new HashMap<>();
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
	 * @throws IOException If an I/O exception occurs while reading the given URL
	 */
	private NodeClassLoader(NodeClassLoader parent, URL url, ZipInputStream in, boolean recursive) throws IOException {
		super(Objects.requireNonNull(parent));

		this.base = Objects.requireNonNull(url);
		this.protectionDomain = new ProtectionDomain(new CodeSource(base, (Certificate[]) null), null, this, null);
		this.resources = new HashMap<>();
		this.children = recursive ? new LinkedList<>() : Collections.emptyList();

		init(url, in, recursive);
		// Do not close the inputstream because the caller will do it
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
	 * @throws IOException If an I/O exception occurs while reading the given URL
	 */
	private void init(URL url, ZipInputStream jar, boolean recursive) throws IOException {
		log.fine(() -> "Initializing node: " + url);

		int position = 0;
		ZipEntry entry;

		while ((entry = jar.getNextEntry()) != null) {

			if (!entry.isDirectory()) {
				if (entry.getName().endsWith(".class")) {

					// Eagerly load class files
					try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
						jar.transferTo(bytes);

						// Add to resources
						resources.put(entry.getName(),
								URLFactory.buildNested(url, entry.getName(), bytes.toByteArray()));
					}
				} else {

					// Add to resources
					resources.put(entry.getName(), URLFactory.buildNested(url, entry.getName(), position));

					// Add everything in jar
					if (recursive && entry.getName().endsWith(".jar")) {
						children.add(new NodeClassLoader(this, resources.get(entry.getName()), new ZipInputStream(jar),
								recursive));
					}
				}
			}

			position++;
		}
	}

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		// Shortcut for standard classes
		if (name.startsWith("java."))
			return getSystemClassLoader().loadClass(name);

		return loadClass(name, null);
	}

	/**
	 * Load a class by first searching down the classloader hierarchy and then
	 * delegating to the parent classloader (up the hierarchy).
	 * 
	 * @param name   The binary name of the class
	 * @param caller The {@link NodeClassLoader} that delegated loading to
	 *               {@code this} (required to avoid infinite loop)
	 * @return The resulting {@code Class} object
	 * @throws ClassNotFoundException If the class was not found
	 */
	private Class<?> loadClass(String name, NodeClassLoader caller) throws ClassNotFoundException {
		log.finer(() -> "[NODE: " + base + "] Loading class: " + name);

		// Delegate down hierarchy
		try {
			return loadDown(name, caller);
		} catch (ClassNotFoundException e) {
			// Rethrow if not empty
			if (e.getCause() != null)
				throw e;
		}

		// Delegate up hierarchy
		if (getParent() instanceof NodeClassLoader)
			return ((NodeClassLoader) getParent()).loadClass(name, this);
		if (getParent() != null)
			return getParent().loadClass(name);
		throw new ClassNotFoundException(name);
	}

	/**
	 * Load a class by searching the classloader's managed resources and delegating
	 * to children if not found.
	 * 
	 * @param name The binary name of the class
	 * @param skip A child classloader that must be skipped
	 * @return The resulting {@code Class} object
	 * @throws ClassNotFoundException If the class was not found
	 */
	private Class<?> loadDown(String name, NodeClassLoader skip) throws ClassNotFoundException {
		synchronized (getClassLoadingLock(name)) {
			Class<?> c = findLoadedClass(name);
			if (c != null)
				return c;

			try {
				return findClass(name);
			} catch (ClassNotFoundException e) {
				// Rethrow if not empty
				if (e.getCause() != null)
					throw e;
			}

			for (NodeClassLoader component : children) {
				if (component != skip) {
					try {
						return component.loadDown(name, null);
					} catch (ClassNotFoundException e) {
						// Rethrow if not empty
						if (e.getCause() != null)
							throw e;
					}
				}
			}

			throw new ClassNotFoundException(name);
		}
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		URL resource = resources.get(name.replace('.', '/') + ".class");
		if (resource != null) {

			// Load class from the resource
			try (var in = resource.openStream()) {
				return defineClass(name, ByteBuffer.wrap(in.readAllBytes()), protectionDomain);
			} catch (IOException e) {
				throw new ClassNotFoundException(name, e);
			}
		}

		throw new ClassNotFoundException(name);
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

	@Override
	protected URL findResource(String name) {
		return resources.get(name);
	}

	@Override
	protected Enumeration<URL> findResources(String name) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public Stream<URL> resources(String name) {
		return resources(name, null);
	}

	private Stream<URL> resources(String name, NodeClassLoader caller) {
		log.finer(() -> "[NODE: " + base + "] Loading resources: " + name);

		// Delegate down hierarchy first
		Stream<URL> r = resourcesDown(name, caller);

		// Delegate up hierarchy next
		if (getParent() instanceof NodeClassLoader)
			return Stream.concat(r, ((NodeClassLoader) getParent()).resources(name, this));
		if (getParent() != null)
			return Stream.concat(r, getParent().resources(name));
		return r;
	}

	/**
	 * Build a stream of resources for this classloader and its descendents.
	 * 
	 * @param name The resource name
	 * @param skip A child classloader that must be skipped
	 * @return The resulting resource stream
	 */
	private Stream<URL> resourcesDown(String name, NodeClassLoader skip) {
		Stream<URL> descendents = children.stream().filter(c -> c != skip).flatMap(c -> c.resourcesDown(name, null));
		URL resource = findResource(name);
		if (resource != null)
			return Stream.concat(descendents, Stream.of(resource));
		return descendents;
	}

	@Override
	public URL getResource(String name) {
		return resources(name).findFirst().orElse(null);
	}

	@Override
	public Enumeration<URL> getResources(String name) throws IOException {
		return Collections.enumeration(resources(name).collect(Collectors.toList()));
	}
}
