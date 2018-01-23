![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

Project status: Early alpha. Expect bugs and rapid iterative development.

### Voltron

A [trade finance](http://www.investopedia.com/terms/t/tradefinance.asp) solution using the R3 Corda framework.

### Technologies

* [JDK 8](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) 
  installed and available on your path.
* Latest version of [IntelliJ IDEA](https://www.jetbrains.com/idea/download/) 
  (note the Community Edition is free)
* [H2 web console](http://www.h2database.com/html/download.html)
  (download the "platform-independent zip") - you can get by with the Database tab in Intellij
* [git](https://help.github.com/articles/set-up-git/)

### Getting started

Voltron is a Gradle build from a git repository. It relies on libraries provided by R3 which are
in the public domain (Corda).

#### Verify you have Git

```
$ git --version
```

[Install git](https://help.github.com/articles/set-up-git/) if necessary.

#### Verify you have R3 Corda

If this is your first time working with CorDapps, clone and build the source code repository (over HTTPS) for
the `release-M10` milestone (for example) using:

```
$ git clone https://github.com/corda/corda.git
$ cd corda
$ git checkout release-M10
$ ./gradlew
$ gradle clean install
```
This will install the release artifacts into the local repository so they can be picked up by this project.

#### Clone this repo

Get the source code from here:

```
$ git clone https://{your-bitbucket-user}@bitbucket.org/R3-CEV/voltron.git 
```
A sub-directory called `voltron` will be created which is your project root directory.

To update a previous clone of the project use a pull instead:

```
$ cd <project root>
$ git pull
```

We use the Release Branch approach, where `master` is the current release candidate, and `release-X` is the release
of version X. Feature branches are named after their respective issue numbers which are included in each commit to assist tracking.
Once feature branches are merged, they are deleted.

#### Build with Gradle

Everything is automated, including installing gradle on your system. Just type the following:

```
$ cd <project root>
$ ./gradlew
```

Gradle uses a file called `build.gradle` present in the project root directory to provide all the build information.
Make sure you reference that file when opening the project.

Also, in Intellij the default grade wrapper isn't always supported. If you've installed gradle through Homebrew you
may need to directly reference Gradle home using `/usr/local/opt/gradle/libexec` instead. If you are having a 
compilation error, and the issue might be gradle version. [Corda issue #18](https://github.com/corda/corda/issues/18) 
Make sure that your Gradle version is 2.10. 
```
$ ./gradlew wrapper --gradle-version 2.10 
```

#### Start the application (from an IDE)

Create a runtime configurations with the following specification:

`Main [run]`:
```
Main Class: com.template.MainKt
VM Options: -javaagent:lib/quasar.jar -Dco.paralleluniverse.fibers.verifyInstrumentation=false
```

Running this will automatically initiate trades between all nodes.
 
Note the presence of the `lib/quasar.jar`. This is to allow Quasar to instrument the classes that are using the
Fiber classes using an Ahead-Of-Time (AOT) approach. Since the location of the local Gradle repo is not known for all 
users of the code it has been provided in the project as a convenience.

#### Start the application (from the command line)

To run the application from the command line do the following:
```
$ cd <project root>
$ ./gradlew kotlin-source:installDist
$ ./gradlew kotlin-source:deployNodes
$ ./kotlin-source/build/nodes/runnodes
$ ./kotlin-source/build/install/kotlin-source/bin/kotlin-source --role ApplyForLOC
```
This will create a small network of Corda nodes and deploy the Voltron CorDapp into them.

### Issue tracking

We use the `project-voltron` private Slack channel to discuss the current situation with the code. 

### Contributing

Please refer to the [Corda code style guide](https://docs.corda.net/codestyle.html) to ensure that your contribution
fits well with every one else's. 

### Troubleshooting

#### My Corda nodes take 30 seconds or more to start

This is most likely due to a network timeout occurring during startup/run phases within the node. You will need to 
ensure that you have a network connection (even if it is dead) so that localhost resolution occurs in a timely manner. 

(Quick fix tip: Try a mobile hotspot network). 

A typical startup time for a Corda node on a basic development machine is around 200 ms.

#### When the format of transactions changes, then system triggers this error.   

```
Requesting signature by Notary service
E 14:01:09 [263:Node thread] [f0a602c5-353f-4c84-b1a4-e0d460256c3e].uncaughtException - Caught exception from protocol
 java.util.concurrent.ExecutionException: java.lang.IllegalArgumentException: Failed requirement.
	at com.r3corda.node.services.statemachine.ProtocolStateMachineImpl.run(ProtocolStateMachineImpl.kt:90) ~[node-0.4.jar:?]
```
Solution: Delete the build directory.

#### When building Gradle, an index error regarding `https://dl.bintray.com/kotlin/exposed` occurs.

```
Caused by: java.lang.RuntimeException: java.lang.RuntimeException: java.io.FileNotFoundException: Resource nexus-maven-repository-index.properties does not exist
	at org.jetbrains.idea.maven.server.Maven3ServerIndexerImpl$2.run(Maven3ServerIndexerImpl.java:204)
	at org.jetbrains.idea.maven.server.Maven30ServerEmbedderImpl.executeWithMavenSession(Maven30ServerEmbedderImpl.java:568)
	at org.jetbrains.idea.maven.server.Maven3ServerIndexerImpl.updateIndex(Maven3ServerIndexerImpl.java:170)
	at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
```
Solution: Known issue - see https://youtrack.jetbrains.com/issue/IDEA-138029
