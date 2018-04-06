# Fake SFTP Server Rule

[![Build Status](https://travis-ci.org/stefanbirkner/fake-sftp-server-rule.svg?branch=master)](https://travis-ci.org/stefanbirkner/fake-sftp-server-rule)

Fake SFTP Server Rule is a JUnit rule that runs an in-memory SFTP server while
your tests are running. It uses the SFTP server of the
[Apache SSHD](http://mina.apache.org/sshd-project/index.html) project.

Fake SFTP Server Rule is published under the
[MIT license](http://opensource.org/licenses/MIT). It requires at least Java 8.
Please
[open an issue](https://github.com/stefanbirkner/fake-sftp-server-rule/issues/new)
if you want to use it with an older version of Java.

I want to thank my former team SAM at ThoughtWorks for using this library and
@crizzis, @OArtyomov and @TheSentinel454 for their feature requests.


## Installation

Fake SFTP Server Rule is available from
[Maven Central](https://search.maven.org/#search|ga|1|fake-sftp-server-rule).

    <dependency>
      <groupId>com.github.stefanbirkner</groupId>
      <artifactId>fake-sftp-server-rule</artifactId>
      <version>2.0.0</version>
    </dependency>

If you upgrade from a version < 2.x to the newest version please read the last
section of this readme.

## Usage

The Fake SFTP Server Rule is used by adding it to your test class.

    import com.github.stefanbirkner.fakesftpserver.rule.FakeSftpServerRule;

    public class TestClass {
      @Rule
      public final FakeSftpServerRule sftpServer = new FakeSftpServerRule();

      ...
    }

This rule starts a server before your test and stops it afterwards.

You can interact with the SFTP server by using the SFTP protocol with password
authentication. By default the server accepts every pair of username and
password, but you can restrict it to specific pairs.

    public class TestClass {
      @Rule
      public final FakeSftpServerRule sftpServer = new FakeSftpServerRule()
          .addUser("username", "password");

      ...
    }

It is also possible to do this during the test using the same method.

By default the SFTP server listens on an auto-allocated port. During the test
this port can be obtained by `sftpServer.getPort()`. It can be changed
by calling `setPort(int)`. If you do this from within a test then the server
gets restarted. The time-consuming restart can be avoided by setting the port
immediately after creating the rule.

    public class TestClass {
      @Rule
      public final FakeSftpServerRule sftpServer = new FakeSftpServerRule()
          .setPort(1234);

      ...
    }

### Testing code that reads files

If you test code that reads files from an SFTP server then you need a server
that provides these files. Fake SFTP Server Rule provides a shortcut for
uploading files to the server.

    @Test
    public void testTextFile() {
      sftpServer.putFile("/directory/file.txt", "content of file", UTF_8);
      //code that downloads the file
    }

    @Test
    public void testBinaryFile() {
      byte[] content = createContent();
      sftpServer.putFile("/directory/file.bin", content);
      //code that downloads the file
    }

Test data that is provided as an input stream can be uploaded directly from that
input stream. This is very handy if your test data is available as a resource.

    @Test
    public void testFileFromInputStream() {
      InputStream is = getClass().getResourceAsStream("data.bin");
      sftpServer.putFile("/directory/file.bin", is);
      //code that downloads the file
    }

If you need an empty directory then you can use the method
`createDirectory(String)`.

    @Test
    public void testDirectory() {
      sftpServer.createDirectory("/a/directory");
      //code that reads from or writes to that directory
    }

You may create multiple directories at once with `createDirectories(String...)`.

    @Test
    public void testDirectories() {
      sftpServer.createDirectories(
        "/a/directory",
        "/another/directory"
      );
      //code that reads from or writes to that directories
    }


### Testing code that writes files

If you test code that writes files to an SFTP server then you need to verify
the upload. Fake SFTP Server Rule provides a shortcut for getting the file's
content from the server.

    @Test
    public void testTextFile() {
      //code that uploads the file
      String fileContent = sftpServer.getFileContent("/directory/file.txt", UTF_8);
      ...
    }

    @Test
    public void testBinaryFile() {
      //code that uploads the file
      byte[] fileContent = sftpServer.getFileContent("/directory/file.bin");
      ...
    }

### Testing existence of files

If you want to check whether a file hast been created or deleted then you can
verify that it exists or not.

    @Test
    public void testFile() {
      //code that uploads or deletes the file
      boolean exists = sftpServer.existsFile("/directory/file.txt");
      ...
    }

The method returns `true` iff the file exists and it is not a directory.

### Delete all files

If you want to reuse the SFTP server then you can delete all files and
directories on the SFTP server. (This is rarely necessary because the rule
itself takes care that every test starts and ends with a clean SFTP server.)

    sftpServer.deleteAllFilesAndDirectories()

## Contributing

You have three options if you have a feature request, found a bug or
simply have a question about Fake SFTP Server Rule.

* [Write an issue.](https://github.com/stefanbirkner/fake-sftp-server-rule/issues/new)
* Create a pull request. (See [Understanding the GitHub Flow](https://guides.github.com/introduction/flow/index.html))
* [Write a mail to mail@stefan-birkner.de](mailto:mail@stefan-birkner.de)


## Development Guide

Fake SFTP Server Rule is build with [Maven](http://maven.apache.org/). If you
want to contribute code then

* Please write a test for your change.
* Ensure that you didn't break the build by running `mvn verify -Dgpg.skip`.
* Fork the repo and create a pull request. (See [Understanding the GitHub Flow](https://guides.github.com/introduction/flow/index.html))

The basic coding style is described in the
[EditorConfig](http://editorconfig.org/) file `.editorconfig`.

Fake SFTP Server Rule supports [Travis CI](https://travis-ci.org/) for
continuous integration. Your pull request will be automatically build by Travis
CI.


## Release Guide

* Select a new version according to the
  [Semantic Versioning 2.0.0 Standard](http://semver.org/).
* Set the new version in `pom.xml` and in the `Installation` section of
  this readme.
* Commit the modified `pom.xml` and `README.md`.
* Run `mvn clean deploy` with JDK 8.
* Add a tag for the release: `git tag fake-sftp-server-rule-X.X.X`


## Upgrading from 0.x.y or 1.x.y to version >= 2

In older versions the SFTP server listened to port 23454 by default. From
version 2 on it selects an arbitrary free port by default. If your tests fail
after an upgrade you may consider to restore the old behaviour by immediately
setting the old port after creating the rule.

    @Rule
    public final FakeSftpServerRule sftpServer = new FakeSftpServerRule()
        .setPort(23454);
