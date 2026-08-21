package dev.jobflow.ingestion;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class GmailAesGcmTokenCipher implements GmailTokenCipher {
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();
    public GmailAesGcmTokenCipher(String base64Key) {
        byte[] bytes = Base64.getDecoder().decode(base64Key);
        if (bytes.length != 32) throw new IllegalArgumentException("Gmail token key must be 32 bytes");
        key = new SecretKeySpec(bytes, "AES");
    }
    @Override public String encrypt(String plaintext) {
        try { byte[] iv=new byte[12]; random.nextBytes(iv); Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,iv)); byte[] out=c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)); byte[] packed=new byte[iv.length+out.length]; System.arraycopy(iv,0,packed,0,iv.length); System.arraycopy(out,0,packed,iv.length,out.length); return Base64.getUrlEncoder().withoutPadding().encodeToString(packed); } catch (GeneralSecurityException e) { throw new IllegalStateException("Gmail token encryption failed",e); }
    }
    @Override public String decrypt(String encoded) {
        try { byte[] packed=Base64.getUrlDecoder().decode(encoded); if(packed.length<=12) throw new IllegalArgumentException("ciphertext is too short"); byte[] iv=java.util.Arrays.copyOf(packed,12); byte[] body=java.util.Arrays.copyOfRange(packed,12,packed.length); Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,iv)); return new String(c.doFinal(body),StandardCharsets.UTF_8); } catch (GeneralSecurityException|IllegalArgumentException e) { throw new IllegalStateException("Gmail token decryption failed",e); }
    }
}
