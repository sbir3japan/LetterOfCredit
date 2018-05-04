package eloc.api

import eloc.state.LOCApplicationState
import eloc.state.LOCState

/** These classes map state properties into a format that can be passed to the
 * front-end as JSON. */

/**
 * Converts the [LOCApplicationState] into the [LocAppFormData] to be
 * parsed by the front-end.
 */
fun locApplicationStateToLocApplicationFormData(state: LOCApplicationState) = LocAppFormData(
        applicationId = state.props.letterOfCreditApplicationID,
        applicationDate = state.props.applicationDate.toString(),
        typeCredit = state.props.typeCredit.toString(),
        amount = state.props.amount.quantity.toInt(),
        currency = state.props.amount.token.currencyCode,
        expiryDate = state.props.expiryDate.toString(),
        portLoadingCountry = state.props.portLoading.country,
        portLoadingCity = state.props.portLoading.city,
        portLoadingAddress = state.props.portLoading.address ?: "na",
        portDischargeCountry = state.props.portDischarge.country,
        portDischargeCity = state.props.portDischarge.city,
        portDischargeAddress = state.props.portDischarge.address ?: "na",
        goodsDescription = state.props.goods.first().description,
        goodsQuantity = state.props.goods.first().quantity,
        goodsWeight = state.props.goods.first().grossWeight!!.quantity.toInt(),
        goodsWeightUnit = state.props.goods.first().grossWeight!!.unit.toString(),
        goodsUnitPrice = state.props.goods.first().unitPrice.quantity.toInt(),
        goodsPurchaseOrderRef = state.props.goods.first().purchaseOrderRef ?: "na",
        placePresentationCountry = state.props.placePresentation.country,
        placePresentationState = state.props.placePresentation.state ?: "na",
        placePresentationCity = state.props.placePresentation.city,
        lastShipmentDate = state.props.lastShipmentDate.toString(),
        periodPresentation = state.props.periodPresentation.days,
        beneficiary = state.props.beneficiary.name.organisation,
        issuer = state.props.issuer.name.organisation,
        applicant = state.props.applicant.name.organisation,
        advisingBank = state.props.advisingBank.name.organisation)

/**
 * Converts the [LOCState] into the [LocDataA] to be parsed by the
 * front-end.
 */
fun locStateToLocDataA(state: LOCState) = LocDataA(
        beneficiaryPaid = state.beneficiaryPaid,
        advisoryPaid = state.advisoryPaid,
        issuerPaid = state.issuerPaid,
        issued = state.issued,
        terminated = state.terminated,
        beneficiary = state.props.beneficiary.name.organisation,
        applicant = state.props.applicant.name.organisation,
        advisoryBank = state.props.advisingBank.name.organisation,
        issuingBank = state.props.issuingBank.name.organisation,
        amount = state.props.amount.quantity.toInt(),
        currency = state.props.amount.token.currencyCode,
        quantity = state.props.descriptionGoods.first().quantity,
        purchaseOrderRef = state.props.descriptionGoods.first().purchaseOrderRef,
        description = state.props.descriptionGoods.first().description,
        status = state.status)

/**
 * Converts the [LOCState] into the [LocDataB] to be parsed by the
 * front-end.
 */
fun locStateToLocDataB(state: LOCState) = LocDataB(
        letterOfCreditId = state.props.letterOfCreditID,
        applicationDate = state.props.applicationDate.toString(),
        issueDate = state.props.issueDate.toString(),
        typeCredit = state.props.typeCredit.toString(),
        amount = state.props.amount.quantity.toInt(),
        currency = state.props.amount.token.currencyCode,
        expiryDate = state.props.expiryDate.toString(),
        portLoadingCountry = state.props.portLoading.country,
        portLoadingCity = state.props.portLoading.city,
        portLoadingAddress = state.props.portLoading.address ?: "na",
        portDischargeCountry = state.props.portDischarge.country,
        portDischargeCity = state.props.portDischarge.city,
        portDischargeAddress = state.props.portDischarge.address ?: "na",
        goodsDescription = state.props.descriptionGoods.first().description,
        goodsQuantity = state.props.descriptionGoods.first().quantity,
        goodsWeight = state.props.descriptionGoods.first().grossWeight!!.quantity.toInt(),
        goodsWeightUnit = state.props.descriptionGoods.first().grossWeight!!.unit.toString(),
        goodsUnitPrice = state.props.descriptionGoods.first().unitPrice.quantity.toInt(),
        goodsPurchaseOrderRef = state.props.descriptionGoods.first().purchaseOrderRef ?: "na",
        placePresentationCountry = state.props.placePresentation.country,
        placePresentationState = state.props.placePresentation.state ?: "na",
        placePresentationCity = state.props.placePresentation.city,
        lastShipmentDate = state.props.latestShip.toString(),
        periodPresentation = state.props.periodPresentation.days,
        beneficiary = state.props.beneficiary.name,
        issuer = state.props.issuingBank.name,
        applicant = state.props.applicant.name,
        advisingBank = state.props.advisingBank.name,
        beneficiaryPaid = state.beneficiaryPaid,
        advisoryPaid = state.advisoryPaid,
        issuerPaid = state.issuerPaid,
        issued = state.issued,
        terminated = state.terminated)

/**
 * Converts the [LOCApplicationState] into the [LocAppDataSummary] to be
 * parsed by the front-end.
 */
fun locApplicationStateToLocApplicationDataSummary(state: LOCApplicationState) = LocAppDataSummary(
        state.props.beneficiary.name.organisation,
        state.props.applicant.name.organisation,
        state.props.amount.quantity.toInt(),
        state.props.amount.token.currencyCode,
        state.props.goods.first().description,
        state.props.goods.first().purchaseOrderRef,
        state.status.toString())