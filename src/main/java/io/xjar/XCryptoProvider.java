package io.xjar;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Security;

final class XCryptoProvider {

    private XCryptoProvider() {
    }

    static void ensure() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

}
