package com.github.stefanbirkner.fakesftpserver.extension;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Set;

/**
 * This wrapper prevents, that a fileSystem can be closed.
 */
@Slf4j
@RequiredArgsConstructor
class UnclosableFileSystem extends FileSystem {

    final FileSystem fileSystem;


    @Override
    public FileSystemProvider provider() {
        return fileSystem.provider();
    }

    @Override
    public void close() {
        //will not be closed
    }

    @Override
    public boolean isOpen() {
        return fileSystem.isOpen();
    }

    @Override
    public boolean isReadOnly() {
        return fileSystem.isReadOnly();
    }

    @Override
    public String getSeparator() {
        return fileSystem.getSeparator();
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        return fileSystem.getRootDirectories();
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        return fileSystem.getFileStores();
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        return fileSystem.supportedFileAttributeViews();
    }

    @Override
    public Path getPath(String first, String... more) {
        return fileSystem.getPath(first, more);
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
        return fileSystem.getPathMatcher(syntaxAndPattern);
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        return fileSystem.getUserPrincipalLookupService();
    }

    @Override
    public WatchService newWatchService() throws IOException {
        return fileSystem.newWatchService();
    }
}
