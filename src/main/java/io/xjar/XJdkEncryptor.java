package io.xjar;

import io.xjar.key.XKey;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.spec.AlgorithmParameterSpec;
import java.io.*;
import java.util.Locale;

/**
 * JDK内置加密算法的加密器
 *
 * @author Payne 646742615@qq.com
 * 2018/11/22 14:01
 */
public class XJdkEncryptor implements XEncryptor {

    static {
        XCryptoProvider.ensure();
    }

    @Override
    public void encrypt(XKey key, File src, File dest) throws IOException {
        if (!dest.getParentFile().exists() && !dest.getParentFile().mkdirs()) {
            throw new IOException("could not make directory: " + dest.getParentFile());
        }
        try (
                InputStream in = new FileInputStream(src);
                OutputStream out = new FileOutputStream(dest)
        ) {
            encrypt(key, in, out);
        }
    }

    @Override
    public void encrypt(XKey key, InputStream in, OutputStream out) throws IOException {
        CipherInputStream cis = null;
        try {
            String algorithm = key.getAlgorithm();
            boolean gcmMode = algorithm.toUpperCase(Locale.ROOT).contains("/GCM/");
            Cipher cipher = Cipher.getInstance(algorithm);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key.getEncryptKey(), algorithm.split("[/]")[0]), createParameterSpec(gcmMode, key.getIvParameter()));
            cis = new CipherInputStream(in, cipher);
            XKit.transfer(cis, out);
        } catch (Exception e) {
            throw new IOException(e);
        } finally {
            XKit.close(cis);
        }
    }

    @Override
    public InputStream encrypt(XKey key, InputStream in) throws IOException {
        try {
            String algorithm = key.getAlgorithm();
            boolean gcmMode = algorithm.toUpperCase(Locale.ROOT).contains("/GCM/");
            Cipher cipher = Cipher.getInstance(algorithm);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key.getEncryptKey(), algorithm.split("[/]")[0]), createParameterSpec(gcmMode, key.getIvParameter()));
            return new CipherInputStream(in, cipher);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    public OutputStream encrypt(XKey key, OutputStream out) throws IOException {
        try {
            String algorithm = key.getAlgorithm();
            boolean gcmMode = algorithm.toUpperCase(Locale.ROOT).contains("/GCM/");
            Cipher cipher = Cipher.getInstance(algorithm);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key.getEncryptKey(), algorithm.split("[/]")[0]), createParameterSpec(gcmMode, key.getIvParameter()));
            return new CipherOutputStream(out, cipher);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private AlgorithmParameterSpec createParameterSpec(boolean gcmMode, byte[] iv) {
        if (gcmMode) {
            return new GCMParameterSpec(XConstants.GCM_TAG_LENGTH_BITS, iv);
        }
        return new IvParameterSpec(iv);
    }
}
