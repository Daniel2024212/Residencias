/*
 * DOCUMENTACIÓN DEL ARCHIVO
 * Pruebas unitarias de ParametrosGabor.
 */
package com.example.evogmd.transformaciones;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

public class ParametrosGaborTest {

    /**
     * Verifica que best tiene cuatro filtros de seis parametros.
     */
    @Test
    public void bestTieneCuatroFiltrosDeSeisParametros() {
        double[][] banco = ParametrosGabor.obtenerParametros("BEST");
        assertEquals(4, banco.length);
        for (double[] filtro : banco) {
            assertEquals(6, filtro.length);
        }
    }

    /**
     * Verifica que devuelve copia defensiva.
     */
    @Test
    public void devuelveCopiaDefensiva() {
        double[][] primero = ParametrosGabor.obtenerParametros("BEST");
        double[][] segundo = ParametrosGabor.obtenerParametros("BEST");
        assertNotSame(primero, segundo);
        primero[0][0] = 999;
        assertEquals(19.0, segundo[0][0], 0.0);
    }
}
