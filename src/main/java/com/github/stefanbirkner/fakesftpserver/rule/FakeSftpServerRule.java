package com.github.stefanbirkner.fakesftpserver.rule;

import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.password.StaticPasswordAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.subsystem.sftp.SftpSubsystemFactory;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Set;

import static com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder.newLinux;
import static java.nio.file.Files.*;
import static java.util.Collections.singletonList;

/**
 * Fake SFTP Server Rule is a JUnit rule that runs an in-memory SFTP server
 * while your tests are running.
 * <p>The Fake SFTP Server Rule is used by adding it to your test class.
 * <pre>
 * public class TestClass {
 *   &#064;Rule
 *   public final FakeSftpServerRule sftpServer = new FakeSftpServerRule();
 *
 *   ...
 * }
 * </pre>
 * <p>This rule starts a server before your test and stops it afterwards.
 * <p>You can interact with the SFTP server by using the SFTP protocol with an
 * arbitrary username and password. (The server accepts every combination of
 * username and password.) The port of the server is obtained by
 * {@link #getPort() sftpServer.getPort()}.
 *
 * <h2>Testing code that reads files</h2>
 * <p>If you test code that reads files from an SFTP server then you need the
 * server to provide these files. Fake SFTP Server Rule has a shortcut for
 * uploading files to the server.
 * <pre>
 * &#064;Test
 * public void testTextFile() {
 *   {@link #putFile(String, String, Charset) sftpServer.putFile}("/directory/file.txt", "content of file", UTF_8);
 *   //code that downloads the file
 * }
 *
 * &#064;Test
 * public void testBinaryFile() {
 *   byte[] content = createContent();
 *   {@link #putFile(String, byte[]) sftpServer.putFile}("/directory/file.bin", content);
 *   //code that downloads the file
 * }
 * </pre>
 *
 * <h2>Testing code that writes files</h2>
 * <p>If you test code that writes files to an SFTP server then you need to
 * verify the upload. Fake SFTP Server Rule provides a shortcut for getting the
 * file's content from the server.
 * <pre>
 * &#064;Test
 * public void testTextFile() {
 *   //code that uploads the file
 *   String fileContent = {@link #getFileContent(String, Charset) sftpServer.getFileContent}("/directory/file.txt", UTF_8);
 *   ...
 * }
 *
 * &#064;Test
 * public void testBinaryFile() {
 *   //code that uploads the file
 *   byte[] fileContent = {@link #getFileContent(String) sftpServer.getFileContent}("/directory/file.bin");
 *   ...
 * }
 * </pre>
 *
 * <h2>Testing existence of files</h2>
 * <p>If you want to check whether a file hast been created or deleted then you
 * can verify that it exists or not.
 * <pre>
 * &#064;Test
 * public void testFile() {
 *   //code that uploads or deletes the file
 *   boolean exists = {@link #existsFile(String) sftpServer.existsFile}("/directory/file.txt");
 *   ...
 * }
 * </pre>
 * <p>The method returns {@code true} iff the file exists and it is not a directory.
 */
public class FakeSftpServerRule implements TestRule {
    private static final int PORT = 23454;

    private FileSystem fileSystem;

    /**
     * Returns the port of the SFTP server.
     *
     * @return the port of the SFTP server.
     */
    public int getPort() {
        return PORT;
    }

    /**
     * Put a text file on the SFTP folder. The file is available by the
     * specified path.
     * @param path the path to the file.
     * @param content the files content.
     * @param encoding the encoding of the file.
     * @throws IOException if the file cannot be written.
     */
    public void putFile(String path, String content, Charset encoding)
            throws IOException {
        byte[] contentAsBytes = content.getBytes(encoding);
        putFile(path, contentAsBytes);
    }

    /**
     * Put a file on the SFTP folder. The file is available by the specified
     * path.
     * @param path the path to the file.
     * @param content the files content.
     * @throws IOException if the file cannot be written.
     */
    public void putFile(String path, byte[] content) throws IOException {
        verifyThatTestIsRunning("upload");
        Path pathAsObject = fileSystem.getPath(path);
        Path directory = pathAsObject.getParent();
        if (directory != null && !directory.equals(pathAsObject.getRoot()))
            createDirectories(directory);
        write(pathAsObject, content);
    }

    /**
     * Get a text file from the SFTP server. The file is decoded using the
     * specified encoding.
     * @param path the path to the file.
     * @param encoding the file's encoding.
     * @return the content of the text file.
     * @throws IOException if the file cannot be read.
     * @throws IllegalStateException if not called from within a test.
     */
    public String getFileContent(String path, Charset encoding) throws IOException {
        byte[] content = getFileContent(path);
        return new String(content, encoding);
    }

    /**
     * Get a file from the SFTP server.
     * @param path the path to the file.
     * @return the content of the file.
     * @throws IOException if the file cannot be read.
     * @throws IllegalStateException if not called from within a test.
     */
    public byte[] getFileContent(String path) throws IOException {
        verifyThatTestIsRunning("download");
        Path pathAsObject = fileSystem.getPath(path);
        return readAllBytes(pathAsObject);
    }

    /**
     * Checks the existence of a file. returns {@code true} iff the file exists
     * and it is not a directory.
     * @param path the path to the file.
     * @return {@code true} iff the file exists and it is not a directory.
     * @throws IllegalStateException if not called from within a test.
     */
    public boolean existsFile(String path) {
        verifyThatTestIsRunning("check existence of");
        Path pathAsObject = fileSystem.getPath(path);
        return exists(pathAsObject) && !isDirectory(pathAsObject);
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try (FileSystem fileSystem = createFileSystem();
                     SshServer server = startServer(fileSystem)) {
                    base.evaluate();
                } finally {
                    fileSystem = null;
                }
            }
        };
    }

    private FileSystem createFileSystem() throws IOException {
        fileSystem = newLinux().build("FakeSftpServerRule@" + hashCode());
        return fileSystem;
    }

    private SshServer startServer(FileSystem fileSystem) throws IOException {
        SshServer server = SshServer.setUpDefaultServer();
        server.setPort(PORT);
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
        server.setPasswordAuthenticator(new StaticPasswordAuthenticator(true));
        server.setSubsystemFactories(singletonList(new SftpSubsystemFactory()));
        /* When a channel is closed SshServer calls close() on the file system.
         * In order to use the file system for multiple channels/sessions we
         * have to use a file system wrapper whose close() does nothing.
         */
        server.setFileSystemFactory(session -> new DoNotClose(fileSystem));
        server.start();
        return server;
    }

    private void verifyThatTestIsRunning(String mode) {
        if (fileSystem == null)
            throw new IllegalStateException("Failed to " + mode + " file because"
                + " test has not been started or is already finished.");
    }

    private static class DoNotClose extends FileSystem {
        final FileSystem fileSystem;

        DoNotClose(FileSystem fileSystem) {
            this.fileSystem = fileSystem;
        }

        @Override
        public FileSystemProvider provider() {
            return fileSystem.provider();
        }

        @Override
        public void close() throws IOException {
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
}
