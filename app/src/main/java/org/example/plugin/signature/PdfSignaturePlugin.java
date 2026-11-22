package org.example.plugin.signature;

import java.io.IOException;

/**
 * Stub {@link SignaturePlugin} for embedded PDF digital signature verification ({@code plugin: "pdf"}).
 */
public class PdfSignaturePlugin implements SignaturePlugin {

    @Override
    public String getPluginName() {
        return "pdf";
    }

    @Override
    public VerificationResult verify(byte[] content, String publicKeyPath) throws IOException {
        throw new UnsupportedOperationException("PdfSignaturePlugin.verify is not yet implemented");
    }
}
