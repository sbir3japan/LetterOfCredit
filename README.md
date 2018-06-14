![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

# Letter of Credit Demo

This is a CorDapp that uses a letter-of-credit business scenario to showcase Corda.

This CorDapp was written with the goal of creating a good demo, not a commercial letter-of-credit application. A real 
letter-of-credit application would be structured differently, especially in terms of the contract code.

## Running the nodes:

* Create the node driver by running `./gradlew buildExecutableJar` (osX) or `gradlew buildExecutableJar` (Windows)
* Run the node driver using `java -Xmx8192m -jar kotlin-source/build/libs/eloc-demo.jar`

If you receive error messages that say `Exception in thread "main" java.net.BindException: Address already in use`, you 
have zombie Java processes. You can kill them using `killall java -9` on osX, or 
`wmic process where "name like '%java%'" delete` on Windows.

## Interacting with the nodes:

Run the front-end from the [front-end repo](https://github.com/corda/LetterOfCreditWeb).

## Updating the cash issuance CorDapp

* Clone the [cash issuance CorDapp](https://bitbucket.org/R3-CEV/wildfire)
  * This repository is currently private
* Check out the `cais_v2` branch
* Make the changes
* From the root of the project, run `yarn run deploy`
* Rename the `kotlin-source-0.1.jar` file in `kotlin-source/build/libs` to `wildfire.jar`
* Copy `wildfire.jar` into the `lib` folder in the root of the `LetterOfCredit` project, overwriting the existing file