/*
 * DOCUMENTACIÓN DEL ARCHIVO
 * Calcula máscaras heurísticas; dispone de rutas diferentes para fotos de cámara e imágenes generales. No es un modelo de segmentación entrenado.
 */
package com.example.evogmd.transformaciones;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Genera la máscara ROI:
 *
 * BLANCO = lesión
 * NEGRO  = piel/fondo
 *
 * Objetivo:
 * - eliminar vellos
 * - conservar una sola lesión
 * - rellenar agujeros internos
 * - suavizar el contorno
 * - conservar concavidades importantes
 *
 * @author luisd
 */
public class RecortarROI {
    /**
     * Una lesión fotografiada de cerca puede ocupar gran parte del encuadre.
     * El algoritmo anterior descartaba cualquier componente mayor al 60 % y
     * también cualquier componente que rozara el margen de búsqueda. En esos
     * casos terminaba eligiendo una zona pequeña dentro de la lesión y el
     * descriptor Gabor dejaba de representar la misma región usada al entrenar.
     */
    private static final double FRACCION_MAXIMA_ROI_CAMARA = 0.92;
    private static final double PENALIZACION_BORDE_CAMARA = 0.65;

    /**
     * Calcula una ROI con evidencia de oscuridad local y cromaticidad; selecciona una región, aplica morfología y valida su área. No modifica la foto de entrada.
     * 
     * Segmentación automática para fotografías tomadas con la cámara.
     * 
     * La foto NO se modifica: no se cambia brillo, contraste ni saturación.
     * Este método únicamente calcula una máscara para localizar la lesión.
     * 
     * La evidencia combina:
     *  1) oscuridad respecto a la iluminación local;
     *  2) diferencia cromática respecto a la piel del borde;
     *  3) preferencia por una región compacta cercana al centro;
     *  4) penalización fuerte de sombras que se pegan a varios bordes.
     * 
     * @param rgb matriz de píxeles ARGB de la imagen.
     * @param gris matriz de intensidades de gris.
     * @return resultado de la operación descrita.
     */
    public static int[][] generarMascaraCamara(int[][] rgb, int[][] gris) {
        if (rgb == null || gris == null || gris.length == 0 || gris[0].length == 0) {
            throw new IllegalArgumentException("La imagen de cámara no es válida.");
        }

        final int h = gris.length;
        final int w = gris[0].length;
        final int mx = Math.max(6, w / 20);   // 5 %
        final int my = Math.max(6, h / 20);

        // Suavizado sólo para calcular la ROI. La fotografía original no se altera.
        int[][] clean = filtroMediana(gris, 2);

        // Integral para obtener la iluminación local de forma rápida.
        long[][] integral = new long[h + 1][w + 1];
        for (int y = 0; y < h; y++) {
            long fila = 0;
            for (int x = 0; x < w; x++) {
                fila += clean[y][x] & 0xFF;
                integral[y + 1][x + 1] = integral[y][x + 1] + fila;
            }
        }

        double[] piel = estimarCromaticidadBorde(rgb, mx, my);
        int radius = Math.max(12, Math.min(w, h) / 8);
        int[][] evidencia = new int[h][w];

        for (int y = my; y < h - my; y++) {
            for (int x = mx; x < w - mx; x++) {
                int x0 = Math.max(0, x - radius);
                int x1 = Math.min(w, x + radius + 1);
                int y0 = Math.max(0, y - radius);
                int y1 = Math.min(h, y + radius + 1);

                double mediaLocal = (integral[y1][x1] - integral[y0][x1]
                        - integral[y1][x0] + integral[y0][x0])
                        / (double) ((x1 - x0) * (y1 - y0));

                int g = clean[y][x] & 0xFF;
                double oscuridad = Math.max(0.0, mediaLocal - g);

                int p = rgb[y][x];
                double r = (p >>> 16) & 0xFF;
                double gg = (p >>> 8) & 0xFF;
                double b = p & 0xFF;
                double suma = Math.max(1.0, r + gg + b);
                double rn = r / suma;
                double gn = gg / suma;
                double bn = b / suma;
                double diferenciaColor = Math.sqrt(
                        (rn - piel[0]) * (rn - piel[0])
                                + (gn - piel[1]) * (gn - piel[1])
                                + (bn - piel[2]) * (bn - piel[2]));

                // La diferencia cromática ayuda a no confundir una sombra gris
                // grande con una lesión azulada, rojiza o marrón.
                double valor = 1.5 * oscuridad + 5.0 * 255.0 * diferenciaColor;
                evidencia[y][x] = (int) Math.max(0, Math.min(255, Math.round(valor)));
            }
        }

        int[][] interior = new int[h - 2 * my][w - 2 * mx];
        for (int y = my; y < h - my; y++) {
            System.arraycopy(evidencia[y], mx, interior[y - my], 0, w - 2 * mx);
        }
        int otsu = calcularUmbralOtsu(interior);
        otsu = Math.max(18, otsu);

        boolean[][] mejor = null;
        double mejorPuntaje = -1.0;
        double[] factores = {0.70, 0.80, 0.90, 1.00, 1.10};

        for (double factor : factores) {
            int umbral = (int) Math.max(15, Math.min(245, Math.round(otsu * factor)));
            boolean[][] candidata = new boolean[h][w];
            for (int y = my; y < h - my; y++) {
                for (int x = mx; x < w - mx; x++) {
                    candidata[y][x] = evidencia[y][x] >= umbral;
                }
            }

            // Une pequeños huecos por reflejos/vellos, pero sin convertir una
            // sombra amplia en una gran masa blanca.
            candidata = dilatar(candidata, 2);
            candidata = erosionar(candidata, 2);

            ResultadoComponente resultado = seleccionarComponenteCamara(
                    candidata, evidencia, mx, my);
            if (resultado != null && resultado.puntaje > mejorPuntaje) {
                mejorPuntaje = resultado.puntaje;
                mejor = resultado.mascara;
            }
        }

        if (mejor == null) {
            throw new IllegalArgumentException(
                    "No se pudo delimitar automáticamente la lesión. "
                            + "Toma una foto normal con la lesión visible y aproximadamente al centro.");
        }

        mejor = dilatar(mejor, Math.max(1, Math.min(w, h) / 160));
        mejor = erosionar(mejor, Math.max(1, Math.min(w, h) / 160));
        mejor = rellenarAgujeros(mejor);
        mejor = suavizarMascara5x5(mejor, 2);
        mejor = seleccionarComponentePrincipal(mejor);
        mejor = rellenarAgujeros(mejor);

        int[][] mascara = new int[h][w];
        int area = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (mejor[y][x]) {
                    mascara[y][x] = 0xFFFFFFFF;
                    area++;
                } else {
                    mascara[y][x] = 0xFF000000;
                }
            }
        }

        double fraccion = area / (double) (w * h);
        if (area < Math.max(120, w * h / 3000) || fraccion > 0.68) {
            throw new IllegalArgumentException(
                    "La ROI automática no fue confiable. "
                            + "Toma la foto con la lesión completa y aproximadamente al centro.");
        }
        return mascara;
    }

    /**
     * Estima una referencia de color de piel o fondo a partir de muestras del borde.
     * 
     * Cromaticidad robusta del borde: sirve como referencia de piel/fondo.
     * 
     * @param rgb matriz de píxeles ARGB de la imagen.
     * @param mx margen horizontal en píxeles.
     * @param my margen vertical en píxeles.
     * @return resultado de la operación descrita.
     */
    private static double[] estimarCromaticidadBorde(int[][] rgb, int mx, int my) {
        int h = rgb.length;
        int w = rgb[0].length;
        int salto = Math.max(1, Math.min(w, h) / 240);
        int capacidad = ((w * 2 * my) + (h * 2 * mx)) / (salto * salto) + 1024;
        double[] rr = new double[Math.max(1024, capacidad)];
        double[] gg = new double[rr.length];
        double[] bb = new double[rr.length];
        int n = 0;

        for (int y = 0; y < h; y += salto) {
            for (int x = 0; x < w; x += salto) {
                boolean borde = x < mx || x >= w - mx || y < my || y >= h - my;
                if (!borde) continue;
                int p = rgb[y][x];
                double r = (p >>> 16) & 0xFF;
                double g = (p >>> 8) & 0xFF;
                double b = p & 0xFF;
                double suma = r + g + b;
                if (suma < 75.0 || suma > 735.0) continue;
                if (n >= rr.length) break;
                rr[n] = r / suma;
                gg[n] = g / suma;
                bb[n] = b / suma;
                n++;
            }
        }

        if (n < 20) return new double[]{1.0 / 3.0, 1.0 / 3.0, 1.0 / 3.0};
        Arrays.sort(rr, 0, n);
        Arrays.sort(gg, 0, n);
        Arrays.sort(bb, 0, n);
        int medio = n / 2;
        if ((n & 1) == 1) {
            return new double[]{rr[medio], gg[medio], bb[medio]};
        }
        return new double[]{
                (rr[medio - 1] + rr[medio]) * 0.5,
                (gg[medio - 1] + gg[medio]) * 0.5,
                (bb[medio - 1] + bb[medio]) * 0.5
        };
    }

    private static final class ResultadoComponente {
        final boolean[][] mascara;
        final double puntaje;
        /**
         * Inicializa ResultadoComponente con los datos recibidos y establece su estado de trabajo.
         * 
         * @param mascara máscara que delimita la región de interés.
         * @param puntaje valor de calidad asignado a la región candidata.
         */
        ResultadoComponente(boolean[][] mascara, double puntaje) {
            this.mascara = mascara;
            this.puntaje = puntaje;
        }
    }

    /**
     * Evalúa componentes conectados según área, forma, evidencia y proximidad a los bordes para escoger una ROI candidata.
     * 
     * Selecciona la lesión y evita sombras grandes pegadas a los bordes.
     * 
     * @param entrada matriz de entrada que se procesará.
     * @param evidencia matriz de evidencia combinada de oscuridad y color.
     * @param mx margen horizontal en píxeles.
     * @param my margen vertical en píxeles.
     * @return resultado de la operación descrita.
     */
    private static ResultadoComponente seleccionarComponenteCamara(
            boolean[][] entrada, int[][] evidencia, int mx, int my) {
        int h = entrada.length;
        int w = entrada[0].length;
        boolean[][] visitado = new boolean[h][w];
        int[] cola = new int[w * h];
        double mejorPuntaje = -1.0;
        boolean[][] mejor = null;
        int areaMinima = Math.max(120, w * h / 3000);

        for (int y = my; y < h - my; y++) {
            for (int x = mx; x < w - mx; x++) {
                if (!entrada[y][x] || visitado[y][x]) continue;

                int ini = 0, fin = 1;
                cola[0] = y * w + x;
                visitado[y][x] = true;
                int minX = x, maxX = x, minY = y, maxY = y;
                long sumaX = 0, sumaY = 0, sumaEvidencia = 0;
                int bordes = 0;

                while (ini < fin) {
                    int pos = cola[ini++];
                    int px = pos % w;
                    int py = pos / w;
                    sumaX += px;
                    sumaY += py;
                    sumaEvidencia += evidencia[py][px];
                    minX = Math.min(minX, px);
                    maxX = Math.max(maxX, px);
                    minY = Math.min(minY, py);
                    maxY = Math.max(maxY, py);

                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            if (dx == 0 && dy == 0) continue;
                            int nx = px + dx, ny = py + dy;
                            if (nx >= mx && nx < w - mx && ny >= my && ny < h - my
                                    && entrada[ny][nx] && !visitado[ny][nx]) {
                                visitado[ny][nx] = true;
                                cola[fin++] = ny * w + nx;
                            }
                        }
                    }
                }

                int area = fin;
                if (area < areaMinima) continue;
                double fraccion = area / (double) (w * h);
                if (fraccion > 0.68) continue;

                int bw = maxX - minX + 1;
                int bh = maxY - minY + 1;
                if (Math.min(bw, bh) < 8 || Math.max(bw, bh) > 5.0 * Math.min(bw, bh)) continue;

                if (minX <= mx + 1) bordes++;
                if (maxX >= w - mx - 2) bordes++;
                if (minY <= my + 1) bordes++;
                if (maxY >= h - my - 2) bordes++;
                // Una sombra suele formar una masa que llega a dos o más lados.
                if (bordes >= 2) continue;

                double cx = sumaX / (double) area;
                double cy = sumaY / (double) area;
                double dxCentro = (cx - w / 2.0) / (w / 2.0);
                double dyCentro = (cy - h / 2.0) / (h / 2.0);
                double distancia2 = dxCentro * dxCentro + dyCentro * dyCentro;
                if (distancia2 > 0.90) continue;

                double compactacion = area / (double) (bw * bh);
                if (compactacion < 0.10) continue;
                double evidenciaMedia = sumaEvidencia / (double) area;
                double pesoCentro = 1.0 / (1.0 + 4.0 * distancia2);
                double penalizacionGrande = Math.exp(-Math.max(0.0, fraccion - 0.35) * 10.0);
                double penalizacionBorde = bordes == 1 ? 0.30 : 1.0;

                double puntaje = evidenciaMedia * Math.sqrt(area)
                        * Math.sqrt(compactacion)
                        * pesoCentro * penalizacionGrande * penalizacionBorde;

                if (puntaje > mejorPuntaje) {
                    mejorPuntaje = puntaje;
                    mejor = new boolean[h][w];
                    for (int i = 0; i < fin; i++) {
                        int pos = cola[i];
                        mejor[pos / w][pos % w] = true;
                    }
                }
            }
        }

        return mejor == null ? null : new ResultadoComponente(mejor, mejorPuntaje);
    }

    
    /**
     * Genera una máscara binaria con la segmentación original o delega en ella según la sobrecarga.
     * 
     * Método principal.
     * 
     * @param matrizRGB matriz de píxeles ARGB de entrada.
     * @param matrizGris matriz en escala de grises.
     * @return resultado de la operación descrita.
     */
    public static int[][] generarMascara(int[][] matrizRGB,int[][] matrizGris) {
        int alto = matrizGris.length;
        int ancho = matrizGris[0].length;
        System.out.println("\n======================================");
        System.out.println("GENERANDO MÁSCARA ROI");
        System.out.println("======================================");

        // =========================================================
        // 1. ELIMINAR VELLOS Y RUIDO
        //
        // Radio 3 = ventana 7x7.
        // =========================================================

        int[][] grisLimpio=filtroMediana(matrizGris,3);
        // =========================================================
        // 2. SEGUNDO SUAVIZADO MUY LIGERO
        //
        // Reduce pequeñas irregularidades sin borrar la lesión.
        // =========================================================
        grisLimpio=filtroMediana(grisLimpio,1);
        // =========================================================
        // 3. OTSU
        // =========================================================
        int umbral=calcularUmbralOtsu(grisLimpio);
        System.out.println("Umbral Otsu = " + umbral);
        // =========================================================
        // 4. BINARIZACIÓN
        //
        // La lesión es la región más oscura.
        // =========================================================
        boolean[][] binaria=new boolean[alto][ancho];
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                int gris=grisLimpio[y][x]& 0xFF;
                binaria[y][x]=gris <= umbral;
            }
        }

        // =========================================================
        // 5. VERIFICAR POLARIDAD
        // =========================================================

        int candidatos=contarPixeles(binaria,true);
        double porcentaje=candidatos*100.0/(alto * ancho);
        System.out.printf("Área candidata = %.2f%%%n",porcentaje);
        /*
         * Si prácticamente toda la imagen quedó blanca,
         * invertir.
         */
        if (porcentaje > 65.0) {
            binaria=invertir(binaria);
        }

        // =========================================================
        // 6. ELIMINAR 4 PÍXELES DEL BORDE
        //
        // Evita que sombras periféricas entren a la ROI.
        // =========================================================

        limpiarBordes(binaria,4);
        // =========================================================
        // 7. CONSERVAR LA LESIÓN PRINCIPAL
        // =========================================================

        boolean[][] lesion=seleccionarComponentePrincipal(binaria);

        // =========================================================
        // 8. RELLENAR AGUJEROS
        // =========================================================

        lesion=rellenarAgujeros(lesion);

        // =========================================================
        // 9. SUAVIZADO BINARIO 5x5
        //
        // Este es el cambio importante.
        //
        // Elimina dientes y vellos pegados al borde.
        // =========================================================

        lesion=suavizarMascara5x5(lesion,2);

        // =========================================================
        // 10. APERTURA SUAVE
        //
        // Quita salientes estrechos.
        // =========================================================

        lesion=erosionar(lesion,1);
        lesion=dilatar(lesion,1);

        // =========================================================
        // 11. CIERRE MUY SUAVE
        //
        // Corrige pequeños cortes.
        // =========================================================

        lesion=dilatar(lesion,1);
        lesion=erosionar(lesion,1);

        // =========================================================
        // 12. VOLVER A SUAVIZAR
        // =========================================================

        lesion=suavizarMascara5x5(lesion,2);

        // =========================================================
        // 13. CONSERVAR COMPONENTE PRINCIPAL
        // =========================================================

        lesion=seleccionarComponentePrincipal(lesion);

        // =========================================================
        // 14. RELLENAR AGUJEROS
        // =========================================================

        lesion=rellenarAgujeros(lesion);

        // =========================================================
        // 15. CONVERTIR A BLANCO / NEGRO
        // =========================================================

        int[][] mascara=new int[alto][ancho];
        int area=0;
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                if (lesion[y][x]) {
                    // Blanco
                    mascara[y][x] =0xFFFFFFFF;
                    area++;
                } else {
                    // Negro
                    mascara[y][x]=0xFF000000;
                }
            }
        }

        double areaPorcentaje=area*100.0/(alto * ancho);
        System.out.printf("ROI final = %d píxeles (%.2f%%)%n",area,areaPorcentaje);
        return mascara;
    }

    // =============================================================
    // FILTRO DE MEDIANA
    // =============================================================

    /**
     * Sustituye intensidades por la mediana del vecindario para reducir ruido en el cálculo de la máscara.
     * 
     * @param imagen imagen de entrada representada por el tipo de la firma.
     * @param radio radio del vecindario.
     * @return resultado de la operación descrita.
     */
    private static int[][] filtroMediana(int[][] imagen,int radio) {
        int alto=imagen.length;
        int ancho=imagen[0].length;
        int lado=radio * 2 + 1;
        int tamano=lado * lado;
        int[][] salida=new int[alto][ancho];
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                int[] valores=new int[tamano];
                int indice=0;
                for (int dy=-radio;dy<=radio;dy++) {
                    for (int dx=-radio;dx<=radio;dx++) {
                        int yy=y+dy;
                        int xx=x+dx;
                        // Replicar borde
                        if(yy<0) {
                            yy=0;
                        }
                        if(yy>=alto) {
                            yy=alto - 1;
                        }
                        if(xx < 0) {
                            xx = 0;
                        }

                        if(xx>=ancho) {
                            xx=ancho-1;
                        }
                        valores[indice++]=imagen[yy][xx]&0xFF;
                    }
                }

                Arrays.sort(valores);
                int gris=valores[valores.length/2];
                salida[y][x]=crearGris(gris);
            }
        }
        return salida;
    }

    // =============================================================
    // OTSU
    // =============================================================
    /**
     * Busca el umbral que maximiza la separación de dos grupos en el histograma de intensidades.
     * 
     * @param imagen imagen de entrada representada por el tipo de la firma.
     * @return resultado de la operación descrita.
     */
    private static int calcularUmbralOtsu(int[][] imagen) {
        int alto=imagen.length;
        int ancho=imagen[0].length;
        int[] histograma=new int[256];
        for (int y=0;y<alto;y++) {
            for (int x=0;x<ancho;x++) {
                int gris=imagen[y][x]& 0xFF;
                histograma[gris]++;
            }
        }
        int total=alto * ancho;
        double sumaTotal=0.0;
        for (int i=0;i<256;i++) {
            sumaTotal+=i*histograma[i];
        }
        double sumaFondo=0.0;
        int pesoFondo=0;
        double varianzaMax=-1.0;
        int umbral=0;
        for (int t=0;t<256;t++) {
            pesoFondo+=histograma[t];
            if (pesoFondo==0) {
                continue;
            }
            int pesoObjeto=total-pesoFondo;
            if (pesoObjeto == 0) {
                break;
            }
            sumaFondo+=t*histograma[t];
            double mediaFondo=sumaFondo/pesoFondo;
            double mediaObjeto =(sumaTotal-sumaFondo)/pesoObjeto;
            double diferencia=mediaFondo-mediaObjeto;
            double varianza=(double)pesoFondo*pesoObjeto*diferencia*diferencia;
            if (varianza > varianzaMax) {
                varianzaMax=varianza;
                umbral=t;
            }
        }
        return umbral;
    }

    // =============================================================
    // COMPONENTE PRINCIPAL
    //
    // Prioriza:
    // - tamaño grande
    // - cercanía al centro
    // - no tocar bordes
    // =============================================================

    /**
     * Recorre las regiones conectadas y selecciona el componente principal según los criterios del algoritmo.
     * 
     * @param entrada matriz de entrada que se procesará.
     * @return true cuando se cumple la condición descrita; false en caso contrario.
     */
    private static boolean[][]seleccionarComponentePrincipal(boolean[][] entrada) {
        int alto=entrada.length;
        int ancho=entrada[0].length;
        boolean[][] visitado=new boolean[alto][ancho];
        boolean[][] mejor=new boolean[alto][ancho];
        double mejorPuntuacion=-1;
        int centroX=ancho / 2;
        int centroY=alto / 2;

        double distanciaMax=Math.sqrt(centroX * centroX+centroY * centroY);
        int[] dx = {
            -1, 0, 1,
            -1,    1,
            -1, 0, 1
        };

        int[] dy = {
            -1, -1, -1,
             0,      0,
             1,  1,  1
        };

        for (int y=0;y<alto;y++) {
            for (int x=0;x<ancho;x++) {
                if (!entrada[y][x]||visitado[y][x]) {
                    continue;
                }
                Queue<int[]> cola=new LinkedList<int[]>();
                List<int[]> componente=new ArrayList<int[]>();
                cola.add(new int[]{x,y});
                visitado[y][x]=true;
                long sumaX=0;
                long sumaY=0;
                boolean tocaBorde=false;
                while(!cola.isEmpty()){
                    int[] punto=cola.poll();
                    componente.add(punto);
                    sumaX+=punto[0];

                    sumaY+=punto[1];
                    if (punto[0]<=3||punto[0] >= ancho - 4||punto[1] <= 3||punto[1] >= alto - 4) {
                        tocaBorde=true;
                    }
                    for (int i=0;i<8;i++) {
                        int nx=punto[0]+dx[i];
                        int ny=punto[1]+dy[i];
                        if (nx>=0&&nx<ancho&&ny>=0&&ny<alto&&entrada[ny][nx]&&!visitado[ny][nx]) {
                            visitado[ny][nx]=true;
                            cola.add(new int[]{nx,ny}
                            );
                        }
                    }
                }
                int area=componente.size();
                if(area<300) {
                    continue;
                }
                double cx=sumaX/(double) area;
                double cy=sumaY/(double) area;
                double distancia=Math.sqrt((cx - centroX)*(cx - centroX)+(cy - centroY)*(cy - centroY));
                double centralidad=1.0-distancia/distanciaMax;
                if (centralidad<0) {
                    centralidad=0;
                }
                double puntuacion=area*(0.70+0.30*centralidad);
                if(tocaBorde) {
                    puntuacion*=0.15;
                }

                if(puntuacion>mejorPuntuacion) {
                    mejorPuntuacion=puntuacion;
                    mejor=new boolean[alto][ancho];
                    for(int[] punto:componente) {
                        mejor[punto[1]][punto[0]]=true;
                    }
                }
            }
        }
        return mejor;
    }

    // =============================================================
    // SUAVIZAR MÁSCARA 5x5
    //
    // Utiliza mayoría local.
    //
    // Esto quita el aspecto "dentado".
    // =============================================================

    /**
     * Aplica votación en vecindarios de 5 por 5 para suavizar el contorno binario.
     * 
     * @param entrada matriz de entrada que se procesará.
     * @param repeticiones cantidad de pasadas de suavizado.
     * @return true cuando se cumple la condición descrita; false en caso contrario.
     */
    private static boolean[][]suavizarMascara5x5(boolean[][] entrada,int repeticiones) {
        boolean[][] actual=copiar(entrada);
        for (int r=0;r<repeticiones;r++) {
            int alto=actual.length;
            int ancho=actual[0].length;
            boolean[][] salida=copiar(actual);
            for (int y=2;y<alto-2;y++) {
                for (int x=2;x<ancho-2;x++) {
                    int blancos=0;
                    for (int dy=-2;dy<=2;dy++) {
                        for (int dx=-2;dx<=2;dx++) {
                            if (actual[y+dy][x + dx]) {
                                blancos++;
                            }
                        }
                    }

                    /*
                     * 25 píxeles en total.
                     *
                     * 13 = mayoría.
                     */
                    salida[y][x]=blancos >= 13;
                }
            }
            actual=salida;
        }
        return actual;
    }

    // =============================================================
    // RELLENAR AGUJEROS
    // =============================================================

    /**
     * Marca el fondo conectado con el exterior y rellena los huecos encerrados por la lesión.
     * 
     * @param mascara máscara que delimita la región de interés.
     * @return true cuando se cumple la condición descrita; false en caso contrario.
     */
    private static boolean[][]rellenarAgujeros(boolean[][] mascara) {
        int alto=mascara.length;
        int ancho=mascara[0].length;
        boolean[][] fondo=new boolean[alto][ancho];
        Queue<int[]> cola=new LinkedList<int[]>();
        for(int x=0;x<ancho;x++) {
            agregarFondo(mascara,fondo,cola,x,0);
            agregarFondo(mascara,fondo,cola,x,alto - 1);
        }
        for (int y=0;y<alto;y++) {
            agregarFondo(mascara,fondo,cola,0,y);
            agregarFondo(mascara,fondo,cola,ancho - 1,y);
        }
        int[] dx = {
            -1, 0, 1,
            -1,    1,
            -1, 0, 1
        };

        int[] dy = {
            -1, -1, -1,
             0,      0,
             1,  1,  1
        };
        while(!cola.isEmpty()) {
            int[] p=cola.poll();

            for (int i=0;i<8;i++) {
                int nx=p[0]+dx[i];
                int ny=p[1]+dy[i];
                if (nx<0||nx>=ancho||ny<0||ny >= alto) {
                    continue;
                }
                if(!mascara[ny][nx]&&!fondo[ny][nx]) {
                    fondo[ny][nx]=true;
                    cola.add(new int[]{nx,ny});
                }
            }
        }

        boolean[][] salida=new boolean[alto][ancho];
        for (int y=0;y<alto;y++) {
            for (int x=0;x<ancho;x++) {
                salida[y][x]=mascara[y][x]||!fondo[y][x];
            }
        }
        return salida;
    }
    /**
     * Añade una posición válida de fondo a la cola del recorrido, evitando visitarla dos veces.
     * 
     * @param mascara máscara que delimita la región de interés.
     * @param fondo matriz que registra el fondo exterior ya visitado.
     * @param cola cola de píxeles pendientes del recorrido.
     * @param x columna del píxel.
     * @param y fila del píxel.
     */
    private static void agregarFondo(boolean[][] mascara,boolean[][] fondo,Queue<int[]> cola,int x,int y) {
        if(!mascara[y][x]&&!fondo[y][x]) {
            fondo[y][x]=true;
            cola.add(new int[]{x,y});
        }
    }

    // =============================================================
    // EROSIÓN
    // =============================================================
    /**
     * Reduce las regiones blancas mediante erosiones sucesivas de la máscara.
     * 
     * @param entrada matriz de entrada que se procesará.
     * @param iteraciones cantidad de operaciones morfológicas.
     * @return true cuando se cumple la condición descrita; false en caso contrario.
     */
    private static boolean[][] erosionar(boolean[][] entrada,int iteraciones) {
        boolean[][]actual=copiar(entrada);
        for(int it=0;it<iteraciones;it++) {
            int alto=actual.length;
            int ancho=actual[0].length;
            boolean[][] salida=new boolean[alto][ancho];
            for(int y=1;y<alto-1;y++) {
                for (int x=1;x<ancho-1;x++) {
                    if(!actual[y][x]) {
                        continue;
                    }
                    boolean conservar=true;
                    for(int dy=-1;dy<=1;dy++) {
                        for(int dx=-1;dx <= 1;dx++) {
                            if(!actual[y + dy][x + dx]) {
                                conservar=false;
                                break;
                            }
                        }
                        if (!conservar) {
                            break;
                        }
                    }
                    salida[y][x]=conservar;
                }
            }
            actual=salida;
        }
        return actual;
    }

    // =============================================================
    // DILATACIÓN
    // =============================================================

    /**
     * Expande las regiones blancas mediante dilataciones sucesivas de la máscara.
     * 
     * @param entrada matriz de entrada que se procesará.
     * @param iteraciones cantidad de operaciones morfológicas.
     * @return true cuando se cumple la condición descrita; false en caso contrario.
     */
    private static boolean[][] dilatar(
            boolean[][] entrada,
            int iteraciones) {

        boolean[][] actual =
                copiar(
                        entrada
                );

        for (int it = 0;
                it < iteraciones;
                it++) {

            int alto =
                    actual.length;

            int ancho =
                    actual[0].length;

            boolean[][] salida =
                    new boolean[alto][ancho];

            for (int y = 0; y < alto; y++) {

                for (int x = 0;
                        x < ancho;
                        x++) {

                    if (!actual[y][x]) {

                        continue;
                    }

                    for (int dy = -1;
                            dy <= 1;
                            dy++) {

                        for (int dx = -1;
                                dx <= 1;
                                dx++) {

                            int ny =
                                    y + dy;

                            int nx =
                                    x + dx;

                            if (ny >= 0
                                    &&
                                    ny < alto
                                    &&
                                    nx >= 0
                                    &&
                                    nx < ancho) {

                                salida[ny][nx] =
                                        true;
                            }
                        }
                    }
                }
            }

            actual =
                    salida;
        }

        return actual;
    }

    // =============================================================
    // LIMPIAR BORDES
    // =============================================================

    /**
     * Elimina regiones del margen indicado para reducir artefactos del encuadre.
     * 
     * @param mascara máscara que delimita la región de interés.
     * @param grosor ancho en píxeles del margen que se limpiará.
     */
    private static void limpiarBordes(
            boolean[][] mascara,
            int grosor) {

        int alto =
                mascara.length;

        int ancho =
                mascara[0].length;

        for (int y = 0; y < alto; y++) {

            for (int x = 0; x < ancho; x++) {

                if (x < grosor
                        ||
                        x >= ancho - grosor
                        ||
                        y < grosor
                        ||
                        y >= alto - grosor) {

                    mascara[y][x] =
                            false;
                }
            }
        }
    }

    // =============================================================
    // INVERTIR
    // =============================================================

    /**
     * Intercambia fondo y primer plano de la máscara booleana.
     * 
     * @param entrada matriz de entrada que se procesará.
     * @return true cuando se cumple la condición descrita; false en caso contrario.
     */
    private static boolean[][] invertir(
            boolean[][] entrada) {

        int alto =
                entrada.length;

        int ancho =
                entrada[0].length;

        boolean[][] salida =
                new boolean[alto][ancho];

        for (int y = 0; y < alto; y++) {

            for (int x = 0; x < ancho; x++) {

                salida[y][x] =
                        !entrada[y][x];
            }
        }

        return salida;
    }

    // =============================================================
    // CONTAR
    // =============================================================

    /**
     * Cuenta las posiciones activas de la máscara booleana.
     * 
     * @param entrada matriz de entrada que se procesará.
     * @param valor valor numérico que se transforma o valida.
     * @return resultado de la operación descrita.
     */
    private static int contarPixeles(
            boolean[][] entrada,
            boolean valor) {

        int contador=0;

        for (int y = 0;
                y < entrada.length;
                y++) {

            for (int x = 0;
                    x < entrada[0].length;
                    x++) {

                if (entrada[y][x]
                        ==
                        valor) {

                    contador++;
                }
            }
        }

        return contador;
    }

    // =============================================================
    // COPIAR
    // =============================================================

    /**
     * Copia las filas de la matriz para obtener almacenamiento independiente.
     * 
     * @param origen origen de los datos que se copia o describe.
     * @return true cuando se cumple la condición descrita; false en caso contrario.
     */
    private static boolean[][] copiar(
            boolean[][] origen) {

        boolean[][] copia =
                new boolean[
                    origen.length
                ][
                    origen[0].length
                ];

        for (int y = 0;
                y < origen.length;
                y++) {

            System.arraycopy(
                    origen[y],
                    0,
                    copia[y],
                    0,
                    origen[y].length
            );
        }

        return copia;
    }

    // =============================================================
    // CREAR GRIS
    // =============================================================

    /**
     * Empaqueta una intensidad en los canales RGB con opacidad completa.
     * 
     * @param gris matriz de intensidades de gris.
     * @return resultado de la operación descrita.
     */
    private static int crearGris(
            int gris) {

        if (gris < 0) {
            gris = 0;
        }

        if (gris > 255) {
            gris = 255;
        }

        return (0xFF << 24)
                |
                (gris << 16)
                |
                (gris << 8)
                |
                gris;
    }

    // =============================================================
    // COMPATIBILIDAD
    // =============================================================

    /**
     * Genera una máscara binaria con la segmentación original o delega en ella según la sobrecarga.
     * 
     * @param matrizGris matriz en escala de grises.
     * @return resultado de la operación descrita.
     */
    public static int[][] generarMascara(
            int[][] matrizGris) {

        int alto =
                matrizGris.length;

        int ancho =
                matrizGris[0].length;

        int[][] rgb =
                new int[alto][ancho];

        for (int y = 0; y < alto; y++) {

            for (int x = 0; x < ancho; x++) {

                rgb[y][x] =
                        matrizGris[y][x];
            }
        }

        return generarMascara(
                rgb,
                matrizGris
        );
    }
}
