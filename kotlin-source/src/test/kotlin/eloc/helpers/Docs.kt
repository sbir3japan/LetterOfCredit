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
import eloc.state.PackingListProperties
import net.corda.core.crypto.SecureHash
import net.corda.finance.DOLLARS
import java.time.LocalDate

var invoiceProperties = InvoiceProperties(
        invoiceID = "123",
        seller = Company(
                name = "Mega Corp LTD.",
                address = "123 Main St. Awesome Town, ZZ 11111",
                phone = null
        ),
        buyer = Company(
                name = "Sandworm Imports",
                address = "555 Elm St. Little Town, VV, 22222",
                phone = null
        ),
        invoiceDate = LocalDate.now(),
        attachmentHash = SecureHash.SHA256(ByteArray(32, { 0.toByte() })),
        term = 60,
        goods = arrayListOf(
                PricedGood(
                        description = "Salt",
                        purchaseOrderRef = null,
                        quantity = 10,
                        unitPrice = 3.DOLLARS,
                        grossWeight = null
                ),
                PricedGood(
                        description = "Pepper",
                        purchaseOrderRef = null,
                        quantity = 20,
                        unitPrice = 4.DOLLARS,
                        grossWeight = null
                )
        )

)

val plProperties = PackingListProperties(
        issueDate = LocalDate.now(),
        orderNumber = "123",
        sellersOrderNumber = "123",
        transportMethod = "Ship",
        nameOfVessel = "SurfRider",
        billOfLadingNumber = "123",
        seller = Company(
                name = "Mega Corp LTD.",
                address = "123 Main St. Awesome Town, ZZ 11111",
                phone = null
        ),
        buyer = Company(
                name = "Sandworm Imports",
                address = "555 Elm St. Little Town, VV, 22222",
                phone = null
        ),
        descriptionOfGoods = arrayListOf(
                PricedGood(
                        description = "Salt",
                        purchaseOrderRef = null,
                        quantity = 10,
                        unitPrice = 3.DOLLARS,
                        grossWeight = null
                ),
                PricedGood(
                        description = "Pepper",
                        purchaseOrderRef = null,
                        quantity = 20,
                        unitPrice = 4.DOLLARS,
                        grossWeight = null
                )
        ),
        attachmentHash = SecureHash.SHA256(ByteArray(32, { 0.toByte() }))
)

val bolProperties = BillOfLadingProperties(
        billOfLadingID = "billOfLadingID",
        issueDate = LocalDate.now(),
        carrierOwner = MINI_CORP.party,
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