/*
 * DOCUMENTACIÓN DEL ARCHIVO
 * Contiene los bancos BEST, MEDIAN y WORST y devuelve copias para proteger sus constantes.
 */
package com.example.evogmd.transformaciones;

/** Bancos Evo-GMD. Orden: {m, sigma, theta, lambda, gamma, psi}. */
public final class ParametrosGabor {

    /**
     * Impide crear instancias de esta clase de utilidades.
     */
    private ParametrosGabor() {
    }

    public static final double[][] MEDIAN = {
        {9, 4.81270877, 3.13540763, 7.47685794, 0.31561081, 2.20899015},
        {8, 1.96396977, 0.24860970, 2.96959010, 0.72105363, 4.60142864},
        {13, 1.64780153, 1.61016196, 2.83743557, 0.66358951, 1.73766008},
        {15, 3.39179532, 0.55921120, 1.16027452, 0.96851020, 0.19780057}
    };

    public static final double[][] BEST = {
        {19, 3.12069459, 1.51606866, 3.89019095, 0.65310207, 4.58528896},
        {7, 3.02631751, 0.45433475, 5.25153474, 0.83115052, 2.04598812},
        {8, 4.91978266, 1.01728566, 1.73791384, 0.62092611, 2.98742533},
        {8, 2.76768679, 0.29284487, 2.12407121, 0.50966019, 4.89246233}
    };

    public static final double[][] WORST = {
        {14, 2.87441456, 0.64689645, 7.50065226, 0.48449909, 0.16186625},
        {9, 4.60047050, 2.76179466, 6.09504738, 0.27625496, 1.01749004},
        {19, 2.91841521, 1.58793182, 5.60334183, 0.17517662, 3.61273987},
        {10, 1.54570447, 1.59859262, 2.73917312, 0.98337168, 1.01393177}
    };

    /**
     * Selecciona BEST, MEDIAN o WORST y devuelve una copia del banco correspondiente.
     * 
     * @param tipo nombre del banco BEST, MEDIAN o WORST.
     * @return resultado de la operación descrita.
     */
    public static double[][] obtenerParametros(String tipo) {
        if (tipo == null) {
            return copiar(MEDIAN);
        }
        switch (tipo.toUpperCase()) {
            case "BEST":
                return copiar(BEST);
            case "WORST":
                return copiar(WORST);
            case "MEDIAN":
            default:
                return copiar(MEDIAN);
        }
    }

    /**
     * Copia las filas de la matriz para obtener almacenamiento independiente.
     * 
     * @param original datos originales que se copian o transforman según la firma.
     * @return resultado de la operación descrita.
     */
    private static double[][] copiar(double[][] original) {
        double[][] copia = new double[original.length][];
        for (int i = 0; i < original.length; i++) {
            copia[i] = original[i].clone();
        }
        return copia;
    }
}
