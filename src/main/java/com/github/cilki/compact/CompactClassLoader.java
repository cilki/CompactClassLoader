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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A {@link ClassLoader} that can load nested jar files and bootstrap
 * applications.<br>
 * <br>
 * Note: Although this class is not entirely thread safe, it can safely load
 * classes in parallel.
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
		if (Boolean.getBoolean("compactcl.debug"))
			log.setLevel(Level.FINE);
		else
			log.setLevel(Level.WARNING);

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
	 * Build a {@link CompactClassLoader} for the current jar file.
	 * 
	 * @param recursive Whether all encountered jars will also be added
	 * @throws IOException
	 */
	public CompactClassLoader(boolean recursive) throws IOException {
		this(ClassLoader.getSystemClassLoader());

		add(getClass().getProtectionDomain().getCodeSource().getLocation(), recursive);
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

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {

		// Shortcut for standard classes
		if (name.startsWith("java."))
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
		return components.stream().flatMap(component -> component.resources(name));
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
