package com.github.stefanbirkner.fakesftpserver.rule;

import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.password.StaticPasswordAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.subsystem.sftp.SftpSubsystemFactory;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.IOException;
import java.nio.file.FileSystem;

import static com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder.newLinux;
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
 */
public class FakeSftpServerRule implements TestRule {
    private static final int PORT = 23454;

    /**
     * Returns the port of the SFTP server.
     *
     * @return the port of the SFTP server.
     */
    public int getPort() {
        return PORT;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try (FileSystem fileSystem = createFileSystem();
                     SshServer server = startServer(fileSystem)) {
                    base.evaluate();
                }
            }
        };
    }

    private FileSystem createFileSystem() throws IOException {
        return newLinux().build("FakeSftpServerRule@" + hashCode());
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
}
