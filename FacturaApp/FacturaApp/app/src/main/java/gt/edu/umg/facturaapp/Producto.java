package gt.edu.umg.facturaapp;

public class Producto {
    public final int idProducto;
    public final String nombre;
    public final double precio;
    public final int existencia;

    public Producto(int idProducto, String nombre, double precio, int existencia) {
        this.idProducto = idProducto;
        this.nombre = nombre;
        this.precio = precio;
        this.existencia = existencia;
    }
}
