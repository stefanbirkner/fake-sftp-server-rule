package com.github.stefanbirkner.fakesftpserver.rule;


import com.jcraft.jsch.*;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;

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
}
