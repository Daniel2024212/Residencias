/*
 * DOCUMENTACIÓN DEL ARCHIVO
 * Entrada de compatibilidad que redirige al login.
 */
package com.example.evogmd;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
/** Compatibilidad con entradas antiguas: el acceso se resuelve por sesión y rol. */
public final class MainActivity extends Activity {
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
