package com.example.webhook

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WebhookMessage(
    val from: String,
    val id: String,
    val timestamp: String,
    val type: String,
    val text: WebhookText? = null,
    val image: WebhookImage? = null,
    val audio: WebhookAudio? = null,
    val video: WebhookVideo? = null,
    val document: WebhookDocument? = null,
    val location: WebhookLocation? = null,
    val contacts: List<WebhookContactDetail>? = null,
    val interactive: WebhookInteractive? = null,
    val reaction: WebhookReaction? = null,
    val sticker: WebhookSticker? = null,
    val order: WebhookOrder? = null,
    @SerialName("system") val systemContent: WebhookSystemContent? = null,
    val context: WebhookContext? = null,
    val referral: WebhookReferral? = null,
    val errors: List<WebhookError>? = null
)

@Serializable
data class WebhookText(val body: String)

@Serializable
data class WebhookImage(
    val id: String,
    @SerialName("mime_type") val mimeType: String? = null,
    val sha256: String? = null,
    val caption: String? = null,
    val url: String? = null
)

@Serializable
data class WebhookAudio(
    val id: String,
    @SerialName("mime_type") val mimeType: String? = null,
    val sha256: String? = null,
    val voice: Boolean? = null,
    val url: String? = null
)

@Serializable
data class WebhookVideo(
    val id: String,
    @SerialName("mime_type") val mimeType: String? = null,
    val sha256: String? = null,
    val caption: String? = null,
    val url: String? = null,
    @SerialName("static_url") val staticUrl: String? = null
)

@Serializable
data class WebhookDocument(
    val id: String,
    @SerialName("mime_type") val mimeType: String? = null,
    val sha256: String? = null,
    val caption: String? = null,
    val filename: String,
    val url: String? = null
)

@Serializable
data class WebhookLocation(
    val latitude: Double,
    val longitude: Double,
    val name: String? = null,
    val address: String? = null
)

@Serializable
data class WebhookContactDetail(
    val addresses: List<WebhookAddress>? = null,
    val birthday: String? = null,
    val emails: List<WebhookEmail>? = null,
    val name: WebhookContactName,
    val org: WebhookOrg? = null,
    val phones: List<WebhookPhone>? = null,
    val urls: List<WebhookUrl>? = null,
    @SerialName("wa_id") val waId: String? = null
)

@Serializable
data class WebhookContactName(
    @SerialName("formatted_name") val formattedName: String,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("middle_name") val middleName: String? = null,
    val prefix: String? = null,
    val suffix: String? = null
)

@Serializable
data class WebhookPhone(
    val phone: String? = null,
    @SerialName("wa_id") val waId: String? = null,
    val type: String? = null
)

@Serializable
data class WebhookEmail(
    val email: String,
    val type: String? = null
)

@Serializable
data class WebhookAddress(
    val street: String? = null,
    val city: String? = null,
    val state: String? = null,
    val zip: String? = null,
    val country: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    val type: String? = null
)

@Serializable
data class WebhookOrg(
    val company: String? = null,
    val department: String? = null,
    val title: String? = null
)

@Serializable
data class WebhookUrl(
    val url: String,
    val type: String? = null
)

@Serializable
data class WebhookInteractive(
    val type: String,
    @SerialName("button_reply") val buttonReply: WebhookButtonReply? = null,
    @SerialName("list_reply") val listReply: WebhookListReply? = null
)

@Serializable
data class WebhookButtonReply(
    val id: String,
    val title: String
)

@Serializable
data class WebhookListReply(
    val id: String,
    val title: String,
    val description: String? = null
)

@Serializable
data class WebhookReaction(
    val emoji: String? = null,
    @SerialName("message_id") val messageId: String
)

@Serializable
data class WebhookSticker(
    val id: String,
    @SerialName("mime_type") val mimeType: String? = null,
    val sha256: String? = null,
    val animated: Boolean? = null,
    val url: String? = null
)

@Serializable
data class WebhookOrder(
    @SerialName("catalog_id") val catalogId: String,
    @SerialName("product_items") val productItems: List<WebhookProductItem>
)

@Serializable
data class WebhookProductItem(
    @SerialName("product_retailer_id") val productRetailerId: String,
    val quantity: String,
    @SerialName("item_price") val itemPrice: Double? = null,
    val currency: String? = null
)

@Serializable
data class WebhookSystemContent(
    val body: String,
    val type: String,
    val identity: String? = null,
    @SerialName("new_wa_id") val newWaId: String? = null,
    @SerialName("wa_id") val waId: String? = null
)

@Serializable
data class WebhookContext(
    val from: String? = null,
    val id: String? = null,
    val forwarded: Boolean? = null,
    @SerialName("frequently_forwarded") val frequentlyForwarded: Boolean? = null
)

@Serializable
data class WebhookReferral(
    @SerialName("source_url") val sourceUrl: String? = null,
    @SerialName("source_id") val sourceId: String? = null,
    @SerialName("source_type") val sourceType: String? = null,
    val body: String? = null,
    val headline: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("video_url") val videoUrl: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("ctwa_clid") val ctwaClid: String? = null
)

@Serializable
data class WebhookError(
    val code: Int,
    val title: String,
    @SerialName("error_data") val errorData: WebhookErrorData? = null,
    val details: String? = null
)

@Serializable
data class WebhookErrorData(
    val details: String? = null
)
