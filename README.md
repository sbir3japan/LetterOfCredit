![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

# Letter of Credit Demo

This is a CorDapp that uses a letter-of-credit business scenario to showcase Corda.

## Running the nodes:

* Create the node driver by running `./gradlew buildExecutableJar` (osX) or `./gradlew buildExecutableJar` (Windows)
* Run the node driver using `java -Xmx8192m -jar kotlin-source/build/libs/eloc-demo.jar`

## Interacting with the nodes:

Once all the nodes are started, go to `localhost:10007/map`.

## Update process (development only)

### Updating the web frontend

If the [front-end](https://github.com/corda/LetterOfCreditWeb) has been modified, it needs to be redeployed onto the nodes. You do this by...
