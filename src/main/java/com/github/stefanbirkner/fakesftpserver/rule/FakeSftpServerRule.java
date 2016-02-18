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
import java.nio.file.FileSystem;
import java.nio.file.Path;

import static com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder.newLinux;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.write;
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
        verifyThatTestIsRunning();
        Path pathAsObject = fileSystem.getPath(path);
        Path directory = pathAsObject.getParent();
        if (directory != null && !directory.equals(pathAsObject.getRoot()))
            createDirectories(directory);
        write(pathAsObject, content);
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
        server.setFileSystemFactory((session) -> fileSystem);
        server.start();
        return server;
    }

    private void verifyThatTestIsRunning() {
        if (fileSystem == null)
            throw new IllegalStateException("Failed to upload file because"
                + " test has not been started or is already finished.");
    }
}
