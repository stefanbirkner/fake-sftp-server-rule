package com.github.stefanbirkner.fakesftpserver.rule;


import static com.github.stefanbirkner.fakesftpserver.rule.Executor.executeTestThatThrowsExceptionWithRule;
import static com.github.stefanbirkner.fakesftpserver.rule.Executor.executeTestWithRule;
import static com.github.stefanbirkner.fishbowl.Fishbowl.exceptionThrownBy;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.io.IOUtils.toByteArray;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

/* Wording according to the draft:
 * http://tools.ietf.org/html/draft-ietf-secsh-filexfer-13
 */
@RunWith(Enclosed.class)
public class FakeSftpServerRuleTest {
    private static final byte[] DUMMY_CONTENT = {1, 4, 2, 4, 2, 4};
    private static final int DUMMY_PORT = 46354;
    private static final InputStream DUMMY_STREAM = new ByteArrayInputStream(DUMMY_CONTENT);
    private static final JSch JSCH = new JSch();
    private static final int TIMEOUT = 500;

    public static class round_trip {
        @Test
        public void a_file_that_is_written_to_the_SFTP_server_can_be_read() {
            FakeSftpServerRule sftpServer = new FakeSftpServerRule();
            executeTestWithRule(
                () -> {
                    Session session = connectToServer(sftpServer);
                    ChannelSftp channel = connectSftpChannel(session);
                    channel.put(
                        new ByteArrayInputStream(
                            "dummy content".getBytes(UTF_8)
                        ),
                        "dummy_file.txt"
                    );
                    InputStream file = channel.get("dummy_file.txt");
                    assertThat(IOUtils.toString(file, UTF_8))
                        .isEqualTo("dummy content");
                    channel.disconnect();
                    session.disconnect();
                },
                sftpServer
            );
        }
    }

    public static class connection {

        @Test
        public void multiple_connections_to_the_server_are_possible() {
            FakeSftpServerRule sftpServer = new FakeSftpServerRule();
            executeTestWithRule(
                () -> {
                    connectAndDisconnect(sftpServer);
                    connectAndDisconnect(sftpServer);
                },
                sftpServer
            );
        }

        @Test
        public void a_client_can_connect_to_the_server_at_a_user_specified_port() {
            FakeSftpServerRule sftpServer = new FakeSftpServerRule()
                .setPort(8394);
            executeTestWithRule(
                () -> connectToServerAtPort(8394),
                sftpServer
            );
        }
    }

    @RunWith(Enclosed.class)
    public static class authentication {
        public static class server_without_credentials {
            @Test
            public void the_server_accepts_connections_with_password() {
                FakeSftpServerRule sftpServer = new FakeSftpServerRule();
                executeTestWithRule(
                    () -> {
                        Session session = createSessionWithCredentials(
                            sftpServer,
                            "dummy user",
                            "dummy password"
                        );
                        session.connect(TIMEOUT);
                    },
                    sftpServer
                );
            }
        }

        public static class server_with_credentials_immediately_set {
            @Test
            public void the_server_accepts_connections_with_correct_password() {
                FakeSftpServerRule sftpServer = new FakeSftpServerRule()
                    .addUser("dummy user", "dummy password");
                executeTestWithRule(
                    () -> {
                        Session session = createSessionWithCredentials(
                            sftpServer,
                            "dummy user",
                            "dummy password"
                        );
                        session.connect(TIMEOUT);
                    },
                    sftpServer
                );
            }


            @Test
            public void the_server_rejects_connections_with_wrong_password() {
                FakeSftpServerRule sftpServer = new FakeSftpServerRule()
                    .addUser("dummy user", "correct password");
                executeTestWithRule(
                    () -> {
                        Session session = createSessionWithCredentials(
                            sftpServer,
                            "dummy user",
                            "wrong password"
                        );
                        assertAuthenticationFails(
                            () -> session.connect(TIMEOUT)
                        );
                    },
                    sftpServer
                );
            }

            @Test
            public void the_last_password_is_effective_if_addUser_is_called_multiple_times() {
                FakeSftpServerRule sftpServer = new FakeSftpServerRule()
                    .addUser("dummy user", "first password")
                    .addUser("dummy user", "second password");
                executeTestWithRule(
                    () -> {
                        Session session = createSessionWithCredentials(
                            sftpServer,
                            "dummy user",
                            "second password"
                        );
                        session.connect(TIMEOUT);
                    },
                    sftpServer
                );
            }
        }

        public static class server_with_credentials_set_during_test {
            @Test
            public void the_server_accepts_connections_with_correct_password() {
                FakeSftpServerRule sftpServer = new FakeSftpServerRule();
                executeTestWithRule(
                    () -> {
                        sftpServer.addUser("dummy user", "dummy password");
                        Session session = createSessionWithCredentials(
                            sftpServer,
                            "dummy user",
                            "dummy password"
                        );
                        session.connect(TIMEOUT);
                    },
                    sftpServer
                );
            }

            @Test
            public void the_server_rejects_connections_with_wrong_password() {
                FakeSftpServerRule sftpServer = new FakeSftpServerRule();
                executeTestWithRule(
                    () -> {
                        sftpServer.addUser("dummy user", "correct password");
                        Session session = createSessionWithCredentials(
                            sftpServer,
                            "dummy user",
                            "wrong password"
                        );
                        assertAuthenticationFails(
                            () -> session.connect(TIMEOUT)
                        );
                    },
                    sftpServer
                );
            }

            @Test
            public void the_last_password_is_effective_if_addUser_is_called_multiple_times() {
                FakeSftpServerRule sftpServer = new FakeSftpServerRule();
                executeTestWithRule(
                    () -> {
                        sftpServer
                            .addUser("dummy user", "first password")
                            .addUser("dummy user", "second password");
                        Session session = createSessionWithCredentials(
                            sftpServer,
                            "dummy user",
                            "second password"
                        );
                        session.connect(TIMEOUT);
                    },
                    sftpServer
                );
            }
        }

        private static Session createSessionWithCredentials(
            FakeSftpServerRule sftpServer,
            String username,
            String password
        ) throws JSchException {
            return FakeSftpServerRuleTest.createSessionWithCredentials(
                username, password, sftpServer.getPort()
            );
        }

        private static void assertAuthenticationFails(
            ThrowingCallable connectToServer
        ) {
            assertThatThrownBy(connectToServer)
                .isInstanceOf(JSchException.class)
                .hasMessage("Auth fail");
        }
    }

    @RunWith(Enclosed.class)
    public static class file_upload {
        public static class a_text_file {
            @Test
            public void that_is_put_to_root_directory_via_the_rule_can_be_read_from_server() {
                FakeSftpServerRule sftpServer = new FakeSftpServerRule();
                executeTestWithRule(
                    () -> {
                        sftpServer.putFile(
                            "/dummy_file.txt",
                            "dummy content with umlaut ü",
                            UTF_8
                        );
                        byte[] file = downloadFile(sftpServer, "/dummy_file.txt");
                        assertThat(new String(file, UTF_8))
                            .isEqualTo("dummy content with umlaut ü");
                    },
                    sftpServer
                );
            }

            @Test
            public void that_is_put_to_directory_via_the_rule_can_be_read_from_server() {
                FakeSftpServerRule sftpServer = new FakeSftpServerRule();
                executeTestWithRule(
                    () -> {
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
                    },
                    sftpServer
                );
            }

            @Test
            public void cannot_be_put_before_the_test_is_started() {
                FakeSftpServerRule sftpServer = new FakeSftpServerRule();
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
            public void cannot_be_put_after_the_test_is_finished() {
                FakeSftpServerRule sftpServer = new FakeSftpServerRule();
                executeTestWithRule(
                    () -> {},
                    sftpServer
                );
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
        }

        public static class a_binary_file {
            @Test
            public void that_is_put_to_root_directory_via_the_rule_can_be_read_from_server() {
                FakeSftpServerRule sftpServer = new FakeSftpServerRule();
                executeTestWithRule(
                    () -> {
                        sftpServer.putFile("/dummy_file.bin", DUMMY_CONTENT);
                        byte[] file = downloadFile(sftpServer, "/dummy_file.bin");
                        assertThat(file).isEqualTo(DUMMY_CONTENT);
                    },
                    sftpServer
                );
            }

            @Test
            public void that_is_put_to_directory_via_the_rule_can_be_read_from_server() {
                FakeSftpServerRule sftpServer = new FakeSftpServerRule();
                executeTestWithRule(
                    () -> {
                        sftpServer.putFile(
                            "/dummy_directory/dummy_file.bin",
                            DUMMY_CONTENT
                        );
                        byte[] file = downloadFile(
                            sftpServer,
                            "/dummy_directory/dummy_file.bin"
                        );
                        assertThat(file).isEqualTo(DUMMY_CONTENT);
                    },
                    sftpServer
                );
            }

            @Test
            public void cannot_be_put_before_the_test_is_started() {
                FakeSftpServerRule sftpServer = new FakeSftpServerRule();
                Throwable exception = exceptionThrownBy(
                    () -> sftpServer.putFile("/dummy_file.bin", DUMMY_CONTENT)
                );
                assertThat(exception)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(
                        "Failed to upload file because test has not been started"
                            + " or is already finished."
                    );
            }

            @Test
            public void cannot_be_put_after_the_test_is_finished() {
                FakeSftpServerRule sftpServer = new FakeSftpServerRule();
                executeTestWithRule(
                    () -> {},
                    sftpServer
                );
                Throwable exception = exceptionThrownBy(
                    () -> sftpServer.putFile("/dummy_file.bin", DUMMY_CONTENT)
                );
                assertThat(exception)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(
                        "Failed to upload file because test has not been started"
                            + " or is already finished."
                    );
            }
        }

        public static class a_file_from_a_stream {
            @Test
            public void that_is_put_to_root_directory_via_the_rule_can_be_read_from_server() {
                FakeSftpServerRule sftpServer = new FakeSftpServerRule();
                executeTestWithRule(
                    () -> {
                        InputStream is = new ByteArrayInputStream(DUMMY_CONTENT);
                        sftpServer.putFile("/dummy_file.bin", is);
                        byte[] file = downloadFile(sftpServer, "/dummy_file.bin");
                        assertThat(file).isEqualTo(DUMMY_CONTENT);
                    },
                    sftpServer
                );
            }

            @Test
            public void that_is_put_to_directory_via_the_rule_can_be_read_from_server() {
                FakeSftpServerRule sftpServer = new FakeSftpServerRule();
                executeTestWithRule(
                    () -> {
                        InputStream is = new ByteArrayInputStream(DUMMY_CONTENT);
                        sftpServer.putFile("/dummy_directory/dummy_file.bin", is);
                        byte[] file = downloadFile(
                            sftpServer,
                            "/dummy_directory/dummy_file.bin"
                        );
                        assertThat(file).isEqualTo(DUMMY_CONTENT);
                    },
                    sftpServer
                );
            }

            @Test
            public void cannot_be_put_before_the_test_is_started() {
                FakeSftpServerRule sftpServer = new FakeSftpServerRule();
                Throwable exception = exceptionThrownBy(
                    () -> sftpServer.putFile("/dummy_file.bin", DUMMY_STREAM)
                );
                assertThat(exception)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(
                        "Failed to upload file because test has not been started"
                            + " or is already finished."
                    );
            }

            @Test
            public void cannot_be_put_after_the_test_is_finished() {
                FakeSftpServerRule sftpServer = new FakeSftpServerRule();
                executeTestWithRule(
                    () -> {},
                    sftpServer
                );
                Throwable exception = exceptionThrownBy(
                    () -> sftpServer.putFile("/dummy_file.bin", DUMMY_STREAM)
                );
                assertThat(exception)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(
                        "Failed to upload file because test has not been started"
                            + " or is already finished."
                    );
            }
        }
    }

    @RunWith(Enclosed.class)
    public static class directory_creation {

        public static class a_single_directory {
            @Test
            public void that_is_created_with_the_rule_can_be_read_by_a_client() {
                FakeSftpServerRule sftpServer = new FakeSftpServerRule();
                executeTestWithRule(
                    () -> {
                        sftpServer.createDirectory("/a/directory");
                        assertEmptyDirectory(sftpServer, "/a/directory");
                    },
                    sftpServer
                );
            }

            @Test
            public void cannot_be_created_before_the_test_is_started() {
                FakeSftpServerRule sftpServer = new FakeSftpServerRule();
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
            public void cannot_be_created_after_the_test_is_finished() {
                FakeSftpServerRule sftpServer = new FakeSftpServerRule();
                executeTestWithRule(
                    () -> {},
                    sftpServer
                );
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
        }

        public static class multiple_directories {
            @Test
            public void that_are_created_with_the_rule_can_be_read_by_a_client() {
                FakeSftpServerRule sftpServer = new FakeSftpServerRule();
                executeTestWithRule(
                    () -> {
                        sftpServer.createDirectories(
                            "/a/directory",
                            "/another/directory"
                        );
                        assertEmptyDirectory(sftpServer, "/a/directory");
                        assertEmptyDirectory(sftpServer, "/another/directory");
                    },
                    sftpServer
                );
            }

            @Test
            public void cannot_be_created_before_the_test_is_started() {
                FakeSftpServerRule sftpServer = new FakeSftpServerRule();
                Throwable exception = exceptionThrownBy(
                    () -> sftpServer.createDirectories(
                        "/a/directory",
                        "/another/directory"
                    )
                );
                assertThat(exception)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(
                        "Failed to create directory because test has not been"
                            + " started or is already finished."
                    );
            }

            @Test
            public void cannot_be_created_after_the_test_is_finished() {
                FakeSftpServerRule sftpServer = new FakeSftpServerRule();
                executeTestWithRule(
                    () -> {},
                    sftpServer
                );
                Throwable exception = exceptionThrownBy(
                    () -> sftpServer.createDirectories(
                        "/a/directory",
                        "/another/directory"
                    )
                );
                assertThat(exception)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(
                        "Failed to create directory because test has not been"
                            + " started or is already finished."
                    );
            }
        }

        private static void assertEmptyDirectory(
            FakeSftpServerRule sftpServer,
            String directory
        ) throws JSchException, SftpException {
            Session session = connectToServer(sftpServer);
            ChannelSftp channel = connectSftpChannel(session);
            Vector<?> entries = channel.ls(directory);
            assertThat(entries).hasSize(2); //these are the entries . and ..
            channel.disconnect();
            session.disconnect();
        }
    }

    @RunWith(Enclosed.class)
    public static class file_download {
        public static class a_text_file {
            @Test
            public void that_is_written_to_the_server_can_be_retrieved_with_the_rule() {
                FakeSftpServerRule sftpServer = new FakeSftpServerRule();
                executeTestWithRule(
                    () -> {
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
                    },
                    sftpServer
                );
            }

            @Test
            public void cannot_be_retrieved_before_the_test_is_started() {
                FakeSftpServerRule sftpServer = new FakeSftpServerRule();
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
            public void cannot_be_retrieved_after_the_test_is_finished() {
                FakeSftpServerRule sftpServer = new FakeSftpServerRule();
                executeTestWithRule(
                    () -> uploadFile(
                        sftpServer,
                        "/dummy_directory/dummy_file.txt",
                        "dummy content".getBytes(UTF_8)
                    ),
                    sftpServer
                );
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
        }

        public static class a_binary_file {
            @Test
            public void that_is_written_to_the_server_can_be_retrieved_with_the_rule() {
                FakeSftpServerRule sftpServer = new FakeSftpServerRule();
                executeTestWithRule(
                    () -> {
                        uploadFile(
                            sftpServer,
                            "/dummy_directory/dummy_file.bin",
                            DUMMY_CONTENT
                        );
                        byte[] fileContent = sftpServer.getFileContent(
                            "/dummy_directory/dummy_file.bin"
                        );
                        assertThat(fileContent).isEqualTo(DUMMY_CONTENT);
                    },
                    sftpServer
                );
            }

            @Test
            public void cannot_be_retrieved_before_the_test_is_started() {
                FakeSftpServerRule sftpServer = new FakeSftpServerRule();
                Throwable exception = exceptionThrownBy(
                    () -> sftpServer.getFileContent(
                        "/dummy_directory/dummy_file.bin"
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
            public void cannot_be_retrieved_after_the_test_is_finished() {
                FakeSftpServerRule sftpServer = new FakeSftpServerRule();
                executeTestWithRule(
                    () -> uploadFile(
                        sftpServer,
                        "/dummy_directory/dummy_file.bin",
                        DUMMY_CONTENT
                    ),
                    sftpServer
                );
                Throwable exception = exceptionThrownBy(
                    () -> sftpServer.getFileContent("/dummy_file.bin")
                );
                assertThat(exception)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(
                        "Failed to download file because test has not been started"
                            + " or is already finished."
                    );
            }
        }
    }

    public static class file_existence_check {

        @Test
        public void exists_returns_true_for_a_file_that_exists_on_the_server() {
            FakeSftpServerRule sftpServer = new FakeSftpServerRule();
            executeTestWithRule(
                () -> {
                    uploadFile(
                        sftpServer,
                        "/dummy_directory/dummy_file.bin",
                        DUMMY_CONTENT
                    );
                    boolean exists = sftpServer.existsFile(
                        "/dummy_directory/dummy_file.bin"
                    );
                    assertThat(exists).isTrue();
                },
                sftpServer
            );
        }

        @Test
        public void exists_returns_false_for_a_file_that_does_not_exists_on_the_server() {
            FakeSftpServerRule sftpServer = new FakeSftpServerRule();
            executeTestWithRule(
                () -> {
                    boolean exists = sftpServer.existsFile(
                        "/dummy_directory/dummy_file.bin"
                    );
                    assertThat(exists).isFalse();
                },
                sftpServer
            );
        }

        @Test
        public void exists_returns_false_for_a_directory_that_exists_on_the_server() {
            FakeSftpServerRule sftpServer = new FakeSftpServerRule();
            executeTestWithRule(
                () -> {
                    uploadFile(
                        sftpServer,
                        "/dummy_directory/dummy_file.bin",
                        DUMMY_CONTENT
                    );
                    boolean exists = sftpServer.existsFile("/dummy_directory");
                    assertThat(exists).isFalse();
                },
                sftpServer
            );
        }

        @Test
        public void existence_of_a_file_cannot_be_checked_before_the_test_is_started() {
            FakeSftpServerRule sftpServer = new FakeSftpServerRule();
            Throwable exception = exceptionThrownBy(
                () -> sftpServer.existsFile("/dummy_file.bin")
            );
            assertThat(exception)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                    "Failed to check existence of file because test has not"
                        + " been started or is already finished."
                );
        }

        @Test
        public void existence_of_a_file_cannot_be_checked_after_the_test_is_finished() {
            FakeSftpServerRule sftpServer = new FakeSftpServerRule();
            executeTestWithRule(
                () -> {},
                sftpServer
            );
            Throwable exception = exceptionThrownBy(
                () -> sftpServer.existsFile("/dummy_file.bin")
            );
            assertThat(exception)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                    "Failed to check existence of file because test has not"
                        + " been started or is already finished."
                );
        }
    }

    public static class server_shutdown {
        @Test
        public void after_a_successful_test_SFTP_server_is_shutdown() {
            FakeSftpServerRule sftpServer = new FakeSftpServerRule()
                .setPort(DUMMY_PORT);
            executeTestWithRule(
                () -> {},
                sftpServer
            );
            assertConnectionToSftpServerNotPossible(DUMMY_PORT);
        }

        @Test
        public void after_an_erroneous_test_SFTP_server_is_shutdown() {
            FakeSftpServerRule sftpServer = new FakeSftpServerRule()
                .setPort(DUMMY_PORT);
            executeTestThatThrowsExceptionWithRule(
                () -> {
                    throw new RuntimeException();
                },
                sftpServer
            );
            assertConnectionToSftpServerNotPossible(DUMMY_PORT);
        }

        @Test
        public void after_a_test_first_SFTP_server_is_shutdown_when_port_was_changed_during_test() {
            FakeSftpServerRule sftpServer = new FakeSftpServerRule()
                .setPort(DUMMY_PORT - 1);
            executeTestWithRule(
                () -> sftpServer.setPort(DUMMY_PORT),
                sftpServer
            );
            assertConnectionToSftpServerNotPossible(DUMMY_PORT - 1);
        }

        @Test
        public void after_a_test_second_SFTP_server_is_shutdown_when_port_was_changed_during_test() {
            FakeSftpServerRule sftpServer = new FakeSftpServerRule();
            executeTestWithRule(
                () -> sftpServer.setPort(DUMMY_PORT),
                sftpServer
            );
            assertConnectionToSftpServerNotPossible(DUMMY_PORT);
        }

        private void assertConnectionToSftpServerNotPossible(
            int port
        ) {
            Throwable throwable = catchThrowable(
                () -> connectToServerAtPort(port)
            );
            assertThat(throwable)
                .withFailMessage(
                    "SFTP server is still running on port %d.",
                    port
                )
                .hasCauseInstanceOf(ConnectException.class);
        }
    }

    public static class port_selection {
        @Test
        public void by_default_two_rules_run_servers_at_different_ports() {
            FakeSftpServerRule firstSftpServer = new FakeSftpServerRule();
            AtomicInteger portCaptureForFirstServer = new AtomicInteger();
            FakeSftpServerRule secondSftpServer = new FakeSftpServerRule();
            AtomicInteger portCaptureForSecondServer = new AtomicInteger();

            executeTestWithRule(
                () -> portCaptureForFirstServer.set(firstSftpServer.getPort()),
                firstSftpServer
            );
            executeTestWithRule(
                () -> portCaptureForSecondServer.set(secondSftpServer.getPort()),
                secondSftpServer
            );

            assertThat(portCaptureForFirstServer)
                .doesNotHaveValue(portCaptureForSecondServer.get());
        }

        @Test
        public void the_port_can_be_changed_during_the_test() {
            FakeSftpServerRule sftpServer = new FakeSftpServerRule();
            executeTestWithRule(
                () -> {
                    sftpServer.setPort(DUMMY_PORT);
                    connectToServerAtPort(DUMMY_PORT);
                },
                sftpServer
            );
        }

        @Test
        public void it_is_not_possible_to_set_a_negative_port() {
            assertThatThrownBy(() -> new FakeSftpServerRule().setPort(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                    "Port cannot be set to -1 because only ports between 1 and"
                        + " 65535 are valid."
                );
        }

        @Test
        public void it_is_not_possible_to_set_port_zero() {
            assertThatThrownBy(() -> new FakeSftpServerRule().setPort(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                    "Port cannot be set to 0 because only ports between 1 and"
                        + " 65535 are valid."
                );
        }

        @Test
        public void the_port_can_be_set_to_1() {
            //I don't test the connection, because port 1 can not be used by a
            //standard user.
            FakeSftpServerRule sftpServer = new FakeSftpServerRule();
            sftpServer.setPort(1);
        }

        @Test
        public void the_server_can_be_run_at_port_65535() {
            FakeSftpServerRule sftpServer = new FakeSftpServerRule()
                .setPort(65535);
            executeTestWithRule(
                () -> connectToServerAtPort(65535),
                sftpServer
            );
        }

        @Test
        public void it_is_not_possible_to_set_a_port_greater_than_65535() {
            assertThatThrownBy(() -> new FakeSftpServerRule().setPort(65536))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                    "Port cannot be set to 65536 because only ports between 1"
                        + " and 65535 are valid."
                );
        }
    }

    @RunWith(Enclosed.class)
    public static class port_query {

        public static class random_port {
            @Test
            public void cannot_be_read_before_the_test() {
                FakeSftpServerRule sftpServer = new FakeSftpServerRule();
                assertPortCannotBeRead(sftpServer);
            }

            @Test
            public void can_be_read_during_the_test() {
                AtomicInteger portCapture = new AtomicInteger();
                FakeSftpServerRule sftpServer = new FakeSftpServerRule();
                executeTestWithRule(
                    () -> portCapture.set(sftpServer.getPort()),
                    sftpServer
                );
                assertThat(portCapture).doesNotHaveValue(0);
            }

            @Test
            public void cannot_be_read_after_the_test() {
                FakeSftpServerRule sftpServer = new FakeSftpServerRule();
                executeTestWithRule(
                    () -> {},
                    sftpServer
                );
                assertPortCannotBeRead(sftpServer);
            }

            private void assertPortCannotBeRead(FakeSftpServerRule sftpServer) {
                assertThatThrownBy(sftpServer::getPort)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(
                        "Failed to call getPort() because test has not been"
                            + " started or is already finished."
                    );
            }
        }

        public static class specified_port {
            @Test
            public void can_be_read_before_the_test() {
                FakeSftpServerRule sftpServer = new FakeSftpServerRule()
                    .setPort(DUMMY_PORT);
                int port = sftpServer.getPort();
                assertThat(port).isEqualTo(DUMMY_PORT);
            }

            @Test
            public void can_be_read_during_the_test() {
                AtomicInteger portCapture = new AtomicInteger();
                FakeSftpServerRule sftpServer = new FakeSftpServerRule()
                    .setPort(DUMMY_PORT);
                executeTestWithRule(
                    () -> portCapture.set(sftpServer.getPort()),
                    sftpServer
                );
                assertThat(portCapture).hasValue(DUMMY_PORT);
            }

            @Test
            public void can_be_read_after_the_test() {
                FakeSftpServerRule sftpServer = new FakeSftpServerRule()
                    .setPort(DUMMY_PORT);
                executeTestWithRule(
                    () -> {},
                    sftpServer
                );
                int port = sftpServer.getPort();
                assertThat(port).isEqualTo(DUMMY_PORT);
            }
        }
    }

    public static class cleanup {

        @Test
        public void deletes_file_in_root_directory() {
            FakeSftpServerRule sftpServer = new FakeSftpServerRule();
            executeTestWithRule(
                () -> {
                    uploadFile(
                        sftpServer,
                        "/dummy_file.bin",
                        DUMMY_CONTENT
                    );
                    sftpServer.deleteAllFilesAndDirectories();
                    assertFileDoesNotExist(
                        sftpServer,
                        "/dummy_file.bin"
                    );
                },
                sftpServer
            );
        }

        @Test
        public void deletes_file_in_directory() {
            FakeSftpServerRule sftpServer = new FakeSftpServerRule();
            executeTestWithRule(
                () -> {
                    uploadFile(
                        sftpServer,
                        "/dummy_directory/dummy_file.bin",
                        DUMMY_CONTENT
                    );
                    sftpServer.deleteAllFilesAndDirectories();
                    assertFileDoesNotExist(
                        sftpServer,
                        "/dummy_directory/dummy_file.bin"
                    );
                },
                sftpServer
            );
        }

        @Test
        public void deletes_directory() {
            FakeSftpServerRule sftpServer = new FakeSftpServerRule();
            executeTestWithRule(
                () -> {
                    sftpServer.createDirectory("/dummy_directory");
                    sftpServer.deleteAllFilesAndDirectories();
                    assertDirectoryDoesNotExist(
                        sftpServer,
                        "/dummy_directory"
                    );
                },
                sftpServer
            );
        }

        @Test
        public void works_on_an_empty_filesystem() {
            FakeSftpServerRule sftpServer = new FakeSftpServerRule();
            executeTestWithRule(
                sftpServer::deleteAllFilesAndDirectories,
                sftpServer
            );
        }

        private static void assertFileDoesNotExist(
            FakeSftpServerRule sftpServer,
            String path
        ) {
            boolean exists = sftpServer.existsFile(path);
            assertThat(exists).isFalse();
        }

        private static void assertDirectoryDoesNotExist(
            FakeSftpServerRule sftpServer,
            String directory
        ) throws JSchException {
            Session session = connectToServer(sftpServer);
            ChannelSftp channel = connectSftpChannel(session);
            try {
                assertThatThrownBy(() -> channel.ls(directory))
                    .isInstanceOf(SftpException.class)
                    .hasMessage("No such file or directory");
            } finally {
                channel.disconnect();
                session.disconnect();
            }
        }
    }

    private static Session connectToServer(
        FakeSftpServerRule sftpServer
    ) throws JSchException {
        return connectToServerAtPort(sftpServer.getPort());
    }

    private static Session connectToServerAtPort(
        int port
    ) throws JSchException {
        Session session = createSessionWithCredentials(
            "dummy user", "dummy password", port
        );
        session.connect(TIMEOUT);
        return session;
    }

    private static ChannelSftp connectSftpChannel(
        Session session
    ) throws JSchException {
        ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
        channel.connect();
        return channel;
    }

    private static void connectAndDisconnect(
        FakeSftpServerRule sftpServer
    ) throws JSchException {
        Session session = connectToServer(sftpServer);
        ChannelSftp channel = connectSftpChannel(session);
        channel.disconnect();
        session.disconnect();
    }

    private static Session createSessionWithCredentials(
        String username,
        String password,
        int port
    ) throws JSchException {
        Session session = JSCH.getSession(username, "127.0.0.1", port);
        session.setConfig("StrictHostKeyChecking", "no");
        session.setPassword(password);
        return session;
    }

    private static byte[] downloadFile(
        FakeSftpServerRule server,
        String path
    ) throws JSchException, SftpException, IOException {
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

    private static void uploadFile(
        FakeSftpServerRule server,
        String pathAsString,
        byte[] content
    ) throws JSchException, SftpException {
        Session session = connectToServer(server);
        ChannelSftp channel = connectSftpChannel(session);
        try {
        	Matcher matcher = Pattern.compile("/.+?/").matcher(pathAsString);
        	int index = 0;
        	String dir = "";
        	while (matcher.find(index)) {
        		index = matcher.end() - 1;
        		dir += matcher.group().substring(0, matcher.end() - 1);
				channel.mkdir(dir);
        	}
            channel.put(new ByteArrayInputStream(content), pathAsString);
        } finally {
            channel.disconnect();
            session.disconnect();
        }
    }
}
