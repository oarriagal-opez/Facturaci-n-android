package gt.edu.umg.facturaapp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Habla con el programa de escritorio (ServidorApi) por HTTP.
 * La app ya NO se conecta a MySQL: solo el programa de escritorio lo hace.
 * Estos métodos hacen red; llamarlos siempre fuera del hilo principal.
 */
public class FacturaRepository {

    /** Error devuelto por el programa de escritorio (código HTTP + mensaje). */
    public static class ApiException extends Exception {
        public final int codigo;
        public ApiException(int codigo, String mensaje) {
            super(mensaje);
            this.codigo = codigo;
        }
    }

    public int obtenerSiguienteIdFactura() throws Exception {
        return new JSONObject(llamar("GET", "/api/siguiente-factura", null)).getInt("id");
    }

    public List<Producto> listarProductos() throws Exception {
        JSONArray arr = new JSONArray(llamar("GET", "/api/productos", null));
        List<Producto> lista = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            lista.add(new Producto(o.getInt("id"), o.getString("nombre"),
                    o.getDouble("precio"), o.getInt("stock")));
        }
        return lista;
    }

    /** Devuelve null si el cliente no existe. */
    public Cliente buscarClientePorNit(String nit) throws Exception {
        try {
            String resp = llamar("GET", "/api/cliente?nit=" + URLEncoder.encode(nit, "UTF-8"), null);
            JSONObject o = new JSONObject(resp);
            Cliente c = new Cliente();
            c.idCliente = o.getInt("id");
            c.nit = o.getString("nit");
            c.nombre = o.getString("nombre");
            c.direccion = o.getString("direccion");
            return c;
        } catch (ApiException e) {
            if (e.codigo == 404) return null;
            throw e;
        }
    }

    /** Envía la factura completa; el programa de escritorio crea el cliente si hace falta. Devuelve el ID. */
    public int guardarFactura(String nit, String nombre, String direccion, String entrega,
                              List<DetalleFactura> detalles) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("nit=").append(limpiar(nit)).append('\n');
        sb.append("nombre=").append(limpiar(nombre)).append('\n');
        sb.append("direccion=").append(limpiar(direccion)).append('\n');
        sb.append("entrega=").append(entrega).append('\n');
        for (DetalleFactura d : detalles) {
            sb.append("item=").append(d.producto.idProducto).append(',')
              .append(d.cantidad).append(',')
              .append(String.format(Locale.US, "%.2f", d.precioUnitario)).append('\n');
        }
        String resp = llamar("POST", "/api/facturas", sb.toString());
        return new JSONObject(resp).getInt("idFactura");
    }

    // ------------------------------------------------------------------ HTTP

    private static String limpiar(String s) {
        return s == null ? "" : s.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private String llamar(String metodo, String ruta, String cuerpo) throws Exception {
        HttpURLConnection c = null;
        try {
            URL url = new URL("http://" + Config.HOST + ":" + Config.PORT + ruta);
            c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(20000);
            c.setRequestMethod(metodo);
            c.setRequestProperty("X-API-KEY", Config.API_KEY);
            if (cuerpo != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
                try (OutputStream os = c.getOutputStream()) {
                    os.write(cuerpo.getBytes(StandardCharsets.UTF_8));
                }
            }
            int codigo = c.getResponseCode();
            InputStream is = codigo >= 400 ? c.getErrorStream() : c.getInputStream();
            String texto = is == null ? "" : leer(is);
            if (codigo >= 400) {
                throw new ApiException(codigo, extraerError(codigo, texto));
            }
            return texto;
        } catch (ApiException e) {
            throw e;
        } catch (IOException e) {
            throw new IOException("No se pudo comunicar con el programa de escritorio ("
                    + Config.HOST + ":" + Config.PORT + ").\nVerifique que esté abierto en la PC, "
                    + "que la IP sea correcta y que el celular esté en la misma WiFi.\n(" + e.getMessage() + ")");
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String extraerError(int codigo, String texto) {
        try {
            return new JSONObject(texto).getString("error");
        } catch (JSONException e) {
            return "Error HTTP " + codigo;
        }
    }

    private static String leer(InputStream is) throws IOException {
        try (InputStream in = is; ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toString("UTF-8");
        }
    }
}
