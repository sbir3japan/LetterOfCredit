![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

# Letter of Credit demo CorDapp

This CorDapp allows nodes to engage in a letter of credit scenario. A buyer and seller have a trade deal facilitated by an issuing and advising bank. 

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
* Seller: `localhost:10016/web/loc/seller`

When using the front-end:

1. Start by issuing yourself some cash using the `Issue cash URL` which is `/api/loc/issue-cash` on your node
2. Begin the deal with the Seller node and then follow the tour guide through
3. If you don't want to use the tour guide - begin with the seller issuing an invoice (hitting autocomplete on this form and all forms is the quickest way forward). Go to the buyer to create a LoC application for that invoice. Now visit the issuing bank to approve the application. Go back to the seller in order to inspect the LoC and add the bill of lading, packing list and mark the product(s) as shipped. Now visit the advising bank and pay the seller, then the issuing bank to pay the advising bank and finally the buyer to pay the issuing bank.
