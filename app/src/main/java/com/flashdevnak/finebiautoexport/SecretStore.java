package com.flashdevnak.finebiautoexport;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Stores only local app secrets using Android Keystore backed AES/GCM. */
public final class SecretStore {
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String ALIAS = "finebi_auto_export_mail_v1";

    private SecretStore() {}

    public static String encrypt(String plain) throws Exception {
        if (plain == null || plain.isEmpty()) return "";
        SecretKey key = getOrCreateKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        ByteBuffer out = ByteBuffer.allocate(4 + iv.length + encrypted.length);
        out.putInt(iv.length);
        out.put(iv);
        out.put(encrypted);
        return Base64.encodeToString(out.array(), Base64.NO_WRAP);
    }

    public static String decrypt(String encoded) throws Exception {
        if (encoded == null || encoded.isEmpty()) return "";
        byte[] all = Base64.decode(encoded, Base64.NO_WRAP);
        ByteBuffer in = ByteBuffer.wrap(all);
        int ivLength = in.getInt();
        if (ivLength < 12 || ivLength > 32 || ivLength > in.remaining()) {
            throw new IllegalStateException("invalid encrypted secret");
        }
        byte[] iv = new byte[ivLength];
        in.get(iv);
        byte[] encrypted = new byte[in.remaining()];
        in.get(encrypted);

        SecretKey key = getOrCreateKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
        ks.load(null);
        KeyStore.Entry existing = ks.getEntry(ALIAS, null);
        if (existing instanceof KeyStore.SecretKeyEntry) {
            return ((KeyStore.SecretKeyEntry) existing).getSecretKey();
        }

        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
        );
        generator.init(new KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generator.generateKey();
    }
}
