package eloc.api

import eloc.state.LOCApplicationState

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