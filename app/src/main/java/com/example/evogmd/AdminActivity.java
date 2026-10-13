/*
 * DOCUMENTACIÓN DEL ARCHIVO
 * Selecciona el rol ADMIN; AnalysisActivity aplica la autorización y carga activity_admin.xml.
 */
package com.example.evogmd;
public final class AdminActivity extends AnalysisActivity {
    /**
     * Indica el rol de la pantalla para elegir el layout y comprobar la autorización.
     * 
     * @return true cuando se cumple la condición descrita; false en caso contrario.
     */
    @Override protected boolean adminScreen() { return true; }
}
