# Fake SFTP Server Rule

[![Build Status](https://travis-ci.org/stefanbirkner/fake-sftp-server-rule.svg?branch=master)](https://travis-ci.org/stefanbirkner/fake-sftp-server-rule)

Fake SFTP Server Rule is a JUnit rule that runs an in-memory SFTP server while
your tests are running. It uses the SFTP server of the
[Apache SSHD](http://mina.apache.org/sshd-project/index.html) project.

Fake SFTP Server Rule is published under the
[MIT license](http://opensource.org/licenses/MIT). It uses Java 8. Please
[open an issue](https://github.com/stefanbirkner/jmarkdown2revealjs/issues/new)
if you want to use it with an older version of Java.


## Installation

Fake SFTP Server Rule is available from [Maven Central](http://search.maven.org/).

    <dependency>
      <groupId>com.github.stefanbirkner</groupId>
      <artifactId>fake-sftp-server-rule</artifactId>
      <version>0.1.0</version>
    </dependency>


## Usage

The Fake SFTP Server Rule is used by adding it to your test class.

    import com.github.stefanbirkner.fakesftpserver.rule.FakeSftpServerRule;

    public class TestClass {
      @Rule
      public final FakeSftpServerRule sftpServer = new FakeSftpServerRule();

      ...
    }

This rule starts a server before your test and stops it afterwards.

You can interact with the SFTP server by using the SFTP protocol with an
arbitrary username and password. (The server accepts every combination of
username and password.) The port of the server is obtained by
`sftpServer.getPort()`.


## Contributing

You have three options if you have a feature request, found a bug or
simply have a question about Fake SFTP Server Rule.

* [Write an issue.](https://github.com/stefanbirkner/fake-sftp-server-rule/issues/new)
* Create a pull request. (See [Understanding the GitHub Flow](https://guides.github.com/introduction/flow/index.html))
* [Write a mail to mail@stefan-birkner.de](mailto:mail@stefan-birkner.de)


## Development Guide

Fake SFTP Server Rule is build with [Maven](http://maven.apache.org/). If you
want to contribute code than

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
