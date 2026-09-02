package dev.jobflow.context;

import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AzureSecretResolver {
    private final String vaultUri;
    private final String secretName;
    private volatile SecretClient client;

    public AzureSecretResolver(@Value("${azure.openai.key-vault-uri:}") String vaultUri,
                               @Value("${azure.openai.key-secret-name:azure-openai-key}") String secretName) {
        this.vaultUri = vaultUri == null ? "" : vaultUri.trim(); this.secretName = secretName;
    }

    public String resolve() {
        String direct = System.getenv("AZURE_OPENAI_KEY");
        if (direct != null && !direct.isBlank()) return direct;
        if (vaultUri.isBlank()) throw new IllegalStateException("Azure OpenAI Key Vault URI is required");
        if (client == null) synchronized (this) {
            if (client == null) {
                DefaultAzureCredential credential = new DefaultAzureCredentialBuilder().build();
                client = new SecretClientBuilder().vaultUrl(vaultUri).credential(credential).buildClient();
            }
        }
        String value = client.getSecret(secretName).getValue();
        if (value == null || value.isBlank()) throw new IllegalStateException("Azure OpenAI secret is empty");
        return value;
    }
}
