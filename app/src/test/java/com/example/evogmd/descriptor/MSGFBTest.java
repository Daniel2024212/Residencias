/*
 * DOCUMENTACIÓN DEL ARCHIVO
 * Pruebas unitarias de MSGFB.
 */
package com.example.evogmd.descriptor;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class MSGFBTest {

    /**
     * Verifica que produce ocho caracteristicas finitas.
     */
    @Test
    public void produceOchoCaracteristicasFinitas() {
        int tamano = 32;
        int[][] imagen = new int[tamano][tamano];
        int[][] mascara = new int[tamano][tamano];
        for (int y = 0; y < tamano; y++) {
            for (int x = 0; x < tamano; x++) {
                int gris = (x * 7 + y * 3) & 0xFF;
                imagen[y][x] = 0xFF000000
                        | (gris << 16) | (gris << 8) | gris;
                mascara[y][x] = 0xFFFFFFFF;
            }
        }

        double[] vector = new MSGFB("BEST")
                .calcularDescriptorLigero(imagen, mascara);

        assertEquals(8, vector.length);
        for (double valor : vector) {
            assertFalse(Double.isNaN(valor));
            assertFalse(Double.isInfinite(valor));
        }
    }
}
