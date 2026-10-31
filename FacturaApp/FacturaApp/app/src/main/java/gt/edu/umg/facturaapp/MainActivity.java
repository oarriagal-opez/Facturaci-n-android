package gt.edu.umg.facturaapp;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Equivalente móvil de FrmFactura.java (escritorio). */
public class MainActivity extends AppCompatActivity {

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final FacturaRepository repo = new FacturaRepository();

    private EditText etNumeroFactura, etNit, etNombre, etDireccion, etFecha, etIdProducto, etCantidad, etPrecio;
    private Spinner spProductos;
    private TextView tvTotal;
    private RadioButton rbRecoger, rbEnvio;
    private Button btnAgregar, btnEliminar, btnGuardar;
    private DetalleAdapter adapter;

    private List<Producto> productos = new ArrayList<>();
    private final List<DetalleFactura> detalles = new ArrayList<>();
    private Cliente clienteSeleccionado;
    private Producto productoSeleccionado;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle("Facturación");

        etNumeroFactura = findViewById(R.id.etNumeroFactura);
        etNit = findViewById(R.id.etNit);
        etNombre = findViewById(R.id.etNombre);
        etDireccion = findViewById(R.id.etDireccion);
        etFecha = findViewById(R.id.etFecha);
        etIdProducto = findViewById(R.id.etIdProducto);
        etCantidad = findViewById(R.id.etCantidad);
        etPrecio = findViewById(R.id.etPrecio);
        spProductos = findViewById(R.id.spProductos);
        tvTotal = findViewById(R.id.tvTotal);
        rbRecoger = findViewById(R.id.rbRecoger);
        rbEnvio = findViewById(R.id.rbEnvio);
        btnAgregar = findViewById(R.id.btnAgregar);
        btnEliminar = findViewById(R.id.btnEliminar);
        btnGuardar = findViewById(R.id.btnGuardar);

        etFecha.setText(LocalDate.now().toString());

        adapter = new DetalleAdapter(detalles);
        RecyclerView rv = findViewById(R.id.rvDetalle);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        // Si cambia el NIT, la selección anterior ya no es válida
        etNit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { clienteSeleccionado = null; }
            @Override public void afterTextChanged(Editable s) { }
        });
        // Búsqueda al salir del campo (equivale al focusLost / Enter del escritorio)
        etNit.setOnFocusChangeListener((v, hasFocus) -> { if (!hasFocus) buscarClientePorNit(); });
        etIdProducto.setOnFocusChangeListener((v, hasFocus) -> { if (!hasFocus) buscarProductoPorId(true); });

        spProductos.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (pos > 0 && (pos - 1) < productos.size()) {
                    productoSeleccionado = productos.get(pos - 1);
                    etIdProducto.setText(String.valueOf(productoSeleccionado.idProducto));
                    etPrecio.setText(String.valueOf(productoSeleccionado.precio));
                    etCantidad.requestFocus();
                } else {
                    productoSeleccionado = null;
                    etIdProducto.setText("");
                    etPrecio.setText("");
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        btnAgregar.setOnClickListener(v -> agregarProducto());
        btnEliminar.setOnClickListener(v -> eliminarSeleccion());
        btnGuardar.setOnClickListener(v -> guardarFactura());

        cargarDatosIniciales();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    // ---------------------------------------------------------------- carga inicial

    private void cargarDatosIniciales() {
        io.execute(() -> {
            try {
                List<Producto> lista = repo.listarProductos();
                int siguiente = repo.obtenerSiguienteIdFactura();
                ui.post(() -> {
                    productos = lista;
                    llenarSpinner();
                    etNumeroFactura.setText(String.valueOf(siguiente));
                });
            } catch (Exception e) {
                ui.post(() -> mostrarError("No se pudo conectar con el programa de escritorio", e));
            }
        });
    }

    private void llenarSpinner() {
        List<String> items = new ArrayList<>();
        items.add("-- Seleccione Producto --");
        for (Producto p : productos) {
            items.add(p.idProducto + " - " + p.nombre + " (Stock: " + p.existencia + ")");
        }
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProductos.setAdapter(a);
    }

    // ---------------------------------------------------------------- búsquedas

    private void buscarClientePorNit() {
        String nit = etNit.getText().toString().trim();
        if (nit.isEmpty()) return;
        io.execute(() -> {
            try {
                Cliente c = repo.buscarClientePorNit(nit);
                ui.post(() -> {
                    // Evita aplicar un resultado viejo si el NIT cambió mientras consultaba
                    if (!nit.equals(etNit.getText().toString().trim())) return;
                    boolean habiaCliente = clienteSeleccionado != null;
                    clienteSeleccionado = c;
                    if (c != null) {
                        etNombre.setText(c.nombre);
                        etDireccion.setText(c.direccion != null ? c.direccion : "");
                    } else if (habiaCliente) {
                        etNombre.setText("");
                        etDireccion.setText("");
                    }
                });
            } catch (Exception e) {
                ui.post(() -> mostrarError("Error al buscar cliente", e));
            }
        });
    }

    private void buscarProductoPorId(boolean avisar) {
        String txt = etIdProducto.getText().toString().trim();
        if (txt.isEmpty()) return;
        int id;
        try {
            id = Integer.parseInt(txt);
        } catch (NumberFormatException e) {
            return;
        }
        for (int i = 0; i < productos.size(); i++) {
            Producto p = productos.get(i);
            if (p.idProducto == id) {
                productoSeleccionado = p;
                spProductos.setSelection(i + 1);   // dispara el listener y rellena precio
                etPrecio.setText(String.valueOf(p.precio));
                return;
            }
        }
        if (avisar) Toast.makeText(this, "Producto no encontrado.", Toast.LENGTH_SHORT).show();
        productoSeleccionado = null;
        etPrecio.setText("");
    }

    // ---------------------------------------------------------------- tabla

    private void agregarProducto() {
        if (productoSeleccionado == null) buscarProductoPorId(false);
        if (productoSeleccionado == null) {
            Toast.makeText(this, "Seleccione o busque un producto válido.", Toast.LENGTH_SHORT).show();
            return;
        }
        int cantidad;
        double precio;
        try {
            cantidad = Integer.parseInt(etCantidad.getText().toString().trim());
            precio = Double.parseDouble(etPrecio.getText().toString().trim());
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Ingrese valores numéricos válidos en Cantidad y Precio.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (cantidad <= 0) {
            Toast.makeText(this, "La cantidad debe ser mayor a 0.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (precio < 0) {
            Toast.makeText(this, "El precio no puede ser negativo.", Toast.LENGTH_SHORT).show();
            return;
        }

        DetalleFactura existente = null;
        for (DetalleFactura d : detalles) {
            if (d.producto.idProducto == productoSeleccionado.idProducto) { existente = d; break; }
        }
        int yaEnCarrito = existente != null ? existente.cantidad : 0;

        if (cantidad + yaEnCarrito > productoSeleccionado.existencia) {
            new AlertDialog.Builder(this)
                    .setTitle("Stock insuficiente")
                    .setMessage("Disponible: " + productoSeleccionado.existencia + "\nYa en carrito: " + yaEnCarrito)
                    .setPositiveButton("OK", null).show();
            return;
        }

        if (existente != null) {
            existente.cantidad += cantidad;
            existente.precioUnitario = precio;
        } else {
            detalles.add(new DetalleFactura(productoSeleccionado, cantidad, precio));
        }
        adapter.limpiarSeleccion();
        adapter.notifyDataSetChanged();
        calcularTotal();
        limpiarCamposProducto();
    }

    private void eliminarSeleccion() {
        int pos = adapter.getSeleccionada();
        if (pos < 0 || pos >= detalles.size()) {
            Toast.makeText(this, "Toque un producto de la tabla para seleccionarlo.", Toast.LENGTH_SHORT).show();
            return;
        }
        detalles.remove(pos);
        adapter.limpiarSeleccion();
        adapter.notifyDataSetChanged();
        calcularTotal();
    }

    private void calcularTotal() {
        double total = 0;
        for (DetalleFactura d : detalles) total += d.calcularSubtotal();
        tvTotal.setText(String.format(Locale.US, "%.2f", total));
    }

    private void limpiarCamposProducto() {
        etIdProducto.setText("");
        spProductos.setSelection(0);
        etCantidad.setText("");
        etPrecio.setText("");
        productoSeleccionado = null;
        etIdProducto.requestFocus();
    }

    // ---------------------------------------------------------------- guardar

    private void guardarFactura() {
        if (detalles.isEmpty()) {
            Toast.makeText(this, "Debe agregar al menos un producto al detalle.", Toast.LENGTH_SHORT).show();
            return;
        }

        String nitTxt = etNit.getText().toString().trim();
        String nombreTxt = etNombre.getText().toString().trim();
        final String nit = nitTxt.isEmpty() ? "C/F" : nitTxt;
        final String nombre = nombreTxt.isEmpty() ? "Consumidor Final" : nombreTxt;
        final String direccion = etDireccion.getText().toString().trim();

        final String entrega, tipoEntrega;
        if (rbRecoger.isChecked()) { entrega = "RECOGER"; tipoEntrega = "Recoger en tienda"; }
        else if (rbEnvio.isChecked()) { entrega = "ENVIO"; tipoEntrega = "Envío a domicilio"; }
        else { entrega = "TIENDA"; tipoEntrega = "Venta en tienda"; }

        // Copia del carrito para enviar sin riesgo de que cambie mientras se guarda
        final List<DetalleFactura> copia = new ArrayList<>();
        double t = 0;
        for (DetalleFactura d : detalles) {
            copia.add(new DetalleFactura(d.producto, d.cantidad, d.precioUnitario));
            t += d.calcularSubtotal();
        }
        final double total = t;

        btnGuardar.setEnabled(false);
        io.execute(() -> {
            try {
                int idFactura = repo.guardarFactura(nit, nombre, direccion, entrega, copia);
                ui.post(() -> {
                    btnGuardar.setEnabled(true);
                    new AlertDialog.Builder(this)
                            .setTitle("Factura guardada")
                            .setMessage("Factura No. " + idFactura
                                    + "\nCliente: " + nombre
                                    + "\nNIT: " + nit
                                    + "\nTipo entrega: " + tipoEntrega
                                    + "\nTotal: Q" + String.format(Locale.US, "%.2f", total))
                            .setPositiveButton("OK", null).show();
                    limpiarFormularioCompleto();
                });
            } catch (Exception e) {
                ui.post(() -> {
                    btnGuardar.setEnabled(true);
                    mostrarError("No se pudo guardar la factura", e);
                });
            }
        });
    }

    private void limpiarFormularioCompleto() {
        etNit.setText("");
        etNombre.setText("");
        etDireccion.setText("");
        etFecha.setText(LocalDate.now().toString());
        clienteSeleccionado = null;
        detalles.clear();
        adapter.limpiarSeleccion();
        adapter.notifyDataSetChanged();
        tvTotal.setText("0.00");
        limpiarCamposProducto();
        cargarDatosIniciales();   // refresca stock y número de factura
    }

    private void mostrarError(String titulo, Exception e) {
        new AlertDialog.Builder(this)
                .setTitle(titulo)
                .setMessage(String.valueOf(e.getMessage()))
                .setPositiveButton("OK", null).show();
    }
}
