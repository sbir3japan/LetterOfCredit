/*package eloc.contract

import eloc.state.BillOfLadingProperties
import eloc.state.BillofLadingState
import net.corda.core.seconds
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.core.utilities.DUMMY_NOTARY_KEY
import net.corda.core.utilities.TEST_TX_TIME
import net.corda.testing.*
import net.corda.testing.node.MockServices
import org.junit.Test
import java.time.Instant
import java.time.LocalDate


class BillOfLadingAgreementTests {
    val pros = BillOfLadingProperties(
            billOfLadingID = "billOfLadingID",
            issueDate = LocalDate.now(),
            carrierOwner = ALICE,
            nameOfVessel = "Karaboudjan",
            descriptionOfGoods = listOf(LocDataStructures.Good(description="Crab meet cans",quantity = 10000,grossWeight = null)),
            dateOfShipment = LocalDate.now(),
            portOfLoading = LocDataStructures.Port(country = "Morokko",city = "Larache",address = null,state = null,name=null),
            portOfDischarge = LocDataStructures.Port(country = "Belgium",city = "Antwerpen",address = null,state = null,name=null),
            shipper = null,
            notify = LocDataStructures.Person(
                    name = "Some guy",
                    address = "Some address",
                    phone = "+11 23456789"
            ),
            consignee = LocDataStructures.Company(
                    name = "Some company",
                    address = "Some other address",
                    phone = "+11 12345678"
            ),
            grossWeight = LocDataStructures.Weight(
                    quantity =  2500.0,
                    unit = LocDataStructures.WeightUnit.KG
            ),
            placeOfReceipt = LocDataStructures.Location(
              country = "USA",
              state ="Iowa",
              city = "Des Moines"

            )
    )

    val billState = BillofLadingState(
            owner = MEGA_CORP_PUBKEY,
            beneficiary = BOB,
            props = pros
    )

    //val attachments = MockStorageService().attachments

    val aliceService = MockServices( ALICE_KEY )
    val bigCorpService = MockServices( MEGA_CORP_KEY )

    fun issueSignedTransaction() : SignedTransaction {
        val ptx = BillOfLadingAgreement().generateIssue( billState.beneficiary, billState.owner.singleKey, DUMMY_NOTARY, billState.props).apply {
            setTime(TEST_TX_TIME, 30.seconds)

            //owner
            signWith(MEGA_CORP_KEY)

            //beneficiary
            signWith(BOB_KEY)

            //carrier
            signWith(ALICE_KEY)

            //notary
            signWith(DUMMY_NOTARY_KEY)
        }

        // officially signed
        return ptx.toSignedTransaction()
    }


    //Generation method tests
    @Test
    fun generateIssueMethod() {
        // officially signed
        val stx = issueSignedTransaction()

        // verified
        stx.toLedgerTransaction(aliceService).verify()

        // and recorded
        aliceService.recordTransactions(listOf( stx ))
    }

    @Test(expected = IllegalStateException::class)
    fun generateIssueMethod_UnsignedNotary() {
        val ptx = BillOfLadingAgreement().generateIssue( billState.beneficiary, billState.owner.singleKey, DUMMY_NOTARY, billState.props).apply {
            setTime(TEST_TX_TIME, 30.seconds)
            signWith(MEGA_CORP_KEY)
            signWith(BOB_KEY)
            signWith(ALICE_KEY)
            //missing signWith(DUMMY_NOTARY_KEY)
        }
        ptx.toSignedTransaction() //exception thrown
    }


    @Test
    fun transferAndEndorseGenerationMethod() {
        val issueTX = issueSignedTransaction()

        //record
        bigCorpService.recordTransactions(listOf( issueTX ))

        //transfer ownership
        val txBuilder = TransactionBuilder(notary = DUMMY_NOTARY)

        // TODO: same owner and beneficiary - is this a problem?
        BillOfLadingAgreement().generateTransferAndEndorse( txBuilder, issueTX.tx.outRef(0), CHARLIE_PUBKEY.singleKey, CHARLIE)
        txBuilder.setTime(TEST_TX_TIME, 30.seconds)
        txBuilder.signWith(MEGA_CORP_KEY)
        txBuilder.signWith(DUMMY_NOTARY_KEY)
        txBuilder.signWith(BOB_KEY)
        txBuilder.signWith(ALICE_KEY)
        val stx = txBuilder.toSignedTransaction()
        stx.toLedgerTransaction(bigCorpService).verify()
    }

    @Test (expected = IllegalStateException::class)
    fun transferAndEndorseGenerationMethod_MissingOwnerSignature() {
        val issueTX = issueSignedTransaction()
        bigCorpService.recordTransactions( listOf( issueTX ) )

        println( "Owner: " + MEGA_CORP_PUBKEY)
        println( "Input State Beneficiary: " + BOB_PUBKEY)
        println( "Input State Carrier: " + ALICE_PUBKEY)
        println( "New Owner: " + CHARLIE_PUBKEY)

        val txBuilder = TransactionBuilder(notary = DUMMY_NOTARY)


        BillOfLadingAgreement().generateTransferAndEndorse( txBuilder, issueTX.tx.outRef(0), CHARLIE_PUBKEY.singleKey, CHARLIE)
        //txBuilder.signWith(MEGA_CORP_KEY)
        txBuilder.signWith(DUMMY_NOTARY_KEY)
        txBuilder.signWith(BOB_KEY)
        txBuilder.signWith(ALICE_KEY)
        txBuilder.toSignedTransaction()
    }


    @Test
    fun transferPossessionGenerationMethod() {
        val issueTX = issueSignedTransaction()
        bigCorpService.recordTransactions( listOf( issueTX ) )

        val txBuilder = TransactionBuilder(notary = DUMMY_NOTARY)
        BillOfLadingAgreement().generateTransferPossession( txBuilder, issueTX.tx.outRef(0), CHARLIE_PUBKEY.singleKey)
        txBuilder.setTime(TEST_TX_TIME, 30.seconds)
        txBuilder.signWith(MEGA_CORP_KEY)
        txBuilder.signWith(DUMMY_NOTARY_KEY)
        val stx = txBuilder.toSignedTransaction()
        stx.toLedgerTransaction(bigCorpService).verify()
    }

    @Test (expected = IllegalStateException::class)
    fun transferPossessionGenerationMethod_Unsigned() {
        val issueTX = issueSignedTransaction()
        bigCorpService.recordTransactions( listOf( issueTX ) )

        val txBuilder = TransactionBuilder(notary = DUMMY_NOTARY)
        BillOfLadingAgreement().generateTransferPossession( txBuilder, issueTX.tx.outRef(0), CHARLIE_PUBKEY.singleKey)
        txBuilder.setTime(TEST_TX_TIME, 30.seconds)
        //txBuilder.signWith(MEGA_CORP_KEY)
        txBuilder.signWith(DUMMY_NOTARY_KEY)
        val stx = txBuilder.toSignedTransaction()
        stx.toLedgerTransaction(bigCorpService).verify()
    }

    @Test
    fun generalConsistencyTests() {
        transaction {
            input { billState }
            input { billState.copy(owner = CHARLIE_PUBKEY, beneficiary = CHARLIE) }
            command(MEGA_CORP_PUBKEY, BillOfLadingAgreement.Commands.TransferAndEndorseBL())
            command(MEGA_CORP_PUBKEY, BillOfLadingAgreement.Commands.TransferPossession())
            timestamp(Instant.now())
            //multiple commands
            this.`fails with`("List has more than one element.")
        }

        transaction {
            input { billState }
            output { billState.copy(owner = CHARLIE_PUBKEY, beneficiary = CHARLIE) }
            timestamp(Instant.now())
            //There are no commands
            this.`fails with`("Required ${BillOfLadingAgreement.Commands::class.qualifiedName} command")
        }
    }

    @Test
    fun issueTests() {
        transaction {
            output { billState }
            command(ALICE_PUBKEY) { BillOfLadingAgreement.Commands.IssueBL() }
            timestamp(Instant.now())
            this.verifies()
        }

        transaction {
            input { billState }
            output { billState.copy(owner = CHARLIE_PUBKEY, beneficiary = CHARLIE) }
            command(MEGA_CORP_PUBKEY, BOB_PUBKEY) { BillOfLadingAgreement.Commands.IssueBL() }
            timestamp(Instant.now())
            this.`fails with`("there is no input state")
        }

        transaction {
            output { billState }
            command(BOB_PUBKEY) { BillOfLadingAgreement.Commands.IssueBL() }
            timestamp(Instant.now())
            this.`fails with`("the transaction is signed by the carrier")
        }

    }

    @Test
    fun transferAndEndorseTests() {
        transaction {
            input { billState }
            output { billState.copy(owner = CHARLIE_PUBKEY, beneficiary = CHARLIE) }
            command(MEGA_CORP_PUBKEY, BOB_PUBKEY) { BillOfLadingAgreement.Commands.TransferAndEndorseBL() }
            timestamp(Instant.now())
            this.verifies()
        }

        transaction {
            input { billState }
            output { billState.copy(owner = CHARLIE_PUBKEY, beneficiary = CHARLIE) }
            command(MEGA_CORP_PUBKEY, BOB_PUBKEY) { BillOfLadingAgreement.Commands.TransferAndEndorseBL() }
            //There is no timestamp
            this.`fails with`("must be timestamped")
        }

        transaction {
            input { billState }
            input { billState.copy(owner = CHARLIE_PUBKEY, beneficiary = CHARLIE) }
            output { billState.copy(owner = CHARLIE_PUBKEY, beneficiary = CHARLIE) }
            command(MEGA_CORP_PUBKEY, BOB_PUBKEY) { BillOfLadingAgreement.Commands.TransferAndEndorseBL() }
            timestamp(Instant.now())
            //There are two inputs
            this.`fails with`("List has more than one element.")
        }

        transaction {
            output { billState.copy(owner = CHARLIE_PUBKEY, beneficiary = CHARLIE) }
            command(MEGA_CORP_PUBKEY, BOB_PUBKEY) { BillOfLadingAgreement.Commands.TransferAndEndorseBL() }
            timestamp(Instant.now())
            //There are no inputs
            this.`fails with`("List is empty.")
        }

        transaction {
            input { billState }
            output { billState.copy(owner = CHARLIE_PUBKEY, beneficiary = CHARLIE) }
            output { billState }
            command(MEGA_CORP_PUBKEY, BOB_PUBKEY) { BillOfLadingAgreement.Commands.TransferAndEndorseBL() }
            timestamp(Instant.now())
            //There are two outputs
            this.`fails with`("List has more than one element.")
        }

        transaction {
            input { billState }
            command(MEGA_CORP_PUBKEY, BOB_PUBKEY) { BillOfLadingAgreement.Commands.TransferAndEndorseBL() }
            timestamp(Instant.now())
            //There are no outputs
            this.`fails with`("List is empty.")
        }

        transaction {
            input { billState }
            output { billState.copy(owner = CHARLIE_PUBKEY, beneficiary = CHARLIE) }
            command(MEGA_CORP_PUBKEY) { BillOfLadingAgreement.Commands.TransferAndEndorseBL() }
            timestamp(Instant.now())
            this.`fails with`("the transaction is signed by the beneficiary")
        }

        transaction {
            input { billState }
            output { billState.copy(owner = CHARLIE_PUBKEY, beneficiary = CHARLIE) }
            command(BOB_PUBKEY) { BillOfLadingAgreement.Commands.TransferAndEndorseBL() }
            timestamp(Instant.now())
            this.`fails with`("the transaction is signed by the state object owner")
        }

        transaction {
            input { billState }
            output { billState.copy(owner = CHARLIE_PUBKEY, beneficiary = CHARLIE, props = pros.copy(nameOfVessel = "Svet")) }
            command(MEGA_CORP_PUBKEY, BOB_PUBKEY) { BillOfLadingAgreement.Commands.TransferAndEndorseBL() }
            timestamp(Instant.now())
            this.`fails with`("the bill of lading agreement properties are unchanged")
        }

    }

    @Test
    fun transferPossessionTests() {
        transaction {
            input { billState }
            output { billState.copy(owner = CHARLIE_PUBKEY) }
            command(MEGA_CORP_PUBKEY) { BillOfLadingAgreement.Commands.TransferPossession() }
            timestamp(Instant.now())
            this.verifies()
        }

        transaction {
            input { billState }
            output { billState.copy(owner = CHARLIE_PUBKEY) }
            command(MEGA_CORP_PUBKEY) { BillOfLadingAgreement.Commands.TransferPossession() }
            //There is no timestamp
            this.`fails with`("must be timestamped")
        }

        transaction {
            input { billState }
            input { billState.copy(owner = BOB_PUBKEY) }
            output { billState.copy(owner = CHARLIE_PUBKEY) }
            command(MEGA_CORP_PUBKEY) { BillOfLadingAgreement.Commands.TransferPossession() }
            timestamp(Instant.now())
            //There are two inputs
            this.`fails with`("List has more than one element.")
        }

        transaction {
            output { billState.copy(owner = CHARLIE_PUBKEY) }
            command(MEGA_CORP_PUBKEY) { BillOfLadingAgreement.Commands.TransferPossession() }
            timestamp(Instant.now())
            //There are no inputs
            this.`fails with`("List is empty.")
        }

        transaction {
            input { billState }
            output { billState.copy(owner = CHARLIE_PUBKEY) }
            output { billState.copy(owner = ALICE_PUBKEY) }
            command(MEGA_CORP_PUBKEY) { BillOfLadingAgreement.Commands.TransferPossession() }
            timestamp(Instant.now())
            //There are two outputs
            this.`fails with`("List has more than one element.")
        }

        transaction {
            input { billState }
            command(MEGA_CORP_PUBKEY) { BillOfLadingAgreement.Commands.TransferPossession() }
            timestamp(Instant.now())
            //There are no outputs
            this.`fails with`("List is empty.")
        }

        transaction {
            input { billState }
            output { billState.copy(owner = CHARLIE_PUBKEY) }
            command(ALICE_PUBKEY) { BillOfLadingAgreement.Commands.TransferPossession() }
            timestamp(Instant.now())
            this.`fails with`("the transaction is signed by the state object owner")
        }

        transaction {
            input { billState }
            output { billState.copy(owner = CHARLIE_PUBKEY,beneficiary = CHARLIE) }
            command(MEGA_CORP_PUBKEY) { BillOfLadingAgreement.Commands.TransferPossession() }
            timestamp(Instant.now())
            this.`fails with`("the beneficiary is unchanged")
        }


        transaction {
            input { billState }
            output { billState.copy(owner = CHARLIE_PUBKEY, props = pros.copy(nameOfVessel = "Svet")) }
            command(MEGA_CORP_PUBKEY) { BillOfLadingAgreement.Commands.TransferPossession() }
            timestamp(Instant.now())
            this.`fails with`("the bill of lading agreement properties are unchanged")
        }

    }

}
*/