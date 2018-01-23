/*package eloc.contract

import eloc.state.*
import net.corda.core.contracts.DOLLARS
import net.corda.core.contracts.TransactionType
import net.corda.core.crypto.SecureHash
import net.corda.core.seconds
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Test
import net.corda.core.utilities.*
import net.corda.testing.*
import net.corda.testing.node.MockServices
import java.io.File
import java.io.FileInputStream

/**
 * unit test cases that confirms the correct behavior of the AccountReceivable smart contract
 */
class PackingListTests {
    val PACKING_LIST_TIME: Instant = Instant.parse("2015-04-17T12:00:00.00Z")

    val plProperties = PackingListProperties(
            issueDate = PACKING_LIST_TIME.atZone(ZoneOffset.UTC).toLocalDate(),
            orderNumber = "123",
            sellersOrderNumber = "123",
            transportMethod = "Ship",
            nameOfVessel = "SurfRider",
            billOfLadingNumber = "123",
            seller = LocDataStructures.Company(
                    name = "Mega Corp LTD.",
                    address = "123 Main St. Awesome Town, ZZ 11111",
                    phone = null
            ),
            buyer = LocDataStructures.Company(
                    name = "Sandworm Imports",
                    address = "555 Elm St. Little Town, VV, 22222",
                    phone = null
            ),
            descriptionOfGoods = arrayListOf(
                    LocDataStructures.PricedGood(
                            description = "Salt",
                            purchaseOrderRef = null,
                            quantity = 10,
                            unitPrice = 3.DOLLARS,
                            grossWeight = null
                    ),
                    LocDataStructures.PricedGood(
                            description = "Pepper",
                            purchaseOrderRef = null,
                            quantity = 20,
                            unitPrice = 4.DOLLARS,
                            grossWeight = null
                    )
            ),
            attachmentHash = SecureHash.zeroHash
    )

    val initialState = PackingListState( ALICE, BOB, PackingList.Status.DRAFT, plProperties)

    @Test
    fun `Draft Tests`() {

        // good
        ledger {
            transaction {
                output { initialState }
                timestamp(TEST_TX_TIME, 30.seconds)
                command(ALICE_PUBKEY) {
                    PackingList.Commands.Create()
                }

                this.verifies()
            }
        }

        // missing buyer sig
        transaction {
            output { initialState }
            command(BOB_PUBKEY) {
                PackingList.Commands.Create()
            }
            timestamp(TEST_TX_TIME)
            this.`fails with`("the transaction is signed by the buyer")
        }
    }

    @Test
    fun `Update Tests`() {

        //test node
        val sellersBank = MockServices()

        // create
        val draftTx: SignedTransaction = PackingList().generateDraft( ALICE, BOB, DUMMY_NOTARY, plProperties ).apply {
            setTime( TEST_TX_TIME, 30.seconds )
            signWith( ALICE_KEY )
        }.toSignedTransaction(false)

        // load a jar and store it
        //val invoiceStream = FileInputStream(File( "attachments/invoice.jar"))
        //val invoiceAttachmentId = sellersBank.storageService.attachments.importAttachment( invoiceStream )

        // pretend we update some props and add an attachment
        val updateTx: SignedTransaction = run {
            val ptx = TransactionType.General.Builder(DUMMY_NOTARY)
            //ptx.addAttachment( invoiceAttachmentId )
            PackingList().update(ptx, draftTx.tx.outRef(0), plProperties)
            ptx.signWith(ALICE_KEY)
            ptx.toSignedTransaction(false)
        }

        // update
        val updateVerifiesSigTx: SignedTransaction = run {
            val ptx = TransactionType.General.Builder(DUMMY_NOTARY)
            PackingList().update(ptx, draftTx.tx.outRef(0), plProperties)
            ptx.signWith(ALICE_KEY)
            ptx.signWith(BOB_KEY)
            ptx.signWith(DUMMY_NOTARY_KEY)
            ptx.toSignedTransaction()
        }

        sellersBank.recordTransactions( listOf( updateTx, updateVerifiesSigTx ) )


/*        ledger {
            transaction {
                output { PackingListState( ALICE, BOB, PackingList.Status.SIGNED, plProperties) }
                command(ALICE_PUBKEY) { PackingList.Commands.Update() }
                timestamp(TEST_TX_TIME)
                verifies()
            }
        }*/
    }

}*/