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
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A {@link ClassLoader} that can load nested jar files and bootstrap
 * applications.
 * 
 * <p>
 * This classloader uses the parent-first delegation model.
 * 
 * @author cilki
 */
public final class CompactClassLoader extends ClassLoader {

	static {
		if (System.getProperty("java.util.logging.SimpleFormatter.format") == null)
			System.setProperty("java.util.logging.SimpleFormatter.format", "[%1$tF %1$tT] [%4$-4s] %5$s %n");
	}

	public static final Logger log = Logger.getLogger(CompactClassLoader.class.getName());

	static {
		log.setLevel(Level.parse(System.getProperty("ccl.level", "warning").toUpperCase()));
		var handler = new ConsoleHandler();
		handler.setLevel(Level.FINE);
		log.addHandler(handler);

		registerAsParallelCapable();
	}

	private static CompactClassLoader system;

	public static CompactClassLoader getSystem() {
		return system;
	}

	/**
	 * A list of classloaders responsible for loading {@link URL}s introduced by
	 * {@link #add(URL)}.
	 */
	protected final List<ClassLoader> components;

	/**
	 * Build an empty {@link CompactClassLoader}.
	 * 
	 * @param parent The parent {@link ClassLoader} which may be {@code null}
	 */
	public CompactClassLoader(ClassLoader parent) {
		super(parent);
		components = new LinkedList<>();

		// Examine the stack to determine whether this classloader is being built by the
		// runtime
		var stack = Thread.currentThread().getStackTrace();
		if (stack[stack.length - 1].getMethodName().equals("initPhase3")) {
			if (system != null)
				throw new RuntimeException();
			system = this;
		}
	}

	/**
	 * Add the given {@link URL} to the {@link ClassLoader} as a new component.
	 * 
	 * @param url The {@link URL} to add which may be a jar file or a jar file
	 *            within a jar file
	 * @throws IOException If an I/O exception occurs while reading the given URL
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
	 * @throws IOException If an I/O exception occurs while reading the given URL
	 */
	public void add(URL url, boolean recursive) throws IOException {
		Objects.requireNonNull(url);
		if (components.stream().filter(NodeClassLoader.class::isInstance).map(NodeClassLoader.class::cast)
				.anyMatch(c -> c.findBaseUrl(url)))
			throw new IllegalArgumentException("The URL already has a classloader in the hierarchy");

		components.add(new NodeClassLoader(this, URLFactory.toNested(url), recursive));
	}

	public void add(CompactClassLoader cl) {
		Objects.requireNonNull(cl);
		if (components.contains(cl))
			throw new IllegalArgumentException("The classloader already exists in the hierarchy");

		components.add(cl);
	}

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		return loadClass(name, null);
	}

	Class<?> loadClass(String name, ClassLoader skip) throws ClassNotFoundException {
		log.finer(() -> "Loading class: " + name);

		// Delegate to parent first
		try {
			return super.loadClass(name, false);
		} catch (ClassNotFoundException e) {
			// continue
		}

		// Try components
		return loadDown(name, skip);
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
		return components.stream().flatMap(component -> component.resources(name)).findFirst().orElse(null);
	}

	@Override
	protected Enumeration<URL> findResources(String name) {
		return Collections.enumeration(
				components.stream().flatMap(component -> component.resources(name)).collect(Collectors.toList()));
	}

	@Override
	public Stream<URL> resources(String name) {
		return resources(name, null);
	}

	Stream<URL> resources(String name, ClassLoader skip) {
		log.finer(() -> "Loading resources: " + name);

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
		return findResource(name);
	}

	@Override
	public Enumeration<URL> getResources(String name) throws IOException {
		return findResources(name);
	}

}
