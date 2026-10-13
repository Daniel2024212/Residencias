/*
 * DOCUMENTACIÓN DEL ARCHIVO
 * Implementa kernels, convolución, estadísticas y normalización exclusiva para visualización.
 */
package com.example.evogmd.filtrogabor;

/**
 * Implementación del filtro de Gabor.
 *
 * @author luisd
 */
public class FiltroGabor {

    /**
     * Construye el kernel de Gabor a partir del tamaño, envolvente gaussiana, orientación y frecuencia espacial.
     * 
     * Genera kernel Gabor.
     * 
     * Orden:
     * 
     * m, sigma, theta, lambda, gamma, psi
     * 
     * @param m tamaño del kernel de Gabor.
     * @param sigma dispersión de la envolvente gaussiana.
     * @param theta orientación o vector de parámetros, según la firma.
     * @param lambda longitud de onda del filtro.
     * @param gamma relación de aspecto de la envolvente.
     * @param psi fase del filtro.
     * @return resultado de la operación descrita.
     */
    public static double[][] generarKernel(
            int m,
            double sigma,
            double theta,
            double lambda,
            double gamma,
            double psi) {

        double[][] kernel
                = new double[m][m];

        int centro
                = m / 2;

        for (int y = 0;
                y < m;
                y++) {

            for (int x = 0;
                    x < m;
                    x++) {

                double x0
                        = x - centro;

                double y0
                        = y - centro;

                // Coordenadas rotadas
                double xPrima
                        = x0 * Math.cos(theta)
                        + y0 * Math.sin(theta);

                double yPrima
                        = -x0 * Math.sin(theta)
                        + y0 * Math.cos(theta);

                // Parte Gaussiana
                double exponencial
                        = Math.exp(
                                -(Math.pow(xPrima, 2)
                                + Math.pow(
                                        gamma * yPrima,
                                        2
                                ))
                                / (2
                                * Math.pow(
                                        sigma,
                                        2
                                ))
                        );

                double senoidal
                        = Math.cos(
                                2.0
                                * Math.PI
                                * (xPrima / lambda)
                                + psi
                        );

                kernel[y][x]
                        = exponencial
                        * senoidal;
            }
        }

        return kernel;
    }

    /**
     * Aplica el kernel a la imagen y obtiene una matriz de respuestas de valor real.
     * 
     * Convolución.
     * 
     * @param imagenProcesada matriz de respuestas sobre la que se calculan estadísticas.
     * @param kernel matriz de coeficientes del filtro de Gabor.
     * @return resultado de la operación descrita.
     */
    public static double[][] aplicarConvolucion(
            int[][] imagenProcesada,
            double[][] kernel) {

        int alto
                = imagenProcesada.length;

        int ancho
                = imagenProcesada[0].length;

        int m
                = kernel.length;

        int offset
                = m / 2;

        double[][] respuesta
                = new double[alto][ancho];

        for (int y = offset;
                y < alto - offset;
                y++) {

            for (int x = offset;
                    x < ancho - offset;
                    x++) {

                double suma
                        = 0.0;

                for (int ky = 0;
                        ky < m;
                        ky++) {

                    for (int kx = 0;
                            kx < m;
                            kx++) {

                        int gris
                                = imagenProcesada[y
                                + ky
                                - offset][x
                                + kx
                                - offset]
                                & 0xFF;

                        suma
                                += gris
                                * kernel[ky][kx];
                    }
                }

                respuesta[y][x]
                        = suma;
            }
        }

        return respuesta;
    }

    /**
     * Lleva la respuesta a intensidades visibles; esta normalización se usa para mostrar imágenes y no sustituye las características del SVM.
     * 
     * Convierte la respuesta matemática Gabor a imagen 0-255.
     * 
     * @param respuestaGabor matriz de respuesta real del filtro.
     * @param mascaraROI máscara binaria que indica los píxeles de la lesión.
     * @return resultado de la operación descrita.
     */
    public static int[][]
            normalizarParaVisualizar(
                    double[][] respuestaGabor,
                    int[][] mascaraROI) {

        int alto
                = respuestaGabor.length;

        int ancho
                = respuestaGabor[0].length;

        int[][] salida
                = new int[alto][ancho];

        // CORRECCIÓN IMPORTANTE
        double min
                = Double.POSITIVE_INFINITY;

        double max
                = Double.NEGATIVE_INFINITY;

        boolean hayROI
                = false;

        // ============================================
        // Buscar min/max solamente en ROI
        // ============================================
        for (int y = 0;
                y < alto;
                y++) {

            for (int x = 0;
                    x < ancho;
                    x++) {

                int mascara
                        = (mascaraROI[y][x] >> 16)
                        & 0xFF;

                if (mascara >= 128) {

                    hayROI = true;

                    double valor
                            = respuestaGabor[y][x];

                    if (valor < min) {

                        min = valor;
                    }

                    if (valor > max) {

                        max = valor;
                    }
                }
            }
        }

        if (!hayROI) {

            min = 0;
            max = 1;
        }

        if (Math.abs(max - min)
                < 1e-12) {

            max = min + 1.0;
        }

        // ============================================
        // Normalizar
        // ============================================
        for (int y = 0;
                y < alto;
                y++) {

            for (int x = 0;
                    x < ancho;
                    x++) {

                int mascara
                        = (mascaraROI[y][x] >> 16)
                        & 0xFF;

                if (mascara >= 128) {

                    double valorNormalizado
                            = (respuestaGabor[y][x]
                            - min)
                            / (max - min);

                    int gris
                            = (int) Math.round(
                                    valorNormalizado
                                    * 255
                            );

                    if (gris < 0) {
                        gris = 0;
                    }

                    if (gris > 255) {
                        gris = 255;
                    }

                    salida[y][x]
                            = 0xFF000000
                            | (gris << 16)
                            | (gris << 8)
                            | gris;

                } else {

                    // fondo blanco
                    salida[y][x]
                            = 0xFFFFFFFF;
                }
            }
        }

        return salida;
    }

    /**
     * Calcula la media de los valores de respuesta considerados por esta sobrecarga.
     * 
     * Media sobre imagen completa.
     * 
     * @param respuestaGabor matriz de respuesta real del filtro.
     * @return resultado de la operación descrita.
     */
    public static double calcularMedia(
            double[][] respuestaGabor) {

        int alto
                = respuestaGabor.length;

        int ancho
                = respuestaGabor[0].length;

        double suma
                = 0.0;

        for (int y = 0;
                y < alto;
                y++) {

            for (int x = 0;
                    x < ancho;
                    x++) {

                suma
                        += respuestaGabor[y][x];
            }
        }

        return suma
                / (alto * ancho);
    }

    /**
     * Calcula la desviación estándar de las respuestas respecto de la media indicada.
     * 
     * Desviación sobre imagen completa.
     * 
     * @param respuestaGabor matriz de respuesta real del filtro.
     * @param media media calculada previamente para las mismas respuestas.
     * @return resultado de la operación descrita.
     */
    public static double calcularDesviacion(
            double[][] respuestaGabor,
            double media) {

        int alto
                = respuestaGabor.length;

        int ancho
                = respuestaGabor[0].length;

        double suma
                = 0.0;

        for (int y = 0;
                y < alto;
                y++) {

            for (int x = 0;
                    x < ancho;
                    x++) {

                double diferencia
                        = respuestaGabor[y][x]
                        - media;

                suma
                        += diferencia
                        * diferencia;
            }
        }

        return Math.sqrt(
                suma
                / (alto * ancho)
        );
    }

    /**
     * Calcula la media únicamente en los píxeles pertenecientes a la máscara ROI.
     * 
     * Media exclusivamente dentro de ROI.
     * 
     * @param respuestaGabor matriz de respuesta real del filtro.
     * @param mascaraROI máscara binaria que indica los píxeles de la lesión.
     * @return resultado de la operación descrita.
     */
    public static double calcularMediaEnROI(
            double[][] respuestaGabor,
            int[][] mascaraROI) {

        int alto
                = respuestaGabor.length;

        int ancho
                = respuestaGabor[0].length;

        double suma
                = 0.0;

        int contador
                = 0;

        for (int y = 0;
                y < alto;
                y++) {

            for (int x = 0;
                    x < ancho;
                    x++) {

                int mascara
                        = (mascaraROI[y][x] >> 16)
                        & 0xFF;

                if (mascara >= 128) {

                    suma
                            += respuestaGabor[y][x];

                    contador++;
                }
            }
        }

        if (contador == 0) {

            return 0.0;
        }

        return suma
                / contador;
    }

    /**
     * Calcula la desviación estándar únicamente dentro de la ROI.
     * 
     * Desviación exclusivamente dentro de ROI.
     * 
     * @param respuestaGabor matriz de respuesta real del filtro.
     * @param media media calculada previamente para las mismas respuestas.
     * @param mascaraROI máscara binaria que indica los píxeles de la lesión.
     * @return resultado de la operación descrita.
     */
    public static double calcularDesviacionEnROI(
            double[][] respuestaGabor,
            double media,
            int[][] mascaraROI) {

        int alto
                = respuestaGabor.length;

        int ancho
                = respuestaGabor[0].length;

        double suma
                = 0.0;

        int contador
                = 0;

        for (int y = 0;
                y < alto;
                y++) {

            for (int x = 0;
                    x < ancho;
                    x++) {

                int mascara
                        = (mascaraROI[y][x] >> 16)
                        & 0xFF;

                if (mascara >= 128) {

                    double diferencia
                            = respuestaGabor[y][x]
                            - media;

                    suma
                            += diferencia
                            * diferencia;

                    contador++;
                }
            }
        }

        if (contador == 0) {

            return 0.0;
        }

        return Math.sqrt(
                suma
                / contador
        );
    }
}
