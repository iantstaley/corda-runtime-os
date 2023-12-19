package net.corda.rest.client.auth.credentials

internal object EmptyCredentialsProvider : CredentialsProvider {
    override fun getCredentials(): Any {
        return Any()
    }
}
