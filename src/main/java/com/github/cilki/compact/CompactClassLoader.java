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
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A {@link ClassLoader} that can load nested jar files and bootstrap
 * applications.
 * 
 * <p>
 * This classloader uses a parent-first delegation model.
 * 
 * @author cilki
 */
public final class CompactClassLoader extends ClassLoader {

	public static final boolean LOG_INFO = Boolean.getBoolean("ccl.log.info");
	public static final boolean LOG_FINE = Boolean.getBoolean("ccl.log.fine");

	static {
		registerAsParallelCapable();
	}

	/**
	 * A list of {@link NodeClassLoader} or {@link CompactClassLoader} child
	 * classloaders.
	 */
	protected final List<ClassLoader> components;

	/**
	 * Build an empty {@link CompactClassLoader}.
	 * 
	 * @param parent The parent {@link ClassLoader} (which may be {@code null})
	 */
	public CompactClassLoader(ClassLoader parent) {
		super(parent);
		components = new LinkedList<>();
	}

	/**
	 * Add the given {@link URL} and any nested {@link URL}s to the
	 * {@link ClassLoader} as new components.
	 * 
	 * @param url The optionally nested {@link URL}
	 * @throws IOException If an I/O exception occurs while reading the given URL
	 */
	public void add(URL url) throws IOException {
		add(url, true);
	}

	/**
	 * Add the given {@link URL} to the {@link ClassLoader} as a new component.
	 * 
	 * @param url       The optionally nested {@link URL}
	 * @param recursive Whether all encountered jars will also be added
	 * @throws IOException If an I/O exception occurs while reading the given URL
	 */
	public void add(URL url, boolean recursive) throws IOException {
		Objects.requireNonNull(url);
		if (components.stream().filter(NodeClassLoader.class::isInstance).map(NodeClassLoader.class::cast)
				.anyMatch(c -> c.findBaseUrl(url)))
			throw new IllegalArgumentException("The URL already has a classloader in the hierarchy");

		components.add(new NodeClassLoader(this, URLFactory.toNested(url), recursive));
	}

	/**
	 * Add another compact classloader as a child to this classloader.
	 * 
	 * @param cl The classloader to add
	 */
	public void add(CompactClassLoader cl) {
		Objects.requireNonNull(cl);
		if (components.contains(cl))
			throw new IllegalArgumentException("The classloader already exists in the hierarchy");

		logInfo("Adding child classloader: %s", cl);
		components.add(cl);
	}

	/**
	 * Remove the given classloader.
	 * 
	 * @param cl The classloader to remove
	 */
	public void remove(CompactClassLoader cl) {
		components.remove(cl);
	}

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		return loadClass(name, null);
	}

	Class<?> loadClass(String name, ClassLoader skip) throws ClassNotFoundException {
		logFine("Load class request: %s", name);

		try {
			// Delegate to parent first
			return super.loadClass(name, true);
		} catch (ClassNotFoundException e) {
			// Try components last
			return loadDown(name, skip);
		}
	}

	Class<?> loadDown(String name, ClassLoader skip) throws ClassNotFoundException {
		for (ClassLoader component : components) {
			if (component != skip) {
				try {
					if (component instanceof NodeClassLoader)
						return ((NodeClassLoader) component).loadDown(name, null);
					if (component instanceof CompactClassLoader)
						return ((CompactClassLoader) component).loadDown(name, null);
				} catch (ClassNotFoundException e) {
					// Rethrow if not empty
					if (e.getCause() != null)
						throw e;
				}
			}
		}

		throw new ClassNotFoundException();
	}

	@Override
	protected URL findResource(String name) {
		// Calling this method is an error
		throw new UnsupportedOperationException();
	}

	@Override
	protected Enumeration<URL> findResources(String name) throws IOException {
		// Calling this method is an error
		throw new UnsupportedOperationException();
	}

	@Override
	public Stream<URL> resources(String name) {
		return resources(name, null);
	}

	Stream<URL> resources(String name, ClassLoader skip) {
		logFine("Load resource request: %s", name);

		if (getParent() != null)
			return Stream.concat(getParent().resources(name), resourcesDown(name, skip));
		else
			return resourcesDown(name, skip);
	}

	Stream<URL> resourcesDown(String name, ClassLoader skip) {
		return components.stream().filter(component -> component != skip).flatMap(component -> {
			if (component instanceof NodeClassLoader)
				return ((NodeClassLoader) component).resourcesDown(name, null);
			if (component instanceof CompactClassLoader)
				return ((CompactClassLoader) component).resourcesDown(name, null);

			throw new RuntimeException();
		});
	}

	@Override
	public URL getResource(String name) {
		return resources(name).findFirst().orElse(null);
	}

	@Override
	public Enumeration<URL> getResources(String name) throws IOException {
		return Collections.enumeration(resources(name).collect(Collectors.toList()));
	}

	private void logInfo(String format, Object... args) {
		if (LOG_INFO)
			System.out.println(String.format("[CCL][INFO] " + format, args));
	}

	private void logFine(String format, Object... args) {
		if (LOG_FINE)
			System.out.println(String.format("[CCL][FINE] " + format, args));
	}
}
