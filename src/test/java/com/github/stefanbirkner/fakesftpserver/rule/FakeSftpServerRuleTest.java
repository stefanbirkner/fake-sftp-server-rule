package com.github.stefanbirkner.fakesftpserver.rule;


import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.ConnectException;

import static com.github.stefanbirkner.fakesftpserver.rule.Executor.executeTestThatThrowsExceptionWithRule;
import static com.github.stefanbirkner.fakesftpserver.rule.Executor.executeTestWithRule;
import static com.github.stefanbirkner.fishbowl.Fishbowl.exceptionThrownBy;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

/* Wording according to the draft:
 * http://tools.ietf.org/html/draft-ietf-secsh-filexfer-13
 */
public class FakeSftpServerRuleTest {
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
            ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect();
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
}
