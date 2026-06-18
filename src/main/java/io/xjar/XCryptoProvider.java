package io.xjar;

import java.security.Provider;
import java.security.Security;

final class XCryptoProvider {

    private XCryptoProvider() {
    }

    static void ensure() {
        Provider provider = loadBouncyCastleProvider();
        if (provider != null && Security.getProvider(provider.getName()) == null) {
            Security.addProvider(provider);
        }
    }

    private static Provider loadBouncyCastleProvider() {
        try {
            Class<?> providerClass = Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider");
            return (Provider) providerClass.getDeclaredConstructor().newInstance();
        } catch (Throwable ignored) {
            return null;
        }
    }

}
