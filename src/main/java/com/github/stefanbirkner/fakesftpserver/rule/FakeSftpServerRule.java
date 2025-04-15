package com.github.stefanbirkner.fakesftpserver.rule;

import static com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder.newLinux;
import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.Files.copy;
import static java.nio.file.Files.delete;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.isDirectory;
import static java.nio.file.Files.readAllBytes;
import static java.nio.file.Files.walkFileTree;
import static java.nio.file.Files.write;
import static java.util.Collections.singletonList;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.sshd.common.file.FileSystemFactory;
import org.apache.sshd.common.session.SessionContext;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

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
 * <p>By default the SFTP server listens on an auto-allocated port. During the
 * test this port can be obtained by {@link #getPort() sftpServer.getPort()}. It
 * can be changed by calling {@link #setPort(int)}. If you do this from within a
 * test then the server gets restarted. The time-consuming restart can be
 * avoided by setting the port immediately after creating the rule.
 * <pre>
 * public class TestClass {
 *   &#064;Rule
 *   public final FakeSftpServerRule sftpServer = new FakeSftpServerRule()
 *       .setPort(1234);
 *
 *   ...
 * }
 * </pre>
 * <p>You can interact with the SFTP server by using the SFTP protocol with
 * password authentication. By default the server accepts every pair of
 * username and password, buy you can restrict it to specific pairs.
 * <pre>
 * public class TestClass {
 *   &#064;Rule
 *   public final FakeSftpServerRule sftpServer = new FakeSftpServerRule()
 *       .{@link #addUser(String, String) addUser}("username", "password");
 *
 *   ...
 * }
 * </pre>
 * <p>It is also possible to do this during the test using the same method.
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
 * <p>Test data that is provided as an input stream can be uploaded directly
 * from that input stream. This is very handy if your test data is available as
 * a resource.
 * <pre>
 * &#064;Test
 * public void testFileFromInputStream() {
 *   InputStream is = getClass().getResourceAsStream("data.bin");
 *   {@link #putFile(String, InputStream) sftpServer.putFile}("/directory/file.bin", is);
 *   //code that downloads the file
 * }
 * </pre>
 * <p>If you need an empty directory then you can use the method
 * {@link #createDirectory(String)}.
 * <pre>
 * &#064;Test
 * public void testDirectory() {
 *   sftpServer.{@link #createDirectory(String) createDirectory}("/a/directory");
 *   //code that reads from or writes to that directory
 * }
 * </pre>
 * <p>You may create multiple directories at once with
 * {@link #createDirectories(String...)}.
 * <pre>
 * &#064;Test
 * public void testDirectories() {
 *   sftpServer.{@link #createDirectories(String...) createDirectories}(
 *     "/a/directory",
 *     "/another/directory"
 *   );
 *   //code that reads from or writes to that directories
 * }
 * </pre>
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
 *
 * <h2>Delete all files</h2>
 * <p>If you want to reuse the SFTP server then you can delete all files and
 * directories on the SFTP server. (This is rarely necessary because the rule
 * itself takes care that every test starts and ends with a clean SFTP server.)
 * <pre>{@link #deleteAllFilesAndDirectories() sftpServer.deleteAllFilesAndDirectories()};</pre>
 */
public class FakeSftpServerRule implements TestRule {
    private static final SimpleFileVisitor<Path> DELETE_FILES_AND_DIRECTORIES
        = new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(
                Path file,
                BasicFileAttributes attrs
            ) throws IOException {
                delete(file);
                return CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(
                Path dir,
                IOException exc
            ) throws IOException {
                if (dir.getParent() != null) {
					delete(dir);
				}
                return super.postVisitDirectory(dir, exc);
            }
    };
    private final Map<String, String> usernamesAndPasswords = new HashMap<>();
    private int port = 0;

    private FileSystem fileSystem;
    private SshServer server;

    /**
     * Returns the port of the SFTP server. If the SFTP server listens on an
     * auto-allocated port (that means you didn't call {@link #setPort(int)})
     * then you can only call this method during the test.
     *
     * @return the port of the SFTP server.
     * @throws IllegalStateException if you call the method outside of a test
     * but haven't called {@link #setPort(int)}) before.
     */
    public int getPort() {
        if (port == 0) {
			return getPortFromServer();
		} else {
			return port;
		}
    }

    private int getPortFromServer() {
        verifyThatTestIsRunning("call getPort()");
        return server.getPort();
    }

    /**
     * Set the port of the SFTP server. The SFTP server gets restarted if you
     * call {@code setPort} from within a test. The time-consuming restart can
     * be avoided by setting the port immediately after creating the rule.
     * @param port the port. Must be between 1 and 65535.
     * @return the rule itself.
     * @throws IllegalArgumentException if the port is not between 1 and 65535.
     * @throws IllegalStateException if the server cannot be restarted.
     */
    public FakeSftpServerRule setPort(
        int port
    ) {
        if (port < 1 || port > 65535) {
			throw new IllegalArgumentException(
                "Port cannot be set to " + port
                    + " because only ports between 1 and 65535 are valid."
            );
		}
        this.port = port;
        if (server != null) {
			restartServer();
		}
        return this;
    }

    /**
     * Register a username with its password. After registering a username
     * it is only possible to connect to the server with one of the registered
     * username/password pairs.
     * <p>If {@code addUser} is called multiple times with the same username but
     * different passwords then the last password is effective.
     * @param username the username.
     * @param password the password for the specified username.
     * @return the rule itself.
     */
    public FakeSftpServerRule addUser(
        String username,
        String password
    ) {
        usernamesAndPasswords.put(username, password);
        return this;
    }

    private void restartServer() {
        try {
            server.stop();
            startServer(fileSystem);
        } catch (IOException e) {
            throw new IllegalStateException(
                "The SFTP server cannot be restarted.",
                e
            );
        }
    }

    /**
     * Put a text file on the SFTP folder. The file is available by the
     * specified path.
     * @param path the path to the file.
     * @param content the files content.
     * @param encoding the encoding of the file.
     * @throws IOException if the file cannot be written.
     */
    public void putFile(
        String path,
        String content,
        Charset encoding
    ) throws IOException {
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
    public void putFile(
        String path,
        byte[] content
    ) throws IOException {
        verifyThatTestIsRunning("upload file");
        Path pathAsObject = fileSystem.getPath(path);
        ensureDirectoryOfPathExists(pathAsObject);
        write(pathAsObject, content);
    }

    /**
     * Put a file on the SFTP folder. The file is available by the specified
     * path. The file content is read from an {@code InputStream}.
     * @param path the path to the file.
     * @param is an {@code InputStream} that provides the file's content.
     * @throws IOException if the file cannot be written or the input stream
     * cannot be read.
     */
    public void putFile(
        String path,
        InputStream is
    ) throws IOException {
        verifyThatTestIsRunning("upload file");
        Path pathAsObject = fileSystem.getPath(path);
        ensureDirectoryOfPathExists(pathAsObject);
        copy(is, pathAsObject);
    }

    /**
     * Create a directory on the SFTP server.
     * @param path the directory's path.
     * @throws IOException if the directory cannot be created.
     */
    public void createDirectory(
        String path
    ) throws IOException {
        verifyThatTestIsRunning("create directory");
        Path pathAsObject = fileSystem.getPath(path);
        Files.createDirectories(pathAsObject);
    }

    /**
     * Create multiple directories on the SFTP server.
     * @param paths the directories' paths.
     * @throws IOException if at least one directory cannot be created.
     */
    public void createDirectories(
        String... paths
    ) throws IOException {
        for (String path: paths) {
			createDirectory(path);
		}
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
    public String getFileContent(
        String path,
        Charset encoding
    ) throws IOException {
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
    public byte[] getFileContent(
        String path
    ) throws IOException {
        verifyThatTestIsRunning("download file");
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
    public boolean existsFile(
        String path
    ) {
        verifyThatTestIsRunning("check existence of file");
        Path pathAsObject = fileSystem.getPath(path);
        return exists(pathAsObject) && !isDirectory(pathAsObject);
    }

    /**
     * Deletes all files and directories.
     * @throws IOException if an I/O error is thrown while deleting the files
     * and directories
     */
    public void deleteAllFilesAndDirectories() throws IOException {
        for (Path directory: fileSystem.getRootDirectories()) {
			walkFileTree(directory, DELETE_FILES_AND_DIRECTORIES);
		}
    }

    @Override
    public Statement apply(
        Statement base,
        Description description
    ) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try (
                    FileSystem fileSystem = createFileSystem()
                ) {
                    startServer(fileSystem);
                    try {
                        base.evaluate();
                    } finally {
                        server.stop();
                        server = null;
                    }
                } finally {
                    fileSystem = null;
                }
            }
        };
    }

    private FileSystem createFileSystem(
    ) throws IOException {
        fileSystem = newLinux().build("FakeSftpServerRule@" + hashCode());
        return fileSystem;
    }

    private SshServer startServer(
        FileSystem fileSystem
    ) throws IOException {
        SshServer server = SshServer.setUpDefaultServer();
        server.setPort(port);
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
        server.setPasswordAuthenticator(this::authenticate);
        server.setSubsystemFactories(singletonList(new SftpSubsystemFactory()));
        /* When a channel is closed SshServer calls close() on the file system.
         * In order to use the file system for multiple channels/sessions we
         * have to use a file system wrapper whose close() does nothing.
         */
        FileSystemFactory fsFactory = new FileSystemFactory() {
			
			@Override
			public Path getUserHomeDir(SessionContext session) throws IOException {
				return Path.of("/");
			}
			
			@Override
			public FileSystem createFileSystem(SessionContext session) throws IOException {
				return new DoNotClose(fileSystem);
			}
		};
        
        server.setFileSystemFactory(fsFactory);
        server.start();
        this.server = server;
        return server;
    }

    private boolean authenticate(
        String username,
        String password,
        ServerSession session
    ) {
        return usernamesAndPasswords.isEmpty()
            || Objects.equals(
                usernamesAndPasswords.get(username),
                password
            );
    }

    private void ensureDirectoryOfPathExists(
        Path path
    ) throws IOException {
        Path directory = path.getParent();
        if (directory != null && !directory.equals(path.getRoot())) {
			Files.createDirectories(directory);
		}
    }

    private void verifyThatTestIsRunning(
        String mode
    ) {
        if (fileSystem == null) {
			throw new IllegalStateException(
                "Failed to " + mode + " because test has not been started or"
                    + " is already finished."
            );
		}
    }

    private static class DoNotClose extends FileSystem {
        final FileSystem fileSystem;

        DoNotClose(
            FileSystem fileSystem
        ) {
            this.fileSystem = fileSystem;
        }

        @Override
        public FileSystemProvider provider() {
            return fileSystem.provider();
        }

        @Override
        public void close(
        ) throws IOException {
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
        public Path getPath(
            String first,
            String... more
        ) {
            return fileSystem.getPath(first, more);
        }

        @Override
        public PathMatcher getPathMatcher(
            String syntaxAndPattern
        ) {
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
