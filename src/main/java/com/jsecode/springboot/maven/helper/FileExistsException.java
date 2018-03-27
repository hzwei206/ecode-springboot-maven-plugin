package com.jsecode.springboot.maven.helper;


import java.io.File;
import java.io.IOException;

/**
 * Indicates that a file already exists.
 * 
 * @version $Id: FileExistsException.java 1415850 2012-11-30 20:51:39Z ggregory $
 * @since 2.0
 */
public class FileExistsException extends IOException {

    /**
     * Defines the serial version UID.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Default Constructor.
     */
    public FileExistsException() {
        super();
    }

    /**
     * Construct an instance with the specified message.
     *
     * @param message The error message
     */
    public FileExistsException(final String message) {
        super(message);
    }

    /**
     * Construct an instance with the specified file.
     *
     * @param file The file that exists
     */
    public FileExistsException(final File file) {
        super("File " + file + " exists");
    }

}
