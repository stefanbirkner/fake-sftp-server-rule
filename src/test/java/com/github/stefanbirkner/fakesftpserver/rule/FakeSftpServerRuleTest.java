package com.github.stefanbirkner.fakesftpserver.rule;


import com.jcraft.jsch.*;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Vector;

import static com.github.stefanbirkner.fakesftpserver.rule.Executor.executeTestThatThrowsExceptionWithRule;
import static com.github.stefanbirkner.fakesftpserver.rule.Executor.executeTestWithRule;
import static com.github.stefanbirkner.fishbowl.Fishbowl.exceptionThrownBy;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.io.IOUtils.toByteArray;
import static org.assertj.core.api.Assertions.assertThat;

/* Wording according to the draft:
 * http://tools.ietf.org/html/draft-ietf-secsh-filexfer-13
 */
public class FakeSftpServerRuleTest {
    private static final byte[] DUMMY_CONTENT = new byte[]{1, 4, 2, 4, 2, 4};
    private static final InputStream DUMMY_STREAM = new ByteArrayInputStream(DUMMY_CONTENT);
    private static final JSch JSCH = new JSch();
    private static final int TIMEOUT = 100;

    @Test
    public void SFTP_server_accepts_connections_with_password() {
        FakeSftpServerRule sftpServer = new FakeSftpServerRule();
        executeTestWithRule(
            () -> connectToServer(sftpServer),
            sftpServer);
    }

    @Test
    public void a_file_that_is_written_to_the_SFTP_server_can_be_read() {
        FakeSftpServerRule sftpServer = new FakeSftpServerRule();
        executeTestWithRule(() -> {
            Session session = connectToServer(sftpServer);
            ChannelSftp channel = connectSftpChannel(session);
            channel.put(
                new ByteArrayInputStream("dummy content".getBytes(UTF_8)),
                "dummy_file.txt");
            InputStream file = channel.get("dummy_file.txt");
            assertThat(IOUtils.toString(file, UTF_8))
                .isEqualTo("dummy content");
            channel.disconnect();
            session.disconnect();
        }, sftpServer);
    }

    @Test
    public void a_text_file_that_is_put_to_root_directory_via_the_rule_can_be_read_from_server() {
        FakeSftpServerRule sftpServer = new FakeSftpServerRule();
        executeTestWithRule(() -> {
            sftpServer.putFile("/dummy_file.txt", "dummy content with umlaut ü",
                UTF_8);
            byte[] file = downloadFile(sftpServer, "/dummy_file.txt");
            assertThat(new String(file, UTF_8))
                .isEqualTo("dummy content with umlaut ü");
        }, sftpServer);
    }

    @Test
    public void a_text_file_that_is_put_to_directory_via_the_rule_can_be_read_from_server() {
        FakeSftpServerRule sftpServer = new FakeSftpServerRule();
        executeTestWithRule(() -> {
            sftpServer.putFile("/dummy_directory/dummy_file.txt",
                "dummy content with umlaut ü", UTF_8);
            byte[] file = downloadFile(sftpServer,
                "/dummy_directory/dummy_file.txt");
            assertThat(new String(file, UTF_8))
                .isEqualTo("dummy content with umlaut ü");
        }, sftpServer);
    }

    @Test
    public void a_text_file_cannot_be_put_before_the_test_is_started() {
        FakeSftpServerRule sftpServer = new FakeSftpServerRule();
        Throwable exception = exceptionThrownBy(() ->
            sftpServer.putFile("/dummy_file.txt", "dummy content", UTF_8));
        assertThat(exception)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Failed to upload file because test has not been"
                + " started or is already finished.");
    }

    @Test
    public void a_text_file_cannot_be_put_after_the_test_is_finished() {
        FakeSftpServerRule sftpServer = new FakeSftpServerRule();
        executeTestWithRule(() -> {
        }, sftpServer);
        Throwable exception = exceptionThrownBy(() ->
            sftpServer.putFile("/dummy_file.txt", "dummy content", UTF_8));
        assertThat(exception)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Failed to upload file because test has not been"
                + " started or is already finished.");
    }

    @Test
    public void a_binary_file_that_is_put_to_root_directory_via_the_rule_can_be_read_from_server() {
        FakeSftpServerRule sftpServer = new FakeSftpServerRule();
        executeTestWithRule(() -> {
            sftpServer.putFile("/dummy_file.bin", DUMMY_CONTENT);
            byte[] file = downloadFile(sftpServer, "/dummy_file.bin");
            assertThat(file).isEqualTo(DUMMY_CONTENT);
        }, sftpServer);
    }

    @Test
    public void a_binary_file_that_is_put_to_directory_via_the_rule_can_be_read_from_server() {
        FakeSftpServerRule sftpServer = new FakeSftpServerRule();
        executeTestWithRule(() -> {
            sftpServer.putFile("/dummy_directory/dummy_file.bin",
                DUMMY_CONTENT);
            byte[] file = downloadFile(sftpServer,
                "/dummy_directory/dummy_file.bin");
            assertThat(file).isEqualTo(DUMMY_CONTENT);
        }, sftpServer);
    }

    @Test
    public void a_binary_file_cannot_be_put_before_the_test_is_started() {
        FakeSftpServerRule sftpServer = new FakeSftpServerRule();
        Throwable exception = exceptionThrownBy(() ->
            sftpServer.putFile("/dummy_file.bin", DUMMY_CONTENT));
        assertThat(exception)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Failed to upload file because test has not been"
                + " started or is already finished.");
    }

    @Test
    public void a_binary_file_cannot_be_put_after_the_test_is_finished() {
        FakeSftpServerRule sftpServer = new FakeSftpServerRule();
        executeTestWithRule(() -> {
        }, sftpServer);
        Throwable exception = exceptionThrownBy(() ->
            sftpServer.putFile("/dummy_file.bin", DUMMY_CONTENT));
        assertThat(exception)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Failed to upload file because test has not been"
                + " started or is already finished.");
    }

    @Test
    public void a_file_from_a_stream_that_is_put_to_root_directory_via_the_rule_can_be_read_from_server() {
        FakeSftpServerRule sftpServer = new FakeSftpServerRule();
        executeTestWithRule(() -> {
            InputStream is = new ByteArrayInputStream(DUMMY_CONTENT);
            sftpServer.putFile("/dummy_file.bin", is);
            byte[] file = downloadFile(sftpServer, "/dummy_file.bin");
            assertThat(file).isEqualTo(DUMMY_CONTENT);
        }, sftpServer);
    }

    @Test
    public void a_file_from_a_stream_that_is_put_to_directory_via_the_rule_can_be_read_from_server() {
        FakeSftpServerRule sftpServer = new FakeSftpServerRule();
        executeTestWithRule(() -> {
            InputStream is = new ByteArrayInputStream(DUMMY_CONTENT);
            sftpServer.putFile("/dummy_directory/dummy_file.bin", is);
            byte[] file = downloadFile(sftpServer,
                "/dummy_directory/dummy_file.bin");
            assertThat(file).isEqualTo(DUMMY_CONTENT);
        }, sftpServer);
    }

    @Test
    public void a_file_from_a_stream_cannot_be_put_before_the_test_is_started() {
        FakeSftpServerRule sftpServer = new FakeSftpServerRule();
        Throwable exception = exceptionThrownBy(() ->
            sftpServer.putFile("/dummy_file.bin", DUMMY_STREAM));
        assertThat(exception)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Failed to upload file because test has not been"
                + " started or is already finished.");
    }

    @Test
    public void a_file_from_a_stream_cannot_be_put_after_the_test_is_finished() {
        FakeSftpServerRule sftpServer = new FakeSftpServerRule();
        executeTestWithRule(() -> {
        }, sftpServer);
        Throwable exception = exceptionThrownBy(() ->
            sftpServer.putFile("/dummy_file.bin", DUMMY_STREAM));
        assertThat(exception)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Failed to upload file because test has not been"
                + " started or is already finished.");
    }

    @Test
    public void a_directory_that_is_created_with_the_rule_can_be_read_by_a_client() {
        FakeSftpServerRule sftpServer = new FakeSftpServerRule();
        executeTestWithRule(() -> {
            sftpServer.createDirectory("/a/directory");
            Session session = connectToServer(sftpServer);
            ChannelSftp channel = connectSftpChannel(session);
            Vector entries = channel.ls("/a/directory");
            assertThat(entries).hasSize(2); //these are the entries . and ..
            channel.disconnect();
            session.disconnect();
        }, sftpServer);
    }

    @Test
    public void a_directory_cannot_be_created_before_the_test_is_started() {
        FakeSftpServerRule sftpServer = new FakeSftpServerRule();
        Throwable exception = exceptionThrownBy(() ->
            sftpServer.createDirectory("/a/directory"));
        assertThat(exception)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Failed to create directory because test has not been"
                + " started or is already finished.");
    }

    @Test
    public void a_directory_cannot_be_created_after_the_test_is_finished() {
        FakeSftpServerRule sftpServer = new FakeSftpServerRule();
        executeTestWithRule(() -> {
        }, sftpServer);
        Throwable exception = exceptionThrownBy(() ->
            sftpServer.createDirectory("/a/directory"));
        assertThat(exception)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Failed to create directory because test has not been"
                + " started or is already finished.");
    }

    @Test
    public void a_text_file_that_is_written_to_the_server_can_be_retrieved_with_the_rule() {
        FakeSftpServerRule sftpServer = new FakeSftpServerRule();
        executeTestWithRule(() -> {
            uploadFile(sftpServer, "/dummy_directory/dummy_file.txt",
                "dummy content with umlaut ü".getBytes(UTF_8));
            String fileContent = sftpServer.getFileContent(
                "/dummy_directory/dummy_file.txt", UTF_8);
            assertThat(fileContent)
                .isEqualTo("dummy content with umlaut ü");
        }, sftpServer);
    }

    @Test
    public void a_text_file_cannot_be_retrieved_before_the_test_is_started() {
        FakeSftpServerRule sftpServer = new FakeSftpServerRule();
        Throwable exception = exceptionThrownBy(() ->
            sftpServer.getFileContent("/dummy_directory/dummy_file.txt"));
        assertThat(exception)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Failed to download file because test has not been"
                + " started or is already finished.");
    }

    @Test
    public void a_text_file_cannot_be_retrieved_after_the_test_is_finished() {
        FakeSftpServerRule sftpServer = new FakeSftpServerRule();
        executeTestWithRule(() -> uploadFile(sftpServer,
            "/dummy_directory/dummy_file.txt", "dummy content".getBytes(UTF_8)),
            sftpServer);
        Throwable exception = exceptionThrownBy(() ->
            sftpServer.getFileContent("/dummy_directory/dummy_file.txt"));
        assertThat(exception)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Failed to download file because test has not been"
                + " started or is already finished.");
    }

    @Test
    public void a_binary_file_that_is_written_to_the_server_can_be_retrieved_with_the_rule() {
        FakeSftpServerRule sftpServer = new FakeSftpServerRule();
        executeTestWithRule(() -> {
            uploadFile(sftpServer, "/dummy_directory/dummy_file.bin",
                DUMMY_CONTENT);
            byte[] fileContent = sftpServer.getFileContent(
                "/dummy_directory/dummy_file.bin");
            assertThat(fileContent).isEqualTo(DUMMY_CONTENT);
        }, sftpServer);
    }

    @Test
    public void a_binary_file_cannot_be_retrieved_before_the_test_is_started() {
        FakeSftpServerRule sftpServer = new FakeSftpServerRule();
        Throwable exception = exceptionThrownBy(() ->
            sftpServer.getFileContent("/dummy_directory/dummy_file.bin"));
        assertThat(exception)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Failed to download file because test has not been"
                + " started or is already finished.");
    }

    @Test
    public void a_binary_file_cannot_be_retrieved_after_the_test_is_finished() {
        FakeSftpServerRule sftpServer = new FakeSftpServerRule();
        executeTestWithRule(() -> uploadFile(sftpServer,
            "/dummy_directory/dummy_file.bin", DUMMY_CONTENT),
            sftpServer);
        Throwable exception = exceptionThrownBy(() ->
            sftpServer.getFileContent("/dummy_file.bin"));
        assertThat(exception)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Failed to download file because test has not been"
                + " started or is already finished.");
    }

    @Test
    public void exists_returns_true_for_a_file_that_exists_on_the_server() {
        FakeSftpServerRule sftpServer = new FakeSftpServerRule();
        executeTestWithRule(() -> {
            uploadFile(
                sftpServer, "/dummy_directory/dummy_file.bin", DUMMY_CONTENT);
            boolean exists = sftpServer.existsFile(
                "/dummy_directory/dummy_file.bin");
            assertThat(exists).isTrue();
        }, sftpServer);
    }

    @Test
    public void exists_returns_false_for_a_file_that_does_not_exists_on_the_server() {
        FakeSftpServerRule sftpServer = new FakeSftpServerRule();
        executeTestWithRule(() -> {
            boolean exists = sftpServer.existsFile(
                "/dummy_directory/dummy_file.bin");
            assertThat(exists).isFalse();
        }, sftpServer);
    }

    @Test
    public void exists_returns_false_for_a_directory_that_exists_on_the_server() {
        FakeSftpServerRule sftpServer = new FakeSftpServerRule();
        executeTestWithRule(() -> {
            uploadFile(
                sftpServer, "/dummy_directory/dummy_file.bin", DUMMY_CONTENT);
            boolean exists = sftpServer.existsFile("/dummy_directory");
            assertThat(exists).isFalse();
        }, sftpServer);
    }

    @Test
    public void existence_of_a_file_cannot_be_checked_before_the_test_is_started() {
        FakeSftpServerRule sftpServer = new FakeSftpServerRule();
        Throwable exception = exceptionThrownBy(() ->
            sftpServer.existsFile("/dummy_file.bin"));
        assertThat(exception)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Failed to check existence of file because test has not"
                + " been started or is already finished.");
    }

    @Test
    public void existence_of_a_file_cannot_be_checked_after_the_test_is_finished() {
        FakeSftpServerRule sftpServer = new FakeSftpServerRule();
        executeTestWithRule(() -> {
        }, sftpServer);
        Throwable exception = exceptionThrownBy(() ->
            sftpServer.existsFile("/dummy_file.bin"));
        assertThat(exception)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Failed to check existence of file because test has not"
                + " been started or is already finished.");
    }

    @Test
    public void multiple_connections_to_the_server_are_possible() {
        FakeSftpServerRule sftpServer = new FakeSftpServerRule();
        executeTestWithRule(() -> {
            connectAndDisconnect(sftpServer);
            connectAndDisconnect(sftpServer);
        }, sftpServer);
    }

    @Test
    public void after_a_successful_test_SFTP_server_is_shutdown() {
        FakeSftpServerRule sftpServer = new FakeSftpServerRule();
        executeTestWithRule(() -> {
        }, sftpServer);
        Throwable exception = exceptionThrownBy(
            () -> connectToServer(sftpServer));
        assertThat(exception).hasCauseInstanceOf(ConnectException.class);
    }

    @Test
    public void after_an_erroneous_test_SFTP_server_is_shutdown() {
        FakeSftpServerRule sftpServer = new FakeSftpServerRule();
        executeTestThatThrowsExceptionWithRule(() -> {
            throw new RuntimeException();
        }, sftpServer);
        Throwable exception = exceptionThrownBy(
            () -> connectToServer(sftpServer));
        assertThat(exception).hasCauseInstanceOf(ConnectException.class);
    }

    private Session connectToServer(FakeSftpServerRule sftpServer)
        throws JSchException {
        Session session = JSCH.getSession(
            "dummy user", "127.0.0.1", sftpServer.getPort());
        session.setConfig("StrictHostKeyChecking", "no");
        session.setPassword("dummy password");
        session.connect(TIMEOUT);
        return session;
    }

    private ChannelSftp connectSftpChannel(Session session)
            throws JSchException {
        ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
        channel.connect();
        return channel;
    }

    private void connectAndDisconnect(FakeSftpServerRule sftpServer)
            throws JSchException {
        Session session = connectToServer(sftpServer);
        ChannelSftp channel = connectSftpChannel(session);
        channel.disconnect();
        session.disconnect();
    }

    private byte[] downloadFile(FakeSftpServerRule server, String path)
            throws JSchException, SftpException, IOException {
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

    private void uploadFile(FakeSftpServerRule server, String pathAsString,
            byte[] content) throws JSchException, SftpException, IOException {
        Session session = connectToServer(server);
        ChannelSftp channel = connectSftpChannel(session);
        try {
            Path path = Paths.get(pathAsString);
            channel.mkdir(path.getParent().toString());
            channel.put(new ByteArrayInputStream(content), pathAsString);
        } finally {
            channel.disconnect();
            session.disconnect();
        }

    }
}
