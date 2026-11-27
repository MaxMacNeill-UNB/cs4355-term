package org.example.plugin.hash;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Stub {@link HashPlugin} for {@code SHA-256}.
 */
public class Sha256HashPlugin implements HashPlugin {

    @Override
    public String getAlgorithmName() {
        return "SHA-256";
    }

    @Override
    public String hash(byte[] data) {
        return HexFormat.of().formatHex(newDigest().digest(data));
    }

    @Override
    public String hash(InputStream input) throws IOException {
        MessageDigest md = newDigest();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = input.read(buffer)) != -1){
            md.update(buffer, 0, n);
        }
        return HexFormat.of().formatHex(md.digest());
    }

    private MessageDigest newDigest(){
        try{
            return MessageDigest.getInstance("SHA-256");
        } 
        catch (NoSuchAlgorithmException e){
            throw new IllegalStateException(e);
        }
    }
}
