/*
 * DOCUMENTACIÓN DEL ARCHIVO
 * Deriva y verifica contraseñas con PBKDF2 y sal aleatoria; no almacena contraseñas en texto plano.
 */
package com.example.evogmd.login;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Hash de contraseña con PBKDF2-HMAC-SHA1 y sal por usuario. */
public final class PasswordHasher {
    public static final int ITERATIONS = 1_300_000;
    /**
     * Impide crear instancias de esta clase de utilidades.
     */
    private PasswordHasher() { }
    /**
     * Genera 16 bytes aleatorios para individualizar el hash de cada contraseña.
     * 
     * @return resultado de la operación descrita.
     */
    public static byte[] newSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return salt;
    }
    /**
     * Deriva el hash de la contraseña con PBKDF2 usando la sal y el número de iteraciones indicados.
     * 
     * @param password caracteres de la contraseña recibida.
     * @param salt sal criptográfica asociada a la contraseña.
     * @param iterations número de iteraciones PBKDF2.
     * @return resultado de la operación descrita.
     */
    public static byte[] hash(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                    .generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo proteger la contraseña", e);
        } finally { spec.clearPassword(); }
    }
    /**
     * Calcula el hash de la contraseña recibida y lo compara con el valor almacenado.
     * 
     * @param password caracteres de la contraseña recibida.
     * @param salt sal criptográfica asociada a la contraseña.
     * @param iterations número de iteraciones PBKDF2.
     * @param expected hash almacenado que se compara con el calculado.
     * @return true cuando se cumple la condición descrita; false en caso contrario.
     */
    public static boolean verify(char[] password, byte[] salt, int iterations, byte[] expected) {
        return MessageDigest.isEqual(expected, hash(password, salt, iterations));
    }
}
