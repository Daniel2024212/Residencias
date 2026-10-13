/*
 * DOCUMENTACIÓN DEL ARCHIVO
 * Entrada antigua que redirige al login para respetar el flujo por roles.
 */
package com.example.evogmd;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
/** Redirección de pantallas restauradas de la instalación anterior. */
public final class VentanaActivity extends Activity {
    /**
     * Redirige al login y cierra esta entrada histórica de la aplicación.
     * 
     * @param state estado guardado por Android, o null en una apertura nueva.
     */
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        startActivity(new Intent(this,com.example.evogmd.login.LoginActivity.class));finish();
    }
}
