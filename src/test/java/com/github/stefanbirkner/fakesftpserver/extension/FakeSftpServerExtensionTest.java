package com.github.stefanbirkner.fakesftpserver.extension;


import com.jcraft.jsch.*;
import org.apache.commons.io.IOUtils;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.ConnectException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Vector;

import static com.github.stefanbirkner.fishbowl.Fishbowl.exceptionThrownBy;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.io.IOUtils.toByteArray;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/* Wording according to the draft:
 * http://tools.ietf.org/html/draft-ietf-secsh-filexfer-13
 */
class FakeSftpServerExtensionTest {

    public static final String DUMMY_PASSWORD = "dummy password";
    public static final String DUMMY_USER = "dummy user";

    private static final byte[] DUMMY_CONTENT = new byte[]{1, 4, 2, 4, 2, 4};
    private static final int DUMMY_PORT = 46354;
    private static final JSch JSCH = new JSch();
    private static final int TIMEOUT = 500;

    private static final int HIGHEST_PORT = 65535;
    private static final int LOWEST_PORT = 1026;


    @RegisterExtension
    private final FakeSftpServerExtension sftpServer = new FakeSftpServerExtension();

    private static Session createSessionWithCredentials(FakeSftpServerExtension sftpServer,
                                                        String password) throws JSchException {
        return FakeSftpServerExtensionTest.createSessionWithCredentials(password, sftpServer.getPort());
    }

    private static void assertAuthenticationFails(ThrowingCallable connectToServer) {
        assertThatThrownBy(connectToServer)
            .isInstanceOf(JSchException.class)
            .hasMessage("Auth fail");
    }

    private static void assertEmptyDirectory(FakeSftpServerExtension sftpServer,
                                             String directory) throws JSchException, SftpException {
        Session session = connectToServer(sftpServer);
        ChannelSftp channel = connectSftpChannel(session);
        Vector<?> entries = channel.ls(directory);
        assertThat(entries).hasSize(2); //these are the entries "." and ".."
        channel.disconnect();
        session.disconnect();
    }

    private static void assertFileDoesNotExist(FakeSftpServerExtension sftpServer, String path) {
        boolean exists = sftpServer.existsFile(path);
        assertThat(exists).isFalse();
    }

    private static void assertDirectoryDoesNotExist(FakeSftpServerExtension sftpServer) throws JSchException {
        Session session = connectToServer(sftpServer);
        ChannelSftp channel = connectSftpChannel(session);
        try {
            assertThatThrownBy(() -> channel.ls("/dummy_directory"))
                .isInstanceOf(SftpException.class)
                .hasMessage("No such file or directory");
        } finally {
            channel.disconnect();
            session.disconnect();
        }
    }

    private static Session connectToServer(FakeSftpServerExtension sftpServer) throws JSchException {
        return connectToServerAtPort(sftpServer.getPort());
    }

    private static Session connectToServerAtPort(int port) throws JSchException {
        Session session = createSessionWithCredentials(DUMMY_PASSWORD, port);
        session.connect(TIMEOUT);
        return session;
    }

    private static ChannelSftp connectSftpChannel(Session session) throws JSchException {
        ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
        channel.connect();
        return channel;
    }

    private static void connectAndDisconnect(FakeSftpServerExtension sftpServer) throws JSchException {
        Session session = connectToServer(sftpServer);
        ChannelSftp channel = connectSftpChannel(session);
        channel.disconnect();
        session.disconnect();
    }

    private static Session createSessionWithCredentials(String password, int port) throws JSchException {
        Session session = JSCH.getSession(DUMMY_USER, "127.0.0.1", port);
        session.setConfig("StrictHostKeyChecking", "no");
        session.setPassword(password);
        return session;
    }

    private static byte[] downloadFile(FakeSftpServerExtension server, String path) throws Exception {
        Session session = connectToServer(server);
        ChannelSftp channel = connectSftpChannel(session);
        try {
            InputStream is = channel.get(path);
            return toByteArray(is);
        } finally {
            channel.disconnect();
            session.disconnect();
        }
    }

    private static void uploadFile(FakeSftpServerExtension server,
                                   String pathAsString,
                                   byte[] content) throws Exception {
        Session session = connectToServer(server);
        ChannelSftp channel = connectSftpChannel(session);
        try {
            Path path = Paths.get(pathAsString);
            if (!path.getParent().equals(path.getRoot()))
                channel.mkdir(path.getParent().toString());
            channel.put(new ByteArrayInputStream(content), pathAsString);
        } finally {
            channel.disconnect();
            session.disconnect();
        }
    }

    @Test
    void a_file_that_is_written_to_the_SFTP_server_can_be_read() throws Exception {
        Session session = connectToServer(sftpServer);
        ChannelSftp channel = connectSftpChannel(session);
        channel.put(new ByteArrayInputStream("dummy content".getBytes(UTF_8)),
            "dummy_file.txt"
        );
        InputStream file = channel.get("dummy_file.txt");

        assertThat(IOUtils.toString(file, UTF_8))
            .isEqualTo("dummy content");

        channel.disconnect();
        session.disconnect();
    }

    @Test
    void multiple_connections_to_the_server_are_possible() {
        assertDoesNotThrow(() -> connectAndDisconnect(sftpServer));
        assertDoesNotThrow(() -> connectAndDisconnect(sftpServer));
    }

    @Test
    void a_client_can_connect_to_the_server_at_a_user_specified_port() throws Exception {
        var port = 8394;
        try (var sftpServer = new FakeSftpServerExtension().setPort(port)) {
            sftpServer.beforeEach(null);
            assertDoesNotThrow(() -> connectToServerAtPort(port));
        }
    }

    @Test
    void the_server_accepts_connections_with_password() throws Exception {
        Session session = createSessionWithCredentials(
            sftpServer,
            DUMMY_PASSWORD
        );
        assertDoesNotThrow(() -> session.connect(TIMEOUT));
    }

    @Test
    void the_server_rejects_connections_with_wrong_password() throws JSchException {
        sftpServer.addUser(DUMMY_USER, "correct password");

        Session session = createSessionWithCredentials(
            sftpServer,
            "wrong password"
        );
        assertAuthenticationFails(
            () -> session.connect(TIMEOUT)
        );
    }

    @Test
    void the_last_password_is_effective_if_addUser_is_called_multiple_times() throws JSchException {
        sftpServer.addUser(DUMMY_USER, "first password")
            .addUser(DUMMY_USER, "second password");

        Session session = createSessionWithCredentials(
            sftpServer,
            "second password"
        );

        assertDoesNotThrow(() -> session.connect(TIMEOUT));
    }

    @Test
    void the_server_accepts_connections_with_correct_password() throws JSchException {
        sftpServer.addUser(DUMMY_USER, DUMMY_PASSWORD);
        Session session = createSessionWithCredentials(
            sftpServer,
            DUMMY_PASSWORD
        );
        assertDoesNotThrow(() -> session.connect(TIMEOUT));
    }

    @Test
    void that_is_put_to_root_directory_via_the_rule_can_be_read_from_server() throws Exception {
        sftpServer.putFile(
            "/dummy_file.txt",
            "dummy content with umlaut ü",
            UTF_8
        );
        byte[] file = downloadFile(sftpServer, "/dummy_file.txt");
        assertThat(new String(file, UTF_8))
            .isEqualTo("dummy content with umlaut ü");
    }

    @Test
    void that_is_put_to_directory_via_the_rule_can_be_read_from_server() throws Exception {
        sftpServer.putFile(
            "/dummy_directory/dummy_file.txt",
            "dummy content with umlaut ü",
            UTF_8
        );
        byte[] file = downloadFile(
            sftpServer,
            "/dummy_directory/dummy_file.txt"
        );
        assertThat(new String(file, UTF_8))
            .isEqualTo("dummy content with umlaut ü");

    }

    @Test
    void cannot_be_put_before_the_test_is_started() {
        var sftpServer = new FakeSftpServerExtension();
        Throwable exception = exceptionThrownBy(
            () -> sftpServer.putFile(
                "/dummy_file.txt", "dummy content", UTF_8
            )
        );
        assertThat(exception)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(
                "Failed to upload file because test has not been started"
                    + " or is already finished."
            );
    }

    @Test
    void cannot_be_put_after_the_test_is_finished() throws Exception {
        sftpServer.afterEach(null);

        Throwable exception = exceptionThrownBy(
            () -> sftpServer.putFile(
                "/dummy_file.txt", "dummy content", UTF_8
            )
        );
        assertThat(exception)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(
                "Failed to upload file because test has not been started"
                    + " or is already finished."
            );
    }

    @Test
    void that_is_created_with_the_rule_can_be_read_by_a_client() throws Exception {
        sftpServer.createDirectory("/a/directory");
        assertEmptyDirectory(sftpServer, "/a/directory");
    }

    @Test
    void cannot_be_created_before_the_test_is_started() {
        FakeSftpServerExtension sftpServer = new FakeSftpServerExtension();
        Throwable exception = exceptionThrownBy(
            () -> sftpServer.createDirectory("/a/directory")
        );
        assertThat(exception)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(
                "Failed to create directory because test has not been"
                    + " started or is already finished."
            );
    }

    @Test
    void cannot_be_created_after_the_test_is_finished() throws Exception {
        sftpServer.afterEach(null);

        Throwable exception = exceptionThrownBy(
            () -> sftpServer.createDirectory("/a/directory")
        );
        assertThat(exception)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(
                "Failed to create directory because test has not been"
                    + " started or is already finished."
            );
    }

    @Test
    void that_are_created_with_the_rule_can_be_read_by_a_client() throws Exception {
        sftpServer.createDirectories(
            "/a/directory",
            "/another/directory"
        );
        assertEmptyDirectory(sftpServer, "/a/directory");
        assertEmptyDirectory(sftpServer, "/another/directory");
    }

    @Test
    void that_is_written_to_the_server_can_be_retrieved_with_the_rule() throws Exception {
        uploadFile(
            sftpServer,
            "/dummy_directory/dummy_file.txt",
            "dummy content with umlaut ü".getBytes(UTF_8)
        );
        String fileContent = sftpServer.getFileContent(
            "/dummy_directory/dummy_file.txt",
            UTF_8
        );
        assertThat(fileContent)
            .isEqualTo("dummy content with umlaut ü");
    }

    @Test
    void cannot_be_retrieved_before_the_test_is_started() {
        FakeSftpServerExtension sftpServer = new FakeSftpServerExtension();
        Throwable exception = exceptionThrownBy(
            () -> sftpServer.getFileContent(
                "/dummy_directory/dummy_file.txt"
            )
        );
        assertThat(exception)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(
                "Failed to download file because test has not been started"
                    + " or is already finished."
            );
    }

    @Test
    void cannot_be_retrieved_after_the_test_is_finished() throws Exception {
        uploadFile(sftpServer,
            "/dummy_directory/dummy_file.txt",
            "dummy content".getBytes(UTF_8)
        );

        sftpServer.afterEach(null);

        Throwable exception = exceptionThrownBy(
            () -> sftpServer.getFileContent(
                "/dummy_directory/dummy_file.txt"
            )
        );

        assertThat(exception)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(
                "Failed to download file because test has not been started"
                    + " or is already finished."
            );
    }

    @Test
    void exists_returns_true_for_a_file_that_exists_on_the_server() throws Exception {
        uploadFile(sftpServer,
            "/dummy_directory/dummy_file.bin",
            DUMMY_CONTENT
        );

        boolean exists = sftpServer.existsFile("/dummy_directory/dummy_file.bin");
        assertThat(exists).isTrue();
    }

    @Test
    void exists_returns_false_for_a_file_that_does_not_exists_on_the_server() {
        assertThat(sftpServer.existsFile("/dummy_directory/dummy_file.bin")).isFalse();
    }

    @Test
    void exists_returns_false_for_a_directory_that_exists_on_the_server() throws Exception {
        uploadFile(sftpServer,
            "/dummy_directory/dummy_file.bin",
            DUMMY_CONTENT
        );

        assertThat(sftpServer.existsFile("/dummy_directory")).isFalse();
    }

    @Test
    void existence_of_a_file_cannot_be_checked_before_the_test_is_started() {
        FakeSftpServerExtension sftpServer = new FakeSftpServerExtension();
        Throwable exception = exceptionThrownBy(() -> sftpServer.existsFile("/dummy_file.bin"));

        assertThat(exception)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(
                "Failed to check existence of file because test has not"
                    + " been started or is already finished."
            );
    }

    @Test
    void existence_of_a_file_cannot_be_checked_after_the_test_is_finished() throws Exception {
        sftpServer.afterEach(null);

        Throwable exception = exceptionThrownBy(() -> sftpServer.existsFile("/dummy_file.bin"));
        assertThat(exception)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(
                "Failed to check existence of file because test has not"
                    + " been started or is already finished."
            );
    }

    @Test
    void after_a_successful_test_SFTP_server_is_shutdown() throws Exception {
        sftpServer.setPort(DUMMY_PORT).afterEach(null);
        assertConnectionToSftpServerNotPossible(DUMMY_PORT);
    }

    @Test
    void after_a_test_first_SFTP_server_is_shutdown_when_port_was_changed_during_test() {
        sftpServer.setPort(DUMMY_PORT - 1);
        sftpServer.setPort(DUMMY_PORT);
        assertConnectionToSftpServerNotPossible(DUMMY_PORT - 1);
    }

    private void assertConnectionToSftpServerNotPossible(int port) {
        Throwable throwable = catchThrowable(() -> connectToServerAtPort(port));
        assertThat(throwable)
            .withFailMessage(
                "SFTP server is still running on port %d.",
                port
            )
            .hasCauseInstanceOf(ConnectException.class);
    }

    @Test
    void by_default_two_rules_run_servers_at_different_ports() throws Exception {
        FakeSftpServerExtension secondSftpServer = new FakeSftpServerExtension();
        secondSftpServer.beforeEach(null);

        assertThat(sftpServer.getPort()).isNotEqualTo(secondSftpServer.getPort());
    }

    @Test
    void the_port_can_be_changed_during_the_test() {
        sftpServer.setPort(DUMMY_PORT);
        assertDoesNotThrow(() -> connectToServerAtPort(DUMMY_PORT));
    }

    @Test
    void testing_border_port_values() {
        var invalidPorts = List.of(-1, 0, HIGHEST_PORT + 1);

        invalidPorts.forEach(port -> assertThatThrownBy(() -> sftpServer.setPort(port))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Port cannot be set to " + port + " because only ports between 1 and 65535 are valid."));
    }

    @Test
    void the_port_can_be_set_to_1() {
        // test must run as root to use a port <1024
        // this code should not run as root
        assertThatThrownBy(() -> sftpServer.setPort(1))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("The SFTP server cannot be restarted.");
    }

    @Test
    void the_server_can_be_run_at_highest_port() {
        sftpServer.setPort(HIGHEST_PORT);
        assertDoesNotThrow(() -> connectToServerAtPort(HIGHEST_PORT));
    }

    @Test
    void cannot_be_read_before_the_test() {
        FakeSftpServerExtension sftpServer = new FakeSftpServerExtension();
        assertPortCannotBeRead(sftpServer);
    }

    @Test
    void can_be_read_during_the_test() {
        assertThat(sftpServer.getPort())
            .isBetween(LOWEST_PORT, HIGHEST_PORT);
    }

    @Test
    void cannot_be_read_after_the_test() {
        FakeSftpServerExtension sftpServer = new FakeSftpServerExtension();
        assertPortCannotBeRead(sftpServer);
    }

    private void assertPortCannotBeRead(FakeSftpServerExtension sftpServer) {
        assertThatThrownBy(sftpServer::getPort)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Failed to call getPort() because test has not been started or is already finished.");
    }

    @Test
    void can_be_read_before_the_test() {
        FakeSftpServerExtension sftpServer = new FakeSftpServerExtension()
            .setPort(DUMMY_PORT);
        int port = sftpServer.getPort();
        assertThat(port).isEqualTo(DUMMY_PORT);
    }

    @Test
    void can_be_read_after_the_test() throws Exception {
        sftpServer.setPort(DUMMY_PORT).afterEach(null);
        int port = sftpServer.getPort();
        assertThat(port).isEqualTo(DUMMY_PORT);
    }

    @Test
    void deletes_file_in_root_directory() throws Exception {
        uploadFile(sftpServer, "/dummy_file.bin", DUMMY_CONTENT);
        sftpServer.deleteAllFilesAndDirectories();
        assertFileDoesNotExist(sftpServer, "/dummy_file.bin");
    }

    @Test
    void deletes_file_in_directory() throws Exception {
        uploadFile(sftpServer, "/dummy_directory/dummy_file.bin", DUMMY_CONTENT);
        sftpServer.deleteAllFilesAndDirectories();
        assertFileDoesNotExist(sftpServer, "/dummy_directory/dummy_file.bin");
    }

    @Test
    void deletes_directory() throws Exception {
        sftpServer.createDirectory("/dummy_directory");
        sftpServer.deleteAllFilesAndDirectories();
        assertDirectoryDoesNotExist(sftpServer);
    }

    @Test
    void works_on_an_empty_filesystem() {
        assertDoesNotThrow(sftpServer::deleteAllFilesAndDirectories);
    }
}
