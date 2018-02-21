![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

# Option CorDapp

This CorDapp allows nodes to issue, trade and exercise call and put options.

When issuing or trading an option, an oracle is used to ensure that the option is being exchanged for the correct 
amount of cash, based on the oracle's knowledge of stock prices, volatility and the Black-Scholes model.

The CorDapp is split into three modules:

* Client: The flows required by non-oracle nodes to query the oracle and request their signature over a transaction 
  including oracle data. Also includes a web front-end and a flow to self-issue cash
* Oracle: The flows and services required by oracle nodes to respond to data and signing queries
* Base: A collection of files that non-oracle and oracle nodes need to share, such as contract and state definitions

There is also a series of tests under `src/`.

The project is structured in this way so that non-oracle nodes only have to run the non-oracle flows and the web API, 
while oracle nodes only have to run the oracle flows and services.

# Pre-requisites:
  
See https://docs.corda.net/getting-set-up.html.

# Usage

## Running the nodes:

See https://docs.corda.net/tutorial-cordapp.html#running-the-example-cordapp.

## Interacting with the nodes:

You should interact with this CorDapp using the web front-end. Each node exposes this front-end on a different address:

* Issuing Bank: `localhost:10007/web/loc/issuing`
* Advising Bank: `localhost:10010/web/loc/advising`
* Buyer: `localhost:10013/web/loc/buyer`
* Seller: `localhost:10010/web/loc/seller`

When using the front-end:

1. Start by issuing yourself some cash using the `Issue cash url` which is /api/loc/issue-cash on your node
2. Begin the deal with the Seller node and then follow the tour guide through
3. If you don't want to use the tour guide - begin with the seller issuing an invoice (hitting autocomplete on this form and all forms is the quickest way forward). Go to the buyer to create a LoC application for that invoice. Now visit the issuing bank to approve the application. Go back to the seller in order to inspect the LoC and add the bill of lading, packing list and mark the product(s) as shipped. Now visit the advising bank and pay the seller, then the issuing bank to pay the advising bank and finally the buyer to pay the issuing bank.
