package eloc.api

import eloc.state.LOCApplicationState
import eloc.state.LOCState

/**
 * Converts the [LOCApplicationState] into the [LocAppData] to be
 * parsed by the front-end.
 */
fun locApplicationStateToLocApplicationData(state: LOCApplicationState) = LocAppData(
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
        // TODO: Different than below.
        advisoryBank = state.props.advisingBank.name.organisation,
        // TODO: Different than below.
        issuingBank = state.props.issuingBank.name.organisation,
        amount = state.props.amount.quantity.toInt(),
        currency = state.props.amount.token.currencyCode,
        // TODO: Different than below.
        quantity = state.props.descriptionGoods.first().quantity,
        // TODO: Different than below.
        purchaseOrderRef = state.props.descriptionGoods.first().purchaseOrderRef,
        // TODO: Different than below.
        description = state.props.descriptionGoods.first().description,
        // TODO: Not included below.
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