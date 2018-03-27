package com.jsecode.springboot.maven.helper;

import java.io.Closeable;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class FileUtil {
	public static void moveDirectory(final File srcDir, final File destDir) throws IOException {
		if (srcDir == null) {
			throw new NullPointerException("Source must not be null");
		}
		if (destDir == null) {
			throw new NullPointerException("Destination must not be null");
		}
		if (!srcDir.exists()) {
			throw new FileNotFoundException("Source '" + srcDir + "' does not exist");
		}
		if (!srcDir.isDirectory()) {
			throw new IOException("Source '" + srcDir + "' is not a directory");
		}
		
		if (destDir.exists()) {
			throw new FileExistsException("Destination '" + destDir + "' already exists");
		}
		final boolean rename = srcDir.renameTo(destDir);
		if (!rename) {
			if (destDir.getCanonicalPath().startsWith(srcDir.getCanonicalPath() + File.separator)) {
				throw new IOException("Cannot move directory: " + srcDir + " to a subdirectory of itself: " + destDir);
			}
			copyDirectory(srcDir, destDir);
			deleteDirectory(srcDir);
			if (srcDir.exists()) {
				throw new IOException(
						"Failed to delete original directory '" + srcDir + "' after copy to '" + destDir + "'");
			}
		}
	}

	public static void moveDirectoryToDirectory(final File src, final File destDir, final boolean createDestDir)
			throws IOException {
		if (src == null) {
			throw new NullPointerException("Source must not be null");
		}
		if (destDir == null) {
			throw new NullPointerException("Destination directory must not be null");
		}
		if (!destDir.exists() && createDestDir) {
			destDir.mkdirs();
		}
		if (!destDir.exists()) {
			throw new FileNotFoundException(
					"Destination directory '" + destDir + "' does not exist [createDestDir=" + createDestDir + "]");
		}
		if (!destDir.isDirectory()) {
			throw new IOException("Destination '" + destDir + "' is not a directory");
		}
		moveDirectory(src, new File(destDir, src.getName()));

	}

	public static void moveFileToDirectory(final File srcFile, final File destDir) throws IOException {
		moveFileToDirectory(srcFile, destDir, true);
	}

	public static void moveFileToDirectory(final File srcFile, final File destDir, final boolean createDestDir)
			throws IOException {
		if (srcFile == null) {
			throw new NullPointerException("Source must not be null");
		}
		if (destDir == null) {
			throw new NullPointerException("Destination directory must not be null");
		}
		if (!destDir.exists() && createDestDir) {
			destDir.mkdirs();
		}
		if (!destDir.exists()) {
			throw new FileNotFoundException(
					"Destination directory '" + destDir + "' does not exist [createDestDir=" + createDestDir + "]");
		}
		if (!destDir.isDirectory()) {
			throw new IOException("Destination '" + destDir + "' is not a directory");
		}
		moveFile(srcFile, new File(destDir, srcFile.getName()));
	}

	public static void moveFile(final File srcFile, final File destFile) throws IOException {
		if (srcFile == null) {
			throw new NullPointerException("Source must not be null");
		}
		if (destFile == null) {
			throw new NullPointerException("Destination must not be null");
		}
		if (!srcFile.exists()) {
			throw new FileNotFoundException("Source '" + srcFile + "' does not exist");
		}
		if (srcFile.isDirectory()) {
			throw new IOException("Source '" + srcFile + "' is a directory");
		}
		if (destFile.exists() && !destFile.delete()) {
			throw new FileExistsException("Destination '" + destFile + "' already exists");
		}
		if (destFile.isDirectory()) {
			throw new IOException("Destination '" + destFile + "' is a directory");
		}
		final boolean rename = srcFile.renameTo(destFile);
		if (!rename) {
			copyFile(srcFile, destFile);
			if (!srcFile.delete()) {
				deleteQuietly(destFile);
				throw new IOException(
						"Failed to delete original file '" + srcFile + "' after copy to '" + destFile + "'");
			}
		}
	}

	public static void copyDirectory(final File srcDir, final File destDir) throws IOException {
		copyDirectory(srcDir, destDir, true);
	}

	public static void copyDirectory(final File srcDir, final File destDir, final boolean preserveFileDate)
			throws IOException {
		copyDirectory(srcDir, destDir, null, preserveFileDate);
	}

	public static void copyDirectory(final File srcDir, final File destDir, final FileFilter filter)
			throws IOException {
		copyDirectory(srcDir, destDir, filter, true);
	}

	public static void copyDirectory(final File srcDir, final File destDir, final FileFilter filter,
			final boolean preserveFileDate) throws IOException {
		checkFileRequirements(srcDir, destDir);
		if (!srcDir.isDirectory()) {
			throw new IOException("Source '" + srcDir + "' exists but is not a directory");
		}
		if (srcDir.getCanonicalPath().equals(destDir.getCanonicalPath())) {
			throw new IOException("Source '" + srcDir + "' and destination '" + destDir + "' are the same");
		}

		// Cater for destination being directory within the source directory (see
		// IO-141)
		List<String> exclusionList = null;
		if (destDir.getCanonicalPath().startsWith(srcDir.getCanonicalPath())) {
			final File[] srcFiles = filter == null ? srcDir.listFiles() : srcDir.listFiles(filter);
			if (srcFiles != null && srcFiles.length > 0) {
				exclusionList = new ArrayList<String>(srcFiles.length);
				for (final File srcFile : srcFiles) {
					final File copiedFile = new File(destDir, srcFile.getName());
					exclusionList.add(copiedFile.getCanonicalPath());
				}
			}
		}
		doCopyDirectory(srcDir, destDir, filter, preserveFileDate, exclusionList);
	}

	private static void doCopyDirectory(final File srcDir, final File destDir, final FileFilter filter,
			final boolean preserveFileDate, final List<String> exclusionList) throws IOException {
		// recurse
		final File[] srcFiles = filter == null ? srcDir.listFiles() : srcDir.listFiles(filter);
		if (srcFiles == null) { // null if abstract pathname does not denote a directory, or if an I/O error
								// occurs
			throw new IOException("Failed to list contents of " + srcDir);
		}
		if (destDir.exists()) {
			if (destDir.isDirectory() == false) {
				throw new IOException("Destination '" + destDir + "' exists but is not a directory");
			}
		} else {
			if (!destDir.mkdirs() && !destDir.isDirectory()) {
				throw new IOException("Destination '" + destDir + "' directory cannot be created");
			}
		}
		if (destDir.canWrite() == false) {
			throw new IOException("Destination '" + destDir + "' cannot be written to");
		}
		for (final File srcFile : srcFiles) {
			final File dstFile = new File(destDir, srcFile.getName());
			if (exclusionList == null || !exclusionList.contains(srcFile.getCanonicalPath())) {
				if (srcFile.isDirectory()) {
					doCopyDirectory(srcFile, dstFile, filter, preserveFileDate, exclusionList);
				} else {
					doCopyFile(srcFile, dstFile, preserveFileDate);
				}
			}
		}

		// Do this last, as the above has probably affected directory metadata
		if (preserveFileDate) {
			destDir.setLastModified(srcDir.lastModified());
		}
	}

	public static void copyDirectoryToDirectory(final File srcDir, final File destDir) throws IOException {
		if (srcDir == null) {
			throw new NullPointerException("Source must not be null");
		}
		if (srcDir.exists() && srcDir.isDirectory() == false) {
			throw new IllegalArgumentException("Source '" + destDir + "' is not a directory");
		}
		if (destDir == null) {
			throw new NullPointerException("Destination must not be null");
		}
		if (destDir.exists() && destDir.isDirectory() == false) {
			throw new IllegalArgumentException("Destination '" + destDir + "' is not a directory");
		}
		copyDirectory(srcDir, new File(destDir, srcDir.getName()), true);
	}

	public static void copyFileToDirectory(final File srcFile, final File destDir) throws IOException {
		copyFileToDirectory(srcFile, destDir, true);
	}

	public static void copyFileToDirectory(final File srcFile, final File destDir, final boolean createDestDir)
			throws IOException {
		if (srcFile == null) {
			throw new NullPointerException("Source must not be null");
		}
		if (destDir == null) {
			throw new NullPointerException("Destination directory must not be null");
		}
		if (!destDir.exists() && createDestDir) {
			destDir.mkdirs();
		}
		if (!destDir.exists()) {
			throw new FileNotFoundException(
					"Destination directory '" + destDir + "' does not exist [createDestDir=" + createDestDir + "]");
		}
		if (!destDir.isDirectory()) {
			throw new IOException("Destination '" + destDir + "' is not a directory");
		}
		copyFile(srcFile, new File(destDir, srcFile.getName()));
	}

	public static void copyFile(final File srcFile, final File destFile) throws IOException {
		copyFile(srcFile, destFile, true);
	}

	public static void copyFile(final File srcFile, final File destFile, final boolean preserveFileDate)
			throws IOException {
		checkFileRequirements(srcFile, destFile);
		if (srcFile.isDirectory()) {
			throw new IOException("Source '" + srcFile + "' exists but is a directory");
		}
		if (srcFile.getCanonicalPath().equals(destFile.getCanonicalPath())) {
			throw new IOException("Source '" + srcFile + "' and destination '" + destFile + "' are the same");
		}
		final File parentFile = destFile.getParentFile();
		if (parentFile != null) {
			if (!parentFile.mkdirs() && !parentFile.isDirectory()) {
				throw new IOException("Destination '" + parentFile + "' directory cannot be created");
			}
		}
		if (destFile.exists() && destFile.canWrite() == false) {
			throw new IOException("Destination '" + destFile + "' exists but is read-only");
		}
		doCopyFile(srcFile, destFile, preserveFileDate);
	}

	private static void doCopyFile(final File srcFile, final File destFile, final boolean preserveFileDate)
			throws IOException {
		if (destFile.exists() && destFile.isDirectory()) {
			throw new IOException("Destination '" + destFile + "' exists but is a directory");
		}

		FileInputStream fis = null;
		FileOutputStream fos = null;
		FileChannel input = null;
		FileChannel output = null;
		try {
			fis = new FileInputStream(srcFile);
			fos = new FileOutputStream(destFile);
			input = fis.getChannel();
			output = fos.getChannel();
			final long size = input.size(); // TODO See IO-386
			long pos = 0;
			long count = 0;
			while (pos < size) {
				final long remain = size - pos;
				count = remain > FILE_COPY_BUFFER_SIZE ? FILE_COPY_BUFFER_SIZE : remain;
				final long bytesCopied = output.transferFrom(input, pos, count);
				if (bytesCopied == 0) { // IO-385 - can happen if file is truncated after caching the size
					break; // ensure we don't loop forever
				}
				pos += bytesCopied;
			}
		} finally {
			closeQuietly(output, fos, input, fis);
		}

		final long srcLen = srcFile.length(); // TODO See IO-386
		final long dstLen = destFile.length(); // TODO See IO-386
		if (srcLen != dstLen) {
			throw new IOException("Failed to copy full contents from '" + srcFile + "' to '" + destFile
					+ "' Expected length: " + srcLen + " Actual: " + dstLen);
		}
		if (preserveFileDate) {
			destFile.setLastModified(srcFile.lastModified());
		}
	}

	public static void closeQuietly(final Closeable... closeables) {
		if (closeables == null) {
			return;
		}
		for (final Closeable closeable : closeables) {
			closeQuietly(closeable);
		}
	}

	public static void closeQuietly(final Closeable closeable) {
		try {
			if (closeable != null) {
				closeable.close();
			}
		} catch (final IOException ioe) {
			// ignore
		}
	}

	public static void deleteDirectory(final File directory) throws IOException {
		if (!directory.exists()) {
			return;
		}

		if (!isSymlink(directory)) {
			cleanDirectory(directory);
		}

		if (!directory.delete()) {
			final String message = "Unable to delete directory " + directory + ".";
			throw new IOException(message);
		}
	}

	public static boolean deleteQuietly(final File file) {
		if (file == null) {
			return false;
		}
		try {
			if (file.isDirectory()) {
				cleanDirectory(file);
			}
		} catch (final Exception ignored) {
		}

		try {
			return file.delete();
		} catch (final Exception ignored) {
			return false;
		}
	}

	public static void cleanDirectory(final File directory) throws IOException {
		final File[] files = verifiedListFiles(directory);

		IOException exception = null;
		for (final File file : files) {
			try {
				forceDelete(file);
			} catch (final IOException ioe) {
				exception = ioe;
			}
		}

		if (null != exception) {
			throw exception;
		}
	}

	private static File[] verifiedListFiles(File directory) throws IOException {
		if (!directory.exists()) {
			final String message = directory + " does not exist";
			throw new IllegalArgumentException(message);
		}

		if (!directory.isDirectory()) {
			final String message = directory + " is not a directory";
			throw new IllegalArgumentException(message);
		}

		final File[] files = directory.listFiles();
		if (files == null) { // null if security restricted
			throw new IOException("Failed to list contents of " + directory);
		}
		return files;
	}

	public static void forceDeleteOnExit(final File file) throws IOException {
		if (file.isDirectory()) {
			deleteDirectoryOnExit(file);
		} else {
			file.deleteOnExit();
		}
	}

	public static void forceDelete(final File file) throws IOException {
		if (file.isDirectory()) {
			deleteDirectory(file);
		} else {
			final boolean filePresent = file.exists();
			if (!file.delete()) {
				if (!filePresent) {
					throw new FileNotFoundException("File does not exist: " + file);
				}
				final String message = "Unable to delete file: " + file;
				throw new IOException(message);
			}
		}
	}

	private static void deleteDirectoryOnExit(final File directory) throws IOException {
		if (!directory.exists()) {
			return;
		}

		directory.deleteOnExit();
		if (!isSymlink(directory)) {
			cleanDirectoryOnExit(directory);
		}
	}

	/**
	 * Cleans a directory without deleting it.
	 *
	 * @param directory
	 *            directory to clean, must not be {@code null}
	 * @throws NullPointerException
	 *             if the directory is {@code null}
	 * @throws IOException
	 *             in case cleaning is unsuccessful
	 */
	private static void cleanDirectoryOnExit(final File directory) throws IOException {
		final File[] files = verifiedListFiles(directory);

		IOException exception = null;
		for (final File file : files) {
			try {
				forceDeleteOnExit(file);
			} catch (final IOException ioe) {
				exception = ioe;
			}
		}

		if (null != exception) {
			throw exception;
		}
	}

	public static boolean isSymlink(final File file) throws IOException {
		if (Java7Support.isAtLeastJava7()) {
			return Java7Support.isSymLink(file);
		}

		if (file == null) {
			throw new NullPointerException("File must not be null");
		}
		if (isSystemWindows()) {
			return false;
		}
		File fileInCanonicalDir = null;
		if (file.getParent() == null) {
			fileInCanonicalDir = file;
		} else {
			final File canonicalDir = file.getParentFile().getCanonicalFile();
			fileInCanonicalDir = new File(canonicalDir, file.getName());
		}

		if (fileInCanonicalDir.getCanonicalFile().equals(fileInCanonicalDir.getAbsoluteFile())) {
			return isBrokenSymlink(file);
		} else {
			return true;
		}
	}

	private static boolean isBrokenSymlink(final File file) throws IOException {
		// if file exists then if it is a symlink it's not broken
		if (file.exists()) {
			return false;
		}
		// a broken symlink will show up in the list of files of its parent directory
		final File canon = file.getCanonicalFile();
		File parentDir = canon.getParentFile();
		if (parentDir == null || !parentDir.exists()) {
			return false;
		}

		// is it worthwhile to create a FileFilterUtil method for this?
		// is it worthwhile to create an "identity" IOFileFilter for this?
		File[] fileInDir = parentDir.listFiles(new FileFilter() {
			public boolean accept(File aFile) {
				return aFile.equals(canon);
			}
		});
		return fileInDir != null && fileInDir.length > 0;
	}

	private static void checkFileRequirements(File src, File dest) throws FileNotFoundException {
		if (src == null) {
			throw new NullPointerException("Source must not be null");
		}
		if (dest == null) {
			throw new NullPointerException("Destination must not be null");
		}
		if (!src.exists()) {
			throw new FileNotFoundException("Source '" + src + "' does not exist");
		}
	}

	static boolean isSystemWindows() {
		return SYSTEM_SEPARATOR == WINDOWS_SEPARATOR;
	}

	/**
	 * The Unix separator character.
	 */
	static final char UNIX_SEPARATOR = '/';

	/**
	 * The Windows separator character.
	 */
	static final char WINDOWS_SEPARATOR = '\\';

	/**
	 * The system separator character.
	 */
	static final char SYSTEM_SEPARATOR = File.separatorChar;

	/**
	 * The number of bytes in a kilobyte.
	 */
	public static final long ONE_KB = 1024;

	/**
	 * The number of bytes in a kilobyte.
	 *
	 * @since 2.4
	 */
	public static final BigInteger ONE_KB_BI = BigInteger.valueOf(ONE_KB);

	/**
	 * The number of bytes in a megabyte.
	 */
	public static final long ONE_MB = ONE_KB * ONE_KB;

	/**
	 * The number of bytes in a megabyte.
	 *
	 * @since 2.4
	 */
	public static final BigInteger ONE_MB_BI = ONE_KB_BI.multiply(ONE_KB_BI);

	/**
	 * The file copy buffer size (30 MB)
	 */
	private static final long FILE_COPY_BUFFER_SIZE = ONE_MB * 30;

}
