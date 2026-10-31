package gt.edu.umg.facturaapp;

public class DetalleFactura {
    public final Producto producto;
    public int cantidad;
    public double precioUnitario;

    public DetalleFactura(Producto producto, int cantidad, double precioUnitario) {
        this.producto = producto;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
    }

    public double calcularSubtotal() {
        return cantidad * precioUnitario;
    }
}
