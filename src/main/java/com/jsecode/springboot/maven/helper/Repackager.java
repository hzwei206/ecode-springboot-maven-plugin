package com.jsecode.springboot.maven.helper;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.springframework.boot.loader.tools.CustomLoaderLayout;
import org.springframework.boot.loader.tools.DefaultLayoutFactory;
import org.springframework.boot.loader.tools.LaunchScript;
import org.springframework.boot.loader.tools.Layout;
import org.springframework.boot.loader.tools.LayoutFactory;
import org.springframework.boot.loader.tools.Libraries;
import org.springframework.boot.loader.tools.Library;
import org.springframework.boot.loader.tools.LibraryCallback;
import org.springframework.boot.loader.tools.LibraryScope;
import org.springframework.boot.loader.tools.MainClassFinder;
import org.springframework.boot.loader.tools.RepackagingLayout;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.lang.UsesJava8;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.jsecode.springboot.maven.helper.JarWriter.EntryTransformer;

/**
 * Utility class that can be used to repackage an archive so that it can be executed using
 * '{@literal java -jar}'.
 *
 * @author Phillip Webb
 * @author Andy Wilkinson
 * @author Stephane Nicoll
 */
public class Repackager {

	private static final String MAIN_CLASS_ATTRIBUTE = "Main-Class";

	private static final String START_CLASS_ATTRIBUTE = "Start-Class";
	private static final String CLASS_PATH = "Class-Path";
	private static final String BOOT_VERSION_ATTRIBUTE = "Spring-Boot-Version";

	private static final String BOOT_LIB_ATTRIBUTE = "Spring-Boot-Lib";

	private static final String BOOT_CLASSES_ATTRIBUTE = "Spring-Boot-Classes";

	private static final byte[] ZIP_FILE_HEADER = new byte[] { 'P', 'K', 3, 4 };

	private static final long FIND_WARNING_TIMEOUT = TimeUnit.SECONDS.toMillis(10);

	private static final String SPRING_BOOT_APPLICATION_CLASS_NAME = "org.springframework.boot.autoconfigure.SpringBootApplication";

	private List<MainClassTimeoutWarningListener> mainClassTimeoutListeners = new ArrayList<MainClassTimeoutWarningListener>();

	private String mainClass;

	private boolean backupSource = true;

	private final File source;

	private Layout layout;

	private LayoutFactory layoutFactory;

	public Repackager(File source) {
		this(source, null);
	}

	public Repackager(File source, LayoutFactory layoutFactory) {
		if (source == null) {
			throw new IllegalArgumentException("Source file must be provided");
		}
		if (!source.exists() || !source.isFile()) {
			throw new IllegalArgumentException("Source must refer to an existing file, "
					+ "got " + source.getAbsolutePath());
		}
		this.source = source.getAbsoluteFile();
		this.layoutFactory = layoutFactory;
	}

	/**
	 * Add a listener that will be triggered to display a warning if searching for the
	 * main class takes too long.
	 * @param listener the listener to add
	 */
	public void addMainClassTimeoutWarningListener(
			MainClassTimeoutWarningListener listener) {
		this.mainClassTimeoutListeners.add(listener);
	}

	/**
	 * Sets the main class that should be run. If not specified the value from the
	 * MANIFEST will be used, or if no manifest entry is found the archive will be
	 * searched for a suitable class.
	 * @param mainClass the main class name
	 */
	public void setMainClass(String mainClass) {
		this.mainClass = mainClass;
	}

	/**
	 * Sets if source files should be backed up when they would be overwritten.
	 * @param backupSource if source files should be backed up
	 */
	public void setBackupSource(boolean backupSource) {
		this.backupSource = backupSource;
	}

	/**
	 * Sets the layout to use for the jar. Defaults to {@link Layouts#forFile(File)}.
	 * @param layout the layout
	 */
	public void setLayout(Layout layout) {
		if (layout == null) {
			throw new IllegalArgumentException("Layout must not be null");
		}
		this.layout = layout;
	}

	/**
	 * Sets the layout factory for the jar. The factory can be used when no specific
	 * layout is specified.
	 * @param layoutFactory the layout factory to set
	 */
	public void setLayoutFactory(LayoutFactory layoutFactory) {
		this.layoutFactory = layoutFactory;
	}

	/**
	 * Repackage the source file so that it can be run using '{@literal java -jar}'.
	 * @param libraries the libraries required to run the archive
	 * @param allInOne true|false
	 * @param distDir 
	 * @throws IOException if the file cannot be repackaged
	 */
	public void repackage(Libraries libraries, boolean allInOne, File distDir) throws IOException {
		repackage(this.source, libraries, allInOne, distDir);
	}

	/**
	 * Repackage to the given destination so that it can be launched using '
	 * {@literal java -jar}'.
	 * @param destination the destination file (may be the same as the source)
	 * @param libraries the libraries required to run the archive
	 * @param allInOne true|false
	 * @param distDir 
	 * @throws IOException if the file cannot be repackaged
	 */
	public void repackage(File destination, Libraries libraries, boolean allInOne, File distDir) throws IOException {
		repackage(destination, libraries, null, allInOne, distDir);
	}

	/**
	 * Repackage to the given destination so that it can be launched using '
	 * {@literal java -jar}'.
	 * @param destination the destination file (may be the same as the source)
	 * @param libraries the libraries required to run the archive
	 * @param launchScript an optional launch script prepended to the front of the jar
	 * @param allInOne  true|false
	 * @param distDir 
	 * @throws IOException if the file cannot be repackaged
	 * @since 1.3.0
	 */
	public void repackage(File destination, Libraries libraries,
			LaunchScript launchScript, boolean allInOne, File distDir) throws IOException {
		if (destination == null || destination.isDirectory()) {
			throw new IllegalArgumentException("Invalid destination");
		}
		if (libraries == null) {
			throw new IllegalArgumentException("Libraries must not be null");
		}
		if (this.layout == null) {
			this.layout = getLayoutFactory().getLayout(this.source);
		}
		if (alreadyRepackaged()) {
			return;
		}
		destination = destination.getAbsoluteFile();
		File workingSource = this.source;
		if (this.source.equals(destination)) {
			workingSource = getBackupFile();
			workingSource.delete();
			renameFile(this.source, workingSource);
		}
		destination.delete();
		try {
			JarFile jarFileSource = new JarFile(workingSource);
			try {
				repackage(jarFileSource, destination, libraries, launchScript, allInOne);
				if (distDir != null && distDir.isDirectory()) {
					FileUtil.moveFileToDirectory(destination, distDir);
					if (!allInOne) {
						String targetPath = this.source.getParent();
						File srcLibDir = new File((targetPath==null?".":targetPath) + File.separator + getLibraryDest());
						File destLibDir = new File(distDir.getAbsolutePath() + File.separator + getLibraryDest());
						if (destLibDir.exists()) {
							FileUtil.cleanDirectory(destLibDir);
						}
						
						FileUtil.copyDirectory(srcLibDir, destLibDir);
						FileUtil.deleteDirectory(srcLibDir);
					}
				}
			}
			finally {
				jarFileSource.close();
			}
		}
		finally {
			try {
				if (!this.backupSource && !this.source.equals(workingSource)) {
					deleteFile(workingSource);
				}else if (distDir != null && distDir.isDirectory()) {
					FileUtil.moveFileToDirectory(workingSource, distDir);
				}
			}catch(Exception e) {}
		}
	}

	private LayoutFactory getLayoutFactory() {
		if (this.layoutFactory != null) {
			return this.layoutFactory;
		}
		List<LayoutFactory> factories = SpringFactoriesLoader
				.loadFactories(LayoutFactory.class, null);
		if (factories.isEmpty()) {
			return new DefaultLayoutFactory();
		}
		Assert.state(factories.size() == 1, "No unique LayoutFactory found");
		return factories.get(0);
	}

	/**
	 * Return the {@link File} to use to backup the original source.
	 * @return the file to use to backup the original source
	 */
	public final File getBackupFile() {
		return new File(this.source.getParentFile(), this.source.getName() + ".original");
	}

	private boolean alreadyRepackaged() throws IOException {
		JarFile jarFile = new JarFile(this.source);
		try {
			Manifest manifest = jarFile.getManifest();
			return (manifest != null && manifest.getMainAttributes()
					.getValue(BOOT_VERSION_ATTRIBUTE) != null);
		}
		finally {
			jarFile.close();
		}
	}

	private void repackage(JarFile sourceJar, File destination, Libraries libraries,
			LaunchScript launchScript, boolean allInOne) throws IOException {
		JarWriter writer = new JarWriter(destination, launchScript);
		try {
			final List<Library> unpackLibraries = new ArrayList<Library>();
			final List<Library> standardLibraries = new ArrayList<Library>();
			libraries.doWithLibraries(new LibraryCallback() {

				@Override
				public void library(Library library) throws IOException {
					File file = library.getFile();
					if (isZip(file)) {
						if (library.isUnpackRequired()) {
							unpackLibraries.add(library);
						}
						else {
							standardLibraries.add(library);
						}
					}
				}

			});
			repackage(sourceJar, writer, unpackLibraries, standardLibraries, allInOne);
		}
		finally {
			try {
				writer.close();
			}
			catch (Exception ex) {
				// Ignore
			}
		}
	}

	private void repackage(JarFile sourceJar, JarWriter writer,
			final List<Library> unpackLibraries, final List<Library> standardLibraries, boolean allInOne)
					throws IOException { 
    	StringBuilder libJarStr = new StringBuilder();
    	String springBootVersion = null;
	    if (!allInOne && (!unpackLibraries.isEmpty() || !standardLibraries.isEmpty())) {
	    	libJarStr.append(" . ");
	    	String prefix = getLibraryDest() + "/" +"spring-boot-";
	    	for (Library library: unpackLibraries) {
	    		String jar = getLibraryDest() + "/" + library.getName();
	    		libJarStr.append(jar).append(" ");
	    		if (springBootVersion == null && jar.startsWith(prefix)) {
	    			springBootVersion = jar.substring(jar.lastIndexOf("-")+1, jar.lastIndexOf(".jar"));
	    		}
	    	}
	    	
	    	for (Library library: standardLibraries) {
	    		String jar = getLibraryDest() + "/" + library.getName();
	    		libJarStr.append(jar).append(" ");
	    		if (springBootVersion == null && jar.startsWith(prefix)) {
	    			springBootVersion = jar.substring(jar.lastIndexOf("-")+1, jar.lastIndexOf(".jar"));
	    		}
	    	} 
	    }
	     
		writer.writeManifest(buildManifest(sourceJar, libJarStr.toString(), springBootVersion));
		 
		Set<String> seen = new HashSet<String>();
		if (allInOne) {
			writeNestedLibraries(unpackLibraries, seen, writer);
		}else {
			copyNestedLibraries(unpackLibraries, this.source.getParent(), seen);
		}
		
		if (this.layout instanceof RepackagingLayout) {
			writer.writeEntries(sourceJar, new RenamingEntryTransformer(
					((RepackagingLayout) this.layout).getRepackagedClassesLocation()));
		} else {
			writer.writeEntries(sourceJar);
		}
		
		if (allInOne) {
			writeNestedLibraries(standardLibraries, seen, writer);
		}else {
			copyNestedLibraries(standardLibraries, this.source.getParent(), seen);
		}
		 
		writeLoaderClasses(writer);
		
	}

	private void writeNestedLibraries(List<Library> libraries, Set<String> alreadySeen,
			JarWriter writer) throws IOException {
		for (Library library : libraries) {
			String destination = Repackager.this.layout
					.getLibraryDestination(library.getName(), library.getScope());
			if (destination != null) {
				if (!alreadySeen.add(destination + library.getName())) {
					throw new IllegalStateException(
							"Duplicate library " + library.getName());
				}
				writer.writeNestedLibrary(destination, library);
			}
		}
	}
	
	private void copyNestedLibraries(List<Library> libraries, String targetPath, Set<String> alreadySeen) throws IOException { 
		File targetDir = new File((targetPath==null?".":targetPath) + File.separator + getLibraryDest());
		for (Library library : libraries) {
			if (!alreadySeen.add(getLibraryDest() + "/" + library.getName())) {
				throw new IllegalStateException("Duplicate library " + library.getName());
			}
			 
			//copy lib-jar to lib-dir
			FileUtil.copyFileToDirectory(library.getFile(), targetDir);
		}
	}
	
	public String getLibraryDest() {
		return "lib";
	}

	private void writeLoaderClasses(JarWriter writer) throws IOException {
		if (this.layout instanceof CustomLoaderLayout) {
			((CustomLoaderLayout) this.layout).writeLoadedClasses(writer);
		}
		else if (this.layout.isExecutable()) {
			writer.writeLoaderClasses();
		}
	}

	private boolean isZip(File file) {
		try {
			FileInputStream fileInputStream = new FileInputStream(file);
			try {
				return isZip(fileInputStream);
			}
			finally {
				fileInputStream.close();
			}
		}
		catch (IOException ex) {
			return false;
		}
	}

	private boolean isZip(InputStream inputStream) throws IOException {
		for (int i = 0; i < ZIP_FILE_HEADER.length; i++) {
			if (inputStream.read() != ZIP_FILE_HEADER[i]) {
				return false;
			}
		}
		return true;
	}

	private Manifest buildManifest(JarFile source, String classpath, String springBootVersion) throws IOException {
		Manifest manifest = source.getManifest();
		if (manifest == null) {
			manifest = new Manifest();
			manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
		}
		manifest = new Manifest(manifest);
		String startClass = this.mainClass;
		if (startClass == null) {
			startClass = manifest.getMainAttributes().getValue(MAIN_CLASS_ATTRIBUTE);
		}
		if (startClass == null) {
			startClass = findMainMethodWithTimeoutWarning(source);
		}
		String launcherClassName = this.layout.getLauncherClassName();
		if (launcherClassName != null) {
			manifest.getMainAttributes().putValue(MAIN_CLASS_ATTRIBUTE,
					launcherClassName);
			if (startClass == null) {
				throw new IllegalStateException("Unable to find main class");
			}
			manifest.getMainAttributes().putValue(START_CLASS_ATTRIBUTE, startClass);
		}
		else if (startClass != null) {
			manifest.getMainAttributes().putValue(MAIN_CLASS_ATTRIBUTE, startClass);
		}
		
		if (classpath != null && classpath.length() > 0) {
			manifest.getMainAttributes().putValue(CLASS_PATH, classpath);
		}
		
		String bootVersion = getClass().getPackage().getImplementationVersion();
		manifest.getMainAttributes().putValue(BOOT_VERSION_ATTRIBUTE, bootVersion==null?springBootVersion:bootVersion);
		manifest.getMainAttributes().putValue(BOOT_CLASSES_ATTRIBUTE,
				(this.layout instanceof RepackagingLayout)
						? ((RepackagingLayout) this.layout).getRepackagedClassesLocation()
						: this.layout.getClassesLocation());
		String lib = this.layout.getLibraryDestination("", LibraryScope.COMPILE);
		if (StringUtils.hasLength(lib)) {
			manifest.getMainAttributes().putValue(BOOT_LIB_ATTRIBUTE, lib);
		}
		return manifest;
	}

	private String findMainMethodWithTimeoutWarning(JarFile source) throws IOException {
		long startTime = System.currentTimeMillis();
		String mainMethod = findMainMethod(source);
		long duration = System.currentTimeMillis() - startTime;
		if (duration > FIND_WARNING_TIMEOUT) {
			for (MainClassTimeoutWarningListener listener : this.mainClassTimeoutListeners) {
				listener.handleTimeoutWarning(duration, mainMethod);
			}
		}
		return mainMethod;
	}

	protected String findMainMethod(JarFile source) throws IOException {
		return MainClassFinder.findSingleMainClass(source,
				this.layout.getClassesLocation(), SPRING_BOOT_APPLICATION_CLASS_NAME);
	}

	private void renameFile(File file, File dest) {
		if (!file.renameTo(dest)) {
			throw new IllegalStateException(
					"Unable to rename '" + file + "' to '" + dest + "'");
		}
	}

	private void deleteFile(File file) {
		if (!file.delete()) {
			throw new IllegalStateException("Unable to delete '" + file + "'");
		}
	}

	/**
	 * Callback interface used to present a warning when finding the main class takes too
	 * long.
	 */
	public interface MainClassTimeoutWarningListener {

		/**
		 * Handle a timeout warning.
		 * @param duration the amount of time it took to find the main method
		 * @param mainMethod the main method that was actually found
		 */
		void handleTimeoutWarning(long duration, String mainMethod);

	}

	/**
	 * An {@code EntryTransformer} that renames entries by applying a prefix.
	 */
	private static final class RenamingEntryTransformer implements EntryTransformer {

		private final String namePrefix;

		private RenamingEntryTransformer(String namePrefix) {
			this.namePrefix = namePrefix;
		}

		@Override
		public JarEntry transform(JarEntry entry) {
			if (entry.getName().equals("META-INF/INDEX.LIST")) {
				return null;
			}
			if ((entry.getName().startsWith("META-INF/")
					&& !entry.getName().equals("META-INF/aop.xml"))
					|| entry.getName().startsWith("BOOT-INF/")) {
				return entry;
			}
			JarEntry renamedEntry = new JarEntry(this.namePrefix + entry.getName());
			renamedEntry.setTime(entry.getTime());
			renamedEntry.setSize(entry.getSize());
			renamedEntry.setMethod(entry.getMethod());
			if (entry.getComment() != null) {
				renamedEntry.setComment(entry.getComment());
			}
			renamedEntry.setCompressedSize(entry.getCompressedSize());
			renamedEntry.setCrc(entry.getCrc());
			setCreationTimeIfPossible(entry, renamedEntry);
			if (entry.getExtra() != null) {
				renamedEntry.setExtra(entry.getExtra());
			}
			setLastAccessTimeIfPossible(entry, renamedEntry);
			setLastModifiedTimeIfPossible(entry, renamedEntry);
			return renamedEntry;
		}

		@UsesJava8
		private void setCreationTimeIfPossible(JarEntry source, JarEntry target) {
			try {
				if (source.getCreationTime() != null) {
					target.setCreationTime(source.getCreationTime());
				}
			}
			catch (NoSuchMethodError ex) {
				// Not running on Java 8. Continue.
			}
		}

		@UsesJava8
		private void setLastAccessTimeIfPossible(JarEntry source, JarEntry target) {
			try {
				if (source.getLastAccessTime() != null) {
					target.setLastAccessTime(source.getLastAccessTime());
				}
			}
			catch (NoSuchMethodError ex) {
				// Not running on Java 8. Continue.
			}
		}

		@UsesJava8
		private void setLastModifiedTimeIfPossible(JarEntry source, JarEntry target) {
			try {
				if (source.getLastModifiedTime() != null) {
					target.setLastModifiedTime(source.getLastModifiedTime());
				}
			}
			catch (NoSuchMethodError ex) {
				// Not running on Java 8. Continue.
			}
		}

	}

}
