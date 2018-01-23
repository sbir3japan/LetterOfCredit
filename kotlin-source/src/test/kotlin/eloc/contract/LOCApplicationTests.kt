/*package eloc.contract

import eloc.state.LOCApplicationProperties
import eloc.state.LOCApplicationState
import net.corda.core.contracts.Amount
import net.corda.core.contracts.DOLLARS
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.Party
import net.corda.core.crypto.SecureHash
import net.corda.core.seconds
import net.corda.core.utilities.TEST_TX_TIME
import net.corda.testing.*
import org.junit.Test
import java.time.LocalDate
import java.time.Period
import java.util.*

/**
 * Created by natixis on 4/12/17.
 */

class LOCApplicationTests {
    val applicationProps = LOCApplicationProperties(
            letterOfCreditApplicationID = "LOC01",
            applicationDate = LocalDate.of(2016, 5, 15),
            typeCredit = LocDataStructures.CreditType.SIGHT,
            amount = 100000.DOLLARS,
            expiryDate = LocalDate.of(2017, 12, 14),
            portLoading = LocDataStructures.Port("SG", "Singapore", null, null, null),
            portDischarge = LocDataStructures.Port("US", "Oakland", null, null, null),
            goods = listOf(LocDataStructures.PricedGood(description = "Tiger balm",
                    quantity = 10000,
                    grossWeight = null,
                    unitPrice = Amount(1, Currency.getInstance("USD")),
                    purchaseOrderRef = null
            )),
            placePresentation = LocDataStructures.Location("US", "California", "Oakland"),
            lastShipmentDate = LocalDate.of(2016, 6, 12), // TODO does it make sense to include shipment date?
            periodPresentation = Period.ofDays(31),
            beneficiary = BOB,
            issuer = MEGA_CORP,
            applicant = ALICE,
            advisingBank = BIG_CORP,
            invoiceRef = StateRef(SecureHash.randomSHA256(), 0)
    )



    val initialState = LOCApplicationState(owner= MEGA_CORP, status = LOCApplication.Status.PENDING_ISSUER_REVIEW,
            props = applicationProps, purchaseOrder = null)

    @Test
    fun `Apply - requireThat Tests`() {
        ledger {
            transaction {
                output { initialState }
                timestamp(TEST_TX_TIME, 30.seconds)
                command(MEGA_CORP_PUBKEY) {
                    LOCApplication.Commands.ApplyForLOC()
                }

                this.verifies()
            }
        }

        transaction {
            // owned by Alice
            output { initialState.copy(owner= ALICE) }
            command(ALICE_PUBKEY) {
                LOCApplication.Commands.ApplyForLOC()
            }
            timestamp(TEST_TX_TIME)
            this.`fails with`("the owner must be the issuer")
        }

        transaction {
            output { initialState.copy(status= LOCApplication.Status.APPROVED) }
            command(MEGA_CORP_PUBKEY) {
                LOCApplication.Commands.ApplyForLOC()
            }
            timestamp(TEST_TX_TIME)
            this.`fails with`("the output status must be pending issuer review")
        }

    }
}*/