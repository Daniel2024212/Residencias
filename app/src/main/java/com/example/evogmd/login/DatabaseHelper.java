/*
 * DOCUMENTACIÓN DEL ARCHIVO
 * Gestiona evogmd_login.db versión 4, usuarios, hashes de contraseñas y autorización de operaciones administrativas.
 */
package com.example.evogmd.login;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.Locale;

/** Usar desde el executor, nunca desde el hilo de la interfaz. */
public final class DatabaseHelper extends SQLiteOpenHelper {
    /**
     * Inicializa DatabaseHelper con los datos recibidos y establece su estado de trabajo.
     * 
     * @param context contexto Android para acceder a recursos y almacenamiento privado.
     */
    public DatabaseHelper(Context context) { super(context, "evogmd_login.db", null, 4); }
    /**
     * Inserta el administrador predeterminado mediante INSERT OR IGNORE; el hash corresponde a la contraseña inicial 1234567890.
     * 
     * @param db conexión SQLite utilizada en la operación.
     */
    private static void seedAdmin(SQLiteDatabase db) {
        db.execSQL("INSERT OR IGNORE INTO usuarios (nombre,correo,password_hash,salt,iteraciones,rol) VALUES ('Administrador','admin@evogmd.local',X'3b9abc7c9293ed42f04913ef35b1e1a14b1c403c61fb16c8c787da6339dd09cf',X'd5d2efb79750bc7513df3e484b201acc',1300000,'ADMIN')");
    }
    /**
     * Crea la tabla usuarios y registra el administrador predeterminado al instalar la base de datos.
     * 
     * @param db conexión SQLite utilizada en la operación.
     */
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE usuarios (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "nombre TEXT NOT NULL, correo TEXT NOT NULL UNIQUE, "
                + "password_hash BLOB NOT NULL, salt BLOB NOT NULL, iteraciones INTEGER NOT NULL, rol TEXT NOT NULL DEFAULT 'USER' CHECK(rol IN ('ADMIN','USER')))");
        seedAdmin(db);
    }
    /**
     * Migra el esquema de usuarios sin borrar cuentas; la versión 4 actualiza la contraseña de la cuenta administrativa predeterminada.
     * 
     * @param db conexión SQLite utilizada en la operación.
     * @param oldVersion versión instalada del esquema SQLite.
     * @param newVersion versión de destino del esquema SQLite.
     */
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE usuarios ADD COLUMN rol TEXT NOT NULL DEFAULT 'USER'");
        if (oldVersion < 3) seedAdmin(db);
        if (oldVersion < 4) {
            seedAdmin(db);
            db.execSQL("UPDATE usuarios SET password_hash=X'3b9abc7c9293ed42f04913ef35b1e1a14b1c403c61fb16c8c787da6339dd09cf',salt=X'd5d2efb79750bc7513df3e484b201acc',iteraciones=1300000 WHERE correo='admin@evogmd.local' AND rol='ADMIN'");
        }
    }
    /**
     * Elimina espacios exteriores y convierte el correo a minúsculas con Locale.ROOT.
     * 
     * @param value valor recibido para actualizar el estado o normalizar el texto, según la firma.
     * @return texto resultante de la operación descrita.
     */
    private static String email(String value) { return value.trim().toLowerCase(Locale.ROOT); }
    /**
     * Registra siempre una cuenta USER y rechaza el correo reservado del administrador.
     * 
     * @param name nombre asociado a la cuenta o al recurso.
     * @param email correo de la cuenta.
     * @param password caracteres de la contraseña recibida.
     * @return resultado de la operación descrita.
     */
    public long register(String name, String email, char[] password) {
        if ("admin@evogmd.local".equals(email(email))) throw new IllegalArgumentException("Correo reservado para el administrador.");
        byte[] salt = PasswordHasher.newSalt();
        ContentValues values = new ContentValues();
        values.put("nombre", name.trim());
        values.put("correo", email(email));
        values.put("salt", salt);
        values.put("iteraciones", PasswordHasher.ITERATIONS);
        values.put("password_hash", PasswordHasher.hash(password, salt, PasswordHasher.ITERATIONS));
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            values.put("rol", "USER");
            long id=db.insertOrThrow("usuarios",null,values);
            db.setTransactionSuccessful(); return id;
        } finally { db.endTransaction(); }
    }
    /**
     * Valida los datos y el rol del actor dentro de una transacción antes de crear una cuenta USER o ADMIN.
     * 
     * Solo una cuenta ADMIN autenticada puede elegir el rol de otra cuenta.
     * 
     * @param actorId identificador de la cuenta que autoriza la operación.
     * @param name nombre asociado a la cuenta o al recurso.
     * @param address correo que se registrará.
     * @param password caracteres de la contraseña recibida.
     * @param role rol solicitado, USER o ADMIN.
     * @return resultado de la operación descrita.
     */
    public long createAccount(long actorId, String name, String address, char[] password, String role) {
        if (!"ADMIN".equals(role) && !"USER".equals(role)) throw new IllegalArgumentException("Rol inválido.");
        if (name==null || name.trim().isEmpty()) throw new IllegalArgumentException("Ingresa el nombre.");
        if (address==null || !android.util.Patterns.EMAIL_ADDRESS.matcher(address.trim()).matches()) throw new IllegalArgumentException("Correo inválido.");
        if (password==null || password.length<8) throw new IllegalArgumentException("Usa al menos 8 caracteres.");
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try {
            User actor=findUser(actorId);
            if(actor==null || !actor.isAdmin()) throw new SecurityException("Solo un administrador puede crear cuentas con rol.");
            if("admin@evogmd.local".equals(email(address))) throw new IllegalArgumentException("Correo reservado.");
            byte[] salt=PasswordHasher.newSalt();
            ContentValues values=new ContentValues();
            values.put("nombre",name.trim());values.put("correo",email(address));values.put("rol",role);
            values.put("salt",salt);values.put("iteraciones",PasswordHasher.ITERATIONS);
            values.put("password_hash",PasswordHasher.hash(password,salt,PasswordHasher.ITERATIONS));
            long id=db.insertOrThrow("usuarios",null,values);
            db.setTransactionSuccessful();return id;
        } finally { db.endTransaction(); }
    }
    /**
     * Busca el correo y verifica el hash; devuelve null si no existe la cuenta o la contraseña no coincide.
     * 
     * @param email correo de la cuenta.
     * @param password caracteres de la contraseña recibida.
     * @return resultado de la operación descrita.
     */
    public User login(String email, char[] password) {
        try (Cursor c = getReadableDatabase().query("usuarios", null, "correo = ?",
                new String[]{email(email)}, null, null, null)) {
            if (!c.moveToFirst()) {
                // También realiza el cálculo si no existe la cuenta.
                PasswordHasher.hash(password, new byte[16], PasswordHasher.ITERATIONS);
                return null;
            }
            boolean ok = PasswordHasher.verify(password, c.getBlob(c.getColumnIndexOrThrow("salt")),
                    c.getInt(c.getColumnIndexOrThrow("iteraciones")),
                    c.getBlob(c.getColumnIndexOrThrow("password_hash")));
            return ok ? readUser(c) : null;
        }
    }
    /**
     * Consulta una cuenta por identificador sin exponer su hash ni su sal.
     * 
     * @param id identificador de la cuenta en SQLite.
     * @return resultado de la operación descrita.
     */
    public User findUser(long id) {
        try (Cursor c = getReadableDatabase().query("usuarios", new String[]{"id", "nombre", "correo", "rol"},
                "id = ?", new String[]{Long.toString(id)}, null, null, null)) {
            return c.moveToFirst() ? readUser(c) : null;
        }
    }
    /**
     * Construye el objeto de usuario a partir de la fila actual del cursor.
     * 
     * @param c cursor o contexto Android, según el tipo de la firma.
     * @return resultado de la operación descrita.
     */
    private User readUser(Cursor c) {
        return new User(c.getLong(c.getColumnIndexOrThrow("id")),
                c.getString(c.getColumnIndexOrThrow("nombre")), c.getString(c.getColumnIndexOrThrow("correo")), c.getString(c.getColumnIndexOrThrow("rol")));
    }
    
    /**
     * Devuelve las cuentas ordenadas por nombre después de comprobar que el actor es administrador.
     * 
     * @param actor identificador del administrador que solicita la operación.
     * @return resultado de la operación descrita.
     */
    public java.util.List<User> listUsers(long actor) {
        User admin=findUser(actor);
        if(admin==null || !admin.isAdmin()) throw new SecurityException("Acceso exclusivo de administrador");
        java.util.List<User> result=new java.util.ArrayList<>();
        try(Cursor c=getReadableDatabase().query("usuarios",new String[]{"id","nombre","correo","rol"},null,null,null,null,"nombre")) {
            while(c.moveToNext()) result.add(readUser(c));
        }
        return result;
    }
    /**
     * Actualiza el rol de la cuenta destino a ADMIN dentro de una transacción autorizada.
     * 
     * @param actor identificador del administrador que solicita la operación.
     * @param target identificador de la cuenta cuyo rol se actualizará.
     */
    public void promote(long actor, long target) {
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            User admin=findUser(actor);
            if(admin==null || !admin.isAdmin()) throw new SecurityException("Acceso exclusivo de administrador");
            ContentValues cv=new ContentValues(); cv.put("rol","ADMIN");
            if(db.update("usuarios",cv,"id=?",new String[]{Long.toString(target)})!=1) throw new IllegalArgumentException("Cuenta inexistente");
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    public static final class User {
        public final long id;
        public final String name, email, role;
        /**
         * Inicializa User con los datos recibidos y establece su estado de trabajo.
         * 
         * @param id identificador de la cuenta en SQLite.
         * @param name nombre asociado a la cuenta o al recurso.
         * @param email correo de la cuenta.
         * @param role rol solicitado, USER o ADMIN.
         */
        public User(long id, String name, String email, String role) { this.id=id; this.name=name; this.email=email; this.role=role; }
        /**
         * Comprueba si el rol almacenado es ADMIN.
         * 
         * @return true cuando se cumple la condición descrita; false en caso contrario.
         */
        public boolean isAdmin() { return "ADMIN".equals(role); }
    }
}
