package no.nav.helse.flex.bucketuploader

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
class BucketUploaderService(
    @Value("\${integration.flex-bucket-uploader.url}")
    private val bucketUploderUrl: String,
    private val azureAuthClient: RestTemplate,
) {

    fun slettKvittering(blobName: String): Boolean {
        val response = azureAuthClient.getForEntity(
            "$bucketUploderUrl/maskin/slett/$blobName",
            VedleggRespons::class.java)
        return response.statusCode.is2xxSuccessful
    }
}


data class VedleggRespons(val id: String? = null, val melding: String)
