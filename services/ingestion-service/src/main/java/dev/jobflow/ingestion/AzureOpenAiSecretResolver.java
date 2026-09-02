package dev.jobflow.ingestion;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Resolves the model key without placing the secret in application configuration or logs. */
@Component
class AzureOpenAiSecretResolver {
    private final String vaultUri;
    private final String secretName;
    private volatile SecretClient client;

    AzureOpenAiSecretResolver(
            @Value("${AZURE_OPENAI_KEY_VAULT_URI:}") String vaultUri,
            @Value("${AZURE_OPENAI_KEY_SECRET_NAME:azure-openai-key}") String secretName) {
        this.vaultUri = normalize(vaultUri);
        this.secretName = normalize(secretName);
    }

    String resolve() {
        if (vaultUri.isBlank() || secretName.isBlank()) {
            throw new ClassifierProviderException("Azure OpenAI Key Vault configuration is incomplete");
        }
        SecretClient current = client;
        if (current == null) {
            synchronized (this) {
                current = client;
                if (current == null) {
                    current = new SecretClientBuilder()
                            .vaultUrl(vaultUri)
                            .credential(new DefaultAzureCredentialBuilder().build())
                            .buildClient();
                    client = current;
                }
            }
        }
        String value = current.getSecret(secretName).getValue();
        if (value == null || value.isBlank()) {
            throw new ClassifierProviderException("Azure OpenAI Key Vault secret is empty");
        }
        return value;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
