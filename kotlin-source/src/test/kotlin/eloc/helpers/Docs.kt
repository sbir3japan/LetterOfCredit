package eloc.helpers

import eloc.LetterOfCreditDataStructures.Company
import eloc.LetterOfCreditDataStructures.Good
import eloc.LetterOfCreditDataStructures.Location
import eloc.LetterOfCreditDataStructures.Person
import eloc.LetterOfCreditDataStructures.Port
import eloc.LetterOfCreditDataStructures.PricedGood
import eloc.LetterOfCreditDataStructures.Weight
import eloc.LetterOfCreditDataStructures.WeightUnit
import eloc.state.BillOfLadingProperties
import eloc.state.InvoiceProperties
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.finance.DOLLARS
import net.corda.testing.core.TestIdentity
import java.time.LocalDate

val bolProperties = BillOfLadingProperties(
        billOfLadingID = "billOfLadingID",
        issueDate = LocalDate.now(),
        carrierOwner = TestIdentity(CordaX500Name("MiniCorp", "", "GB")).party,
        nameOfVessel = "Karaboudjan",
        descriptionOfGoods = listOf(Good(description = "Crab meet cans", quantity = 10000, grossWeight = null)),
        dateOfShipment = LocalDate.now(),
        portOfLoading = Port(country = "Morokko", city = "Larache", address = null, state = null, name = null),
        portOfDischarge = Port(country = "Belgium", city = "Antwerpen", address = null, state = null, name = null),
        shipper = null,
        notify = Person(
                name = "Some guy",
                address = "Some address",
                phone = "+11 23456789"
        ),
        consignee = Company(
                name = "Some company",
                address = "Some other address",
                phone = "+11 12345678"
        ),
        grossWeight = Weight(
                quantity = 2500.0,
                unit = WeightUnit.KG
        ),
        placeOfReceipt = Location(
                country = "USA",
                state = "Iowa",
                city = "Des Moines"

        )
)