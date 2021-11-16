package com.github.stefanbirkner.fakesftpserver.extension;


import lombok.RequiredArgsConstructor;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.common.session.SessionContext;

import java.nio.file.FileSystem;
import java.nio.file.Path;


/**
 * VirtualFileSystemFactory with a changeable underlying filesystem.
 */
@RequiredArgsConstructor
class CustomFileSystemFactory extends VirtualFileSystemFactory {

    private final FileSystem fileSystem;

    @Override
    public Path getUserHomeDir(String userName) {
        return Path.of("/");
    }

    @Override
    public FileSystem createFileSystem(SessionContext session) {
        return fileSystem;
    }
}
