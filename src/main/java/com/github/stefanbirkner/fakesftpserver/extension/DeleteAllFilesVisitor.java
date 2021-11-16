package com.github.stefanbirkner.fakesftpserver.extension;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.Files.delete;

/**
 * Visitor, which travels directories and deletes the content.
 */
class DeleteAllFilesVisitor extends SimpleFileVisitor<Path> {

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        delete(file);
        return CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        if (dir.getParent() != null) {
            delete(dir);
        }
        return super.postVisitDirectory(dir, exc);
    }

}
